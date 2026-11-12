package com.google.ar.core.examples.java.helloar.localization;

import com.google.ar.core.examples.java.helloar.segmentation.SegmentationResult;
import java.util.*;

/** Wall/floor contact directions on the AR-origin floor plane, not screen-line angles used as yaw. */
public final class CorridorDirectionEstimator {
  public static final class Observation {
    public final double yaw,confidence,length;public final long timestamp;
    public Observation(double yaw,double confidence,double length,long timestamp){this.yaw=yaw;this.confidence=confidence;this.length=length;this.timestamp=timestamp;}
  }
  private final double floorY;
  public CorridorDirectionEstimator(){this(-.52);}
  public CorridorDirectionEstimator(double floorY){this.floorY=floorY;}
  public static double axisDifference(double a,double b){double d=Rigid2D.wrap(a-b);if(d>Math.PI/2)d-=Math.PI;if(d< -Math.PI/2)d+=Math.PI;return d;}
  public Observation estimate(SegmentationResult result,List<float[]> boundaries,double expectedYaw) {
    Observation best=null;double bestError=Math.toRadians(15);
    for(float[] line:boundaries)for(int i=2;i<line.length;i+=2) {
      float u=line[i-2],v=line[i-1],s=line[i],t=line[i+1];
      double pixels=Math.hypot((s-u)*result.width,(t-v)*result.height);
      if(pixels<12)continue;
      float[] a=onFloor(CaptureGeometry.ray(result.frame,u,v)),b=onFloor(CaptureGeometry.ray(result.frame,s,t));
      if(a==null||b==null)continue;
      double length=Math.hypot(b[0]-a[0],b[2]-a[2]);if(length<1.5||length>12)continue;
      double yaw=Math.atan2(-(b[0]-a[0]),-(b[2]-a[2]));double error=Math.abs(axisDifference(yaw,expectedYaw));
      if(error<bestError){bestError=error;best=new Observation(yaw,Math.min(.95,.75+pixels/200),length,result.frame.timestamp);}
    }
    return best;
  }
  private float[] onFloor(CaptureGeometry.Ray ray) {
    if(ray==null)return null;double height=ray.origin[1]-floorY;
    if(height<.6||height>2.5||ray.direction[1]>-.04)return null;
    double distance=(floorY-ray.origin[1])/ray.direction[1];if(distance<.5||distance>10)return null;
    return new float[]{(float)(ray.origin[0]+distance*ray.direction[0]),(float)floorY,(float)(ray.origin[2]+distance*ray.direction[2])};
  }
}
