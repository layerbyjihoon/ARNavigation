package com.example.arnavigation.model;

import java.util.Objects;

/** Immutable location observation. Bearing is degrees in [0, 360), or null if unavailable. */
public final class LocationSample {
    private final GeoPoint point;
    private final double accuracyMeters;
    private final Double bearingDegrees;
    private final double speedMetersPerSecond;
    private final long elapsedRealtimeNanos;

    public LocationSample(
            GeoPoint point,
            double accuracyMeters,
            Double bearingDegrees,
            double speedMetersPerSecond,
            long elapsedRealtimeNanos) {
        this.point = ModelValidation.requireNonNull(point, "point");
        this.accuracyMeters = ModelValidation.requireNonNegative(accuracyMeters, "accuracyMeters");
        if (bearingDegrees != null) {
            ModelValidation.requireFinite(bearingDegrees, "bearingDegrees");
            if (bearingDegrees < 0.0d || bearingDegrees >= 360.0d) {
                throw new IllegalArgumentException("bearingDegrees must be in [0, 360)");
            }
        }
        this.bearingDegrees = bearingDegrees;
        this.speedMetersPerSecond = ModelValidation.requireNonNegative(
                speedMetersPerSecond, "speedMetersPerSecond");
        this.elapsedRealtimeNanos = ModelValidation.requireNonNegative(
                elapsedRealtimeNanos, "elapsedRealtimeNanos");
    }

    public GeoPoint getPoint() {
        return point;
    }

    public double getAccuracyMeters() {
        return accuracyMeters;
    }

    public Double getBearingDegrees() {
        return bearingDegrees;
    }

    public double getSpeedMetersPerSecond() {
        return speedMetersPerSecond;
    }

    public long getElapsedRealtimeNanos() {
        return elapsedRealtimeNanos;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof LocationSample)) return false;
        LocationSample that = (LocationSample) other;
        return Double.compare(accuracyMeters, that.accuracyMeters) == 0
                && Double.compare(speedMetersPerSecond, that.speedMetersPerSecond) == 0
                && elapsedRealtimeNanos == that.elapsedRealtimeNanos
                && point.equals(that.point)
                && Objects.equals(bearingDegrees, that.bearingDegrees);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                point, accuracyMeters, bearingDegrees, speedMetersPerSecond, elapsedRealtimeNanos);
    }
}
