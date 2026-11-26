package com.google.ar.core.examples.java.helloar.segmentation;

/** Copied capture-time geometry. Never contains a live ARCore Frame, Pose, or Android Image. */
public final class FrameSnapshot {
  public final long timestamp;
  public final int imageWidth, imageHeight, rotationDegrees;
  public final float[] cameraToWorld, worldToRoute, intrinsics, imageToView, imageToDepth;
  public final Depth depth;
  public final boolean tracking;

  public FrameSnapshot(long timestamp, int width, int height, int rotation, float[] cameraToWorld,
      float[] worldToRoute, float[] intrinsics, float[] imageToView, float[] imageToDepth, Depth depth, boolean tracking) {
    this.timestamp=timestamp; imageWidth=width; imageHeight=height; rotationDegrees=rotation;
    this.cameraToWorld=cameraToWorld.clone(); this.worldToRoute=worldToRoute == null ? null : worldToRoute.clone();
    this.intrinsics=intrinsics.clone(); this.imageToView=imageToView.clone(); this.imageToDepth=imageToDepth.clone();
    this.depth=depth; this.tracking=tracking;
  }
  public static final class Depth {
    public final int width,height;
    public final long timestamp;
    public final short[] millimeters;
    public Depth(int width,int height,long timestamp,short[] mm) {
      this.width=width;this.height=height;this.timestamp=timestamp;millimeters=mm.clone();
    }
  }
  public static float[] inputToImage(float u,float v,int rotation) {
    switch(rotation) {
      case 90:return new float[]{v,1-u};
      case 180:return new float[]{1-u,1-v};
      case 270:return new float[]{1-v,u};
      default:return new float[]{u,v};
    }
  }
  public static float[] affine(float[] corners,float u,float v) {
    return new float[]{corners[0]+u*(corners[2]-corners[0])+v*(corners[4]-corners[0]),
        corners[1]+u*(corners[3]-corners[1])+v*(corners[5]-corners[1])};
  }
  public static float[] transform(float[] m,float x,float y,float z) {
    return new float[]{m[0]*x+m[4]*y+m[8]*z+m[12],m[1]*x+m[5]*y+m[9]*z+m[13],m[2]*x+m[6]*y+m[10]*z+m[14]};
  }
}
