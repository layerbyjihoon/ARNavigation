package com.google.ar.core.examples.java.helloar;

import java.util.Collections;
import java.util.List;

/** Rejects depth samples that do not resemble a visible rising or descending stair run. */
final class StairDepthPath {
  private StairDepthPath() {}

  static boolean plausible(List<Waypoint> points, boolean ascending) {
    if (points.size() < 3) return false;
    Waypoint first = points.get(0), last = points.get(points.size() - 1);
    float rise = last.y - first.y;
    if (ascending ? rise < 0.25f : rise > -0.25f) return false;
    if (last.horizontalDistanceTo(first.x, first.z) < 0.3f) return false;
    for (int i = 1; i < points.size(); i++) {
      float step = points.get(i).y - points.get(i - 1).y;
      if (ascending ? step < -0.15f : step > 0.15f) return false;
    }
    return true;
  }

  static List<Waypoint> bestPlausible(List<List<Waypoint>> profiles, boolean ascending) {
    List<Waypoint> best = Collections.emptyList();
    float bestScore = Float.NEGATIVE_INFINITY;
    for (List<Waypoint> points : profiles) {
      if (!plausible(points, ascending)) continue;
      Waypoint first = points.get(0), last = points.get(points.size() - 1);
      float score = points.size() + Math.abs(last.y - first.y);
      if (score > bestScore) {
        best = points;
        bestScore = score;
      }
    }
    return best;
  }

  /** Strong depth evidence can recover a stair line when image labeling misses a side view. */
  static boolean strong(List<Waypoint> points, boolean ascending) {
    if (!plausible(points, ascending) || points.size() < 4) return false;
    Waypoint first = points.get(0), last = points.get(points.size() - 1);
    return Math.abs(last.y - first.y) >= 0.45f
        && last.horizontalDistanceTo(first.x, first.z) >= 0.6f;
  }
}
