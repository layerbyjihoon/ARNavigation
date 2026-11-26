package com.example.arnavigation.location;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertThrows;

import com.example.arnavigation.model.GeoPoint;
import com.example.arnavigation.model.LocationSample;
import java.util.concurrent.TimeUnit;
import org.junit.Test;

public final class LocationQualityEvaluatorTest {
    private static final long NOW = TimeUnit.SECONDS.toNanos(100);
    private final LocationQualityEvaluator evaluator = new LocationQualityEvaluator(25.0, 10_000L);

    @Test
    public void exactAccuracyAndAgeBoundariesAreAvailable() {
        LocationSample sample = sample(25.0, NOW - TimeUnit.SECONDS.toNanos(10));

        assertEquals(LocationStatus.AVAILABLE, evaluator.evaluate(sample, NOW, true));
    }

    @Test
    public void valuesBeyondAccuracyAndAgeBoundariesAreClassified() {
        assertEquals(LocationStatus.LOW_ACCURACY, evaluator.evaluate(sample(25.01, NOW), NOW, true));
        assertEquals(LocationStatus.STALE, evaluator.evaluate(
                sample(5.0, NOW - TimeUnit.SECONDS.toNanos(10) - 1L), NOW, true));
        assertEquals(LocationStatus.STALE, evaluator.evaluate(sample(5.0, NOW + 1L), NOW, true));
    }

    @Test
    public void coarseOnlyIsMapUsableButNotArUsable() {
        LocationStatus status = evaluator.evaluate(sample(5.0, NOW), NOW, false);

        assertEquals(LocationStatus.COARSE_ONLY, status);
        assertTrue(status.isMapUsable());
        assertFalse(status.isArUsable());
    }

    @Test
    public void invalidThresholdsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new LocationQualityEvaluator(-1.0, 1L));
        assertThrows(IllegalArgumentException.class, () -> new LocationQualityEvaluator(1.0, -1L));
    }

    private static LocationSample sample(double accuracyMeters, long elapsedRealtimeNanos) {
        return new LocationSample(
                new GeoPoint(37.4963, 126.9574), accuracyMeters, null, 0.0, elapsedRealtimeNanos);
    }
}
