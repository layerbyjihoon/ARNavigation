package com.google.ar.core.examples.java.helloar.localization;

import java.util.*;

/** Stable IDs from actual image features; low confidence detections remain tentative. */
public final class FeatureLandmarkTracker {
  private final SparseFeatureFlow flow=new SparseFeatureFlow();
  private final List<Track> tracks=new ArrayList<>();private TrackingImage previous;private long nextId=1;
  public void reset(){tracks.clear();previous=null;nextId=1;}
  public List<LandmarkObservation> update(TrackingImage image,List<LandmarkObservation> detections){
    long time=image.frame.timestamp;
    if(previous!=null&&(time<=previous.frame.timestamp||time-previous.frame.timestamp>800_000_000L
        ||image.width!=previous.width||image.height!=previous.height||image.frame.rotationDegrees!=previous.frame.rotationDegrees))reset();
    for(Track track:tracks){
      track.detected=false;track.visible=false;
      if(previous==null)continue;
      SparseFeatureFlow.Motion motion=flow.motion(previous,image,track.points);
      if(motion==null){track.goodFlows=0;continue;}
      float left=Float.POSITIVE_INFINITY,top=left,right=Float.NEGATIVE_INFINITY,bottom=right;
      for(float[] corner:new float[][]{{track.left,track.top},{track.right,track.top},{track.left,track.bottom},{track.right,track.bottom}}){
        float[] point=motion.apply(corner[0]*image.width,corner[1]*image.height);
        left=Math.min(left,point[0]/image.width);right=Math.max(right,point[0]/image.width);top=Math.min(top,point[1]/image.height);bottom=Math.max(bottom,point[1]/image.height);
      }
      if(left<-.05||right>1.05||top<-.05||bottom>1.05||right-left<.015||bottom-top<.02){track.goodFlows=0;continue;}
      track.left=Math.max(0,left);track.top=Math.max(0,top);track.right=Math.min(1,right);track.bottom=Math.min(1,bottom);
      track.points=motion.points;track.visible=true;track.lastGood=time;track.goodFlows++;
    }
    if(detections!=null){Set<Track> used=Collections.newSetFromMap(new IdentityHashMap<>());
      for(LandmarkObservation detection:detections){
        if((!detection.type.equals("DOOR")&&!detection.type.equals("STAIRS"))||detection.confidence<.45f)continue;
        float[] appearance=histogram(image,detection);
        Track best=null;double bestScore=.2;
        for(Track track:tracks)if(!used.contains(track)&&track.type.equals(detection.type)&&time-track.lastGood<700_000_000L){
          double overlap=iou(track,detection);
          if(!track.visible&&(overlap<.5||histogramDistance(track.appearance,appearance)>.5))continue;
          if(overlap>bestScore){bestScore=overlap;best=track;}
        }
        if(best==null){best=new Track(nextId++,detection);tracks.add(best);}else{
          best.hits++;best.confidence=.5f*(best.confidence+detection.confidence);
          best.left=detection.left;best.top=detection.top;best.right=detection.right;best.bottom=detection.bottom;
        }
        best.lastDetection=time;best.lastGood=time;best.detected=true;best.visible=true;best.area=detection.area;best.classId=detection.classId;
        best.appearance=appearance;
        best.points=flow.corners(image,best.left,best.top,best.right,best.bottom);used.add(best);
      }
    }
    tracks.removeIf(t->time-t.lastDetection>1_500_000_000L||time-t.lastGood>600_000_000L);
    tracks.sort(Comparator.comparingDouble((Track t)->-(t.confirmed()?2:0)-t.confidence));
    if(tracks.size()>12)tracks.subList(12,tracks.size()).clear();
    List<LandmarkObservation> result=new ArrayList<>();
    for(Track track:tracks)if(track.visible){
      float confidence=track.confidence*(float)Math.exp(-(time-track.lastDetection)/2e9);
      result.add(new LandmarkObservation(track.type,track.left,track.top,track.right,track.bottom,track.area,track.classId,
          confidence,image.frame,track.confirmed(),null,null,LandmarkObservation.PositionSource.NONE,track.id,track.detected));
    }
    previous=image;return result;
  }
  private double iou(Track a,LandmarkObservation b){double intersection=Math.max(0,Math.min(a.right,b.right)-Math.max(a.left,b.left))*Math.max(0,Math.min(a.bottom,b.bottom)-Math.max(a.top,b.top));
    double union=(a.right-a.left)*(a.bottom-a.top)+(b.right-b.left)*(b.bottom-b.top)-intersection;return union<=0?0:intersection/union;}
  private float[] histogram(TrackingImage image,LandmarkObservation ob){float[] bins=new float[16];int count=0;
    for(int y=Math.max(0,(int)(ob.top*image.height));y<Math.min(image.height,(int)(ob.bottom*image.height));y+=3)
      for(int x=Math.max(0,(int)(ob.left*image.width));x<Math.min(image.width,(int)(ob.right*image.width));x+=3){bins[Math.min(15,(int)image.pyramid[0][y*image.width+x]/16)]++;count++;}
    if(count>0)for(int i=0;i<bins.length;i++)bins[i]/=count;return bins;
  }
  private double histogramDistance(float[] a,float[] b){if(a==null)return Double.POSITIVE_INFINITY;double distance=0;for(int i=0;i<a.length;i++)distance+=Math.abs(a[i]-b[i]);return distance;}
  private static final class Track {
    final long id;final String type;final long first;long lastDetection,lastGood;int hits=1,goodFlows,area,classId;float left,top,right,bottom,confidence;boolean visible,detected;
    List<float[]> points=Collections.emptyList();
    float[] appearance;
    Track(long id,LandmarkObservation ob){this.id=id;type=ob.type;first=lastDetection=lastGood=ob.timestamp;left=ob.left;top=ob.top;right=ob.right;bottom=ob.bottom;confidence=ob.confidence;}
    boolean confirmed(){return goodFlows>=2&&hits>=2&&confidence>=.6f||goodFlows>=4&&confidence>=.85f;}
  }
}
