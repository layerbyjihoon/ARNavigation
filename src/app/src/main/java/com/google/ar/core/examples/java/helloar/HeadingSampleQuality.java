package com.google.ar.core.examples.java.helloar;

/** Validates rotation vectors without confusing an absent error estimate with a bad heading. */
final class HeadingSampleQuality {
  private static final float UNKNOWN_HEADING_ERROR = -1f;
  private static final double MAX_HEADING_ERROR_RADIANS = Math.toRadians(20);

  private HeadingSampleQuality() {}

  static boolean isUsable(int accuracy, float[] values) {
    // Android accuracy: 0=unreliable, 1=low, 2=medium, 3=high.
    if (accuracy < 2 || values == null || values.length < 3) return false;
    for (int i = 0; i < Math.min(4, values.length); i++)
      if (!Float.isFinite(values[i])) return false;
    if (values.length >= 5) {
      float error = values[4];
      // Android explicitly defines -1 as "estimate unavailable". Use event accuracy instead.
      if (error != UNKNOWN_HEADING_ERROR
          && (!Float.isFinite(error) || error < 0 || error > MAX_HEADING_ERROR_RADIANS)) return false;
    }
    return true;
  }
}
