package com.example.arnavigation.heading;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.arnavigation.model.GeoPoint;
import com.example.arnavigation.model.SignalConfidence;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.Test;

public final class SensorHeadingProviderTest {
    @Test
    public void startAndStopAreIdempotentAndLateCallbacksAreIgnored() {
        FakeBackend backend = new FakeBackend(true);
        SensorHeadingProvider provider = provider(backend, 10);
        List<HeadingResult> results = new ArrayList<>();

        provider.start(results::add);
        provider.start(results::add);
        assertEquals(1, backend.startCount);

        provider.stop();
        provider.stop();
        assertEquals(1, backend.stopCount);

        backend.emit(0.0, SignalConfidence.HIGH, 1L, 1L);
        assertTrue(results.isEmpty());
    }

    @Test
    public void unsupportedSensorsProduceUnavailableState() {
        FakeBackend backend = new FakeBackend(false);
        SensorHeadingProvider provider = provider(backend, 10);
        List<HeadingResult> results = new ArrayList<>();

        provider.start(results::add);

        assertEquals(HeadingStatus.UNAVAILABLE, results.get(0).getStatus());
        assertFalse(results.get(0).getStatus().isArrowUsable());
    }

    @Test
    public void lowAccuracyHidesArrowAndDeclinationIsApplied() {
        FakeBackend backend = new FakeBackend(true);
        SensorHeadingProvider provider = new SensorHeadingProvider(
                backend,
                (latitude, longitude, altitude, time) -> 10.0,
                new HeadingSmoother(1),
                10);
        provider.updateGeomagneticReference(new GeoPoint(37.5, 127.0), 50.0, 1L);
        List<HeadingResult> results = new ArrayList<>();
        provider.start(results::add);

        backend.emit(355.0, SignalConfidence.LOW, 1L, 2L);

        HeadingResult result = results.get(0);
        assertEquals(HeadingStatus.LOW_ACCURACY, result.getStatus());
        assertFalse(result.getStatus().isArrowUsable());
        assertEquals(5.0, result.getSample().getTrueHeadingDegrees(), 1.0e-9);
    }

    @Test
    public void outputIsLimitedToConfiguredRate() {
        FakeBackend backend = new FakeBackend(true);
        SensorHeadingProvider provider = provider(backend, 10);
        List<HeadingResult> results = new ArrayList<>();
        provider.start(results::add);
        long interval = TimeUnit.MILLISECONDS.toNanos(100L);

        backend.emit(0.0, SignalConfidence.HIGH, interval, 1L);
        backend.emit(10.0, SignalConfidence.HIGH, interval + 1L, 2L);
        backend.emit(20.0, SignalConfidence.HIGH, interval * 2L, 3L);

        assertEquals(2, results.size());
        assertTrue(results.get(0).getStatus().isArrowUsable());
    }

    private static SensorHeadingProvider provider(FakeBackend backend, int maxUpdatesPerSecond) {
        return new SensorHeadingProvider(
                backend,
                (latitude, longitude, altitude, time) -> 0.0,
                new HeadingSmoother(3),
                maxUpdatesPerSecond);
    }

    private static final class FakeBackend implements HeadingSensorBackend {
        private final boolean supported;
        private Callback callback;
        int startCount;
        int stopCount;

        FakeBackend(boolean supported) {
            this.supported = supported;
        }

        @Override
        public boolean start(Callback callback) {
            startCount++;
            if (supported) this.callback = callback;
            return supported;
        }

        @Override
        public void stop() {
            stopCount++;
        }

        void emit(
                double heading,
                SignalConfidence confidence,
                long elapsedRealtimeNanos,
                long timestampMillis) {
            if (callback != null) {
                callback.onMagneticHeading(
                        heading, confidence, elapsedRealtimeNanos, timestampMillis);
            }
        }
    }
}
