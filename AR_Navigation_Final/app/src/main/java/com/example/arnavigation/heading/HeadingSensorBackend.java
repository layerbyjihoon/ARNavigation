package com.example.arnavigation.heading;

import com.example.arnavigation.model.SignalConfidence;

/** Package-private Android sensor boundary used for deterministic lifecycle tests. */
interface HeadingSensorBackend {
    interface Callback {
        void onMagneticHeading(
                double magneticHeadingDegrees,
                SignalConfidence confidence,
                long elapsedRealtimeNanos,
                long timestampMillis);

        void onUnavailable();
    }

    boolean start(Callback callback);

    void stop();
}
