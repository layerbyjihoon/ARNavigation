package com.example.arnavigation.location;

/** Current availability and quality of a foreground location observation. */
public enum LocationStatus {
    AVAILABLE,
    COARSE_ONLY,
    STALE,
    LOW_ACCURACY,
    DISABLED,
    PERMISSION_DENIED,
    UNAVAILABLE;

    /** Whether this status provides a position that can still be shown on a 2D map. */
    public boolean isMapUsable() {
        return this == AVAILABLE || this == COARSE_ONLY || this == LOW_ACCURACY;
    }

    /** Whether this status is accurate enough for definitive AR guidance. */
    public boolean isArUsable() {
        return this == AVAILABLE;
    }
}
