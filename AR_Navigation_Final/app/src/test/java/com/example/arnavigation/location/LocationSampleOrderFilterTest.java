package com.example.arnavigation.location;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.arnavigation.model.GeoPoint;
import com.example.arnavigation.model.LocationSample;
import org.junit.Test;

public final class LocationSampleOrderFilterTest {
    @Test
    public void rejectsDuplicateAndOlderSamplesUntilReset() {
        LocationSampleOrderFilter filter = new LocationSampleOrderFilter();

        assertTrue(filter.accept(sample(20L)));
        assertFalse(filter.accept(sample(20L)));
        assertFalse(filter.accept(sample(19L)));
        assertTrue(filter.accept(sample(21L)));

        filter.reset();
        assertTrue(filter.accept(sample(1L)));
    }

    private static LocationSample sample(long elapsedRealtimeNanos) {
        return new LocationSample(
                new GeoPoint(37.4963, 126.9574), 5.0, null, 0.0, elapsedRealtimeNanos);
    }
}
