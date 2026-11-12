package com.google.ar.core.examples.java.helloar.localization;

import java.util.*;

/** Pyramidal Lucas-Kanade with forward/backward checks and robust similarity consensus. Pure Java. */
public final class SparseFeatureFlow {
  public static final class Motion {
    public final double a,b,tx,ty;public final List<float[]> points;public final double residual;
    Motion(double a,double b,double tx,double ty,List<float[]> points,double residual){this.a=a;this.b=b;this.tx=tx;this.ty=ty;this.points=points;this.residual=residual;}
    public float[] apply(float x,float y){return new float[]{(float)(a*x-b*y+tx),(float)(b*x+a*y+ty)};}
  }
  public List<float[]> corners(TrackingImage image,float left,float top,float right,float bottom){
    List<float[]> candidates=new ArrayList<>();
    int x0=Math.max(8,(int)(left*image.width)),x1=Math.min(image.width-8,(int)(right*image.width));
    int y0=Math.max(8,(int)(top*image.height)),y1=Math.min(image.height-8,(int)(bottom*image.height));
    for(int y=y0;y<y1;y+=3)for(int x=x0;x<x1;x+=3){
      double xx=0,xy=0,yy=0;
      for(int j=-1;j<=1;j++)for(int i=-1;i<=1;i++){
        double gx=(image.sample(0,x+i+1,y+j)-image.sample(0,x+i-1,y+j))*.5,gy=(image.sample(0,x+i,y+j+1)-image.sample(0,x+i,y+j-1))*.5;
        xx+=gx*gx;xy+=gx*gy;yy+=gy*gy;
      }
      double score=(xx+yy-Math.sqrt((xx-yy)*(xx-yy)+4*xy*xy))*.5;
      if(score>80)candidates.add(new float[]{x,y,(float)score});
    }
    candidates.sort(Comparator.comparingDouble((float[] p)->-p[2]));List<float[]> selected=new ArrayList<>();
    for(float[] p:candidates){boolean close=false;for(float[] old:selected)if(Math.hypot(p[0]-old[0],p[1]-old[1])<5){close=true;break;}
      if(!close)selected.add(new float[]{p[0],p[1]});if(selected.size()>=24)break;}
    return selected;
  }
  private float[] follow(TrackingImage from,TrackingImage to,float x,float y){
    float qx=x/4,qy=y/4;
    for(int level=2;level>=0;level--){
      float px=x/(1<<level),py=y/(1<<level);if(level<2){qx*=2;qy*=2;}
      int w=from.width>>level,h=from.height>>level;
      if(px<5||py<5||px>=w-5||py>=h-5)return null;
      for(int iteration=0;iteration<10;iteration++){
        if(qx<5||qy<5||qx>=w-5||qy>=h-5)return null;
        double xx=0,xy=0,yy=0,ex=0,ey=0;
        for(int j=-3;j<=3;j++)for(int i=-3;i<=3;i++){
          double gx=(from.sample(level,px+i+1,py+j)-from.sample(level,px+i-1,py+j))*.5;
          double gy=(from.sample(level,px+i,py+j+1)-from.sample(level,px+i,py+j-1))*.5;
          double error=to.sample(level,qx+i,qy+j)-from.sample(level,px+i,py+j);
          xx+=gx*gx;xy+=gx*gy;yy+=gy*gy;ex+=gx*error;ey+=gy*error;
        }
        double determinant=xx*yy-xy*xy;if(determinant<1e3)return null;
        double dx=(-yy*ex+xy*ey)/determinant,dy=(xy*ex-xx*ey)/determinant;
        if(!Double.isFinite(dx)||Math.hypot(dx,dy)>5)return null;
        qx+=dx;qy+=dy;if(dx*dx+dy*dy<.0025)break;
      }
    }
    double error=0;for(int j=-2;j<=2;j++)for(int i=-2;i<=2;i++)error+=Math.abs(from.sample(0,x+i,y+j)-to.sample(0,qx+i,qy+j));
    return error/25<25?new float[]{qx,qy}:null;
  }
  public Motion motion(TrackingImage from,TrackingImage to,List<float[]> points){
    List<float[]> pairs=new ArrayList<>();
    for(float[] p:points){float[] q=follow(from,to,p[0],p[1]);if(q==null)continue;float[] back=follow(to,from,q[0],q[1]);
      if(back!=null&&Math.hypot(back[0]-p[0],back[1]-p[1])<1)pairs.add(new float[]{p[0],p[1],q[0],q[1]});}
    if(pairs.size()<6)return null;
    List<float[]> best=Collections.emptyList();
    for(int i=0;i<pairs.size();i++)for(int j=i+1;j<Math.min(pairs.size(),i+6);j++){
      float[] p=pairs.get(i),q=pairs.get(j);double dx=q[0]-p[0],dy=q[1]-p[1],den=dx*dx+dy*dy;if(den<36)continue;
      double ux=q[2]-p[2],uy=q[3]-p[3],a=(dx*ux+dy*uy)/den,b=(dx*uy-dy*ux)/den;
      if(Math.hypot(a,b)<.7||Math.hypot(a,b)>1.4||Math.abs(Math.atan2(b,a))>Math.toRadians(15))continue;
      double tx=p[2]-a*p[0]+b*p[1],ty=p[3]-b*p[0]-a*p[1];List<float[]> inliers=new ArrayList<>();
      for(float[] pair:pairs)if(Math.hypot(a*pair[0]-b*pair[1]+tx-pair[2],b*pair[0]+a*pair[1]+ty-pair[3])<1.8)inliers.add(pair);
      if(inliers.size()>best.size())best=inliers;
    }
    if(best.size()<6||best.size()<pairs.size()*.6)return null;
    double px=0,py=0,qx=0,qy=0;for(float[] p:best){px+=p[0];py+=p[1];qx+=p[2];qy+=p[3];}
    px/=best.size();py/=best.size();qx/=best.size();qy/=best.size();double dot=0,cross=0,den=0;
    for(float[] p:best){double x=p[0]-px,y=p[1]-py,u=p[2]-qx,v=p[3]-qy;dot+=x*u+y*v;cross+=x*v-y*u;den+=x*x+y*y;}
    if(den<80)return null;double a=dot/den,b=cross/den,tx=qx-a*px+b*py,ty=qy-b*px-a*py,error=0;List<float[]> moved=new ArrayList<>();
    for(float[] p:best){error+=Math.pow(a*p[0]-b*p[1]+tx-p[2],2)+Math.pow(b*p[0]+a*p[1]+ty-p[3],2);moved.add(new float[]{p[2],p[3]});}
    return new Motion(a,b,tx,ty,moved,Math.sqrt(error/best.size()));
  }
}
