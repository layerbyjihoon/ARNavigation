package com.example.arnavigation.location;

import com.example.arnavigation.model.LocationSample;
import java.util.Objects;

/** Rejects duplicate or out-of-order observations within one provider session. */
final class LocationSampleOrderFilter {
    private Long lastElapsedRealtimeNanos;

    boolean accept(LocationSample sample) {
        Objects.requireNonNull(sample, "sample must not be null");
        long timestamp = sample.getElapsedRealtimeNanos();
        if (lastElapsedRealtimeNanos != null && timestamp <= lastElapsedRealtimeNanos) {
            return false;
        }
        lastElapsedRealtimeNanos = timestamp;
        return true;
    }

    void reset() {
        lastElapsedRealtimeNanos = null;
    }
}
