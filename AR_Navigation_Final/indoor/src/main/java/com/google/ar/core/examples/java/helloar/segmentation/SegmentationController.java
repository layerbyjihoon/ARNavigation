package com.google.ar.core.examples.java.helloar.segmentation;

import android.content.res.AssetManager;
import android.media.Image;
import com.google.ar.core.*;
import com.google.ar.core.examples.java.helloar.localization.*;
import java.nio.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Copies the current AR frame once, closes images immediately, and permits one inference in flight. */
public final class SegmentationController implements AutoCloseable {
  public static final class Completed {
    public final SegmentationResult segmentation;
    public final List<LandmarkObservation> observations;
    public final List<float[]> wallBoundaries;
    public final MatchResult match;
    public final int accumulatedCount;
    public final Double corridorYaw;
    public final int confirmedTracks,bearingTracks;
    public final FrameSnapshot trackingFrame;
    public final int generation;
    Completed(SegmentationResult result,List<LandmarkObservation> observations,MatchResult match,TemporalSemanticLocalizer.Result localized,
        RouteBearingMatcher.Result bearing,int generation,List<float[]> boundaries,FrameSnapshot trackingFrame) {
      segmentation=result;this.observations=Collections.unmodifiableList(observations);this.match=match;this.generation=generation;
      wallBoundaries=boundaries;accumulatedCount=localized==null?0:localized.accumulated.size();corridorYaw=localized==null?null:localized.corridorYaw;
      int confirmed=0;for(LandmarkObservation ob:observations)if(ob.stable)confirmed++;
      confirmedTracks=confirmed;bearingTracks=bearing.tracks;this.trackingFrame=trackingFrame;
    }
  }
  private final ExecutorService worker=Executors.newSingleThreadExecutor();
  private final AtomicBoolean busy=new AtomicBoolean();
  private final AtomicInteger generation=new AtomicInteger();
  private final AtomicReference<Completed> pending=new AtomicReference<>();
  private final LandmarkExtractor extractor=new LandmarkExtractor();
  private final LandmarkPositionEstimator estimator=new LandmarkPositionEstimator();
  private final TemporalSemanticLocalizer localizer=new TemporalSemanticLocalizer();
  private final WallStructureExtractor wallExtractor=new WallStructureExtractor();
  private final CorridorDirectionEstimator directionEstimator=new CorridorDirectionEstimator();
  private final FeatureLandmarkTracker visualTracker=new FeatureLandmarkTracker();
  private final RouteBearingMatcher bearingMatcher=new RouteBearingMatcher();
  private SegmentationResult lastSegmentation;
  private List<float[]> lastBoundaries=Collections.emptyList();
  private TemporalSemanticLocalizer.Result lastLocalized;
  private SegFormerRunner runner;
  private volatile boolean ready,closed,enabled=true;
  private volatile String state="모델 로딩 중";
  private volatile Completed latest;
  private volatile SemanticLandmarkMap map;
  private int workerGeneration=-1;
  private volatile long lastSubmitted;
  private volatile long lastInferenceSubmitted;
  private volatile long intervalNanos=500_000_000L;

  public SegmentationController(AssetManager assets) {
    worker.execute(()->{
      try {runner=new SegFormerRunner(assets);ready=true;state="SegFormer 준비 완료";}
      catch(Exception|OutOfMemoryError e){state="모델 비활성화: "+e.getClass().getSimpleName()+" "+e.getMessage();}
    });
  }
  public void setEnabled(boolean value){enabled=value;invalidate();}
  public boolean isEnabled(){return enabled;}
  public void setIntervalMillis(long millis){intervalNanos=Math.max(500,millis)*1_000_000L;}
  public void setMap(SemanticLandmarkMap map){this.map=map;invalidate();}
  public SemanticLandmarkMap map(){return map;}
  public String state(){return state;}
  public Completed latest(){return latest;}
  public Completed takeCompleted(){Completed result=pending.getAndSet(null);return result!=null&&result.generation==generation.get()?result:null;}
  public void invalidate(){generation.incrementAndGet();pending.set(null);latest=null;lastSubmitted=lastInferenceSubmitted=0;}

