package com.google.ar.core.examples.java.helloar.debug;

import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.view.View;
import com.google.ar.core.examples.java.helloar.R;
import com.google.ar.core.examples.java.helloar.segmentation.*;
import com.google.ar.core.examples.java.helloar.localization.*;

/** Debug-only mask/observations/status. The normal navigation UI stays unchanged when toggles are off. */
public final class SegmentationDebugOverlay extends View {
  private boolean mask,landmarks,boundaries,status;
  private SegmentationController.Completed result;
  private Bitmap bitmap;
  private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
  private String text="";
  private long displayedAtNanos;
  public SegmentationDebugOverlay(Context context,AttributeSet attrs){super(context,attrs);setWillNotDraw(false);}
  public void configure(boolean mask,boolean landmarks,boolean boundaries,boolean status){
    this.mask=mask;this.landmarks=landmarks;this.boundaries=boundaries;this.status=status;invalidate();
  }
  public void display(SegmentationController.Completed next,String text) {
    this.text=text;
    if(result==null||next==null||result.segmentation!=next.segmentation){if(bitmap!=null){bitmap.recycle();bitmap=null;}}
    result=next;
    displayedAtNanos=android.os.SystemClock.elapsedRealtimeNanos();
    invalidate();
  }
  private static int color(String name) {
    switch(name){case "door":return 0x88F49124;case "floor":return 0x885CB245;case "wall":return 0x88479BC2;
      case "stairs":case "stairway":return 0x88D65BE8;case "column":return 0x88DFCF34;case "railing":return 0x8853B2CA;default:return 0;}
  }
  @Override protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    if(result!=null&&android.os.SystemClock.elapsedRealtimeNanos()-displayedAtNanos<2_000_000_000L) {
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
      if(boundaries)for(float[] boundary:result.wallBoundaries) {
        if(boundary.length<4)continue;
        float[] points=boundary.clone();for(int i=0;i<points.length;i+=2){points[i]*=seg.width;points[i+1]*=seg.height;}
        transform.mapPoints(points);paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeJoin(Paint.Join.ROUND);paint.setStyle(Paint.Style.STROKE);
        Path path=new Path();path.moveTo(points[0],points[1]);for(int i=2;i<points.length;i+=2)path.lineTo(points[i],points[i+1]);
        paint.setColor(Color.BLACK);paint.setStrokeWidth(11);canvas.drawPath(path,paint);
        paint.setColor(0xFFFFD54F);paint.setStrokeWidth(6);canvas.drawPath(path,paint);
      }
      if(boundaries) {
        String label="벽·바닥 경계: "+(result.wallBoundaries.isEmpty()?"미검출":result.wallBoundaries.size()+"개");
        float density=getResources().getDisplayMetrics().density;
        paint.setStyle(Paint.Style.FILL);paint.setTextSize(14*getResources().getDisplayMetrics().scaledDensity);
        View header=getRootView().findViewById(R.id.ar_header);
        float x=16*density,y=header==null?getHeight()*.32f:header.getBottom()+8*density;
        float width=paint.measureText(label)+20*density;
        paint.setColor(0xCC101820);canvas.drawRoundRect(x,y,x+width,y+32*density,8*density,8*density,paint);
        paint.setColor(0xFFFFD54F);canvas.drawText(label,x+10*density,y+22*density,paint);
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
