package com.google.ar.core.examples.java.helloar;

import java.nio.FloatBuffer;

/** Distance to an observed plane polygon in its local X/Z coordinates, in meters. */
final class FloorPlaneGeometry {
  static final float MAX_DISTANCE_METERS = 3f;

  private FloorPlaneGeometry() {}

  static boolean isNearbyFloor(float cameraAbovePlane, float distance) {
    return cameraAbovePlane >= 0.4f && cameraAbovePlane <= 2.5f
        && distance <= MAX_DISTANCE_METERS;
  }

  static float distanceToPolygon(FloatBuffer polygon, float x, float z) {
    int count = polygon.limit() / 2;
    if (count < 3 || !Float.isFinite(x) || !Float.isFinite(z))
      return Float.POSITIVE_INFINITY;
    boolean inside = false;
    double minSquared = Double.POSITIVE_INFINITY;
    for (int i = 0, j = count - 1; i < count; j = i++) {
      float ax = polygon.get(j * 2), az = polygon.get(j * 2 + 1);
      float bx = polygon.get(i * 2), bz = polygon.get(i * 2 + 1);
      if (!Float.isFinite(ax) || !Float.isFinite(az)
          || !Float.isFinite(bx) || !Float.isFinite(bz)) return Float.POSITIVE_INFINITY;
      if ((az > z) != (bz > z) && x < (double) (bx - ax) * (z - az) / (bz - az) + ax)
        inside = !inside;
      double dx = bx - ax, dz = bz - az;
      double lengthSquared = dx * dx + dz * dz;
      double t = lengthSquared == 0 ? 0
          : Math.max(0, Math.min(1, ((x - ax) * dx + (z - az) * dz) / lengthSquared));
      double ex = x - ax - t * dx, ez = z - az - t * dz;
      minSquared = Math.min(minSquared, ex * ex + ez * ez);
    }
    return inside ? 0 : (float) Math.sqrt(minSquared);
  }
}
