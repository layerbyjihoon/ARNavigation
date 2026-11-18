package com.example.entrancetransition;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;
import androidx.camera.view.PreviewView;
import androidx.camera.view.transform.CoordinateTransform;
import androidx.camera.view.transform.OutputTransform;
import com.example.arnavigation.entrance.SegmentationMask;

/** Transparent, non-interactive semantic mask aligned to CameraX's preview transform. */
@androidx.annotation.OptIn(markerClass = androidx.camera.view.TransformExperimental.class)
public final class SegmentationOverlay extends View {
    private static final long MAX_AGE_MS = 2000;
    private static final int[] COLORS = palette();
    private final Paint paint = new Paint();
    private final Matrix maskToPreview = new Matrix();
    private final RectF viewport = new RectF();
    private final RectF sourceViewport = new RectF();
    private Bitmap bitmap;
    private OutputTransform source;
    private PreviewView preview;
    private int frameWidth, frameHeight;
    private long capturedMs;

    public SegmentationOverlay(Context context, AttributeSet attributes) {
        super(context, attributes);
        paint.setFilterBitmap(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    /** Must be called on the UI thread; invalid/low-confidence pixels remain transparent. */
    void show(SegmentationMask mask, OutputTransform source, PreviewView preview,
            int frameWidth, int frameHeight, RectF sourceViewport, long capturedMs) {
        clear();
        if (SystemClock.elapsedRealtime() - capturedMs > MAX_AGE_MS) return;
        int[] pixels = new int[mask.width * mask.height];
        for (int y = 0; y < mask.height; y++) for (int x = 0; x < mask.width; x++) {
            int label = mask.labelAt(x, y);
            pixels[y * mask.width + x] = label < 0 ? Color.TRANSPARENT : COLORS[label];
        }
        bitmap = Bitmap.createBitmap(pixels, mask.width, mask.height, Bitmap.Config.ARGB_8888);
        this.source = source;
        this.preview = preview;
        this.frameWidth = frameWidth;
        this.frameHeight = frameHeight;
        this.sourceViewport.set(sourceViewport);
        this.capturedMs = capturedMs;
        invalidate();
    }

    void clearIfExpired(long nowMs) {
        if (bitmap != null && nowMs - capturedMs > MAX_AGE_MS) clear();
    }

    void clear() {
        // Let the renderer release the previous bitmap after any pending GPU draw completes.
        bitmap = null;
        source = null;
        preview = null;
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (bitmap == null || preview == null
                || SystemClock.elapsedRealtime() - capturedMs > MAX_AGE_MS) return;
        OutputTransform target = preview.getOutputTransform();
        if (target == null) return;
        CoordinateTransform coordinates = new CoordinateTransform(source, target);
        coordinates.transform(maskToPreview);
        maskToPreview.preScale((float) frameWidth / bitmap.getWidth(),
                (float) frameHeight / bitmap.getHeight());
        // Do not paint over PreviewView's FIT_CENTER letterboxing.
        viewport.set(sourceViewport);
        coordinates.mapRect(viewport);
        int checkpoint = canvas.save();
        canvas.clipRect(viewport);
        canvas.drawBitmap(bitmap, maskToPreview, paint);
        canvas.restoreToCount(checkpoint);
    }

    private static int[] palette() {
        int[] colors = new int[SegmentationMask.CLASS_COUNT];
        for (int i = 0; i < colors.length; i++) {
            colors[i] = Color.HSVToColor(110, new float[]{(i * 137.5f) % 360, .65f, .95f});
        }
        colors[0] = 0x78E85D95; // wall: pink
        colors[3] = 0x782ED3A2; // floor: green
        colors[5] = 0x78AC80FF; // ceiling: purple
        colors[14] = 0x78FF953F; // door: orange
        colors[53] = colors[59] = 0x78FFDD38; // stairs/stairway: yellow
        colors[2] = 0x783DBDFF; // sky: blue
        colors[4] = colors[17] = 0x783C9C48; // tree/plant: dark green
        return colors;
    }
}
