package com.google.ar.core.examples.java.helloar.localization;

import java.util.*;

/** Bounded origin-frame tracks. Repeated sightings of one door never become multiple landmarks. */
public final class TemporalLandmarkAccumulator {
  private static final long WINDOW=12_000_000_000L;
  private final List<Track> tracks=new ArrayList<>();
  private final List<BearingTrack> bearings=new ArrayList<>();
  private long lastTimestamp;
  private int fresh;
  public void reset(){tracks.clear();bearings.clear();lastTimestamp=0;fresh=0;}
  public int freshCount(){return fresh;}
  public List<LandmarkObservation> update(List<LandmarkObservation> input,long timestamp) {
    fresh=0;
    if(timestamp<=lastTimestamp)return snapshot(timestamp);
    if(lastTimestamp!=0&&timestamp-lastTimestamp>2_000_000_000L)reset();
    lastTimestamp=timestamp;
    tracks.removeIf(t->timestamp-t.ob.timestamp>WINDOW);
    bearings.removeIf(t->timestamp-t.last.timestamp>1_500_000_000L||timestamp-t.first.timestamp>3_000_000_000L);
    Set<Track> used=Collections.newSetFromMap(new IdentityHashMap<>());
    Set<BearingTrack> usedBearings=Collections.newSetFromMap(new IdentityHashMap<>());
    for(LandmarkObservation original:input) {
      if(!original.frame.tracking||original.timestamp!=timestamp||original.confidence<.65f
          ||(!original.type.equals("DOOR")&&!original.type.equals("STAIRS")))continue;
      LandmarkObservation ob=original;
      if(!ob.metricValid)ob=triangulate(ob,usedBearings);
      if(ob==null||!ob.metricValid||!finite(ob.routePosition))continue;
      Track best=null;double nearest=.45;
      for(Track t:tracks)if(!used.contains(t)&&t.ob.type.equals(ob.type)) {
        double distance=CaptureGeometry.distance(t.ob.routePosition,ob.routePosition);
        if(distance<nearest){nearest=distance;best=t;}
      }
      if(best==null){best=new Track(ob);tracks.add(best);}else{
        float[] route=new float[3],world=new float[3];
        for(int k=0;k<3;k++){route[k]=.5f*(best.ob.routePosition[k]+ob.routePosition[k]);world[k]=.5f*(best.ob.worldPosition[k]+ob.worldPosition[k]);}
        best.count++;best.ob=new LandmarkObservation(ob.type,ob.left,ob.top,ob.right,ob.bottom,ob.area,ob.classId,
            ob.confidence,ob.frame,best.count>=2||ob.stable,world,route,ob.positionSource);
      }
      used.add(best);if(best.ob.stable)fresh++;
    }
    if(tracks.size()>32){tracks.sort(Comparator.comparingLong((Track t)->-t.ob.timestamp));tracks.subList(32,tracks.size()).clear();}
    if(bearings.size()>24)bearings.subList(0,bearings.size()-24).clear();
    return snapshot(timestamp);
  }
  private List<LandmarkObservation> snapshot(long timestamp){
    List<LandmarkObservation> result=new ArrayList<>();
    for(Track t:tracks)if(t.ob.stable&&timestamp-t.ob.timestamp<=WINDOW)result.add(t.ob);
    result.sort(Comparator.comparingLong((LandmarkObservation ob)->-ob.timestamp));return result;
  }
  private LandmarkObservation triangulate(LandmarkObservation ob,Set<BearingTrack> used) {
    CaptureGeometry.Ray ray=CaptureGeometry.ray(ob.frame,ob.centerX,ob.centerY);if(ray==null)return null;
    BearingTrack best=null;double nearest=.12;
    for(BearingTrack t:bearings)if(!used.contains(t)&&t.last.type.equals(ob.type)
        &&(ob.trackId<0||t.last.trackId==ob.trackId)
        &&ob.area/(double)t.last.area>.4&&ob.area/(double)t.last.area<2.5) {
      double screen=Math.hypot(ob.centerX-t.last.centerX,ob.centerY-t.last.centerY);
      if(screen<nearest){nearest=screen;best=t;}
    }
    if(best==null){best=new BearingTrack(ob,ray);bearings.add(best);used.add(best);return null;}
    used.add(best);best.last=ob;
    CaptureGeometry.Ray a=best.ray,b=ray;
    double dot=CaptureGeometry.dot(a.direction,b.direction),den=1-dot*dot;
    if(CaptureGeometry.distance(a.origin,b.origin)<.5||den<Math.pow(Math.sin(Math.toRadians(3)),2))return null;
    float[] offset={a.origin[0]-b.origin[0],a.origin[1]-b.origin[1],a.origin[2]-b.origin[2]};
    double d=CaptureGeometry.dot(a.direction,offset),e=CaptureGeometry.dot(b.direction,offset);
    double s=(dot*e-d)/den,t=(e-dot*d)/den;
    if(s<.3||s>8||t<.3||t>8)return null;
    float[] p=new float[3],q=new float[3],world=new float[3];
    for(int k=0;k<3;k++){p[k]=(float)(a.origin[k]+s*a.direction[k]);q[k]=(float)(b.origin[k]+t*b.direction[k]);world[k]=(float)(a.worldOrigin[k]+s*a.worldDirection[k]);}
    if(CaptureGeometry.distance(p,q)>.12)return null;
    if(best.validationOrigin!=null&&CaptureGeometry.distance(best.validationOrigin,b.origin)<.15)return null;
    if(best.position!=null&&CaptureGeometry.distance(best.position,p)<.2)best.confirmations++;else best.confirmations=1;
    best.position=p;
    best.validationOrigin=b.origin.clone();
    // At least two consistent triangulations (three different capture poses), not a guessed depth.
    return best.confirmations>=2?ob.positioned(world,p,LandmarkObservation.PositionSource.TRIANGULATED):null;
  }
  private boolean finite(float[] p){for(float v:p)if(!Float.isFinite(v))return false;return true;}
  private static final class Track {LandmarkObservation ob;int count=1;Track(LandmarkObservation ob){this.ob=ob;}}
  private static final class BearingTrack {
    final LandmarkObservation first;final CaptureGeometry.Ray ray;LandmarkObservation last;float[] position,validationOrigin;int confirmations;
    BearingTrack(LandmarkObservation ob,CaptureGeometry.Ray ray){first=last=ob;this.ray=ray;}
  }
}
