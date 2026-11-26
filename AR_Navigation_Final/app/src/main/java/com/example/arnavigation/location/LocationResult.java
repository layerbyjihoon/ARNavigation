package com.example.arnavigation.location;

import com.example.arnavigation.model.LocationSample;
import java.util.Objects;

/** Immutable location callback value. sample is null when no position is available. */
public final class LocationResult {
    private final LocationStatus status;
    private final LocationSample sample;

    public LocationResult(LocationStatus status, LocationSample sample) {
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.sample = sample;
    }

    public static LocationResult withoutSample(LocationStatus status) {
        return new LocationResult(status, null);
    }

    public LocationStatus getStatus() {
        return status;
    }

    /** Returns the observation, or null for permission, settings, and availability failures. */
    public LocationSample getSample() {
        return sample;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof LocationResult)) return false;
        LocationResult that = (LocationResult) other;
        return status == that.status && Objects.equals(sample, that.sample);
    }

    @Override
    public int hashCode() {
        return Objects.hash(status, sample);
    }
}
