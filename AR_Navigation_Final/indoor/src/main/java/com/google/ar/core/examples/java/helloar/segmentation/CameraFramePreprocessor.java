package com.google.ar.core.examples.java.helloar.segmentation;

import java.nio.FloatBuffer;

/** Stride-aware full-range YUV420 -> RGB -> bilinear 512 square -> ImageNet NCHW. */
public final class CameraFramePreprocessor {
  public static final class Plane {
    public final byte[] bytes; public final int rowStride,pixelStride;
    public Plane(byte[] bytes,int row,int pixel){this.bytes=bytes;rowStride=row;pixelStride=pixel;}
    int at(int x,int y){int i=y*rowStride+x*pixelStride; if(i<0||i>=bytes.length)throw new IllegalArgumentException("YUV stride bounds");return bytes[i]&255;}
  }
  public static final class Yuv {
    public final int width,height; public final Plane y,u,v;
    public Yuv(int width,int height,Plane y,Plane u,Plane v){this.width=width;this.height=height;this.y=y;this.u=u;this.v=v;}
  }
  public static float[] rgb(Yuv image,int x,int y) {
    float[] result=new float[3];rgb(image,x,y,result);return result;
  }
  private static void rgb(Yuv image,int x,int y,float[] result) {
    int yy=image.y.at(x,y),u=image.u.at(x/2,y/2)-128,v=image.v.at(x/2,y/2)-128;
    result[0]=clamp(yy+1.402f*v);result[1]=clamp(yy-.344136f*u-.714136f*v);result[2]=clamp(yy+1.772f*u);
  }
  private static float clamp(float x){return Math.max(0,Math.min(255,x));}
  public static void writeNchw(Yuv image,int rotation,int width,int height,float[] mean,float[] std,FloatBuffer output) {
    int plane=width*height;
    if(output.capacity()<plane*3)throw new IllegalArgumentException("tensor capacity");
    // Reuse scratch space: a 512-square frame must not allocate a million tiny arrays.
    float[] a=new float[3],b=new float[3],c=new float[3],d=new float[3];
    for(int y=0;y<height;y++)for(int x=0;x<width;x++) {
      float u=(x+.5f)/width,v=(y+.5f)/height,iu=u,iv=v;
      switch(rotation){case 90:iu=v;iv=1-u;break;case 180:iu=1-u;iv=1-v;break;case 270:iu=1-v;iv=u;break;default:break;}
      float px=Math.max(0,Math.min(image.width-1,iu*image.width-.5f));
      float py=Math.max(0,Math.min(image.height-1,iv*image.height-.5f));
      int x0=(int)px,y0=(int)py,x1=Math.min(x0+1,image.width-1),y1=Math.min(y0+1,image.height-1);
      float tx=px-x0,ty=py-y0;
      rgb(image,x0,y0,a);rgb(image,x1,y0,b);rgb(image,x0,y1,c);rgb(image,x1,y1,d);
      for(int k=0;k<3;k++) {
        float value=(a[k]*(1-tx)+b[k]*tx)*(1-ty)+(c[k]*(1-tx)+d[k]*tx)*ty;
        output.put(k*plane+y*width+x,(value/255f-mean[k])/std[k]);
      }
    }
    output.position(0);output.limit(plane*3);
  }
}
