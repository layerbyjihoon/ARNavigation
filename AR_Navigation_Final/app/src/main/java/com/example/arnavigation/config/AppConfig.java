package com.example.arnavigation.config;

/** Central configuration for P0 values that require field validation. */
public final class AppConfig {
    public static final int MIN_SDK = 24;
    public static final int TARGET_SDK = 36;

    // Approximate fallback only. Confirm the production center and zoom during P0 field setup.
    public static final double CAMPUS_CENTER_LATITUDE = 37.4963;
    public static final double CAMPUS_CENTER_LONGITUDE = 126.9574;
    public static final float CAMPUS_DEFAULT_ZOOM = 16.5f;

    public static final double DIRECT_DESTINATION_RADIUS_METERS = 50.0;
    public static final float ARRIVAL_RADIUS_METERS = 20.0f;
    public static final float WAYPOINT_RADIUS_METERS = 8.0f;
    public static final float OFF_ROUTE_DISTANCE_METERS = 35.0f;
    public static final long OFF_ROUTE_DURATION_MILLIS = 2_000L;
    public static final float MAX_ACCEPTABLE_LOCATION_ACCURACY_METERS = 25.0f;
    public static final long MAX_LOCATION_AGE_MILLIS = 10_000L;
    public static final long LOCATION_UPDATE_INTERVAL_MILLIS = 2_000L;
    public static final long LOCATION_MIN_UPDATE_INTERVAL_MILLIS = 1_000L;
    public static final float FORWARD_ANGLE_TOLERANCE_DEGREES = 15.0f;
    public static final float MIN_GPS_BEARING_SPEED_METERS_PER_SECOND = 1.5f;
    public static final int MAX_ARROW_UPDATES_PER_SECOND = 10;
    public static final int HEADING_SMOOTHING_WINDOW_SIZE = 5;
    public static final float MIN_WAYPOINT_SPACING_METERS = 3.0f;
    public static final long ROUTES_CONNECT_TIMEOUT_SECONDS = 10L;
    public static final long ROUTES_READ_TIMEOUT_SECONDS = 20L;
    public static final int ARRIVAL_CONFIRMATION_COUNT = 2;
    public static final long ROUTE_RECALCULATION_COOLDOWN_MILLIS = 5_000L;

    private AppConfig() {
    }
}