  public void onFrame(Frame frame,Pose origin,boolean tracking,Rigid2D currentCorrection) {
    long now=android.os.SystemClock.elapsedRealtimeNanos();
    if(closed||!enabled||!ready||now-lastSubmitted<100_000_000L||!busy.compareAndSet(false,true))return;
    final boolean runInference=now-lastInferenceSubmitted>=intervalNanos;
    final CameraFramePreprocessor.Yuv yuv;
    final FrameSnapshot snapshot;
    try(Image image=frame.acquireCameraImage()) {
      if(image.getFormat()!=android.graphics.ImageFormat.YUV_420_888)throw new IllegalArgumentException("Expected YUV420");
      Image.Plane[] planes=image.getPlanes();
      yuv=new CameraFramePreprocessor.Yuv(image.getWidth(),image.getHeight(),copy(planes[0]),copy(planes[1]),copy(planes[2]));
      Camera camera=frame.getCamera();
      float[] world=new float[16],local=origin==null?null:new float[16];
      camera.getPose().toMatrix(world,0);if(origin!=null)origin.inverse().toMatrix(local,0);
      CameraIntrinsics intrinsics=camera.getImageIntrinsics();
      float[] focal=intrinsics.getFocalLength(),principal=intrinsics.getPrincipalPoint();
      float[] corners={0,0,1,0,0,1},view=new float[6],depthUv=new float[6];
      frame.transformCoordinates2d(Coordinates2d.IMAGE_NORMALIZED,corners,Coordinates2d.VIEW_NORMALIZED,view);
      frame.transformCoordinates2d(Coordinates2d.IMAGE_NORMALIZED,corners,Coordinates2d.TEXTURE_NORMALIZED,depthUv);
      snapshot=new FrameSnapshot(image.getTimestamp(),image.getWidth(),image.getHeight(),rotation(view),world,local,
          new float[]{focal[0],focal[1],principal[0],principal[1]},view,depthUv,runInference?copyDepth(frame):null,tracking);
      lastSubmitted=now;
      if(runInference)lastInferenceSubmitted=now;
    } catch(Exception e) {busy.set(false);return;}
    int request=generation.get();SemanticLandmarkMap capturedMap=map;
    try {worker.execute(()->{
      try {
        if(closed||request!=generation.get())return;
        if(workerGeneration!=request){extractor.reset();localizer.reset();visualTracker.reset();bearingMatcher.reset();lastSegmentation=null;lastLocalized=null;lastBoundaries=Collections.emptyList();workerGeneration=request;}
        List<LandmarkObservation> detected=null;
        if(runInference||lastSegmentation==null){lastSegmentation=runner.infer(yuv,snapshot);detected=extractor.extract(lastSegmentation);lastBoundaries=wallExtractor.extract(lastSegmentation);}
        List<LandmarkObservation> tracked=visualTracker.update(TrackingImage.fromYuv(yuv,snapshot),detected);
        List<LandmarkObservation> observations=new ArrayList<>();
        for(LandmarkObservation observation:tracked)observations.add(detected==null?observation:estimator.estimate(observation,lastSegmentation));
        RouteBearingMatcher.Result bearing;
        MatchResult metric=null;
        if(!snapshot.tracking||snapshot.worldToRoute==null){localizer.reset();lastLocalized=null;bearing=bearingMatcher.update(capturedMap,observations,0,0,currentCorrection,snapshot.timestamp,false);}
        else {
          float[] cameraLocal=FrameSnapshot.transform(snapshot.worldToRoute,snapshot.cameraToWorld[12],snapshot.cameraToWorld[13],snapshot.cameraToWorld[14]);
          bearing=bearingMatcher.update(capturedMap,observations,cameraLocal[0],cameraLocal[2],currentCorrection,snapshot.timestamp,true);
          if(detected!=null){
            double[] predicted=currentCorrection.inverse(cameraLocal[0],cameraLocal[2]);
            Double expected=capturedMap==null?null:capturedMap.corridorYaw(predicted[0],predicted[1]);
            CorridorDirectionEstimator.Observation direction=expected==null?null:directionEstimator.estimate(lastSegmentation,lastBoundaries,expected+currentCorrection.yaw);
            lastLocalized=localizer.update(capturedMap,observations,direction,cameraLocal[0],cameraLocal[2],currentCorrection,snapshot.timestamp,true);metric=lastLocalized.match;
          }
        }
        MatchResult match=SemanticFusion.choose(metric,bearing.match,snapshot.timestamp);
        Completed completed=new Completed(lastSegmentation,observations,match,lastLocalized,bearing,request,lastBoundaries,snapshot);
        if(!closed&&request==generation.get()){latest=completed;pending.set(completed);state="SegFormer 실행 중";}
      } catch(Exception|OutOfMemoryError e){ready=false;state="추론 비활성화: "+e.getClass().getSimpleName()+" "+e.getMessage();}
      finally {busy.set(false);}
    });}catch(RejectedExecutionException e){busy.set(false);}
  }

  private static CameraFramePreprocessor.Plane copy(Image.Plane plane) {
    ByteBuffer buffer=plane.getBuffer().duplicate();byte[] bytes=new byte[buffer.remaining()];buffer.get(bytes);
    return new CameraFramePreprocessor.Plane(bytes,plane.getRowStride(),plane.getPixelStride());
  }
  private static FrameSnapshot.Depth copyDepth(Frame frame) {
    try(Image depth=frame.acquireDepthImage16Bits()) {
      Image.Plane plane=depth.getPlanes()[0];ByteBuffer bytes=plane.getBuffer().duplicate().order(ByteOrder.LITTLE_ENDIAN);
      short[] mm=new short[depth.getWidth()*depth.getHeight()];int start=bytes.position();
      for(int y=0;y<depth.getHeight();y++)for(int x=0;x<depth.getWidth();x++)
        mm[y*depth.getWidth()+x]=bytes.getShort(start+y*plane.getRowStride()+x*plane.getPixelStride());
      return new FrameSnapshot.Depth(depth.getWidth(),depth.getHeight(),depth.getTimestamp(),mm);
    } catch(Exception e){return null;}
  }
  private static int rotation(float[] view) {
    double best=-Double.MAX_VALUE;int selected=0;
    for(int degrees:new int[]{0,90,180,270}) {
      float[] a=FrameSnapshot.inputToImage(0,0,degrees),b=FrameSnapshot.inputToImage(1,0,degrees),c=FrameSnapshot.inputToImage(0,1,degrees);
      a=FrameSnapshot.affine(view,a[0],a[1]);b=FrameSnapshot.affine(view,b[0],b[1]);c=FrameSnapshot.affine(view,c[0],c[1]);
      double score=(b[0]-a[0])/Math.max(1e-6,Math.hypot(b[0]-a[0],b[1]-a[1]))
          +(c[1]-a[1])/Math.max(1e-6,Math.hypot(c[0]-a[0],c[1]-a[1]));
      if(score>best){best=score;selected=degrees;}
    }
    return selected;
  }
  @Override public synchronized void close() {
    if(closed)return;
    closed=true;invalidate();
    worker.execute(()->{if(runner!=null)try{runner.close();}catch(Exception ignored){}});worker.shutdown();
  }
}
