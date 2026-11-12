package com.google.ar.core.examples.java.helloar.localization;

/** GL-owned correction. Only confirmed, fresh, bounded transforms can change its target. */
public final class RouteAlignmentManager {
  private Rigid2D applied=Rigid2D.identity(),target=Rigid2D.identity();
  private long lastTick,lastAccepted;
  public Rigid2D current(){return applied;}
  public void reset(){applied=target=Rigid2D.identity();lastTick=lastAccepted=0;}
  public boolean accept(MatchResult result,long now) {
    if(result==null||result.status!=MatchResult.Status.MATCH_CONFIRMED||result.transform==null
        ||result.timestamp<=lastAccepted||now<result.timestamp||now-result.timestamp>2_000_000_000L)return false;
    Rigid2D next=result.transform;
    if(!Double.isFinite(next.x)||!Double.isFinite(next.z)||!Double.isFinite(next.yaw)
        ||Math.hypot(next.x,next.z)>1.5||Math.abs(next.yaw)>Math.toRadians(10)
        ||Math.hypot(next.x-target.x,next.z-target.z)>.5||Math.abs(Rigid2D.wrap(next.yaw-target.yaw))>Math.toRadians(5))return false;
    target=next;lastAccepted=result.timestamp;return true;
  }
  public void tick(long now,boolean tracking) {
    double dt=lastTick==0?0:Math.min(.1,Math.max(0,(now-lastTick)/1e9));lastTick=now;
    if(!tracking)return;
    double dx=target.x-applied.x,dz=target.z-applied.z,distance=Math.hypot(dx,dz);
    double factor=distance==0?1:Math.min(1,.15*dt/distance);
    double angle=Rigid2D.wrap(target.yaw-applied.yaw),step=Math.copySign(Math.min(Math.abs(angle),Math.toRadians(2)*dt),angle);
    applied=new Rigid2D(applied.yaw+step,applied.x+factor*dx,applied.z+factor*dz);
  }
}
