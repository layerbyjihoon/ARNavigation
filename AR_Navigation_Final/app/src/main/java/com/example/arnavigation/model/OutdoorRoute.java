package com.example.arnavigation.model;

import java.util.List;
import java.util.Objects;

/** Immutable route geometry and guidance. Distances are meters and duration is seconds. */
public final class OutdoorRoute {
    private final List<GeoPoint> polylinePoints;
    private final List<Waypoint> waypoints;
    private final double distanceMeters;
    private final long durationSeconds;
    private final List<String> warnings;

    public OutdoorRoute(
            List<GeoPoint> polylinePoints,
            List<Waypoint> waypoints,
            double distanceMeters,
            long durationSeconds,
            List<String> warnings) {
        this.polylinePoints = ModelValidation.immutableCopy(polylinePoints, "polylinePoints");
        this.waypoints = ModelValidation.immutableCopy(waypoints, "waypoints");
        this.distanceMeters = ModelValidation.requireNonNegative(distanceMeters, "distanceMeters");
        this.durationSeconds = ModelValidation.requireNonNegative(durationSeconds, "durationSeconds");
        this.warnings = ModelValidation.immutableTextCopy(warnings, "warnings");
    }

    public List<GeoPoint> getPolylinePoints() {
        return polylinePoints;
    }

    public List<Waypoint> getWaypoints() {
        return waypoints;
    }

    public double getDistanceMeters() {
        return distanceMeters;
    }

    public long getDurationSeconds() {
        return durationSeconds;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof OutdoorRoute)) return false;
        OutdoorRoute that = (OutdoorRoute) other;
        return Double.compare(distanceMeters, that.distanceMeters) == 0
                && durationSeconds == that.durationSeconds
                && polylinePoints.equals(that.polylinePoints)
                && waypoints.equals(that.waypoints)
                && warnings.equals(that.warnings);
    }

    @Override
    public int hashCode() {
        return Objects.hash(polylinePoints, waypoints, distanceMeters, durationSeconds, warnings);
    }
}
