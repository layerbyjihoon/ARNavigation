package com.example.arnavigation.location;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.SystemClock;
import androidx.core.content.ContextCompat;
import com.example.arnavigation.config.AppConfig;
import com.example.arnavigation.model.LocationSample;
import java.util.Objects;

/** Foreground-only location provider backed by Google Play services. */
public final class FusedLocationProvider implements LocationProvider {
    private final LocationBackend backend;
    private final LocationPermissionChecker permissionChecker;
    private final ElapsedRealtimeClock clock;
    private final LocationQualityEvaluator qualityEvaluator;
    private final LocationSampleOrderFilter orderFilter = new LocationSampleOrderFilter();

    private LocationListener listener;
    private boolean started;
    private boolean updatesRegistered;
    private long generation;

    public FusedLocationProvider(Context context) {
        Objects.requireNonNull(context, "context must not be null");
        Context applicationContext = context.getApplicationContext();
        this.backend = new FusedLocationBackend(applicationContext);
        this.permissionChecker = () -> {
            if (ContextCompat.checkSelfPermission(
                    applicationContext, Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED) {
                return LocationPermissionLevel.FINE;
            }
            if (ContextCompat.checkSelfPermission(
                    applicationContext, Manifest.permission.ACCESS_COARSE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED) {
                return LocationPermissionLevel.COARSE;
            }
            return LocationPermissionLevel.NONE;
        };
        this.clock = SystemClock::elapsedRealtimeNanos;
        this.qualityEvaluator = new LocationQualityEvaluator(
                AppConfig.MAX_ACCEPTABLE_LOCATION_ACCURACY_METERS,
                AppConfig.MAX_LOCATION_AGE_MILLIS);
    }

    FusedLocationProvider(
            LocationBackend backend,
            LocationPermissionChecker permissionChecker,
            ElapsedRealtimeClock clock,
            LocationQualityEvaluator qualityEvaluator) {
        this.backend = Objects.requireNonNull(backend, "backend must not be null");
        this.permissionChecker = Objects.requireNonNull(
                permissionChecker, "permissionChecker must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.qualityEvaluator = Objects.requireNonNull(
                qualityEvaluator, "qualityEvaluator must not be null");
    }

    @Override
    public void start(LocationListener listener) {
        this.listener = Objects.requireNonNull(listener, "listener must not be null");
        if (started) {
            return;
        }
        LocationPermissionLevel permission = permissionChecker.getPermissionLevel();
        if (permission == LocationPermissionLevel.NONE) {
            listener.onLocationChanged(LocationResult.withoutSample(LocationStatus.PERMISSION_DENIED));
            return;
        }

        started = true;
        updatesRegistered = false;
        orderFilter.reset();
        long session = ++generation;
        backend.checkSettings(new LocationBackend.SettingsCallback() {
            @Override
            public void onReady() {
                if (!isCurrentSession(session)) return;
                backend.requestCurrentLocation(streamCallback(session));
                if (!isCurrentSession(session)) return;
                updatesRegistered = true;
                backend.startLocationUpdates(streamCallback(session));
            }

            @Override
            public void onDisabled() {
                if (isCurrentSession(session)) {
                    notifyListener(LocationResult.withoutSample(LocationStatus.DISABLED));
                }
            }
        });
    }

    @Override
    public void stop() {
        if (!started) {
            listener = null;
            return;
        }
        started = false;
        listener = null;
        generation++;
        orderFilter.reset();
        if (updatesRegistered) {
            updatesRegistered = false;
            backend.stopLocationUpdates();
        }
    }

    @Override
    public void requestSingleUpdate(LocationResultCallback callback) {
        Objects.requireNonNull(callback, "callback must not be null");
        LocationPermissionLevel permission = permissionChecker.getPermissionLevel();
        if (permission == LocationPermissionLevel.NONE) {
            callback.onLocationResult(LocationResult.withoutSample(LocationStatus.PERMISSION_DENIED));
            return;
        }
        backend.checkSettings(new LocationBackend.SettingsCallback() {
            private boolean completed;

            @Override
            public void onReady() {
                if (completed) return;
                backend.requestCurrentLocation(new LocationBackend.SampleCallback() {
                    @Override
                    public void onSample(LocationSample sample) {
                        if (completed) return;
                        completed = true;
                        callback.onLocationResult(evaluate(sample, permission));
                    }

                    @Override
                    public void onUnavailable() {
                        if (completed) return;
                        completed = true;
                        callback.onLocationResult(
                                LocationResult.withoutSample(LocationStatus.UNAVAILABLE));
                    }
                });
            }

            @Override
            public void onDisabled() {
                if (completed) return;
                completed = true;
                callback.onLocationResult(LocationResult.withoutSample(LocationStatus.DISABLED));
            }
        });
    }

    private LocationBackend.UpdateCallback streamCallback(long session) {
        return new LocationBackend.UpdateCallback() {
            @Override
            public void onSample(LocationSample sample) {
                if (!isCurrentSession(session) || !orderFilter.accept(sample)) return;
                LocationPermissionLevel permission = permissionChecker.getPermissionLevel();
                if (permission == LocationPermissionLevel.NONE) {
                    notifyListener(LocationResult.withoutSample(LocationStatus.PERMISSION_DENIED));
                    return;
                }
                notifyListener(evaluate(sample, permission));
            }

            @Override
            public void onUnavailable() {
                if (isCurrentSession(session)) {
                    notifyListener(LocationResult.withoutSample(LocationStatus.UNAVAILABLE));
                }
            }
        };
    }

    private LocationResult evaluate(LocationSample sample, LocationPermissionLevel permission) {
        LocationStatus status = qualityEvaluator.evaluate(
                sample, clock.nowNanos(), permission == LocationPermissionLevel.FINE);
        return new LocationResult(status, sample);
    }

    private boolean isCurrentSession(long session) {
        return started && generation == session;
    }

    private void notifyListener(LocationResult result) {
        LocationListener current = listener;
        if (current != null) {
            current.onLocationChanged(result);
        }
    }
}
