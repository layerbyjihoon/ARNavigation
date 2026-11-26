package com.example.arnavigation.heading;

import com.example.arnavigation.model.HeadingSample;
import java.util.Objects;

/** Immutable heading callback value. sample is null only when sensors are unavailable. */
public final class HeadingResult {
    private final HeadingStatus status;
    private final HeadingSample sample;

    public HeadingResult(HeadingStatus status, HeadingSample sample) {
        this.status = Objects.requireNonNull(status, "status must not be null");
        if (status == HeadingStatus.UNAVAILABLE && sample != null) {
            throw new IllegalArgumentException("UNAVAILABLE must not contain a sample");
        }
        if (status != HeadingStatus.UNAVAILABLE && sample == null) {
            throw new IllegalArgumentException("available statuses require a sample");
        }
        this.sample = sample;
    }

    public static HeadingResult unavailable() {
        return new HeadingResult(HeadingStatus.UNAVAILABLE, null);
    }

    public HeadingStatus getStatus() {
        return status;
    }

    public HeadingSample getSample() {
        return sample;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof HeadingResult)) return false;
        HeadingResult that = (HeadingResult) other;
        return status == that.status && Objects.equals(sample, that.sample);
    }

    @Override
    public int hashCode() {
        return Objects.hash(status, sample);
    }
}
