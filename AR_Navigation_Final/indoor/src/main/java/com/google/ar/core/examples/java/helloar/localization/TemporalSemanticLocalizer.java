package com.google.ar.core.examples.java.helloar.localization;

import java.util.*;

/** Accumulated point geometry plus independently observed corridor yaw. Owns no AR session. */
public final class TemporalSemanticLocalizer {
  public static final class Result {
    public final MatchResult match;public final List<LandmarkObservation> accumulated;
    public final Double corridorYaw;public final int fresh;
    Result(MatchResult match,List<LandmarkObservation> accumulated,Double corridorYaw,int fresh){
      this.match=match;this.accumulated=Collections.unmodifiableList(accumulated);this.corridorYaw=corridorYaw;this.fresh=fresh;
    }
  }
  private final TemporalLandmarkAccumulator accumulator=new TemporalLandmarkAccumulator();
  private final SemanticMapMatcher matcher=new SemanticMapMatcher();
  private long lastTime,axisTime;private double lastX,lastZ,axisYaw;private int axisFrames;
  private String floor;
  public void reset(){accumulator.reset();matcher.reset();lastTime=axisTime=0;axisFrames=0;floor=null;}
  public Result update(SemanticLandmarkMap map,List<LandmarkObservation> observations,
      CorridorDirectionEstimator.Observation direction,double x,double z,Rigid2D correction,long time,boolean tracking) {
    if(!tracking||map==null){reset();return empty("NAVIGATION_OR_TRACKING_INACTIVE",time);}
    if(floor!=null&&!floor.equals(map.floorId))reset();
    if(lastTime!=0&&(time<=lastTime||time-lastTime>2_000_000_000L||Math.hypot(x-lastX,z-lastZ)>2.5)) {
      reset();floor=map.floorId;lastTime=time;lastX=x;lastZ=z;return empty("CAPTURE_GAP_OR_POSE_DISCONTINUITY",time);
    }
    floor=map.floorId;lastTime=time;lastX=x;lastZ=z;
    List<LandmarkObservation> pooled=accumulator.update(observations,time);
    double[] predicted=correction.inverse(x,z);Double mapYaw=map.corridorYaw(predicted[0],predicted[1]);
    Double validAxis=null;
    if(direction!=null&&mapYaw!=null&&direction.timestamp==time&&direction.confidence>=.8&&direction.length>=1.5) {
      double candidate=CorridorDirectionEstimator.axisDifference(direction.yaw,mapYaw);
      if(Math.abs(candidate)<=Math.toRadians(10)) {
        axisFrames=axisTime!=0&&time>axisTime&&time-axisTime<=1_000_000_000L
            &&Math.abs(CorridorDirectionEstimator.axisDifference(candidate,axisYaw))<Math.toRadians(2)?axisFrames+1:1;
        axisYaw=candidate;axisTime=time;if(axisFrames>=3)validAxis=candidate;
      }else axisFrames=0;
    }else axisFrames=0;
    MatchResult points=accumulator.freshCount()>0
        ?matcher.match(map,pooled,x,z,correction,time,validAxis)
        :MatchResult.withheld(MatchResult.Status.NO_MATCH,"WAITING_FOR_FRESH_LANDMARK_EVIDENCE",time);
    if(points.status==MatchResult.Status.MATCH_CONFIRMED||points.status==MatchResult.Status.AMBIGUOUS)
      return new Result(points,pooled,validAxis,accumulator.freshCount());
    // Direction alone cannot locate a door or move the camera along a featureless corridor.
    // Rotate about the current estimated camera position, preserving that position exactly.
    if(validAxis!=null) {
      double limit=Math.min(Math.toRadians(.75),.3/Math.max(1,Math.hypot(predicted[0],predicted[1])));
      double delta=Rigid2D.wrap(validAxis-correction.yaw),yaw=correction.yaw+Math.max(-limit,Math.min(limit,delta));
      Rigid2D rotation=new Rigid2D(yaw,0,0);double[] p=rotation.apply(predicted[0],predicted[1]);
      MatchResult result=new MatchResult(MatchResult.Status.MATCH_CONFIRMED,"CORRIDOR_YAW_ONLY_CAMERA_PIVOT",
          new Rigid2D(yaw,x-p[0],z-p[1]),0,1,0,time);
      return new Result(result,pooled,validAxis,accumulator.freshCount());
    }
    return new Result(points,pooled,validAxis,accumulator.freshCount());
  }
  private Result empty(String reason,long time){return new Result(MatchResult.withheld(MatchResult.Status.NO_MATCH,reason,time),Collections.emptyList(),null,0);}
}
