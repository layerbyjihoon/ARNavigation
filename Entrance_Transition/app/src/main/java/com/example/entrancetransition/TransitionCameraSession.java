package com.example.entrancetransition;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.os.SystemClock;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.core.UseCaseGroup;
import androidx.camera.core.ViewPort;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.camera.view.transform.ImageProxyTransformFactory;
import androidx.camera.view.transform.OutputTransform;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** One camera and one serial model worker per foreground session. Close on the main thread. */
@androidx.annotation.OptIn(markerClass = androidx.camera.view.TransformExperimental.class)
final class TransitionCameraSession implements AutoCloseable {
    interface Listener {
        void onReady();
        void onResult(MunhwaClassifier.Result result, long capturedMs,
                OutputTransform source, int frameWidth, int frameHeight, RectF sourceViewport);
        void onError(int messageResource);
    }
    private static final long MIN_FRAME_INTERVAL_MS = 334;
    private final Context context;
    private final LifecycleOwner owner;
    private final PreviewView view;
    private final Listener listener;
    private final Executor main;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private volatile boolean closed;
    private MunhwaClassifier classifier;
    private ProcessCameraProvider provider;
    private ImageAnalysis analysis;
    private long lastFrameMs = -1;

    TransitionCameraSession(Context context, LifecycleOwner owner, PreviewView view, Listener listener) {
        this.context = context.getApplicationContext();
        this.owner = owner;
        this.view = view;
        this.listener = listener;
        main = ContextCompat.getMainExecutor(context);
    }

    void start() {
        worker.execute(() -> {
            try {
                classifier = new MunhwaClassifier(context.getAssets());
                main.execute(() -> { if (!closed) bindCamera(); });
            } catch (Exception error) { fail(R.string.model_error); }
        });
    }

    private void bindCamera() {
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(context);
        future.addListener(() -> {
            if (closed) return;
            try {
                provider = future.get();
                Preview preview = new Preview.Builder().build();
                view.setImplementationMode(PreviewView.ImplementationMode.COMPATIBLE);
                view.setScaleType(PreviewView.ScaleType.FIT_CENTER);
                preview.setSurfaceProvider(view.getSurfaceProvider());
                analysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();
                analysis.setAnalyzer(worker, this::analyze);
                ViewPort viewPort = view.getViewPort();
                if (viewPort == null) throw new IllegalStateException("Preview is not laid out");
                UseCaseGroup group = new UseCaseGroup.Builder().setViewPort(viewPort)
                        .addUseCase(preview).addUseCase(analysis).build();
                provider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, group);
                listener.onReady();
            } catch (Exception error) { fail(R.string.camera_error); }
        }, main);
    }

    private void analyze(ImageProxy image) {
        Bitmap raw = null;
        Bitmap upright = null;
        try {
            long capturedMs = SystemClock.elapsedRealtime();
            if (closed || (lastFrameMs >= 0 && capturedMs - lastFrameMs < MIN_FRAME_INTERVAL_MS)) return;
            lastFrameMs = capturedMs;
            raw = image.toBitmap();
            int rotation = image.getImageInfo().getRotationDegrees();
            Matrix transform = new Matrix();
            transform.postRotate(rotation);
            upright = Bitmap.createBitmap(raw, 0, 0, raw.getWidth(), raw.getHeight(), transform, true);
            ImageProxyTransformFactory factory = new ImageProxyTransformFactory();
            factory.setUsingCropRect(false); // Model sees the entire buffer, not a cropped preview.
            factory.setUsingRotationDegrees(true); // Mask is based on the upright bitmap.
            OutputTransform source = factory.getOutputTransform(image);
            int frameWidth = upright.getWidth();
            int frameHeight = upright.getHeight();
            RectF sourceViewport = new RectF(image.getCropRect());
            RectF fullFrame = new RectF(0, 0, raw.getWidth(), raw.getHeight());
            transform.mapRect(sourceViewport);
            transform.mapRect(fullFrame);
            sourceViewport.offset(-fullFrame.left, -fullFrame.top);
            MunhwaClassifier.Result result = classifier.classify(upright);
            main.execute(() -> {
                if (!closed) listener.onResult(result, capturedMs, source, frameWidth, frameHeight,
                        sourceViewport);
            });
        } catch (Exception error) { fail(R.string.inference_error); }
        finally {
            if (upright != null && upright != raw) upright.recycle();
            if (raw != null) raw.recycle();
            image.close();
        }
    }

    private void fail(int messageResource) {
        main.execute(() -> { if (!closed) listener.onError(messageResource); });
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        if (analysis != null) analysis.clearAnalyzer();
        if (provider != null) provider.unbindAll();
        worker.execute(() -> {
            if (classifier != null) {
                try { classifier.close(); }
                catch (Exception error) { android.util.Log.w("TransitionSession", "Model close failed", error); }
            }
        });
        worker.shutdown();
    }
}
