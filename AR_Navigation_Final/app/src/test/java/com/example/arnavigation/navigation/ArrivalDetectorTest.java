package com.example.arnavigation.navigation;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.arnavigation.model.GeoPoint;
import com.example.arnavigation.model.LocationSample;
import org.junit.Test;

public final class ArrivalDetectorTest {
    @Test
    public void requiresTwoConsecutiveObservationsAndEmitsOnce() {
        NavigationConfig config = new NavigationConfig(8.0, 20.0, 5_000L, 20.0, 2, 10_000L);
        ArrivalDetector detector = new ArrivalDetector(config, new DirectionCalculator());
        GeoPoint destination = new GeoPoint(37.0, 127.0);

        assertFalse(detector.update(sample(37.001, 127.0, 1L), destination));
        assertFalse(detector.update(sample(37.00005, 127.0, 2L), destination));
        assertTrue(detector.update(sample(37.00004, 127.0, 3L), destination));
        assertFalse(detector.update(sample(37.00003, 127.0, 4L), destination));
        assertTrue(detector.isArrived());
    }

    private static LocationSample sample(double latitude, double longitude, long timeNanos) {
        return new LocationSample(
                new GeoPoint(latitude, longitude), 5.0, null, 0.0, timeNanos);
    }
}
