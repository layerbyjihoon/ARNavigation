package com.example.arnavigation.heading;

/** Fixed-window circular mean that remains stable across the 0/360 degree boundary. */
public final class HeadingSmoother {
    private final double[] headings;
    private int size;
    private int nextIndex;

    public HeadingSmoother(int windowSize) {
        if (windowSize <= 0) {
            throw new IllegalArgumentException("windowSize must be positive");
        }
        headings = new double[windowSize];
    }

    public double add(double headingDegrees) {
        double normalized = HeadingMath.normalizeDegrees(headingDegrees);
        headings[nextIndex] = normalized;
        nextIndex = (nextIndex + 1) % headings.length;
        if (size < headings.length) size++;

        double sinSum = 0.0d;
        double cosSum = 0.0d;
        for (int index = 0; index < size; index++) {
            double radians = Math.toRadians(headings[index]);
            sinSum += Math.sin(radians);
            cosSum += Math.cos(radians);
        }
        if (Math.abs(sinSum) < 1.0e-12d && Math.abs(cosSum) < 1.0e-12d) {
            return normalized;
        }
        return HeadingMath.normalizeDegrees(Math.toDegrees(Math.atan2(sinSum, cosSum)));
    }

    public void reset() {
        size = 0;
        nextIndex = 0;
    }
}
