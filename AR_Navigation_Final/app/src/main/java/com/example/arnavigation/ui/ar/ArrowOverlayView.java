package com.example.arnavigation.ui.ar;

import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.view.View;
import androidx.annotation.Nullable;
import com.example.arnavigation.R;
import com.example.arnavigation.navigation.NavigationUiState;

/** One direction arrow; no world-space path or simulated ground-plane projection. */
public final class ArrowOverlayView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private double relativeAngleDegrees;
    private boolean guidanceAvailable;
    public ArrowOverlayView(Context context) { this(context,null); }
    public ArrowOverlayView(Context context,@Nullable AttributeSet attrs) {
        super(context,attrs); setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
    }
    public void setRoute(NavigationUiState state) {
        guidanceAvailable=state.isArrowVisible(); invalidate();
    }
    public void setDirection(double angle,String description) {
        if(!Double.isFinite(angle)) throw new IllegalArgumentException("relativeAngleDegrees must be finite");
        relativeAngleDegrees=angle; setContentDescription(description); invalidate();
    }
    public double getRelativeAngleDegrees() { return relativeAngleDegrees; }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if(!guidanceAvailable) return;
        float dp=getResources().getDisplayMetrics().density;
        RectF safe=new RectF(12*dp,12*dp,getWidth()-12*dp,getHeight()-12*dp);
        if(getParent() instanceof View parent) {
            View header=parent.findViewById(R.id.ar_header_card),footer=parent.findViewById(R.id.ar_guidance_card);
            if(header!=null && header.getVisibility()==VISIBLE && header.getHeight()>0)
                safe.top=Math.max(safe.top,header.getBottom()-getTop()+12*dp);
            if(footer!=null && footer.getVisibility()==VISIBLE && footer.getHeight()>0)
                safe.bottom=Math.min(safe.bottom,footer.getTop()-getTop()-12*dp);
        }
        if(safe.width()<=0 || safe.height()<=0) return;
        float scale=Math.min(dp,Math.min(safe.width(),safe.height())/120f);
        int saved=canvas.save();
        canvas.clipRect(safe);
        canvas.translate(safe.centerX(),safe.centerY());
        canvas.rotate((float)relativeAngleDegrees);
        canvas.scale(scale,scale);
        path.reset(); path.moveTo(0,-44); path.lineTo(32,-8); path.lineTo(9,-8);
        path.lineTo(9,42); path.lineTo(-9,42); path.lineTo(-9,-8); path.lineTo(-32,-8); path.close();
        paint.setStyle(Paint.Style.FILL); paint.setColor(0xff0ae0ff); canvas.drawPath(path,paint);
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(2); paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setColor(0xff041f29); canvas.drawPath(path,paint);
        canvas.restoreToCount(saved);
    }
}
