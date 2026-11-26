package com.example.arnavigation.location;

import android.annotation.SuppressLint;
import android.content.Context;
import android.location.Location;
import android.os.Looper;
import com.example.arnavigation.config.AppConfig;
import com.example.arnavigation.model.GeoPoint;
import com.example.arnavigation.model.LocationSample;
import com.google.android.gms.location.CurrentLocationRequest;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationAvailability;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.Priority;
import com.google.android.gms.location.SettingsClient;
import com.google.android.gms.tasks.CancellationTokenSource;

/** Google Play services adapter. Permission checks are performed by FusedLocationProvider. */
final class FusedLocationBackend implements LocationBackend {
    private final FusedLocationProviderClient fusedClient;
    private final SettingsClient settingsClient;
    private final LocationRequest updateRequest;
    private final LocationSettingsRequest settingsRequest;
    private LocationCallback activeCallback;

    FusedLocationBackend(Context context) {
        Context applicationContext = context.getApplicationContext();
        fusedClient = LocationServices.getFusedLocationProviderClient(applicationContext);
        settingsClient = LocationServices.getSettingsClient(applicationContext);
        updateRequest = new LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY, AppConfig.LOCATION_UPDATE_INTERVAL_MILLIS)
                .setMinUpdateIntervalMillis(AppConfig.LOCATION_MIN_UPDATE_INTERVAL_MILLIS)
                .build();
        settingsRequest = new LocationSettingsRequest.Builder()
                .addLocationRequest(updateRequest)
                .build();
    }

    @Override
    public void checkSettings(SettingsCallback callback) {
        settingsClient.checkLocationSettings(settingsRequest)
                .addOnSuccessListener(ignored -> callback.onReady())
                .addOnFailureListener(error -> callback.onDisabled());
    }

    @Override
    @SuppressLint("MissingPermission")
    public void requestCurrentLocation(SampleCallback callback) {
        CurrentLocationRequest request = new CurrentLocationRequest.Builder()
                .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                .setMaxUpdateAgeMillis(AppConfig.MAX_LOCATION_AGE_MILLIS)
                .build();
        try {
            fusedClient.getCurrentLocation(request, new CancellationTokenSource().getToken())
                    .addOnSuccessListener(location -> deliver(location, callback))
                    .addOnFailureListener(error -> callback.onUnavailable());
        } catch (SecurityException error) {
            callback.onUnavailable();
        }
    }

    @Override
    @SuppressLint("MissingPermission")
    public void startLocationUpdates(UpdateCallback callback) {
        if (activeCallback != null) {
            return;
        }
        activeCallback = new LocationCallback() {
            @Override
            public void onLocationResult(com.google.android.gms.location.LocationResult result) {
                for (Location location : result.getLocations()) {
                    deliver(location, callback);
                }
            }

            @Override
            public void onLocationAvailability(LocationAvailability availability) {
                if (!availability.isLocationAvailable()) {
                    callback.onUnavailable();
                }
            }
        };
        try {
            fusedClient.requestLocationUpdates(updateRequest, activeCallback, Looper.getMainLooper())
                    .addOnFailureListener(error -> callback.onUnavailable());
        } catch (SecurityException error) {
            activeCallback = null;
            callback.onUnavailable();
        }
    }

    @Override
    public void stopLocationUpdates() {
        LocationCallback callback = activeCallback;
        activeCallback = null;
        if (callback != null) {
            fusedClient.removeLocationUpdates(callback);
        }
    }

    private static void deliver(Location location, SampleCallback callback) {
        if (location == null) {
            callback.onUnavailable();
            return;
        }
        try {
            Double bearing = location.hasBearing() ? (double) location.getBearing() : null;
            double speed = location.hasSpeed() ? location.getSpeed() : 0.0d;
            callback.onSample(new LocationSample(
                    new GeoPoint(location.getLatitude(), location.getLongitude()),
                    location.getAccuracy(),
                    bearing,
                    speed,
                    location.getElapsedRealtimeNanos()));
        } catch (IllegalArgumentException error) {
            callback.onUnavailable();
        }
    }
}
