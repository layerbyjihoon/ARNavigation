package com.example.arnavigation.journey;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.os.SystemClock;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.core.content.ContextCompat;
import com.example.entrancetransition.MunhwaClassifier;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** One serial inference worker, sharing the outdoor CameraX session. Never opens a camera. */
public final class EntryVisionWorker implements ImageAnalysis.Analyzer {
    public interface Listener {
        void onResult(String session, double score, boolean valid, long capturedMs);
        void onError();
    }
    public final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Context context;
    private final Executor main;
    private final Listener listener;
    private volatile boolean enabled, closed, failed;
    private volatile String session;
    private long last = -1;
    private MunhwaClassifier classifier;
    public EntryVisionWorker(Context context, Listener listener) {
        this.context = context.getApplicationContext(); this.listener = listener;
        main = ContextCompat.getMainExecutor(context);
    }
    public void enable(String session) { this.session = session; enabled = true; }
    public void suspend() { enabled = false; }
    public void retry() { failed = false; enabled = true; }
    @Override public void analyze(ImageProxy image) {
        Bitmap raw = null, upright = null;
        try {
            long at = SystemClock.elapsedRealtime();
            if (!enabled || closed || failed || (last >= 0 && at - last < 334)) return;
            String currentSession = session;
            last = at;
            if (classifier == null) classifier = new MunhwaClassifier(context.getAssets());
            raw = image.toBitmap();
            Matrix rotate = new Matrix(); rotate.postRotate(image.getImageInfo().getRotationDegrees());
            upright = Bitmap.createBitmap(raw, 0, 0, raw.getWidth(), raw.getHeight(), rotate, true);
            MunhwaClassifier.Result result = classifier.classify(upright);
            main.execute(() -> { if (!closed) listener.onResult(currentSession, result.inside, result.valid, at); });
        } catch (Exception error) {
            failed = true;
            if (classifier != null) { try { classifier.close(); } catch (Exception ignored) { } classifier = null; }
            main.execute(() -> { if (!closed) listener.onError(); });
        } finally {
            if (upright != null && upright != raw) upright.recycle();
            if (raw != null) raw.recycle();
            image.close();
        }
    }
    /** Callback is issued after queued inference, model close, and image.close(). */
    public void close(Runnable completed) {
        if (closed) return;
        closed = true; enabled = false;
        executor.execute(() -> {
            if (classifier != null) { try { classifier.close(); } catch (Exception ignored) { } classifier = null; }
            main.execute(completed);
        });
        executor.shutdown();
    }
}
