package com.google.ar.core.examples.java.helloar.localization;

import com.google.ar.core.examples.java.helloar.segmentation.*;

/** Small, copied grayscale image in the same normalized upright space as the segmentation input. */
public final class TrackingImage {
  public final int width,height;public final float[][] pyramid;public final FrameSnapshot frame;
  public TrackingImage(int width,int height,byte[] gray,FrameSnapshot frame){
    if(width<32||height<32||gray.length!=width*height)throw new IllegalArgumentException("Tracking image shape");
    this.width=width;this.height=height;this.frame=frame;pyramid=new float[3][];
    pyramid[0]=new float[gray.length];for(int i=0;i<gray.length;i++)pyramid[0][i]=gray[i]&255;
    for(int level=1;level<3;level++){
      int w=width>>(level-1),h=height>>(level-1),nw=width>>level,nh=height>>level;pyramid[level]=new float[nw*nh];
      for(int y=0;y<nh;y++)for(int x=0;x<nw;x++)pyramid[level][y*nw+x]=.25f*(pyramid[level-1][2*y*w+2*x]+pyramid[level-1][2*y*w+2*x+1]+pyramid[level-1][(2*y+1)*w+2*x]+pyramid[level-1][(2*y+1)*w+2*x+1]);
    }
  }
  public static TrackingImage fromYuv(CameraFramePreprocessor.Yuv image,FrameSnapshot frame){
    int size=256;byte[] gray=new byte[size*size];
    for(int y=0;y<size;y++)for(int x=0;x<size;x++){
      float u=(x+.5f)/size,v=(y+.5f)/size,iu=u,iv=v;
      switch(frame.rotationDegrees){case 90:iu=v;iv=1-u;break;case 180:iu=1-u;iv=1-v;break;case 270:iu=1-v;iv=u;break;default:break;}
      int xx=Math.min(image.width-1,(int)(iu*image.width)),yy=Math.min(image.height-1,(int)(iv*image.height));
      gray[y*size+x]=image.y.bytes[yy*image.y.rowStride+xx*image.y.pixelStride];
    }
    return new TrackingImage(size,size,gray,frame);
  }
  float sample(int level,float x,float y){
    int w=width>>level,h=height>>level;int xx=(int)x,yy=(int)y;
    if(xx<0||yy<0||xx+1>=w||yy+1>=h)return Float.NaN;
    float a=x-xx,b=y-yy;float[] p=pyramid[level];
    return (p[yy*w+xx]*(1-a)+p[yy*w+xx+1]*a)*(1-b)+(p[(yy+1)*w+xx]*(1-a)+p[(yy+1)*w+xx+1]*a)*b;
  }
}
