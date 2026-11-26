package com.example.arnavigation.camera;

import androidx.camera.view.PreviewView;
import androidx.lifecycle.LifecycleOwner;

interface CameraBindingBackend {
    interface Callback {
        void onBound();

        void onFailure(CameraStatus failureStatus);
    }

    void bind(LifecycleOwner owner, PreviewView previewView, Callback callback);

    void unbind();
}
