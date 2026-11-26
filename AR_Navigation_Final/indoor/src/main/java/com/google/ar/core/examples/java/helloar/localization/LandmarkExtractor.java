package com.google.ar.core.examples.java.helloar.localization;

import com.google.ar.core.examples.java.helloar.segmentation.SegmentationResult;
import java.util.*;

/** Semantic components are candidates, not an asserted count of real physical doors. */
public final class LandmarkExtractor {
  private List<LandmarkObservation> previous=Collections.emptyList();
  public List<LandmarkObservation> extract(SegmentationResult result) {
    int w=result.width,h=result.height;
    boolean[] seen=new boolean[w*h];int[] queue=new int[w*h];
    List<LandmarkObservation> observations=new ArrayList<>();
    for(int start=0;start<w*h;start++) {
      int id=result.labels[start]&255;
      String name=result.names[id];
      String type=name.equals("door")?"DOOR":name.equals("stairs")||name.equals("stairway")?"STAIRS":
          name.equals("column")?"COLUMN":name.equals("wall")?"WALL":null;
      if(seen[start]||type==null)continue;
      int head=0,tail=1;queue[0]=start;seen[start]=true;
      int minX=w,minY=h,maxX=0,maxY=0;double confidence=0;
      while(head<tail) {
        int p=queue[head++],x=p%w,y=p/w;
        minX=Math.min(minX,x);maxX=Math.max(maxX,x);minY=Math.min(minY,y);maxY=Math.max(maxY,y);
        confidence+=result.confidence[p];
        for(int dy=-1;dy<=1;dy++)for(int dx=-1;dx<=1;dx++) {
          int xx=x+dx,yy=y+dy;if(xx<0||yy<0||xx>=w||yy>=h)continue;
          int next=yy*w+xx;
          if(!seen[next]&&(result.labels[next]&255)==id){seen[next]=true;queue[tail++]=next;}
        }
      }
      // Low-score door/stair regions are tentative tracker seeds, not approved map evidence.
      double threshold=type.equals("DOOR")||type.equals("STAIRS")?.45:.6;
      if(tail<Math.max(16,w*h/1000)||confidence/tail<threshold)continue;
      // Wall components are structural evidence only; never matched as point landmarks.
      float left=(float)minX/w,top=(float)minY/h,right=(maxX+1f)/w,bottom=(maxY+1f)/h;
      boolean stable=false;
      for(LandmarkObservation old:previous)
        if(old.type.equals(type)&&result.frame.timestamp>old.timestamp&&result.frame.timestamp-old.timestamp<1_500_000_000L
            &&Math.hypot(old.centerX-(left+right)/2,old.centerY-(top+bottom)/2)<.10
            &&tail/(double)old.area>.4&&tail/(double)old.area<2.5){stable=true;break;}
      observations.add(new LandmarkObservation(type,left,top,right,bottom,tail,id,(float)(confidence/tail),result.frame,stable,null,null));
    }
    observations.sort(Comparator.comparingDouble((LandmarkObservation o)->-o.confidence));
    previous=observations;return observations;
  }
  public void reset(){previous=Collections.emptyList();}
}
