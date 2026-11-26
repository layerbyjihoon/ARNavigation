package com.example.arnavigation.model;

import java.util.Objects;

/** Immutable WGS84 coordinate in decimal degrees. */
public final class GeoPoint {
    private final double latitude;
    private final double longitude;

    public GeoPoint(double latitude, double longitude) {
        ModelValidation.requireFinite(latitude, "latitude");
        ModelValidation.requireFinite(longitude, "longitude");
        if (latitude < -90.0d || latitude > 90.0d) {
            throw new IllegalArgumentException("latitude must be between -90 and 90 degrees");
        }
        if (longitude < -180.0d || longitude > 180.0d) {
            throw new IllegalArgumentException("longitude must be between -180 and 180 degrees");
        }
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof GeoPoint)) return false;
        GeoPoint that = (GeoPoint) other;
        return Double.compare(latitude, that.latitude) == 0
                && Double.compare(longitude, that.longitude) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(latitude, longitude);
    }

    @Override
    public String toString() {
        return "GeoPoint{" + "latitude=" + latitude + ", longitude=" + longitude + '}';
    }
}
