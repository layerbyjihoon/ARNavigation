package com.google.ar.core.examples.java.helloar.localization;

import com.google.ar.core.examples.java.helloar.segmentation.FrameSnapshot;

/** Capture-time rays in the unchanged origin frame. No current camera pose is substituted. */
public final class CaptureGeometry {
  public static final class Ray {
    public final float[] origin,direction,worldOrigin,worldDirection;
    Ray(float[] o,float[] d,float[] wo,float[] wd){origin=o;direction=d;worldOrigin=wo;worldDirection=wd;}
  }
  public static Ray ray(FrameSnapshot f,float u,float v) {
    if(!f.tracking||f.worldToRoute==null)return null;
    float[] image=FrameSnapshot.inputToImage(u,v,f.rotationDegrees);
    float x=(image[0]*f.imageWidth-f.intrinsics[2])/f.intrinsics[0];
    float y=-(image[1]*f.imageHeight-f.intrinsics[3])/f.intrinsics[1];
    float[] wo={f.cameraToWorld[12],f.cameraToWorld[13],f.cameraToWorld[14]};
    float[] wp=FrameSnapshot.transform(f.cameraToWorld,x,y,-1);
    float[] o=FrameSnapshot.transform(f.worldToRoute,wo[0],wo[1],wo[2]);
    float[] p=FrameSnapshot.transform(f.worldToRoute,wp[0],wp[1],wp[2]);
    float[] d={p[0]-o[0],p[1]-o[1],p[2]-o[2]},wd={wp[0]-wo[0],wp[1]-wo[1],wp[2]-wo[2]};
    double length=Math.sqrt(dot(d,d));if(!Double.isFinite(length)||length<1e-6)return null;
    for(int i=0;i<3;i++){d[i]/=length;wd[i]/=length;}
    return new Ray(o,d,wo,wd);
  }
  public static double dot(float[] a,float[] b){return a[0]*b[0]+a[1]*b[1]+a[2]*b[2];}
  public static double distance(float[] a,float[] b){return Math.sqrt(Math.pow(a[0]-b[0],2)+Math.pow(a[1]-b[1],2)+Math.pow(a[2]-b[2],2));}
}
