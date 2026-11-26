package com.example.arnavigation.location;

import com.example.arnavigation.model.LocationSample;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/** Evaluates a location using horizontal accuracy and monotonic elapsed-realtime age. */
public final class LocationQualityEvaluator {
    private final double maxAccuracyMeters;
    private final long maxAgeNanos;

    public LocationQualityEvaluator(double maxAccuracyMeters, long maxAgeMillis) {
        if (!Double.isFinite(maxAccuracyMeters) || maxAccuracyMeters < 0.0d) {
            throw new IllegalArgumentException("maxAccuracyMeters must be finite and non-negative");
        }
        if (maxAgeMillis < 0L) {
            throw new IllegalArgumentException("maxAgeMillis must not be negative");
        }
        this.maxAccuracyMeters = maxAccuracyMeters;
        this.maxAgeNanos = TimeUnit.MILLISECONDS.toNanos(maxAgeMillis);
    }

    public LocationStatus evaluate(
            LocationSample sample, long currentElapsedRealtimeNanos, boolean hasFinePermission) {
        Objects.requireNonNull(sample, "sample must not be null");
        if (currentElapsedRealtimeNanos < 0L) {
            throw new IllegalArgumentException("currentElapsedRealtimeNanos must not be negative");
        }

        long sampleTime = sample.getElapsedRealtimeNanos();
        if (sampleTime > currentElapsedRealtimeNanos
                || currentElapsedRealtimeNanos - sampleTime > maxAgeNanos) {
            return LocationStatus.STALE;
        }
        if (sample.getAccuracyMeters() > maxAccuracyMeters) {
            return LocationStatus.LOW_ACCURACY;
        }
        return hasFinePermission ? LocationStatus.AVAILABLE : LocationStatus.COARSE_ONLY;
    }
}
