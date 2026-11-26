package com.google.ar.core.examples.java.helloar.localization;

public final class MatchResult {
  public enum Status {NO_MATCH,AMBIGUOUS,LOW_CONFIDENCE,MATCH_CONFIRMED}
  public final Status status;
  public final String reason;
  public final Rigid2D transform;
  public final int inliers,candidates;
  public final double residual;
  public final String residualUnit;
  public final long timestamp;
  public MatchResult(Status status,String reason,Rigid2D transform,int inliers,int candidates,double residual,long timestamp) {
    this(status,reason,transform,inliers,candidates,residual,timestamp,"m");
  }
  public MatchResult(Status status,String reason,Rigid2D transform,int inliers,int candidates,double residual,long timestamp,String residualUnit) {
    this.status=status;this.reason=reason;this.transform=transform;this.inliers=inliers;this.candidates=candidates;this.residual=residual;this.timestamp=timestamp;
    this.residualUnit=residualUnit;
  }
  public static MatchResult withheld(Status status,String reason,long time){return new MatchResult(status,reason,null,0,0,Double.NaN,time);}
}
