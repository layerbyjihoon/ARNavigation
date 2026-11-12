package com.google.ar.core.examples.java.helloar.localization;

import static org.junit.Assert.*;
import com.google.ar.core.examples.java.helloar.segmentation.*;
import java.nio.FloatBuffer;
import java.util.*;
import org.junit.Test;

public final class SemanticLocalizationTest {
  private static float[] identity(){return new float[]{1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1};}
  private FrameSnapshot frame(long time,FrameSnapshot.Depth depth){return new FrameSnapshot(time,16,16,0,identity(),identity(),
      new float[]{100,100,8,8},new float[]{0,0,1,0,0,1},new float[]{0,0,1,0,0,1},depth,true);}

  @Test public void yuvStridesPixelStridesNormalizationAndRotationAreHandled() {
    byte[] y=new byte[]{0,127,0,0,(byte)255,64,0,0};
    CameraFramePreprocessor.Yuv image=new CameraFramePreprocessor.Yuv(2,2,
        new CameraFramePreprocessor.Plane(y,4,1),new CameraFramePreprocessor.Plane(new byte[]{(byte)128,0},2,2),
        new CameraFramePreprocessor.Plane(new byte[]{(byte)128,0},2,2));
    FloatBuffer output=FloatBuffer.allocate(12);
    CameraFramePreprocessor.writeNchw(image,0,2,2,new float[]{0,0,0},new float[]{1,1,1},output);
    assertEquals(0,output.get(0),1e-6);assertEquals(1,output.get(2),1e-6);
    CameraFramePreprocessor.writeNchw(image,90,2,2,new float[]{0,0,0},new float[]{1,1,1},output);
    assertEquals(1,output.get(0),1e-6);assertEquals(0,output.get(1),1e-6);
    assertArrayEquals(new float[]{.7f,.8f},FrameSnapshot.inputToImage(.2f,.7f,90),1e-6f);
  }

  @Test public void separateDoorComponentsNeedTemporalConsistency() {
    byte[] mask=new byte[32*32];float[] confidence=new float[mask.length];Arrays.fill(confidence,.9f);
    for(int y=4;y<12;y++)for(int x=3;x<7;x++)mask[y*32+x]=1;
    for(int y=4;y<12;y++)for(int x=18;x<22;x++)mask[y*32+x]=1;
    LandmarkExtractor extractor=new LandmarkExtractor();
    List<LandmarkObservation> first=extractor.extract(new SegmentationResult(frame(1_000_000_000L,null),32,32,mask,confidence,new String[]{"floor","door"},1));
    assertEquals(2,first.size());assertFalse(first.get(0).stable);
    List<LandmarkObservation> second=extractor.extract(new SegmentationResult(frame(1_500_000_000L,null),32,32,mask,confidence,new String[]{"floor","door"},1));
    assertTrue(second.get(0).stable);assertTrue(second.get(1).stable);
  }

  @Test public void wallFloorBoundaryPreservesCorridorCornerAndRejectsMissingFloor() {
    int w=64,h=64;byte[] mask=new byte[w*h];float[] confidence=new float[w*h];Arrays.fill(confidence,.95f);
    for(int x=0;x<w;x++){int edge=16+Math.abs(x-32)/2;for(int y=edge;y<h;y++)mask[y*w+x]=1;}
    WallStructureExtractor extractor=new WallStructureExtractor();
    List<float[]> lines=extractor.extract(new SegmentationResult(frame(1,null),w,h,mask,confidence,new String[]{"wall","floor"},1));
    assertEquals(1,lines.size());assertTrue("Corner must survive simplification",lines.get(0).length>=6);
    assertEquals(.25,lines.get(0)[3],.04);
    assertTrue(extractor.extract(new SegmentationResult(frame(1,null),w,h,mask,confidence,new String[]{"wall","door"},1)).isEmpty());
  }

  @Test public void noDepthAndStaleDepthNeverProduceWorldCoordinates() {
    byte[] mask=new byte[16*16];float[] confidence=new float[mask.length];Arrays.fill(confidence,.9f);
    FrameSnapshot f=frame(1_000_000_000L,null);
    LandmarkObservation ob=observation("DOOR",f,null);
    LandmarkPositionEstimator estimator=new LandmarkPositionEstimator();
    assertFalse(estimator.estimate(ob,new SegmentationResult(f,16,16,mask,confidence,new String[]{"door"},1)).depthValid);
    short[] mm=new short[16*16];Arrays.fill(mm,(short)2000);
    FrameSnapshot good=frame(1_000_000_000L,new FrameSnapshot.Depth(16,16,1_000_000_000L,mm));
    LandmarkObservation positioned=estimator.estimate(observation("DOOR",good,null),
        new SegmentationResult(good,16,16,mask,confidence,new String[]{"door"},1));
    assertTrue(positioned.depthValid);assertEquals(-2,positioned.worldPosition[2],1e-5);
    FrameSnapshot stale=frame(1_000_000_000L,new FrameSnapshot.Depth(16,16,1,mm));
    assertFalse(estimator.estimate(observation("DOOR",stale,null),new SegmentationResult(stale,16,16,mask,confidence,new String[]{"door"},1)).depthValid);
  }

