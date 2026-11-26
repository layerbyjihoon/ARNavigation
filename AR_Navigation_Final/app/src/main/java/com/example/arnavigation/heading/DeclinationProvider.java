package com.example.arnavigation.heading;

/** Supplies the local magnetic declination in degrees. */
public interface DeclinationProvider {
    double getDeclinationDegrees(
            double latitude,
            double longitude,
            double altitudeMeters,
            long timestampMillis);
}
