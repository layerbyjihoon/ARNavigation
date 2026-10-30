package com.google.ar.core.examples.java.helloar;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
/** Immutable handoff from the map screen to the GL thread. First point is the manual origin. */
final class ArRoutePlan {
  final String name;
  final String referenceId;
  final Double entryYawDegrees;
  final String entryDirectionLabel;
  final List<Waypoint> waypoints;
  private ArRoutePlan(String name, List<Waypoint> waypoints, String referenceId,
      Double entryYawDegrees, String entryDirectionLabel) {
    this.entryYawDegrees = entryYawDegrees;
    this.entryDirectionLabel = entryDirectionLabel;
    this.referenceId = referenceId == null ? "legacy" : referenceId;
    this.name = name; this.waypoints = Collections.unmodifiableList(waypoints);
  }
  static ArRoutePlan decode(String name, float[] points) {
    return decode(name, points, "legacy");
  }
  static ArRoutePlan decode(String name, float[] points, String referenceId) {
    return decode(name, points, referenceId, null, null);
  }
  static ArRoutePlan decode(String name, float[] points, String referenceId,
      Double entryYawDegrees, String entryDirectionLabel) {
    if (entryYawDegrees != null && (!Double.isFinite(entryYawDegrees) || Math.abs(entryYawDegrees) > 180))
      throw new IllegalArgumentException("Invalid entry direction");
    if (points == null || points.length < 6 || points.length % 3 != 0 || points.length > 3075)
      throw new IllegalArgumentException("Invalid AR route points");
    for (float point : points)
      if (!Float.isFinite(point) || Math.abs(point) > 100f)
        throw new IllegalArgumentException("AR preview supports points within 100m of the origin");
    if (Math.abs(points[0]) > 0.001f || Math.abs(points[1] + 0.5f) > 0.001f
        || Math.abs(points[2]) > 0.001f) throw new IllegalArgumentException("Invalid AR route origin");
    List<Waypoint> waypoints = new ArrayList<>();
    for (int i = 3; i < points.length; i += 3)
      waypoints.add(new Waypoint(points[i], points[i+1], points[i+2]));
    return new ArRoutePlan(name == null ? "지도 경로" : name, waypoints, referenceId,
        entryYawDegrees, entryDirectionLabel == null ? "문·진입점 → 복도" : entryDirectionLabel);
  }
}
