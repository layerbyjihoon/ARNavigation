package com.example.arnavigation.model;

import java.util.Objects;

/** Immutable true-north heading. Heading is degrees in [0, 360); timestamp is Unix millis. */
public final class HeadingSample {
    private final double trueHeadingDegrees;
    private final SignalConfidence accuracy;
    private final long timestampMillis;

    public HeadingSample(
            double trueHeadingDegrees,
            SignalConfidence accuracy,
            long timestampMillis) {
        ModelValidation.requireFinite(trueHeadingDegrees, "trueHeadingDegrees");
        if (trueHeadingDegrees < 0.0d || trueHeadingDegrees >= 360.0d) {
            throw new IllegalArgumentException("trueHeadingDegrees must be in [0, 360)");
        }
        this.trueHeadingDegrees = trueHeadingDegrees;
        this.accuracy = ModelValidation.requireNonNull(accuracy, "accuracy");
        this.timestampMillis = ModelValidation.requireNonNegative(timestampMillis, "timestampMillis");
    }

    public double getTrueHeadingDegrees() {
        return trueHeadingDegrees;
    }

    public SignalConfidence getAccuracy() {
        return accuracy;
    }

    public long getTimestampMillis() {
        return timestampMillis;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof HeadingSample)) return false;
        HeadingSample that = (HeadingSample) other;
        return Double.compare(trueHeadingDegrees, that.trueHeadingDegrees) == 0
                && timestampMillis == that.timestampMillis
                && accuracy == that.accuracy;
    }

    @Override
    public int hashCode() {
        return Objects.hash(trueHeadingDegrees, accuracy, timestampMillis);
    }
}
