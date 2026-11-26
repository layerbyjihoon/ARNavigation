package com.google.ar.core.examples.java.helloar.localization;

import static org.junit.Assert.*;
import com.google.ar.core.examples.java.helloar.segmentation.*;
import java.util.*;
import org.junit.Test;

public final class FeatureAndRouteBearingTest {
  private float[] identity(){return new float[]{1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1};}
  private FrameSnapshot frame(long time,float x,float z){float[] camera=identity();camera[12]=x;camera[13]=1.55f;camera[14]=z;
    return new FrameSnapshot(time,512,512,0,camera,identity(),new float[]{400,400,256,256},new float[]{0,0,1,0,0,1},new float[]{0,0,1,0,0,1},null,true);}
  private byte[] texture(){byte[] image=new byte[256*256];Random random=new Random(42);for(int y=0;y<256;y++)for(int x=0;x<256;x++)
    image[y*256+x]=(byte)(80+50*Math.sin(x*.19)+45*Math.cos(y*.23)+random.nextInt(20));return image;}
  private byte[] shift(byte[] source,int dx,int dy){byte[] result=new byte[source.length];for(int y=0;y<256;y++)for(int x=0;x<256;x++){
    int xx=x-dx,yy=y-dy;if(xx>=0&&yy>=0&&xx<256&&yy<256)result[y*256+x]=source[yy*256+xx];}return result;}
  private LandmarkObservation detection(FrameSnapshot f,float left){return new LandmarkObservation("DOOR",left,.25f,left+.25f,.7f,128,0,.9f,f,false,null,null);}

