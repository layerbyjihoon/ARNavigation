package com.example.arnavigation.location;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.example.arnavigation.model.GeoPoint;
import com.example.arnavigation.model.LocationSample;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public final class FusedLocationProviderTest {
    private static final long NOW = 20_000_000_000L;

    @Test
    public void startIsIdempotentAndStopRemovesUpdatesOnce() {
        FakeBackend backend = new FakeBackend();
        FusedLocationProvider provider = provider(backend, LocationPermissionLevel.FINE);
        List<LocationResult> results = new ArrayList<>();

        provider.start(results::add);
        provider.start(results::add);
        assertEquals(1, backend.settingsChecks);

        backend.settingsCallback.onReady();
        assertEquals(1, backend.currentRequests);
        assertEquals(1, backend.updateStarts);

        provider.stop();
        provider.stop();
        assertEquals(1, backend.updateStops);
    }

    @Test
    public void permissionIsCheckedBeforeAnyLocationApi() {
        FakeBackend backend = new FakeBackend();
        FusedLocationProvider provider = provider(backend, LocationPermissionLevel.NONE);
        List<LocationResult> results = new ArrayList<>();

        provider.start(results::add);

        assertEquals(0, backend.settingsChecks);
        assertEquals(LocationStatus.PERMISSION_DENIED, results.get(0).getStatus());
        assertNull(results.get(0).getSample());
    }

    @Test
    public void callbacksAfterStopAndOutOfOrderSamplesAreIgnored() {
        FakeBackend backend = new FakeBackend();
        FusedLocationProvider provider = provider(backend, LocationPermissionLevel.FINE);
        List<LocationResult> results = new ArrayList<>();
        provider.start(results::add);
        backend.settingsCallback.onReady();

        backend.updateCallback.onSample(sample(NOW - 1L));
        backend.updateCallback.onSample(sample(NOW - 2L));
        assertEquals(1, results.size());

        provider.stop();
        backend.updateCallback.onSample(sample(NOW));
        assertEquals(1, results.size());
    }

    @Test
    public void sampleFieldsAreDeliveredWithoutLoss() {
        FakeBackend backend = new FakeBackend();
        FusedLocationProvider provider = provider(backend, LocationPermissionLevel.FINE);
        List<LocationResult> results = new ArrayList<>();
        LocationSample expected = new LocationSample(
                new GeoPoint(37.4963, 126.9574), 7.25, 91.5, 1.75, NOW);
        provider.start(results::add);
        backend.settingsCallback.onReady();

        backend.currentCallback.onSample(expected);

        assertEquals(LocationStatus.AVAILABLE, results.get(0).getStatus());
        assertEquals(expected, results.get(0).getSample());
    }

    @Test
    public void singleUpdateCompletesWithDisabledStatus() {
        FakeBackend backend = new FakeBackend();
        FusedLocationProvider provider = provider(backend, LocationPermissionLevel.COARSE);
        List<LocationResult> results = new ArrayList<>();

        provider.requestSingleUpdate(results::add);
        backend.settingsCallback.onDisabled();

        assertEquals(1, results.size());
        assertEquals(LocationStatus.DISABLED, results.get(0).getStatus());
    }

    private static FusedLocationProvider provider(
            FakeBackend backend, LocationPermissionLevel permission) {
        return new FusedLocationProvider(
                backend,
                () -> permission,
                () -> NOW,
                new LocationQualityEvaluator(25.0, 10_000L));
    }

    private static LocationSample sample(long elapsedRealtimeNanos) {
        return new LocationSample(
                new GeoPoint(37.4963, 126.9574), 5.0, null, 0.0, elapsedRealtimeNanos);
    }

    private static final class FakeBackend implements LocationBackend {
        int settingsChecks;
        int currentRequests;
        int updateStarts;
        int updateStops;
        SettingsCallback settingsCallback;
        SampleCallback currentCallback;
        UpdateCallback updateCallback;

        @Override
        public void checkSettings(SettingsCallback callback) {
            settingsChecks++;
            settingsCallback = callback;
        }

        @Override
        public void requestCurrentLocation(SampleCallback callback) {
            currentRequests++;
            currentCallback = callback;
        }

        @Override
        public void startLocationUpdates(UpdateCallback callback) {
            updateStarts++;
            updateCallback = callback;
        }

        @Override
        public void stopLocationUpdates() {
            updateStops++;
        }
    }
}
