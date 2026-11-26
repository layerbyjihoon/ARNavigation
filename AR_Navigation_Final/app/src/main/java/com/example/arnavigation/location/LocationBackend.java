package com.example.arnavigation.location;

import com.example.arnavigation.model.LocationSample;

/** Package-private boundary around Google Play services for deterministic provider tests. */
interface LocationBackend {
    interface SettingsCallback {
        void onReady();

        void onDisabled();
    }

    interface SampleCallback {
        void onSample(LocationSample sample);

        void onUnavailable();
    }

    interface UpdateCallback extends SampleCallback {}

    void checkSettings(SettingsCallback callback);

    void requestCurrentLocation(SampleCallback callback);

    void startLocationUpdates(UpdateCallback callback);

    void stopLocationUpdates();
}
