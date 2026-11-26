package com.example.arnavigation.heading;

import android.content.Context;
import com.example.arnavigation.config.AppConfig;
import com.example.arnavigation.model.GeoPoint;
import com.example.arnavigation.model.HeadingSample;
import com.example.arnavigation.model.SignalConfidence;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/** Foreground-only, smoothed true-north heading provider. */
public final class SensorHeadingProvider implements HeadingProvider {
    private final HeadingSensorBackend backend;
    private final DeclinationProvider declinationProvider;
    private final HeadingSmoother smoother;
    private final long minimumUpdateIntervalNanos;

    private HeadingListener listener;
    private boolean started;
    private long generation;
    private boolean hasLastDelivery;
    private long lastDeliveryElapsedNanos;
    private double declinationDegrees;

    public SensorHeadingProvider(Context context) {
        this(
                new AndroidHeadingSensorBackend(
                        Objects.requireNonNull(context, "context must not be null")),
                new GeomagneticDeclinationProvider(),
                new HeadingSmoother(AppConfig.HEADING_SMOOTHING_WINDOW_SIZE),
                AppConfig.MAX_ARROW_UPDATES_PER_SECOND);
    }

    SensorHeadingProvider(
            HeadingSensorBackend backend,
            DeclinationProvider declinationProvider,
            HeadingSmoother smoother,
            int maxUpdatesPerSecond) {
        this.backend = Objects.requireNonNull(backend, "backend must not be null");
        this.declinationProvider = Objects.requireNonNull(
                declinationProvider, "declinationProvider must not be null");
        this.smoother = Objects.requireNonNull(smoother, "smoother must not be null");
        if (maxUpdatesPerSecond <= 0) {
            throw new IllegalArgumentException("maxUpdatesPerSecond must be positive");
        }
        minimumUpdateIntervalNanos = TimeUnit.SECONDS.toNanos(1L) / maxUpdatesPerSecond;
    }

    /** Updates the location used by Android's geomagnetic model. */
    public void updateGeomagneticReference(
            GeoPoint point, double altitudeMeters, long timestampMillis) {
        Objects.requireNonNull(point, "point must not be null");
        declinationDegrees = declinationProvider.getDeclinationDegrees(
                point.getLatitude(),
                point.getLongitude(),
                altitudeMeters,
                timestampMillis);
        if (!Double.isFinite(declinationDegrees)) {
            throw new IllegalArgumentException("declination must be finite");
        }
    }

    @Override
    public void start(HeadingListener listener) {
        this.listener = Objects.requireNonNull(listener, "listener must not be null");
        if (started) return;

        started = true;
        smoother.reset();
        hasLastDelivery = false;
        long session = ++generation;
        boolean registered = backend.start(new HeadingSensorBackend.Callback() {
            @Override
            public void onMagneticHeading(
                    double magneticHeadingDegrees,
                    SignalConfidence confidence,
                    long elapsedRealtimeNanos,
                    long timestampMillis) {
                handleHeading(
                        session,
                        magneticHeadingDegrees,
                        confidence,
                        elapsedRealtimeNanos,
                        timestampMillis);
            }

            @Override
            public void onUnavailable() {
                if (isCurrentSession(session)) {
                    notifyListener(HeadingResult.unavailable());
                }
            }
        });
        if (!registered && isCurrentSession(session)) {
            started = false;
            notifyListener(HeadingResult.unavailable());
        }
    }

    @Override
    public void stop() {
        if (!started) {
            listener = null;
            return;
        }
        started = false;
        listener = null;
        generation++;
        smoother.reset();
        hasLastDelivery = false;
        backend.stop();
    }

    private void handleHeading(
            long session,
            double magneticHeadingDegrees,
            SignalConfidence confidence,
            long elapsedRealtimeNanos,
            long timestampMillis) {
        if (!isCurrentSession(session)) return;
        if (elapsedRealtimeNanos < 0L || timestampMillis < 0L) return;
        if (hasLastDelivery) {
            if (elapsedRealtimeNanos <= lastDeliveryElapsedNanos
                    || elapsedRealtimeNanos - lastDeliveryElapsedNanos
                    < minimumUpdateIntervalNanos) {
                return;
            }
        }

        double trueHeading = HeadingMath.applyDeclination(
                magneticHeadingDegrees, declinationDegrees);
        double smoothedHeading = smoother.add(trueHeading);
        SignalConfidence safeConfidence = confidence == null
                ? SignalConfidence.UNAVAILABLE : confidence;
        HeadingStatus status = safeConfidence == SignalConfidence.HIGH
                        || safeConfidence == SignalConfidence.MEDIUM
                ? HeadingStatus.AVAILABLE
                : HeadingStatus.LOW_ACCURACY;

        hasLastDelivery = true;
        lastDeliveryElapsedNanos = elapsedRealtimeNanos;
        notifyListener(new HeadingResult(
                status,
                new HeadingSample(smoothedHeading, safeConfidence, timestampMillis)));
    }

    private boolean isCurrentSession(long session) {
        return started && generation == session;
    }

    private void notifyListener(HeadingResult result) {
        HeadingListener current = listener;
        if (current != null) current.onHeadingChanged(result);
    }
}
