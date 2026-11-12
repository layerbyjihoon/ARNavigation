package com.google.ar.core.examples.java.helloar.localization;

import static org.junit.Assert.*;
import com.google.ar.core.examples.java.helloar.segmentation.*;
import java.util.*;
import org.junit.Test;

public final class TemporalLocalizationTest {
  private float[] identity(){return new float[]{1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1};}
  private FrameSnapshot frame(long time,float x,float y){float[] pose=identity();pose[12]=x;pose[13]=y;
    return new FrameSnapshot(time,512,512,0,pose,identity(),new float[]{400,400,256,256},new float[]{0,0,1,0,0,1},new float[]{0,0,1,0,0,1},null,true);}
  private LandmarkObservation ob(long time,String type,float x,float z){float[] p={x,0,z};return new LandmarkObservation(type,.4f,.3f,.5f,.6f,64,0,.95f,frame(time,0,0),true,p,p);}
  private SemanticLandmarkMap map(){return new SemanticLandmarkMap("F1",Arrays.asList(
      new SemanticLandmarkMap.Landmark("A","DOOR",1,-2,true),new SemanticLandmarkMap.Landmark("B","STAIRS",-1,-4,true),
      new SemanticLandmarkMap.Landmark("C","DOOR",1,-6,true)),Collections.singletonList(new SemanticLandmarkMap.Corridor(0,0,0,-20)));}

