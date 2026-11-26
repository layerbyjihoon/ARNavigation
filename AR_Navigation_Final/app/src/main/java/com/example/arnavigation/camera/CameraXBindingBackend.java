package com.example.arnavigation.camera;

import android.content.Context;
import androidx.camera.core.CameraInfoUnavailableException;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;

/** Thin CameraX adapter; all callbacks run on the main executor. */
final class CameraXBindingBackend implements CameraBindingBackend {
    private final Context context;
    private final Executor mainExecutor;
    private ProcessCameraProvider cameraProvider;
    private long generation;
    private final androidx.camera.core.ImageAnalysis.Analyzer analyzer;
    private final Executor analysisExecutor;
    private androidx.camera.core.ImageAnalysis analysis;
    private androidx.camera.core.CameraInfo cameraInfo;

    CameraXBindingBackend(Context context) { this(context, null, null); }
    CameraXBindingBackend(Context context, androidx.camera.core.ImageAnalysis.Analyzer analyzer,
            Executor analysisExecutor) {
        this.analyzer = analyzer; this.analysisExecutor = analysisExecutor;
        this.context = context.getApplicationContext();
        this.mainExecutor = ContextCompat.getMainExecutor(this.context);
    }

    @Override
    public void bind(LifecycleOwner owner, PreviewView previewView, Callback callback) {
        long request = ++generation;
        ListenableFuture<ProcessCameraProvider> providerFuture =
                ProcessCameraProvider.getInstance(context);
        providerFuture.addListener(() -> {
            if (request != generation) return;
            ProcessCameraProvider provider;
            try {
                provider = providerFuture.get();
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                callback.onFailure(CameraStatus.PROVIDER_UNAVAILABLE);
                return;
            } catch (ExecutionException | RuntimeException error) {
                callback.onFailure(CameraStatus.PROVIDER_UNAVAILABLE);
                return;
            }
            if (request != generation) return;
            try {
                if (!provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) {
                    callback.onFailure(CameraStatus.NO_BACK_CAMERA);
                    return;
                }
            } catch (CameraInfoUnavailableException error) {
                callback.onFailure(CameraStatus.PROVIDER_UNAVAILABLE);
                return;
            }

            try {
                provider.unbindAll();
                Preview preview = new Preview.Builder().build();
                // TextureView mode keeps sibling overlay views reliably composited above preview.
                previewView.setImplementationMode(PreviewView.ImplementationMode.COMPATIBLE);
                previewView.setScaleType(PreviewView.ScaleType.FILL_CENTER);
                preview.setSurfaceProvider(previewView.getSurfaceProvider());
                androidx.camera.core.Camera camera;
                if (analyzer != null) {
                    analysis = new androidx.camera.core.ImageAnalysis.Builder()
                        .setBackpressureStrategy(androidx.camera.core.ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build();
                    analysis.setAnalyzer(analysisExecutor, analyzer);
                    camera = provider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis);
                } else camera = provider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview);
                cameraInfo = camera.getCameraInfo();
                cameraProvider = provider;
                callback.onBound();
            } catch (RuntimeException error) {
                provider.unbindAll();
                callback.onFailure(CameraStatus.BIND_FAILED);
            }
        }, mainExecutor);
    }

    @Override
    public void unbind() {
        generation++;
        if (analysis != null) { analysis.clearAnalyzer(); analysis = null; }
        ProcessCameraProvider provider = cameraProvider;
        cameraProvider = null;
        if (provider != null) provider.unbindAll();
    }

    /** Wait for hardware camera closure; do not race ARCore against asynchronous CameraX close. */
    void awaitClosed(java.util.function.Consumer<Boolean> completed) {
        if (cameraInfo == null) { completed.accept(true); return; }
        androidx.lifecycle.LiveData<androidx.camera.core.CameraState> states = cameraInfo.getCameraState();
        android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
        class Closing implements androidx.lifecycle.Observer<androidx.camera.core.CameraState>, Runnable {
            boolean done;
            void finish(boolean success) {
                if (done) return; done = true;
                states.removeObserver(this); handler.removeCallbacks(this); completed.accept(success);
            }
            public void onChanged(androidx.camera.core.CameraState state) {
                if (state.getType() == androidx.camera.core.CameraState.Type.CLOSED) finish(true);
            }
            public void run() { finish(false); }
        }
        Closing closing = new Closing();
        handler.postDelayed(closing, 5000);
        states.observeForever(closing);
    }
}
