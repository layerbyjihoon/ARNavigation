package com.google.ar.core.examples.java.helloar.localization;

import com.google.ar.core.examples.java.helloar.segmentation.FrameSnapshot;

public final class LandmarkObservation {
  public enum PositionSource {NONE,DEPTH,TRIANGULATED}
  public final PositionSource positionSource;
  public final long trackId;
  public final boolean detectorObservation;
  public final String type;
  public final float centerX,centerY,left,top,right,bottom,confidence;
  public final int area,classId;
  public final long timestamp;
  public final FrameSnapshot frame;
  public final boolean stable,depthValid,metricValid;
  public final float[] worldPosition,routePosition;
  public LandmarkObservation(String type,float left,float top,float right,float bottom,int area,int classId,
      float confidence,FrameSnapshot frame,boolean stable,float[] world,float[] route) {
    this(type,left,top,right,bottom,area,classId,confidence,frame,stable,world,route,
        world!=null&&route!=null?PositionSource.DEPTH:PositionSource.NONE);
  }
  public LandmarkObservation(String type,float left,float top,float right,float bottom,int area,int classId,
      float confidence,FrameSnapshot frame,boolean stable,float[] world,float[] route,PositionSource source) {
    this(type,left,top,right,bottom,area,classId,confidence,frame,stable,world,route,source,-1,true);
  }
  public LandmarkObservation(String type,float left,float top,float right,float bottom,int area,int classId,
      float confidence,FrameSnapshot frame,boolean stable,float[] world,float[] route,PositionSource source,long trackId,boolean detected) {
    this.type=type;this.left=left;this.top=top;this.right=right;this.bottom=bottom;
    centerX=(left+right)/2;centerY=(top+bottom)/2;this.area=area;this.classId=classId;this.confidence=confidence;
    this.frame=frame;timestamp=frame.timestamp;this.stable=stable;worldPosition=world;routePosition=route;
    positionSource=source;metricValid=world!=null&&route!=null;depthValid=metricValid&&source==PositionSource.DEPTH;
    this.trackId=trackId;detectorObservation=detected;
  }
  public LandmarkObservation positioned(float[] world,float[] route) {
    return positioned(world,route,PositionSource.DEPTH);
  }
  public LandmarkObservation positioned(float[] world,float[] route,PositionSource source) {
    return new LandmarkObservation(type,left,top,right,bottom,area,classId,confidence,frame,stable,world,route,source,trackId,detectorObservation);
  }
}