  @Test public void accumulatesDifferentDoorsButNeverCountsRepeatedDoorAsThree(){
    TemporalLandmarkAccumulator accumulator=new TemporalLandmarkAccumulator();long start=1_000_000_000L;
    for(int i=0;i<4;i++)assertEquals(1,accumulator.update(Collections.singletonList(ob(start+i*500_000_000L,"DOOR",1,-2)),start+i*500_000_000L).size());
    assertEquals(2,accumulator.update(Collections.singletonList(ob(start+2_000_000_000L,"DOOR",1,-4)),start+2_000_000_000L).size());
    assertEquals(3,accumulator.update(Collections.singletonList(ob(start+2_500_000_000L,"STAIRS",-1,-6)),start+2_500_000_000L).size());
  }
  @Test public void noFreshEvidenceCannotConfirmOldTracksAndCaptureGapsClearThem(){
    TemporalSemanticLocalizer localizer=new TemporalSemanticLocalizer();long t=1_000_000_000L;
    for(int i=0;i<3;i++)localizer.update(map(),Collections.singletonList(ob(t+i*500_000_000L,i==1?"STAIRS":"DOOR",i==1?-1:1,-2-2*i)),null,0,0,Rigid2D.identity(),t+i*500_000_000L,true);
    TemporalSemanticLocalizer.Result idle=localizer.update(map(),Collections.emptyList(),null,0,0,Rigid2D.identity(),t+1_500_000_000L,true);
    assertNotEquals(MatchResult.Status.MATCH_CONFIRMED,idle.match.status);assertEquals(3,idle.accumulated.size());
    assertEquals(0,localizer.update(map(),Collections.emptyList(),null,0,0,Rigid2D.identity(),t+5_000_000_000L,true).accumulated.size());
  }
  @Test public void confirmsTranslationFromOneDifferentLandmarkPerFrame(){
    TemporalSemanticLocalizer localizer=new TemporalSemanticLocalizer();TemporalSemanticLocalizer.Result result=null;
    for(int i=0;i<5;i++){long t=1_000_000_000L+i*500_000_000L;int which=i%3;
      result=localizer.update(map(),Collections.singletonList(ob(t,which==1?"STAIRS":"DOOR",which==1?-.9f:1.1f,-2-2*which)),null,0,0,Rigid2D.identity(),t,true);
    }
    assertEquals(MatchResult.Status.MATCH_CONFIRMED,result.match.status);assertEquals(3,result.match.inliers);
    assertEquals(.1,result.match.transform.x,.01);
  }
  @Test public void stationaryBearingNeverInventsDepthButParallaxTriangulatesThreeViews(){
    TemporalLandmarkAccumulator stationary=new TemporalLandmarkAccumulator(),moving=new TemporalLandmarkAccumulator();
    List<LandmarkObservation> result=Collections.emptyList();
    for(int i=0;i<4;i++){
      long time=1_000_000_000L+i*500_000_000L;float x=i*.3f,center=.5f-x/3*400/512;
      LandmarkObservation still=new LandmarkObservation("DOOR",.47f,.47f,.53f,.53f,64,0,.95f,frame(time,0,0),true,null,null);
      assertTrue(stationary.update(Collections.singletonList(still),time).isEmpty());
      LandmarkObservation ray=new LandmarkObservation("DOOR",center-.03f,.47f,center+.03f,.53f,64,0,.95f,frame(time,x,0),true,null,null);
      result=moving.update(Collections.singletonList(ray),time);
    }
    assertEquals(1,result.size());assertTrue(result.get(0).metricValid);assertFalse(result.get(0).depthValid);
    assertEquals(LandmarkObservation.PositionSource.TRIANGULATED,result.get(0).positionSource);
    assertEquals(-3,result.get(0).routePosition[2],.01);
  }
  @Test public void twoViewsFollowedByStationaryFramesCannotFakeThirdTriangulationView(){
    TemporalLandmarkAccumulator accumulator=new TemporalLandmarkAccumulator();
    for(int i=0;i<4;i++){
      long time=1_000_000_000L+i*500_000_000L;float x=i==0?0:.6f,center=.5f-x/3*400/512;
      LandmarkObservation ray=new LandmarkObservation("DOOR",center-.03f,.47f,center+.03f,.53f,64,0,.95f,frame(time,x,0),true,null,null);
      assertTrue(accumulator.update(Collections.singletonList(ray),time).isEmpty());
    }
  }
  @Test public void oldLandmarksExpireEvenWhileCameraFramesContinue(){
    TemporalLandmarkAccumulator accumulator=new TemporalLandmarkAccumulator();long start=1_000_000_000L;
    accumulator.update(Collections.singletonList(ob(start,"DOOR",1,-2)),start);
    List<LandmarkObservation> result=null;
    for(int i=1;i<=27;i++)result=accumulator.update(Collections.emptyList(),start+i*500_000_000L);
    assertNotNull(result);assertTrue(result.isEmpty());assertEquals(0,accumulator.freshCount());
  }
  @Test public void wallFloorProjectionRecoversWorldCorridorDirection(){
    FrameSnapshot frame=frame(1_000_000_000L,0,1.5f);
    SegmentationResult segmentation=new SegmentationResult(frame,128,128,new byte[128*128],new float[128*128],new String[]{"wall","floor"},0);
    // Projections of (x=1,y=0,z=-3) and (1,0,-7), not raw image-line angles.
    float[] line={.5f+400f/3/512,.5f+600f/3/512,.5f+400f/7/512,.5f+600f/7/512};
    CorridorDirectionEstimator.Observation axis=new CorridorDirectionEstimator(0).estimate(segmentation,Collections.singletonList(line),0);
    assertNotNull(axis);assertEquals(0,Math.abs(CorridorDirectionEstimator.axisDifference(axis.yaw,0)),1e-5);
    assertTrue(axis.length>3.9);
  }
  @Test public void wallYawNeedsThreeFramesAndKeepsCameraPositionAtItsPivot(){
    TemporalSemanticLocalizer localizer=new TemporalSemanticLocalizer();TemporalSemanticLocalizer.Result result=null;
    for(int i=0;i<3;i++){long t=1_000_000_000L+i*500_000_000L;
      result=localizer.update(map(),Collections.emptyList(),new CorridorDirectionEstimator.Observation(.03,.9,4,t),0,-3,Rigid2D.identity(),t,true);
      assertEquals(i<2?MatchResult.Status.NO_MATCH:MatchResult.Status.MATCH_CONFIRMED,result.match.status);
    }
    assertEquals("CORRIDOR_YAW_ONLY_CAMERA_PIVOT",result.match.reason);
    double[] position=result.match.transform.inverse(0,-3);assertArrayEquals(new double[]{0,-3},position,1e-6);
    assertTrue(result.match.transform.yaw>0);
  }
  @Test public void twoLandmarksPlusIndependentCorridorAxisCanConfirmTranslation(){
    TemporalSemanticLocalizer localizer=new TemporalSemanticLocalizer();TemporalSemanticLocalizer.Result result=null;
    for(int i=0;i<6;i++){long t=1_000_000_000L+i*500_000_000L;
      result=localizer.update(map(),Arrays.asList(ob(t,"DOOR",1.1f,-2),ob(t,"STAIRS",-.9f,-4)),
          new CorridorDirectionEstimator.Observation(0,.9,4,t),0,0,Rigid2D.identity(),t,true);
    }
    assertEquals(MatchResult.Status.MATCH_CONFIRMED,result.match.status);assertEquals(2,result.match.inliers);assertEquals(.1,result.match.transform.x,.01);
  }
  @Test public void perpendicularCorridorCornerRejectsYawAndTrackingLossClearsHistory(){
    SemanticLandmarkMap corner=new SemanticLandmarkMap("F1",map().landmarks,Arrays.asList(
        new SemanticLandmarkMap.Corridor(0,0,0,-5),new SemanticLandmarkMap.Corridor(0,0,5,0)));
    assertNull(corner.corridorYaw(0,0));TemporalSemanticLocalizer localizer=new TemporalSemanticLocalizer();
    localizer.update(map(),Collections.singletonList(ob(1_000_000_000L,"DOOR",1,-2)),null,0,0,Rigid2D.identity(),1_000_000_000L,true);
    assertEquals(0,localizer.update(map(),Collections.emptyList(),null,0,0,Rigid2D.identity(),1_500_000_000L,false).accumulated.size());
  }
  @Test public void approximatePlanReferencesAreUsableWithoutFalsifyingSurveyFlags(){
    SemanticLandmarkMap map=new SemanticLandmarkMap("F1",Collections.singletonList(new SemanticLandmarkMap.Landmark("door","DOOR",0,-2,false,true)));
    assertEquals(0,map.verifiedCount());assertEquals(1,map.usableCount());
  }
}
