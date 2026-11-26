package com.example.arnavigation.heading;

/** Pure angle operations shared by sensor and navigation code. */
public final class HeadingMath {
    private HeadingMath() {}

    /** Normalizes an angle to [0, 360). */
    public static double normalizeDegrees(double degrees) {
        requireFinite(degrees, "degrees");
        double normalized = degrees % 360.0d;
        if (normalized < 0.0d) normalized += 360.0d;
        return normalized == 360.0d ? 0.0d : normalized;
    }

    /** Normalizes an angle to [-180, 180). */
    public static double normalizeSignedDegrees(double degrees) {
        double normalized = normalizeDegrees(degrees);
        return normalized >= 180.0d ? normalized - 360.0d : normalized;
    }

    /** Converts magnetic north to true north using local declination in degrees. */
    public static double applyDeclination(double magneticHeadingDegrees, double declinationDegrees) {
        requireFinite(declinationDegrees, "declinationDegrees");
        return normalizeDegrees(magneticHeadingDegrees + declinationDegrees);
    }

    private static void requireFinite(double value, String name) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
    }
}
