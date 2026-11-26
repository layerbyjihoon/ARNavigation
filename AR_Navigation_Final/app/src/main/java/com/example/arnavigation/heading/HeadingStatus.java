package com.example.arnavigation.heading;

/** Availability of a true-north heading for navigation UI. */
public enum HeadingStatus {
    AVAILABLE,
    LOW_ACCURACY,
    UNAVAILABLE;

    public boolean isArrowUsable() {
        return this == AVAILABLE;
    }
}
