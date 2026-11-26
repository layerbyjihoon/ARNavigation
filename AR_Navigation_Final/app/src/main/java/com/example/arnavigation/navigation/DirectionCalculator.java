package com.example.arnavigation.navigation;

import com.example.arnavigation.model.GeoPoint;
import java.util.Objects;

/** Great-circle distance, initial bearing, and signed relative-angle calculations. */
public final class DirectionCalculator {
    private static final double EARTH_RADIUS_METERS = 6_371_000.0d;

    public double distanceMeters(GeoPoint from, GeoPoint to) {
        Objects.requireNonNull(from, "from must not be null");
        Objects.requireNonNull(to, "to must not be null");
        double latitude1 = Math.toRadians(from.getLatitude());
        double latitude2 = Math.toRadians(to.getLatitude());
        double latitudeDelta = latitude2 - latitude1;
        double longitudeDelta = Math.toRadians(to.getLongitude() - from.getLongitude());
        double a = Math.sin(latitudeDelta / 2.0d) * Math.sin(latitudeDelta / 2.0d)
                + Math.cos(latitude1) * Math.cos(latitude2)
                * Math.sin(longitudeDelta / 2.0d) * Math.sin(longitudeDelta / 2.0d);
        return EARTH_RADIUS_METERS * 2.0d
                * Math.atan2(Math.sqrt(a), Math.sqrt(Math.max(0.0d, 1.0d - a)));
    }

    public double bearingDegrees(GeoPoint from, GeoPoint to) {
        Objects.requireNonNull(from, "from must not be null");
        Objects.requireNonNull(to, "to must not be null");
        double latitude1 = Math.toRadians(from.getLatitude());
        double latitude2 = Math.toRadians(to.getLatitude());
        double longitudeDelta = Math.toRadians(to.getLongitude() - from.getLongitude());
        double y = Math.sin(longitudeDelta) * Math.cos(latitude2);
        double x = Math.cos(latitude1) * Math.sin(latitude2)
                - Math.sin(latitude1) * Math.cos(latitude2) * Math.cos(longitudeDelta);
        return normalizeUnsigned(Math.toDegrees(Math.atan2(y, x)));
    }

    /** Returns [-180, 180), where positive means right and negative means left. */
    public double relativeAngleDegrees(double waypointBearing, double deviceHeading) {
        requireFinite(waypointBearing, "waypointBearing");
        requireFinite(deviceHeading, "deviceHeading");
        double relative = normalizeUnsigned(waypointBearing - deviceHeading);
        return relative >= 180.0d ? relative - 360.0d : relative;
    }

    private static double normalizeUnsigned(double degrees) {
        double normalized = degrees % 360.0d;
        if (normalized < 0.0d) normalized += 360.0d;
        return normalized;
    }

    private static void requireFinite(double value, String name) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException(name + " must be finite");
    }
}
