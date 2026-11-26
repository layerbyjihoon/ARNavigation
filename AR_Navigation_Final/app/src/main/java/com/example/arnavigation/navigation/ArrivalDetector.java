package com.example.arnavigation.navigation;

import com.example.arnavigation.model.GeoPoint;
import com.example.arnavigation.model.LocationSample;
import java.util.Objects;

/** Confirms arrival only after consecutive valid observations and emits once per session. */
public final class ArrivalDetector {
    private final double arrivalRadiusMeters;
    private final int requiredConsecutiveSamples;
    private final DirectionCalculator directionCalculator;
    private int consecutiveSamples;
    private boolean arrived;

    public ArrivalDetector(NavigationConfig config, DirectionCalculator directionCalculator) {
        Objects.requireNonNull(config, "config must not be null");
        this.arrivalRadiusMeters = config.getArrivalRadiusMeters();
        this.requiredConsecutiveSamples = config.getArrivalConfirmationCount();
        this.directionCalculator = Objects.requireNonNull(
                directionCalculator, "directionCalculator must not be null");
    }

    /** Returns true only on the observation that first confirms arrival. */
    public boolean update(LocationSample location, GeoPoint destination) {
        Objects.requireNonNull(location, "location must not be null");
        Objects.requireNonNull(destination, "destination must not be null");
        if (arrived) return false;
        if (directionCalculator.distanceMeters(location.getPoint(), destination)
                <= arrivalRadiusMeters) {
            consecutiveSamples++;
        } else {
            consecutiveSamples = 0;
        }
        if (consecutiveSamples >= requiredConsecutiveSamples) {
            arrived = true;
            return true;
        }
        return false;
    }

    public boolean isArrived() { return arrived; }

    public void reset() {
        consecutiveSamples = 0;
        arrived = false;
    }
}
