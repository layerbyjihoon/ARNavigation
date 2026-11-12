package com.google.ar.core.examples.java.helloar.localization;

import java.util.*;

/** Local type-constrained pair hypotheses, unique correspondences, residual and temporal gates. */
public final class SemanticMapMatcher {
  public static final class Config {
    public double searchRadius=8,maxTranslation=1.5,maxYaw=Math.toRadians(10),inlierDistance=.35,
        maxResidual=.25,ambiguityMargin=.05,minBaseline=1;
    public int minInliers=3,confirmFrames=3,maxLandmarks=16,maxObservations=8;
  }
  private final Config config;
  private Rigid2D previous;
  private long previousTimestamp;
  private int confirmations;
  public SemanticMapMatcher(){this(new Config());}
  public SemanticMapMatcher(Config config){this.config=config;}
  public void reset(){previous=null;confirmations=0;previousTimestamp=0;}

  public MatchResult match(SemanticLandmarkMap map,List<LandmarkObservation> input,
      double cameraX,double cameraZ,Rigid2D current,long timestamp) {
    return match(map,input,cameraX,cameraZ,current,timestamp,null);
  }
  public MatchResult match(SemanticLandmarkMap map,List<LandmarkObservation> input,
      double cameraX,double cameraZ,Rigid2D current,long timestamp,Double corridorCorrectionYaw) {
    int minimum=corridorCorrectionYaw==null?config.minInliers:Math.max(2,config.minInliers-1);
    List<LandmarkObservation> observations=new ArrayList<>();
    for(LandmarkObservation ob:input)if(ob.metricValid&&ob.stable&&ob.confidence>=.65f
        &&!ob.type.equals("WALL")&&ob.frame.tracking)observations.add(ob);
    if(observations.size()<minimum)return reject(MatchResult.Status.NO_MATCH,"INSUFFICIENT_STABLE_3D_OBSERVATIONS",timestamp);
    if(map==null||map.usableCount()<minimum)
      return reject(MatchResult.Status.LOW_CONFIDENCE,"MAP_LANDMARKS_NOT_SURVEYED",timestamp);
    List<SemanticLandmarkMap.Landmark> targets=new ArrayList<>();
    double[] predicted=current.inverse(cameraX,cameraZ);
    for(SemanticLandmarkMap.Landmark landmark:map.landmarks)
      if(landmark.usable()&&Math.hypot(landmark.x-predicted[0],landmark.z-predicted[1])<=config.searchRadius)targets.add(landmark);
    targets.sort(Comparator.comparingDouble(p->Math.hypot(p.x-predicted[0],p.z-predicted[1])));
    if(targets.size()>config.maxLandmarks)targets=new ArrayList<>(targets.subList(0,config.maxLandmarks));
    if(observations.size()>config.maxObservations)observations=new ArrayList<>(observations.subList(0,config.maxObservations));
    List<Candidate> hypotheses=new ArrayList<>();
    int tested=0;
    for(int i=0;i<observations.size();i++)for(int j=i+1;j<observations.size();j++)
      for(int a=0;a<targets.size();a++)for(int b=0;b<targets.size();b++) {
        if(a==b||!observations.get(i).type.equals(targets.get(a).type)||!observations.get(j).type.equals(targets.get(b).type))continue;
        SemanticLandmarkMap.Landmark p=targets.get(a),q=targets.get(b);
        float[] u=observations.get(i).routePosition,v=observations.get(j).routePosition;
        double pdx=q.x-p.x,pdz=q.z-p.z,udx=v[0]-u[0],udz=v[2]-u[2];
        if(Math.hypot(pdx,pdz)<config.minBaseline||Math.abs(Math.hypot(pdx,pdz)-Math.hypot(udx,udz))>config.inlierDistance)continue;
        double yaw=Rigid2D.wrap(Math.atan2(pdz,pdx)-Math.atan2(udz,udx));
        Rigid2D seed=new Rigid2D(yaw,u[0]-Math.cos(yaw)*p.x-Math.sin(yaw)*p.z,
            u[2]+Math.sin(yaw)*p.x-Math.cos(yaw)*p.z);
        if(!plausible(seed)||!axisAgrees(seed,corridorCorrectionYaw))continue;
        tested++;
        Candidate candidate=score(seed,targets,observations);
        if(candidate.count<minimum)continue;
        candidate=score(refine(candidate,targets,observations),targets,observations);
        if(!plausible(candidate.transform)||!axisAgrees(candidate.transform,corridorCorrectionYaw)||candidate.count<minimum||candidate.residual>config.maxResidual)continue;
        boolean duplicate=false;
        for(int k=0;k<hypotheses.size();k++) {
          Candidate old=hypotheses.get(k);
          if(close(candidate.transform,old.transform,.05,Math.toRadians(.5))) {
            if(better(candidate,old))hypotheses.set(k,candidate);
            duplicate=true;break;
          }
        }
        if(!duplicate)hypotheses.add(candidate);
      }
    if(hypotheses.isEmpty())return reject(MatchResult.Status.NO_MATCH,"NO_GEOMETRIC_CONSENSUS",timestamp);
    hypotheses.sort((a,b)->a.count!=b.count?Integer.compare(b.count,a.count):Double.compare(a.residual,b.residual));
    Candidate best=hypotheses.get(0);
    if(hypotheses.size()>1) {
      Candidate second=hypotheses.get(1);
      if(second.count==best.count&&second.residual-best.residual<config.ambiguityMargin)
        return reject(MatchResult.Status.AMBIGUOUS,"REPEATED_LAYOUT_OR_SMALL_MARGIN",timestamp);
    }
    if(timestamp<=previousTimestamp)return MatchResult.withheld(MatchResult.Status.LOW_CONFIDENCE,"DUPLICATE_OR_OLD_FRAME",timestamp);
    confirmations=previous!=null&&timestamp-previousTimestamp<1_500_000_000L
        &&close(previous,best.transform,.15,Math.toRadians(2))?confirmations+1:1;
    previous=best.transform;previousTimestamp=timestamp;
    return new MatchResult(confirmations>=config.confirmFrames?MatchResult.Status.MATCH_CONFIRMED:MatchResult.Status.LOW_CONFIDENCE,
        confirmations>=config.confirmFrames?"GEOMETRY_AND_TEMPORAL_CONSENSUS":"WAITING_FOR_TEMPORAL_CONSENSUS",
        best.transform,best.count,tested,best.residual,timestamp);
  }
  private boolean axisAgrees(Rigid2D transform,Double yaw){return yaw==null||Math.abs(CorridorDirectionEstimator.axisDifference(transform.yaw,yaw))<Math.toRadians(4);}
  private MatchResult reject(MatchResult.Status status,String reason,long time){confirmations=0;previous=null;return MatchResult.withheld(status,reason,time);}
  private boolean plausible(Rigid2D transform){return Double.isFinite(transform.yaw)&&Double.isFinite(transform.x)&&Double.isFinite(transform.z)
      &&Math.abs(transform.yaw)<=config.maxYaw&&Math.hypot(transform.x,transform.z)<=config.maxTranslation;}
  private static boolean close(Rigid2D a,Rigid2D b,double meters,double angle){return Math.hypot(a.x-b.x,a.z-b.z)<=meters&&Math.abs(Rigid2D.wrap(a.yaw-b.yaw))<=angle;}
  private static boolean better(Candidate a,Candidate b){return a.count>b.count||(a.count==b.count&&a.residual<b.residual);}
  private Candidate score(Rigid2D transform,List<SemanticLandmarkMap.Landmark> targets,List<LandmarkObservation> observations) {
    Candidate result=new Candidate(transform,observations.size());boolean[] used=new boolean[targets.size()];double sum=0;
    for(int i=0;i<observations.size();i++) {
      LandmarkObservation ob=observations.get(i);double best=config.inlierDistance;int match=-1;
      for(int j=0;j<targets.size();j++) {
        SemanticLandmarkMap.Landmark target=targets.get(j);if(used[j]||!ob.type.equals(target.type))continue;
        double[] p=transform.apply(target.x,target.z);double error=Math.hypot(p[0]-ob.routePosition[0],p[1]-ob.routePosition[2]);
        if(error<best){best=error;match=j;}
      }
      result.matches[i]=match;
      if(match>=0){used[match]=true;result.count++;sum+=best*best;}
    }
    result.residual=result.count==0?Double.POSITIVE_INFINITY:Math.sqrt(sum/result.count);return result;
  }
  private Rigid2D refine(Candidate candidate,List<SemanticLandmarkMap.Landmark> targets,List<LandmarkObservation> observations) {
    double px=0,pz=0,ox=0,oz=0;
    for(int i=0;i<candidate.matches.length;i++)if(candidate.matches[i]>=0){
      SemanticLandmarkMap.Landmark p=targets.get(candidate.matches[i]);float[] o=observations.get(i).routePosition;
      px+=p.x;pz+=p.z;ox+=o[0];oz+=o[2];
    }
    px/=candidate.count;pz/=candidate.count;ox/=candidate.count;oz/=candidate.count;
    double dot=0,cross=0;
    for(int i=0;i<candidate.matches.length;i++)if(candidate.matches[i]>=0){
      SemanticLandmarkMap.Landmark p=targets.get(candidate.matches[i]);float[] o=observations.get(i).routePosition;
      dot+=(p.x-px)*(o[0]-ox)+(p.z-pz)*(o[2]-oz);
      cross+=(p.z-pz)*(o[0]-ox)-(p.x-px)*(o[2]-oz);
    }
    double angle=Math.atan2(cross,dot);
    return new Rigid2D(angle,ox-Math.cos(angle)*px-Math.sin(angle)*pz,oz+Math.sin(angle)*px-Math.cos(angle)*pz);
  }
  private static final class Candidate {
    final Rigid2D transform;final int[] matches;int count;double residual;
    Candidate(Rigid2D transform,int n){this.transform=transform;matches=new int[n];Arrays.fill(matches,-1);}
  }
}
