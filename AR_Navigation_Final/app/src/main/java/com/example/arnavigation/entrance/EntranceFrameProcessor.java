package com.example.arnavigation.entrance;

import android.graphics.Bitmap;
import android.os.SystemClock;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** One in-flight frame, max 3 Hz. Call offer/close on the controller's owner (main) thread. */
public final class EntranceFrameProcessor implements AutoCloseable {
    public interface Listener {
        void onResult();
        void onError(Exception error);
    }
    private final EntranceTransitionController controller;
    private final SegformerSegmenter segmenter;
    private final Executor owner;
    private final Listener listener;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final AtomicBoolean busy = new AtomicBoolean();
    private long lastAccepted = -1;
    private volatile boolean closed;

    public EntranceFrameProcessor(EntranceTransitionController controller,
            SegformerSegmenter segmenter, Executor owner, Listener listener) {
        this.controller = java.util.Objects.requireNonNull(controller);
        this.segmenter = java.util.Objects.requireNonNull(segmenter);
        this.owner = java.util.Objects.requireNonNull(owner);
        this.listener = java.util.Objects.requireNonNull(listener);
    }

    /** Copies accepted frames only; the caller retains ownership of its bitmap. */
    public boolean offer(Bitmap uprightFullFrame, long capturedElapsedMs) {
        long now = SystemClock.elapsedRealtime();
        controller.tick(now);
        if (closed || !controller.shouldRunVision() || capturedElapsedMs < 0
                || capturedElapsedMs > now || now - capturedElapsedMs > 2000
                || (lastAccepted >= 0 && capturedElapsedMs - lastAccepted < 334)
                || !busy.compareAndSet(false, true)) return false;
        Bitmap snapshot;
        try {
            snapshot = uprightFullFrame.copy(Bitmap.Config.ARGB_8888, false);
            if (snapshot == null) throw new IllegalArgumentException("Cannot copy camera frame");
        } catch (RuntimeException error) { busy.set(false); throw error; }
        lastAccepted = capturedElapsedMs;
        long generation = controller.generation();
        worker.execute(() -> {
            SegmentationMask mask = null;
            Exception failure = null;
            try { mask = segmenter.run(snapshot); }
            catch (Exception error) { failure = error; }
            finally { snapshot.recycle(); }
            SegmentationMask result = mask;
            Exception error = failure;
            owner.execute(() -> {
                busy.set(false);
                if (closed || generation != controller.generation()) return;
                if (error != null) listener.onError(error);
                else {
                    controller.mask(generation, capturedElapsedMs, SystemClock.elapsedRealtime(), result);
                    listener.onResult();
                }
            });
        });
        return true;
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        // Queue close AFTER in-flight inference; do not close native session while it is running.
        worker.execute(() -> {
            try { segmenter.close(); }
            catch (Exception ignored) { /* Already inactive; no navigation decision is made. */ }
        });
        worker.shutdown();
    }
}
