package com.google.ar.core.examples.java.helloar.localization;

/** Explicit conflict gate between metric geometry and depth-free route bearings. */
public final class SemanticFusion {
  public static MatchResult choose(MatchResult metric,MatchResult bearing,long time){
    if(metric!=null&&metric.status==MatchResult.Status.MATCH_CONFIRMED&&metric.inliers>=2){
      if(bearing!=null&&bearing.status==MatchResult.Status.MATCH_CONFIRMED
          &&(Math.hypot(metric.transform.x-bearing.transform.x,metric.transform.z-bearing.transform.z)>.25
          ||Math.abs(Rigid2D.wrap(metric.transform.yaw-bearing.transform.yaw))>Math.toRadians(2)))
        return MatchResult.withheld(MatchResult.Status.AMBIGUOUS,"CONFLICT_METRIC_AND_ROUTE_BEARING",time);
      return metric;
    }
    if(bearing!=null&&bearing.status==MatchResult.Status.MATCH_CONFIRMED)return bearing;
    if(metric!=null&&metric.status==MatchResult.Status.MATCH_CONFIRMED)return metric;
    return bearing!=null?bearing:metric;
  }
}