  @Test public void pyramidalFlowRecoversTranslationWithForwardBackwardAndOutlierChecks(){
    byte[] texture=texture();TrackingImage a=new TrackingImage(256,256,texture,frame(1_000_000_000L,0,0));
    TrackingImage b=new TrackingImage(256,256,shift(texture,3,2),frame(1_100_000_000L,0,0));SparseFeatureFlow flow=new SparseFeatureFlow();
    SparseFeatureFlow.Motion motion=flow.motion(a,b,flow.corners(a,.25f,.25f,.7f,.7f));assertNotNull(motion);
    assertEquals(3,motion.tx,.35);assertEquals(2,motion.ty,.35);assertTrue(motion.points.size()>=6);
    assertNull(flow.motion(a,new TrackingImage(256,256,new byte[256*256],frame(1_200_000_000L,0,0)),flow.corners(a,.25f,.25f,.7f,.7f)));
  }
  @Test public void confirmedTrackKeepsIdBetweenDetectionsAndBlankFramesAreNotEvidence(){
    FeatureLandmarkTracker tracker=new FeatureLandmarkTracker();byte[] texture=texture();long id=-1;List<LandmarkObservation> result=null;
    for(int i=0;i<7;i++){FrameSnapshot frame=frame(1_000_000_000L+i*100_000_000L,0,0);TrackingImage image=new TrackingImage(256,256,shift(texture,i,0),frame);
      result=tracker.update(image,i==0||i==5?Collections.singletonList(detection(frame,.25f+i/256f)):null);
      assertEquals(1,result.size());if(i==0)id=result.get(0).trackId;assertEquals(id,result.get(0).trackId);
    }
    assertTrue(result.get(0).stable);assertFalse(result.get(0).detectorObservation);
    assertTrue(tracker.update(new TrackingImage(256,256,new byte[256*256],frame(1_700_000_000L,0,0)),null).isEmpty());
  }
  @Test public void lowScoreSeedRemainsTentativeEvenWhenItsTextureTracks(){
    FeatureLandmarkTracker tracker=new FeatureLandmarkTracker();byte[] texture=texture();
    for(int i=0;i<7;i++){FrameSnapshot frame=frame(1_000_000_000L+i*100_000_000L,0,0);
      LandmarkObservation low=new LandmarkObservation("DOOR",.25f+i/256f,.25f,.5f+i/256f,.7f,128,0,.5f,frame,false,null,null);
      for(LandmarkObservation ob:tracker.update(new TrackingImage(256,256,shift(texture,i,0),frame),i==0||i==5?Collections.singletonList(low):null))assertFalse(ob.stable);
    }
  }
  private SemanticLandmarkMap map(boolean ambiguous){
    List<SemanticLandmarkMap.Landmark> landmarks=Arrays.asList(
        new SemanticLandmarkMap.Landmark("A","DOOR",ambiguous?0:-2,-6,false,true),
        new SemanticLandmarkMap.Landmark("B","DOOR",ambiguous?0:2,-6,false,true),
        new SemanticLandmarkMap.Landmark("C","STAIRS",ambiguous?0:1.5,-9,false,true));
    return new SemanticLandmarkMap("F1",landmarks,Collections.emptyList(),Arrays.asList(new SemanticLandmarkMap.RoutePoint(0,0),new SemanticLandmarkMap.RoutePoint(0,-20)));
  }
  private List<LandmarkObservation> bearings(SemanticLandmarkMap map,FrameSnapshot frame,double truthZ,boolean sameId){
    List<LandmarkObservation> result=new ArrayList<>();long id=0;
    for(SemanticLandmarkMap.Landmark p:map.landmarks){double center=.5+400/512.0*p.x/(-(p.z-truthZ));id++;
      if(center<.03||center>.97)continue;
      result.add(new LandmarkObservation(p.type,(float)center-.02f,.45f,(float)center+.02f,.55f,64,0,.9f,frame,true,null,null,LandmarkObservation.PositionSource.NONE,sameId?1:id,true));
    }
    return result;
  }
  @Test public void depthFreeBearingsRecoverObservableProgressOverMovingViews(){
    SemanticLandmarkMap map=map(false);RouteBearingMatcher matcher=new RouteBearingMatcher();boolean approved=false;RouteBearingMatcher.Result last=null;
    for(int i=0;i<24;i++){double truthZ=-2-i*.1;long time=1_000_000_000L+i*100_000_000L;
      FrameSnapshot frame=frame(time,0,(float)(truthZ+.5));last=matcher.update(map,bearings(map,frame,truthZ,false),0,truthZ+.5,Rigid2D.identity(),time,true);
      if(last.match.status==MatchResult.Status.MATCH_CONFIRMED){approved=true;assertTrue(last.match.reason.contains("_S"));assertTrue(last.match.transform.z>0);assertTrue(last.match.transform.z<=.31);assertEquals("rad",last.match.residualUnit);}
    }
    assertTrue("No depth-free correction: "+last.match.reason+" sigmas="+last.sigmaS+","+last.sigmaD+","+last.sigmaYaw,approved);
  }
  @Test public void stationaryCameraOrOneRepeatedTrackCannotConfirm(){
    for(boolean sameId:new boolean[]{false,true}){SemanticLandmarkMap map=map(false);RouteBearingMatcher matcher=new RouteBearingMatcher();
      for(int i=0;i<20;i++){double truthZ=sameId?-2-i*.1:-2;long time=1_000_000_000L+i*100_000_000L;FrameSnapshot frame=frame(time,0,(float)(truthZ+.5));
        assertNotEquals(MatchResult.Status.MATCH_CONFIRMED,matcher.update(map,bearings(map,frame,truthZ,sameId),0,truthZ+.5,Rigid2D.identity(),time,true).match.status);
      }
    }
  }
  @Test public void slowWalkingRetainsOlderKeyframeInsteadOfErasingItsBaseline(){
    SemanticLandmarkMap map=map(false);RouteBearingMatcher matcher=new RouteBearingMatcher();boolean approved=false;
    for(int i=0;i<80;i++){double truthZ=-2-i*.02;long time=1_000_000_000L+i*100_000_000L;FrameSnapshot frame=frame(time,0,(float)(truthZ+.5));
      if(matcher.update(map,bearings(map,frame,truthZ,false),0,truthZ+.5,Rigid2D.identity(),time,true).match.status==MatchResult.Status.MATCH_CONFIRMED)approved=true;
    }
    assertTrue("Slow motion must accumulate a real baseline across more than one second",approved);
  }
  @Test public void unobservableAlongCorridorBearingsAreWithheld(){
    SemanticLandmarkMap map=map(true);RouteBearingMatcher matcher=new RouteBearingMatcher();
    for(int i=0;i<20;i++){double truthZ=-2-i*.1;long time=1_000_000_000L+i*100_000_000L;FrameSnapshot frame=frame(time,0,(float)(truthZ+.5));
      assertNotEquals(MatchResult.Status.MATCH_CONFIRMED,matcher.update(map,bearings(map,frame,truthZ,false),0,truthZ+.5,Rigid2D.identity(),time,true).match.status);
    }
  }
  @Test public void trackingLossAndUnknownRouteCannotUseOldBearings(){
    RouteBearingMatcher matcher=new RouteBearingMatcher();SemanticLandmarkMap map=map(false);long time=1_000_000_000L;
    assertNotEquals(MatchResult.Status.MATCH_CONFIRMED,matcher.update(map,Collections.emptyList(),0,-3,Rigid2D.identity(),time,false).match.status);
    SemanticLandmarkMap noRoute=new SemanticLandmarkMap("F1",map.landmarks);
    assertEquals("BEARING_ROUTE_OR_TRACKING_UNAVAILABLE",matcher.update(noRoute,Collections.emptyList(),0,-3,Rigid2D.identity(),time,true).match.reason);
  }
  @Test public void metricAndBearingDisagreementIsNotSilentlyApplied(){
    MatchResult a=new MatchResult(MatchResult.Status.MATCH_CONFIRMED,"metric",new Rigid2D(0,.3,0),3,1,0,1);
    MatchResult b=new MatchResult(MatchResult.Status.MATCH_CONFIRMED,"bearing",new Rigid2D(0,-.3,0),2,10,0,1,"rad");
    assertEquals(MatchResult.Status.AMBIGUOUS,SemanticFusion.choose(a,b,1).status);
  }
}
