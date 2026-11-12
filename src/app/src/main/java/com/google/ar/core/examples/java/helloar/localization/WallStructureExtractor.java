package com.google.ar.core.examples.java.helloar.localization;

import com.google.ar.core.examples.java.helloar.segmentation.SegmentationResult;
import java.util.*;

/** Image-space wall/floor contact polylines, preserving corridor slopes and corners.
 * These are structural evidence, never invented metric wall positions or point landmarks. */
public final class WallStructureExtractor {
  public List<float[]> extract(SegmentationResult result) {
    int wall=-1,floor=-1,w=result.width,h=result.height;
    for(int i=0;i<result.names.length;i++) {
      if(result.names[i].equals("wall"))wall=i;
      if(result.names[i].equals("floor"))floor=i;
    }
    if(wall<0||floor<0)return Collections.emptyList();
    List<float[]> lines=new ArrayList<>();List<float[]> run=new ArrayList<>();
    int lastX=-10,lastY=0;
    for(int x=0;x<w;x++) {
      int boundary=-1;
      for(int y=h-2;y>=0;y--) {
        int p=y*w+x;
        if((result.labels[p]&255)==wall&&(result.labels[p+w]&255)==floor
            &&result.confidence[p]>=.6f&&result.confidence[p+w]>=.6f){boundary=y+1;break;}
      }
      if(boundary<0)continue;
      if(x-lastX>2||Math.abs(boundary-lastY)>h*.15f){finish(run,lines,w,h);run.clear();}
      run.add(new float[]{x+.5f,boundary});lastX=x;lastY=boundary;
    }
    finish(run,lines,w,h);return Collections.unmodifiableList(lines);
  }
  private void finish(List<float[]> run,List<float[]> output,int w,int h) {
    if(run.size()<Math.max(6,w/16))return;
    boolean[] keep=new boolean[run.size()];keep[0]=keep[keep.length-1]=true;
    simplify(run,0,run.size()-1,keep);
    int count=0;for(boolean value:keep)if(value)count++;
    float[] line=new float[count*2];int n=0;
    for(int i=0;i<keep.length;i++)if(keep[i]){line[n++]=run.get(i)[0]/w;line[n++]=run.get(i)[1]/h;}
    output.add(line);
  }
  private void simplify(List<float[]> points,int start,int end,boolean[] keep) {
    if(end-start<2)return;
    float[] a=points.get(start),b=points.get(end);double dx=b[0]-a[0],dy=b[1]-a[1],length=Math.hypot(dx,dy);
    double largest=1.5;int selected=-1;
    for(int i=start+1;i<end;i++) {
      float[] p=points.get(i);double distance=Math.abs(dx*(p[1]-a[1])-dy*(p[0]-a[0]))/Math.max(1e-6,length);
      if(distance>largest){largest=distance;selected=i;}
    }
    if(selected>=0){keep[selected]=true;simplify(points,start,selected,keep);simplify(points,selected,end,keep);}
  }
}
