package com.example.arnavigation.navigation;

import com.example.arnavigation.config.AppConfig;

/** Immutable navigation thresholds, with all distance values expressed in meters. */
public final class NavigationConfig {
    private final double waypointRadiusMeters;
    private final double offRouteDistanceMeters;
    private final long offRouteDurationMillis;
    private final double arrivalRadiusMeters;
    private final int arrivalConfirmationCount;
    private final long rerouteCooldownMillis;

    public NavigationConfig(
            double waypointRadiusMeters,
            double offRouteDistanceMeters,
            long offRouteDurationMillis,
            double arrivalRadiusMeters,
            int arrivalConfirmationCount,
            long rerouteCooldownMillis) {
        this.waypointRadiusMeters = nonNegative(waypointRadiusMeters, "waypointRadiusMeters");
        this.offRouteDistanceMeters = nonNegative(
                offRouteDistanceMeters, "offRouteDistanceMeters");
        this.arrivalRadiusMeters = nonNegative(arrivalRadiusMeters, "arrivalRadiusMeters");
        if (offRouteDurationMillis < 0L || rerouteCooldownMillis < 0L) {
            throw new IllegalArgumentException("durations must not be negative");
        }
        if (arrivalConfirmationCount <= 0) {
            throw new IllegalArgumentException("arrivalConfirmationCount must be positive");
        }
        this.offRouteDurationMillis = offRouteDurationMillis;
        this.arrivalConfirmationCount = arrivalConfirmationCount;
        this.rerouteCooldownMillis = rerouteCooldownMillis;
    }

    public static NavigationConfig defaults() {
        return new NavigationConfig(
                AppConfig.WAYPOINT_RADIUS_METERS,
                AppConfig.OFF_ROUTE_DISTANCE_METERS,
                AppConfig.OFF_ROUTE_DURATION_MILLIS,
                AppConfig.ARRIVAL_RADIUS_METERS,
                AppConfig.ARRIVAL_CONFIRMATION_COUNT,
                AppConfig.ROUTE_RECALCULATION_COOLDOWN_MILLIS);
    }

    public double getWaypointRadiusMeters() { return waypointRadiusMeters; }
    public double getOffRouteDistanceMeters() { return offRouteDistanceMeters; }
    public long getOffRouteDurationMillis() { return offRouteDurationMillis; }
    public double getArrivalRadiusMeters() { return arrivalRadiusMeters; }
    public int getArrivalConfirmationCount() { return arrivalConfirmationCount; }
    public long getRerouteCooldownMillis() { return rerouteCooldownMillis; }

    private static double nonNegative(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0d) {
            throw new IllegalArgumentException(name + " must be finite and non-negative");
        }
        return value;
    }
}
