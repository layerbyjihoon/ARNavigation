package com.example.arnavigation.heading;

import android.hardware.GeomagneticField;

/** Android geomagnetic-model implementation of true-north correction. */
public final class GeomagneticDeclinationProvider implements DeclinationProvider {
    @Override
    public double getDeclinationDegrees(
            double latitude,
            double longitude,
            double altitudeMeters,
            long timestampMillis) {
        if (!Double.isFinite(latitude) || latitude < -90.0d || latitude > 90.0d) {
            throw new IllegalArgumentException("latitude must be in [-90, 90]");
        }
        if (!Double.isFinite(longitude) || longitude < -180.0d || longitude > 180.0d) {
            throw new IllegalArgumentException("longitude must be in [-180, 180]");
        }
        if (!Double.isFinite(altitudeMeters)) {
            throw new IllegalArgumentException("altitudeMeters must be finite");
        }
        if (timestampMillis < 0L) {
            throw new IllegalArgumentException("timestampMillis must not be negative");
        }
        GeomagneticField field = new GeomagneticField(
                (float) latitude,
                (float) longitude,
                (float) altitudeMeters,
                timestampMillis);
        return field.getDeclination();
    }
}
