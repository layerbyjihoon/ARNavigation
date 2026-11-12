package com.google.ar.core.examples.java.helloar.localization;

import java.util.*;

/** Local route-constrained bearing hypotheses. No depth or synthetic 3D object positions. */
public final class RouteBearingMatcher {
  public static final class Result {
    public final MatchResult match;public final int tracks;public final double sigmaS,sigmaD,sigmaYaw;
    Result(MatchResult m,int tracks,double s,double d,double yaw){match=m;this.tracks=tracks;sigmaS=s;sigmaD=d;sigmaYaw=yaw;}
  }
  private final Map<Long,Track> history=new LinkedHashMap<>();
  private long lastTime,lastDecision,confirmationTime;private String floor;
  private double lastX,lastZ,confirmationX,confirmationZ;private Rigid2D previous;
  private int confirmations;
  public void reset(){history.clear();lastTime=lastDecision=confirmationTime=0;floor=null;previous=null;confirmations=0;}
  public Result update(SemanticLandmarkMap map,List<LandmarkObservation> input,double cameraX,double cameraZ,
      Rigid2D correction,long time,boolean tracking){
    if(!tracking||map==null||map.route==null||map.route.size()<2){reset();return withheld("BEARING_ROUTE_OR_TRACKING_UNAVAILABLE",time);}
    if(floor!=null&&!floor.equals(map.floorId))reset();
    if(lastTime!=0&&(time<=lastTime||time-lastTime>1_000_000_000L||Math.hypot(cameraX-lastX,cameraZ-lastZ)>2)){
      reset();floor=map.floorId;lastTime=time;lastX=cameraX;lastZ=cameraZ;return withheld("BEARING_CAPTURE_DISCONTINUITY",time);
    }
    floor=map.floorId;lastTime=time;lastX=cameraX;lastZ=cameraZ;
    history.values().removeIf(t->time-t.last>2_000_000_000L);
    boolean fresh=false;
    for(LandmarkObservation ob:input){
      if(ob.trackId<0||!ob.stable||ob.timestamp!=time||ob.confidence<.55||!ob.frame.tracking)continue;
      if(!ob.type.equals("DOOR")&&!ob.type.equals("STAIRS"))continue;
      CaptureGeometry.Ray ray=CaptureGeometry.ray(ob.frame,ob.centerX,ob.centerY);
      if(ray==null||Math.hypot(ray.direction[0],ray.direction[2])<.3)continue;
      Track track=history.computeIfAbsent(ob.trackId,id->new Track(ob.type));track.last=time;fresh=true;
      double bearing=Math.atan2(-ray.direction[0],-ray.direction[2]);Sample last=track.samples.isEmpty()?null:track.samples.get(track.samples.size()-1);
      if(last==null||time-last.time>=200_000_000L&&(Math.hypot(ray.origin[0]-last.x,ray.origin[2]-last.z)>=.1||Math.abs(Rigid2D.wrap(bearing-last.bearing))>=Math.toRadians(1))){
        track.samples.add(new Sample(time,ray.origin[0],ray.origin[2],bearing));
      }
      track.samples.removeIf(s->time-s.time>6_000_000_000L);
      // Keep an older distinct viewpoint instead of letting high-rate duplicates erase the baseline.
      while(track.samples.size()>6)track.samples.remove(1);
    }
    if(history.size()>12){Iterator<Long> it=history.keySet().iterator();while(history.size()>12){it.next();it.remove();}}
    if(!fresh)return withheld("BEARING_WAITING_FOR_CONFIRMED_VISUAL_TRACK",time);
    if(lastDecision!=0&&time-lastDecision<200_000_000L)return new Result(MatchResult.withheld(MatchResult.Status.LOW_CONFIDENCE,"BEARING_RATE_LIMIT",time),history.size(),Double.NaN,Double.NaN,Double.NaN);
    lastDecision=time;
    List<Track> tracks=new ArrayList<>();
    for(Track track:history.values())if(track.samples.size()>=3&&track.baseline()>=.4)tracks.add(track);
    tracks.sort(Comparator.comparingLong((Track t)->-t.last));if(tracks.size()>6)tracks=new ArrayList<>(tracks.subList(0,6));
    if(tracks.size()<2)return withheld("BEARING_NEEDS_TWO_DISTINCT_MOVING_TRACKS",time);
    Path path=new Path(map.route);double[] mapCamera=correction.inverse(cameraX,cameraZ);Projection p=path.project(mapCamera[0],mapCamera[1]);
    if(p==null||p.distance>2||p.s<.75||path.length-p.s<.75||path.nearCorner(p.s))return withheld("BEARING_ROUTE_ENDPOINT_CORNER_OR_OFF_ROUTE",time);
    List<SemanticLandmarkMap.Landmark> references=new ArrayList<>();
    for(SemanticLandmarkMap.Landmark reference:map.landmarks)if(reference.usable()&&Math.hypot(reference.x-mapCamera[0],reference.z-mapCamera[1])<15)references.add(reference);
    references.sort(Comparator.comparingDouble(r->Math.hypot(r.x-mapCamera[0],r.z-mapCamera[1])));
    if(references.size()>16)references=new ArrayList<>(references.subList(0,16));
    if(references.size()<2)return withheld("BEARING_MAP_REFERENCES_UNAVAILABLE",time);
    List<Candidate> candidates=new ArrayList<>();Candidate best=null;double bestRank=Double.POSITIVE_INFINITY,minData=Double.POSITIVE_INFINITY;
    for(int si=-5;si<=5;si++)for(int di=-2;di<=2;di++)for(int yi=-3;yi<=3;yi++){
      double ds=si*.25,dd=di*.3,dyaw=Math.toRadians(yi*2);double s=p.s+ds;if(s<0||s>path.length)continue;
      Rigid2D transform=transform(path,s,p.d+dd,correction.yaw+dyaw,cameraX,cameraZ);
      if(Math.hypot(transform.x,transform.z)>1.5||Math.abs(transform.yaw)>Math.toRadians(10))continue;
      Score score=score(transform,tracks,references);if(!Double.isFinite(score.cost)||score.rms>Math.toRadians(8))continue;
      Candidate c=new Candidate(ds,dd,dyaw,transform,score);candidates.add(c);minData=Math.min(minData,score.cost);
      double rank=score.cost+.1*(ds*ds/.5625+dd*dd/.09+dyaw*dyaw/Math.pow(Math.toRadians(4),2));
      if(rank<bestRank){bestRank=rank;best=c;}
    }
    if(best==null)return withheld("BEARING_NO_GEOMETRIC_CONSENSUS",time);
    double sum=0,ms=0,md=0,my=0;
    for(Candidate c:candidates){c.weight=Math.exp(-.5*(c.score.cost-minData));sum+=c.weight;ms+=c.weight*c.s;md+=c.weight*c.d;my+=c.weight*c.yaw;}
    ms/=sum;md/=sum;my/=sum;double vs=0,vd=0,vy=0;
    for(Candidate c:candidates){vs+=c.weight*Math.pow(c.s-ms,2);vd+=c.weight*Math.pow(c.d-md,2);vy+=c.weight*Math.pow(c.yaw-my,2);}
    double sigmaS=Math.sqrt(vs/sum),sigmaD=Math.sqrt(vd/sum),sigmaYaw=Math.sqrt(vy/sum);
    boolean along=sigmaS<.3&&Math.abs(best.s)<1.24,lateral=sigmaD<.2&&Math.abs(best.d)<.59,
        rotation=sigmaYaw<Math.toRadians(1.4)&&Math.abs(best.yaw)<Math.toRadians(5.9);
    if(!along&&!lateral&&!rotation)return uncertain("BEARING_MULTIPLE_PLAUSIBLE_ROUTE_STATES",tracks.size(),candidates.size(),best.score.rms,time,sigmaS,sigmaD,sigmaYaw);
    Rigid2D target=transform(path,p.s+(along?best.s:0),p.d+(lateral?best.d:0),correction.yaw+(rotation?best.yaw:0),cameraX,cameraZ);
    Score partial=score(target,tracks,references),baseline=score(correction,tracks,references);
    if(partial.rms>Math.toRadians(3)||partial.cost>best.score.cost+2)
      return uncertain("BEARING_COMPONENTS_NOT_INDEPENDENTLY_OBSERVABLE",tracks.size(),candidates.size(),partial.rms,time,sigmaS,sigmaD,sigmaYaw);
    if(Double.isFinite(baseline.cost)&&baseline.cost-partial.cost<1)
      return new Result(MatchResult.withheld(MatchResult.Status.LOW_CONFIDENCE,"BEARING_NO_MEANINGFUL_CHANGE",time),tracks.size(),sigmaS,sigmaD,sigmaYaw);
    // Distinct camera motion, not repeated evaluations of the same bearings, confirms a change.
    if(previous!=null&&Math.hypot(target.x-previous.x,target.z-previous.z)<.2
        &&Math.abs(Rigid2D.wrap(target.yaw-previous.yaw))<Math.toRadians(2)&&time-confirmationTime<1_000_000_000L){
      if(Math.hypot(cameraX-confirmationX,cameraZ-confirmationZ)>=.1){confirmations++;confirmationTime=time;confirmationX=cameraX;confirmationZ=cameraZ;}
    }else{confirmations=1;confirmationTime=time;confirmationX=cameraX;confirmationZ=cameraZ;}
    previous=target;
    if(confirmations<3)return new Result(MatchResult.withheld(MatchResult.Status.LOW_CONFIDENCE,"BEARING_WAITING_FOR_MOVING_VIEW_CONSENSUS",time),tracks.size(),sigmaS,sigmaD,sigmaYaw);
    double step=Math.min(1,Math.min(.3/Math.max(1e-9,Math.hypot(target.x-correction.x,target.z-correction.z)),Math.toRadians(1)/Math.max(1e-9,Math.abs(Rigid2D.wrap(target.yaw-correction.yaw)))));
    Rigid2D bounded=new Rigid2D(correction.yaw+Rigid2D.wrap(target.yaw-correction.yaw)*step,correction.x+(target.x-correction.x)*step,correction.z+(target.z-correction.z)*step);
    String reason="ROUTE_BEARING_2D"+(along?"_S":"")+(lateral?"_D":"")+(rotation?"_YAW":"");
    return new Result(new MatchResult(MatchResult.Status.MATCH_CONFIRMED,reason,bounded,tracks.size(),candidates.size(),partial.rms,time,"rad"),tracks.size(),sigmaS,sigmaD,sigmaYaw);
  }
  private Result withheld(String reason,long time){confirmations=0;previous=null;return new Result(MatchResult.withheld(MatchResult.Status.NO_MATCH,reason,time),history.size(),Double.NaN,Double.NaN,Double.NaN);}
  private Result uncertain(String reason,int tracks,int count,double rms,long time,double s,double d,double yaw){confirmations=0;previous=null;return new Result(new MatchResult(MatchResult.Status.AMBIGUOUS,reason,null,tracks,count,rms,time,"rad"),tracks,s,d,yaw);}
  private Rigid2D transform(Path path,double s,double d,double yaw,double x,double z){double[] p=path.at(s);double mx=p[0]-p[3]*d,mz=p[1]+p[2]*d;
    double[] rotated=new Rigid2D(yaw,0,0).apply(mx,mz);return new Rigid2D(yaw,x-rotated[0],z-rotated[1]);}
  private Score score(Rigid2D transform,List<Track> tracks,List<SemanticLandmarkMap.Landmark> references){
    Set<String> used=new HashSet<>();double cost=0,squares=0;int factors=0;
    for(Track track:tracks){double lowest=Double.POSITIVE_INFINITY,errorSquares=0;SemanticLandmarkMap.Landmark chosen=null;
      for(SemanticLandmarkMap.Landmark reference:references){
        if(!track.type.equals(reference.type)||used.contains(reference.id))continue;double[] point=transform.apply(reference.x,reference.z);double c=0,q=0;boolean valid=true;
        for(Sample sample:track.samples){double dx=point[0]-sample.x,dz=point[1]-sample.z,distance=Math.hypot(dx,dz);
          if(distance<.5||distance>15){valid=false;break;}double e=Math.abs(Rigid2D.wrap(Math.atan2(-dx,-dz)-sample.bearing));
          q+=e*e;double normalized=e/Math.toRadians(2);c+=normalized<2?normalized*normalized:4*normalized-4;
        }
        if(valid&&c/track.samples.size()<lowest){lowest=c/track.samples.size();errorSquares=q;chosen=reference;}
      }
      if(chosen==null)return new Score(Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY);
      used.add(chosen.id);cost+=lowest;squares+=errorSquares;factors+=track.samples.size();
    }
    return new Score(cost,Math.sqrt(squares/Math.max(1,factors)));
  }
  private static final class Sample {final long time;final double x,z,bearing;Sample(long t,double x,double z,double bearing){time=t;this.x=x;this.z=z;this.bearing=bearing;}}
  private static final class Track {final String type;long last;final List<Sample> samples=new ArrayList<>();Track(String type){this.type=type;}
    double baseline(){double max=0;for(Sample a:samples)for(Sample b:samples)max=Math.max(max,Math.hypot(a.x-b.x,a.z-b.z));return max;}}
  private static final class Score {final double cost,rms;Score(double c,double r){cost=c;rms=r;}}
  private static final class Candidate {final double s,d,yaw;final Rigid2D transform;final Score score;double weight;Candidate(double s,double d,double yaw,Rigid2D t,Score score){this.s=s;this.d=d;this.yaw=yaw;transform=t;this.score=score;}}
  private static final class Projection {final double s,d,distance;Projection(double s,double d,double distance){this.s=s;this.d=d;this.distance=distance;}}
  private static final class Path {
    final List<SemanticLandmarkMap.RoutePoint> points;final double[] cumulative;final double length;
    Path(List<SemanticLandmarkMap.RoutePoint> points){this.points=points;cumulative=new double[points.size()];for(int i=1;i<points.size();i++)cumulative[i]=cumulative[i-1]+Math.hypot(points.get(i).x-points.get(i-1).x,points.get(i).z-points.get(i-1).z);length=cumulative[cumulative.length-1];}
    double[] at(double s){for(int i=1;i<points.size();i++){double length=cumulative[i]-cumulative[i-1];if(length<1e-6)continue;if(s<=cumulative[i]||i==points.size()-1){
      SemanticLandmarkMap.RoutePoint a=points.get(i-1),b=points.get(i);double t=Math.max(0,Math.min(1,(s-cumulative[i-1])/length));return new double[]{a.x+(b.x-a.x)*t,a.z+(b.z-a.z)*t,(b.x-a.x)/length,(b.z-a.z)/length};}}
      return new double[]{points.get(0).x,points.get(0).z,0,-1};}
    Projection project(double x,double z){Projection best=null;double distance=Double.POSITIVE_INFINITY;
      for(int i=1;i<points.size();i++){double length=cumulative[i]-cumulative[i-1];if(length<1e-6)continue;SemanticLandmarkMap.RoutePoint a=points.get(i-1),b=points.get(i);
        double dx=(b.x-a.x)/length,dz=(b.z-a.z)/length,t=Math.max(0,Math.min(length,(x-a.x)*dx+(z-a.z)*dz));double ex=x-a.x-dx*t,ez=z-a.z-dz*t,r=Math.hypot(ex,ez);
        if(r<distance){distance=r;best=new Projection(cumulative[i-1]+t,-dz*ex+dx*ez,r);}}
      return best;}
    boolean nearCorner(double s){for(int i=1;i<points.size()-1;i++)if(Math.abs(s-cumulative[i])<.8){double[] a=at(cumulative[i]-.01),b=at(cumulative[i]+.01);if(a[2]*b[2]+a[3]*b[3]<Math.cos(Math.toRadians(25)))return true;}return false;}
  }
}
