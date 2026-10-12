package com.example.modelevaluation.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import com.example.modelevaluation.model.Detection;

import java.util.List;
import java.util.Locale;

public final class DetectionOverlayView extends View {
    private static final int[] COLORS = {
            Color.rgb(67, 216, 255), Color.rgb(255, 184, 77),
            Color.rgb(168, 240, 106), Color.rgb(209, 140, 255)
    };
    private final Paint boxPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelBackgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF drawingBox = new RectF();
    private final RectF drawingLabel = new RectF();
    private List<Detection> detections = List.of();
    private int frameWidth;
    private int frameHeight;

    public DetectionOverlayView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        boxPaint.setStyle(Paint.Style.STROKE);
        boxPaint.setStrokeWidth(dp(3));
        labelPaint.setColor(Color.WHITE);
        labelPaint.setTextSize(dp(13));
        labelPaint.setFakeBoldText(true);
        labelBackgroundPaint.setStyle(Paint.Style.FILL);
    }

    public void setDetections(List<Detection> values, int sourceWidth, int sourceHeight) {
        detections = List.copyOf(values);
        frameWidth = sourceWidth;
        frameHeight = sourceHeight;
        postInvalidateOnAnimation();
    }

    public void clear() {
        detections = List.of();
        postInvalidateOnAnimation();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (frameWidth <= 0 || frameHeight <= 0) return;
        float scale = Math.max(getWidth() / (float) frameWidth, getHeight() / (float) frameHeight);
        float contentWidth = frameWidth * scale;
        float contentHeight = frameHeight * scale;
        float offsetX = (getWidth() - contentWidth) / 2f;
        float offsetY = (getHeight() - contentHeight) / 2f;
        for (Detection detection : detections) {
            RectF normalized = detection.normalizedBox();
            drawingBox.set(
                    offsetX + normalized.left * contentWidth,
                    offsetY + normalized.top * contentHeight,
                    offsetX + normalized.right * contentWidth,
                    offsetY + normalized.bottom * contentHeight);
            int color = COLORS[Math.floorMod(detection.classId(), COLORS.length)];
            boxPaint.setColor(color);
            labelBackgroundPaint.setColor(Color.argb(220, Color.red(color), Color.green(color), Color.blue(color)));
            canvas.drawRoundRect(drawingBox, dp(6), dp(6), boxPaint);
            String label = String.format(Locale.US, "%s %.0f%%", detection.label(), detection.confidence() * 100f);
            float textWidth = labelPaint.measureText(label);
            float labelTop = Math.max(0f, drawingBox.top - dp(25));
            drawingLabel.set(drawingBox.left, labelTop,
                    drawingBox.left + textWidth + dp(12), labelTop + dp(24));
            canvas.drawRoundRect(drawingLabel, dp(5), dp(5), labelBackgroundPaint);
            canvas.drawText(label, drawingBox.left + dp(6), labelTop + dp(17), labelPaint);
        }
    }

    private float dp(float value) { return value * getResources().getDisplayMetrics().density; }
}
