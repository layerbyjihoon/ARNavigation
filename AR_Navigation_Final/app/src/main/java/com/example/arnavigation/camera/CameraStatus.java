package com.example.arnavigation.camera;

/** Camera preview lifecycle and recoverable failure states. */
public enum CameraStatus {
    IDLE,
    BINDING,
    BOUND,
    UNBOUND,
    PERMISSION_DENIED,
    NO_BACK_CAMERA,
    PROVIDER_UNAVAILABLE,
    BIND_FAILED
}
