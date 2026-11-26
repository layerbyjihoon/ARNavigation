package com.google.ar.core.examples.java.helloar.localization;

import com.google.ar.core.examples.java.helloar.segmentation.*;
import java.util.*;

/** Median same-class surface samples; missing depth never becomes a guessed metric position. */
public final class LandmarkPositionEstimator {
  public LandmarkObservation estimate(LandmarkObservation ob,SegmentationResult result) {
    FrameSnapshot f=ob.frame;
    if(ob.type.equals("WALL")||f.depth==null||f.worldToRoute==null||!f.tracking
        ||Math.abs(f.depth.timestamp-f.timestamp)>50_000_000L)return ob;
    List<float[]> points=new ArrayList<>();List<Float> distances=new ArrayList<>();
    for(int yy=-1;yy<=1;yy++)for(int xx=-1;xx<=1;xx++) {
      float u=ob.centerX+xx*(ob.right-ob.left)*.18f,v=ob.centerY+yy*(ob.bottom-ob.top)*.18f;
      int mx=Math.max(0,Math.min(result.width-1,(int)(u*result.width))),my=Math.max(0,Math.min(result.height-1,(int)(v*result.height)));
      if((result.labels[my*result.width+mx]&255)!=ob.classId)continue;
      float[] image=FrameSnapshot.inputToImage(u,v,f.rotationDegrees);
      float[] depth=FrameSnapshot.affine(f.imageToDepth,image[0],image[1]);
      if(depth[0]<0||depth[0]>=1||depth[1]<0||depth[1]>=1)continue;
      int dx=(int)(depth[0]*f.depth.width),dy=(int)(depth[1]*f.depth.height);
      float meters=(f.depth.millimeters[dy*f.depth.width+dx]&65535)/1000f;
      if(meters<.3||meters>8)continue;
      float px=image[0]*f.imageWidth,py=image[1]*f.imageHeight;
      float[] world=FrameSnapshot.transform(f.cameraToWorld,(px-f.intrinsics[2])*meters/f.intrinsics[0],
          -(py-f.intrinsics[3])*meters/f.intrinsics[1],-meters);
      points.add(world);distances.add(meters);
    }
    if(points.size()<5)return ob;
    List<Float> sorted=new ArrayList<>(distances);Collections.sort(sorted);float median=sorted.get(sorted.size()/2);
    float[] world=new float[3];int count=0;
    for(int i=0;i<points.size();i++)if(Math.abs(distances.get(i)-median)<.25f){
      count++;for(int k=0;k<3;k++)world[k]+=points.get(i)[k];
    }
    if(count<5)return ob;
    for(int k=0;k<3;k++)world[k]/=count;
    return ob.positioned(world,FrameSnapshot.transform(f.worldToRoute,world[0],world[1],world[2]));
  }
}
