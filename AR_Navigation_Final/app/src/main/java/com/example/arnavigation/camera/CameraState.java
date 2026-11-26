package com.example.arnavigation.camera;

import java.util.Objects;

/** Immutable state exposed by CameraPreviewController. */
public final class CameraState {
    private final CameraStatus status;

    public CameraState(CameraStatus status) {
        this.status = Objects.requireNonNull(status, "status must not be null");
    }

    public CameraStatus getStatus() {
        return status;
    }

    public boolean isPreviewAvailable() {
        return status == CameraStatus.BOUND;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof CameraState && status == ((CameraState) other).status;
    }

    @Override
    public int hashCode() {
        return status.hashCode();
    }
}
