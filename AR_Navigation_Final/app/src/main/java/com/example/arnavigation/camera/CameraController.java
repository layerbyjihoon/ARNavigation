package com.example.arnavigation.camera;

import androidx.camera.view.PreviewView;
import androidx.lifecycle.LifecycleOwner;

/** Lifecycle-aware camera preview contract used by the AR screen. */
public interface CameraController {
    void bind(LifecycleOwner owner, PreviewView previewView, CameraCallback callback);

    void unbind();
}
