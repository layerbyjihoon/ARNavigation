package com.google.ar.core.examples.java.helloar.debug;

import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.view.View;
import com.google.ar.core.examples.java.helloar.segmentation.*;
import com.google.ar.core.examples.java.helloar.localization.*;

/** Debug-only mask/observations/status. The normal navigation UI stays unchanged when toggles are off. */
public final class SegmentationDebugOverlay extends View {
  private boolean mask,landmarks,status;
  private SegmentationController.Completed result;
  private Bitmap bitmap;
  private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
  private String text="";
  public SegmentationDebugOverlay(Context context,AttributeSet attrs){super(context,attrs);setWillNotDraw(false);}
  public void configure(boolean mask,boolean landmarks,boolean status){this.mask=mask;this.landmarks=landmarks;this.status=status;invalidate();}
  public void display(SegmentationController.Completed next,String text) {
    this.text=text;
    if(result==null||next==null||result.segmentation!=next.segmentation){if(bitmap!=null){bitmap.recycle();bitmap=null;}}
    result=next;
    invalidate();
  }
  private static int color(String name) {
    switch(name){case "door":return 0x88F49124;case "floor":return 0x885CB245;case "wall":return 0x88479BC2;
      case "stairs":case "stairway":return 0x88D65BE8;case "column":return 0x88DFCF34;case "railing":return 0x8853B2CA;default:return 0;}
  }
  @Override protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    if(result!=null&&android.os.SystemClock.elapsedRealtimeNanos()-result.segmentation.frame.timestamp<2_000_000_000L) {
      SegmentationResult seg=result.segmentation;FrameSnapshot frame=seg.frame;
      float[] source={0,0,seg.width,0,0,seg.height},destination=new float[6];
      for(int i=0;i<3;i++) {
        float[] p=FrameSnapshot.inputToImage(i==1?1:0,i==2?1:0,frame.rotationDegrees);
        p=FrameSnapshot.affine(frame.imageToView,p[0],p[1]);destination[2*i]=p[0]*getWidth();destination[2*i+1]=p[1]*getHeight();
      }
      Matrix transform=new Matrix();transform.setPolyToPoly(source,0,destination,0,3);
      if(mask) {
        if(bitmap==null){int[] pixels=new int[seg.labels.length];for(int i=0;i<pixels.length;i++)pixels[i]=color(seg.names[seg.labels[i]&255]);
          bitmap=Bitmap.createBitmap(pixels,seg.width,seg.height,Bitmap.Config.ARGB_8888);}
        paint.setStyle(Paint.Style.FILL);paint.setAlpha(255);canvas.drawBitmap(bitmap,transform,paint);
      }
      if(landmarks)for(LandmarkObservation ob:result.observations) {
        if(ob.type.equals("WALL"))continue;
        float[] center=FrameSnapshot.inputToImage(ob.centerX,ob.centerY,ob.frame.rotationDegrees);
        center=FrameSnapshot.affine(ob.frame.imageToView,center[0],center[1]);center[0]*=getWidth();center[1]*=getHeight();
        paint.setColor(ob.depthValid?Color.GREEN:Color.YELLOW);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(3);
        canvas.drawCircle(center[0],center[1],14,paint);paint.setStyle(Paint.Style.FILL);paint.setTextSize(28);
        canvas.drawText(ob.type+String.format(java.util.Locale.US," #%d %.2f %s%s",ob.trackId,ob.confidence,ob.stable?"확인 ":"후보 ",ob.depthValid?"3D":"2D"),center[0]+16,center[1],paint);
        if(ob.worldPosition!=null)canvas.drawText(String.format(java.util.Locale.US,"world %.2f %.2f %.2f",ob.worldPosition[0],ob.worldPosition[1],ob.worldPosition[2]),center[0]+16,center[1]+30,paint);
      }
      if(landmarks)for(float[] boundary:result.wallBoundaries) {
        float[] points=boundary.clone();for(int i=0;i<points.length;i+=2){points[i]*=seg.width;points[i+1]*=seg.height;}
        transform.mapPoints(points);paint.setColor(Color.CYAN);paint.setStrokeWidth(4);paint.setStyle(Paint.Style.STROKE);
        Path path=new Path();path.moveTo(points[0],points[1]);for(int i=2;i<points.length;i+=2)path.lineTo(points[i],points[i+1]);
        canvas.drawPath(path,paint);
      }
    }
    if(status){
      paint.setTextSize(12*getResources().getDisplayMetrics().scaledDensity);
      java.util.List<String> lines=new java.util.ArrayList<>();
      for(String value:text.split("\n"))while(!value.isEmpty()) {
        int n=paint.breakText(value,true,Math.max(1,getWidth()-48),null);if(n==0)break;
        lines.add(value.substring(0,n));value=value.substring(n);
      }
      float top=getHeight()*.24f,spacing=paint.getTextSize()*1.25f;
      paint.setStyle(Paint.Style.FILL);paint.setColor(0xD0101820);canvas.drawRect(12,top,getWidth()-12,top+spacing*(lines.size()+1),paint);
      paint.setColor(Color.WHITE);for(int i=0;i<lines.size();i++)canvas.drawText(lines.get(i),24,top+spacing*(i+1),paint);
    }
  }
}