  private LandmarkObservation observation(String type,FrameSnapshot f,float[] route) {
    return new LandmarkObservation(type,.3f,.3f,.7f,.7f,64,0,.9f,f,true,route,route);
  }
  private List<SemanticLandmarkMap.Landmark> landmarks(boolean verified) {
    return Arrays.asList(new SemanticLandmarkMap.Landmark("A","DOOR",0,-2,verified),
        new SemanticLandmarkMap.Landmark("B","DOOR",2,-3,verified),new SemanticLandmarkMap.Landmark("C","STAIRS",-1,-5,verified));
  }
  private List<LandmarkObservation> observations(List<SemanticLandmarkMap.Landmark> points,Rigid2D error,long time) {
    List<LandmarkObservation> result=new ArrayList<>();
    for(SemanticLandmarkMap.Landmark p:points){double[] v=error.apply(p.x,p.z);result.add(observation(p.type,frame(time,null),new float[]{(float)v[0],0,(float)v[1]}));}
    return result;
  }

  @Test public void recoversTranslationAndYawOnlyAfterThreeDistinctConsistentFrames() {
    List<SemanticLandmarkMap.Landmark> points=landmarks(true);
    SemanticMapMatcher matcher=new SemanticMapMatcher();Rigid2D error=new Rigid2D(Math.toRadians(3),.2,-.15);
    MatchResult result=null;
    for(int i=0;i<3;i++) {
      long time=1_000_000_000L+i*500_000_000L;
      result=matcher.match(new SemanticLandmarkMap("F1",points),observations(points,error,time),0,0,Rigid2D.identity(),time);
      assertEquals(i<2?MatchResult.Status.LOW_CONFIDENCE:MatchResult.Status.MATCH_CONFIRMED,result.status);
    }
    assertEquals(error.x,result.transform.x,1e-5);assertEquals(error.z,result.transform.z,1e-5);assertEquals(error.yaw,result.transform.yaw,1e-5);
  }

  @Test public void unverifiedMapMissingDepthWrongTypesAndLargeJumpsCannotCorrect() {
    List<SemanticLandmarkMap.Landmark> points=landmarks(true);SemanticMapMatcher matcher=new SemanticMapMatcher();long time=1_000_000_000L;
    assertEquals(MatchResult.Status.LOW_CONFIDENCE,matcher.match(new SemanticLandmarkMap("F1",landmarks(false)),
        observations(points,Rigid2D.identity(),time),0,0,Rigid2D.identity(),time).status);
    assertEquals(MatchResult.Status.NO_MATCH,matcher.match(new SemanticLandmarkMap("F1",points),Collections.emptyList(),0,0,Rigid2D.identity(),time).status);
    assertEquals(MatchResult.Status.NO_MATCH,matcher.match(new SemanticLandmarkMap("F1",points),
        observations(points,new Rigid2D(0,5,0),time),0,0,Rigid2D.identity(),time).status);
    List<LandmarkObservation> wrong=observations(points,Rigid2D.identity(),time);
    wrong.set(2,observation("COLUMN",frame(time,null),new float[]{-1,0,-5}));
    assertNotEquals(MatchResult.Status.MATCH_CONFIRMED,matcher.match(new SemanticLandmarkMap("F1",points),wrong,0,0,Rigid2D.identity(),time).status);
  }

  @Test public void repeatedCorridorLayoutsRemainAmbiguous() {
    List<SemanticLandmarkMap.Landmark> map=new ArrayList<>();
    for(int i=0;i<5;i++)map.add(new SemanticLandmarkMap.Landmark("D"+i,"DOOR",0,-i,true));
    List<LandmarkObservation> observations=observations(map.subList(1,4),Rigid2D.identity(),1_000_000_000L);
    MatchResult result=new SemanticMapMatcher().match(new SemanticLandmarkMap("F1",map),observations,0,0,Rigid2D.identity(),1_000_000_000L);
    assertEquals(MatchResult.Status.AMBIGUOUS,result.status);
  }

  @Test public void managerSmoothsConfirmedFreshChangesAndFreezesWhenTrackingIsLost() {
    RouteAlignmentManager manager=new RouteAlignmentManager();long time=1_000_000_000L;
    assertFalse(manager.accept(MatchResult.withheld(MatchResult.Status.AMBIGUOUS,"repeat",time),time));
    assertTrue(manager.accept(new MatchResult(MatchResult.Status.MATCH_CONFIRMED,"test",new Rigid2D(.03,.3,0),3,1,.01,time),time));
    manager.tick(time,true);manager.tick(time+100_000_000L,true);
    assertTrue(manager.current().x>0&&manager.current().x<.02);
    double x=manager.current().x;manager.tick(time+200_000_000L,false);assertEquals(x,manager.current().x,0);
    assertFalse(manager.accept(new MatchResult(MatchResult.Status.MATCH_CONFIRMED,"stale",new Rigid2D(0,.3,0),3,1,0,time),time+3_000_000_000L));
  }
}
