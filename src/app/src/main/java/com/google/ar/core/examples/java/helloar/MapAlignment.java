package com.google.ar.core.examples.java.helloar;

/** Pure alignment math. Bearings are clockwise from magnetic north; AR yaw is about +Y. */
final class MapAlignment {
  private MapAlignment() {}

  // Sensor matrix: row-major device -> East/North/Up. AR matrix: column-major device -> AR.
  // Select a well-conditioned horizontal device axis, including when the camera points down.
  static double northYaw(float[] sensor, float[] ar) {
    int axis = 0;
    double best = -1;
    for (int i = 0; i < 3; i++) {
      double strength = Math.min(Math.hypot(sensor[i], sensor[3+i]),
          Math.hypot(ar[4*i], ar[4*i+2]));
      if (strength > best) { best = strength; axis = i; }
    }
    if (best < 0.5) throw new IllegalArgumentException("Inconsistent sensor orientation");
    double bearing = Math.atan2(sensor[axis], sensor[3+axis]);
    double worldYaw = Math.atan2(-ar[4*axis], -ar[4*axis+2]);
    return wrap(worldYaw + bearing);
  }

  static double yawForBearing(double northYaw, double bearingDegrees) {
    return wrap(northYaw - Math.toRadians(bearingDegrees));
  }

  static double forwardYaw(float[] camera) {
    if (Math.hypot(camera[8], camera[10]) < 0.3)
      throw new IllegalArgumentException("Point the camera along the corridor");
    return Math.atan2(camera[8], camera[10]);
  }

  static double wrap(double radians) { return Math.atan2(Math.sin(radians), Math.cos(radians)); }

  static double referenceYawForEntry(double cameraYaw, double entryYawDegrees) {
    return wrap(cameraYaw - Math.toRadians(entryYawDegrees));
  }
}
