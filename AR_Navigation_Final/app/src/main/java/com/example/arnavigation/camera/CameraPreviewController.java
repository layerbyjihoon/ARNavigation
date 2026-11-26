package com.example.arnavigation.camera;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import java.util.Objects;

/** Permission-gated and idempotent CameraX preview session controller. */
public final class CameraPreviewController implements CameraController {
    private final CameraPermissionChecker permissionChecker;
    private final CameraBindingBackend backend;
    private LifecycleOwner owner;
    private Object previewIdentity;
    private CameraCallback callback;
    private CameraState currentState = new CameraState(CameraStatus.IDLE);
    private boolean active;
    private long generation;

    private final DefaultLifecycleObserver lifecycleObserver = new DefaultLifecycleObserver() {
        @Override
        public void onDestroy(LifecycleOwner destroyedOwner) {
            if (destroyedOwner == owner) unbind();
        }
    };

    public CameraPreviewController(Context context) { this(context, null, null); }

    public CameraPreviewController(Context context, androidx.camera.core.ImageAnalysis.Analyzer analyzer,
            java.util.concurrent.Executor analysisExecutor) {
        Objects.requireNonNull(context, "context must not be null");
        Context applicationContext = context.getApplicationContext();
        permissionChecker = () -> ContextCompat.checkSelfPermission(
                applicationContext, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
        backend = new CameraXBindingBackend(applicationContext, analyzer, analysisExecutor);
    }

    CameraPreviewController(
            CameraPermissionChecker permissionChecker, CameraBindingBackend backend) {
        this.permissionChecker = Objects.requireNonNull(
                permissionChecker, "permissionChecker must not be null");
        this.backend = Objects.requireNonNull(backend, "backend must not be null");
    }

    @Override
    public void bind(
            LifecycleOwner owner,
            PreviewView previewView,
            CameraCallback callback) {
        Objects.requireNonNull(owner, "owner must not be null");
        Objects.requireNonNull(previewView, "previewView must not be null");
        Objects.requireNonNull(callback, "callback must not be null");
        bindInternal(owner, previewView, previewView, callback);
    }

    /** Runs the same session state machine without constructing an Android View in JVM tests. */
    void bindForTest(LifecycleOwner owner, Object previewIdentity, CameraCallback callback) {
        bindInternal(
                Objects.requireNonNull(owner, "owner must not be null"),
                Objects.requireNonNull(previewIdentity, "previewIdentity must not be null"),
                null,
                Objects.requireNonNull(callback, "callback must not be null"));
    }

    private void bindInternal(
            LifecycleOwner owner,
            Object previewIdentity,
            PreviewView backendPreviewView,
            CameraCallback callback) {
        if (!permissionChecker.hasCameraPermission()) {
            callback.onCameraStateChanged(new CameraState(CameraStatus.PERMISSION_DENIED));
            return;
        }
        if (active && this.owner == owner && this.previewIdentity == previewIdentity) {
            this.callback = callback;
            callback.onCameraStateChanged(currentState);
            return;
        }
        if (active) unbind();
        
        this.owner = owner;
        this.previewIdentity = previewIdentity;
        this.callback = callback;
        this.active = true;
        owner.getLifecycle().addObserver(lifecycleObserver);
        long request = ++generation;
        notifyState(CameraStatus.BINDING);
        backend.bind(owner, backendPreviewView, new CameraBindingBackend.Callback() {
            @Override
            public void onBound() {
                if (isCurrent(request)) notifyState(CameraStatus.BOUND);
            }

            @Override
            public void onFailure(CameraStatus failureStatus) {
                if (!isCurrent(request)) return;
                backend.unbind();
                removeLifecycleObserver();
                active = false;
                CameraCallback failureCallback = CameraPreviewController.this.callback;
                CameraPreviewController.this.owner = null;
                CameraPreviewController.this.previewIdentity = null;
                CameraPreviewController.this.callback = null;
                currentState = new CameraState(failureStatus);
                if (failureCallback != null) {
                    failureCallback.onCameraStateChanged(currentState);
                }
            }
        });
    }

    @Override
    public void unbind() {
        if (!active) return;
        active = false;
        generation++;
        backend.unbind();
        removeLifecycleObserver();
        CameraCallback previousCallback = callback;
        owner = null;
        previewIdentity = null;
        callback = null;
        currentState = new CameraState(CameraStatus.UNBOUND);
        if (previousCallback != null) {
            previousCallback.onCameraStateChanged(currentState);
        }
    }

    public void release(java.util.function.Consumer<Boolean> completed) {
        unbind();
        if (backend instanceof CameraXBindingBackend nativeBackend) nativeBackend.awaitClosed(completed);
        else completed.accept(true);
    }

    private boolean isCurrent(long request) {
        return active && generation == request;
    }

    private void notifyState(CameraStatus status) {
        currentState = new CameraState(status);
        CameraCallback current = callback;
        if (current != null) current.onCameraStateChanged(currentState);
    }

    private void removeLifecycleObserver() {
        LifecycleOwner currentOwner = owner;
        if (currentOwner != null) {
            currentOwner.getLifecycle().removeObserver(lifecycleObserver);
        }
    }
}
