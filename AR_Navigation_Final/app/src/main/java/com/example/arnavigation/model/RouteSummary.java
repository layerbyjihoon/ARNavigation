package com.example.arnavigation.model;

import java.util.Objects;

/** Immutable route summary. Distance is meters and duration is seconds. */
public final class RouteSummary {
    private final double distanceMeters;
    private final long durationSeconds;

    public RouteSummary(double distanceMeters, long durationSeconds) {
        this.distanceMeters = ModelValidation.requireNonNegative(distanceMeters, "distanceMeters");
        this.durationSeconds = ModelValidation.requireNonNegative(durationSeconds, "durationSeconds");
    }

    public double getDistanceMeters() {
        return distanceMeters;
    }

    public long getDurationSeconds() {
        return durationSeconds;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof RouteSummary)) return false;
        RouteSummary that = (RouteSummary) other;
        return Double.compare(distanceMeters, that.distanceMeters) == 0
                && durationSeconds == that.durationSeconds;
    }

    @Override
    public int hashCode() {
        return Objects.hash(distanceMeters, durationSeconds);
    }
}
