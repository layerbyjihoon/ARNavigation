package com.google.ar.core.examples.java.helloar;

import java.util.List;

/** Horizontal progress on the active route leg, independent of lateral camera drift. */
final class RouteProgress {
  private static final Waypoint ORIGIN = new Waypoint(0f, -0.5f, 0f);
  private static final float MIN_LEG_LENGTH = 0.01f;
  private static final float PASSING_CORRIDOR_WIDTH = 1.2f;

  private RouteProgress() {}

  static float distanceToNext(List<Waypoint> route, int index, float x, float z) {
    Waypoint from = index == 0 ? ORIGIN : route.get(index - 1);
    Waypoint to = route.get(index);
    float dx = to.x - from.x;
    float dz = to.z - from.z;
    float length = (float) Math.hypot(dx, dz);
    if (length < MIN_LEG_LENGTH) return 0f;
    float progress = ((x - from.x) * dx + (z - from.z) * dz) / length;
    return Math.max(0f, Math.min(length, length - progress));
  }

  static boolean passedIntermediate(List<Waypoint> route, int index, float x, float z) {
    if (index >= route.size() - 1) return false;
    Waypoint from = index == 0 ? ORIGIN : route.get(index - 1);
    Waypoint to = route.get(index);
    float dx = to.x - from.x;
    float dz = to.z - from.z;
    float length = (float) Math.hypot(dx, dz);
    if (length < MIN_LEG_LENGTH) return false;
    float along = ((x - from.x) * dx + (z - from.z) * dz) / length;
    float across = Math.abs((x - from.x) * dz - (z - from.z) * dx) / length;
    return along >= length && across <= PASSING_CORRIDOR_WIDTH;
  }
}
