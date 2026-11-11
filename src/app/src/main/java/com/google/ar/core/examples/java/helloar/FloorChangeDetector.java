package com.google.ar.core.examples.java.helloar;

final class FloorChangeDetector {
  // shortcut: initial thresholds for height detection, tune with real stair recordings.
  private static final long STABLE_MS = 1000;
  private static final long MAX_SAMPLE_GAP_MS = 500;
  final double expectedChange;
  private final double tolerance;
  private long matchingSince = -1;
  private long lastSample = -1;

  FloorChangeDetector(double expectedChange) {
    if (!Double.isFinite(expectedChange) || expectedChange == 0) {
      throw new IllegalArgumentException("Invalid floor height change");
    }
    this.expectedChange = expectedChange;
    tolerance = Math.min(0.4, Math.abs(expectedChange) * 0.15);
  }

  boolean observe(long now, double heightChange) {
    if (!Double.isFinite(heightChange) || Math.abs(heightChange - expectedChange) > tolerance) {
      reset();
      return false;
    }
    if (matchingSince < 0 || now < lastSample || now - lastSample > MAX_SAMPLE_GAP_MS) {
      matchingSince = now;
    }
    lastSample = now;
    return now - matchingSince >= STABLE_MS;
  }

  void reset() {
    matchingSince = -1;
    lastSample = -1;
  }
}
