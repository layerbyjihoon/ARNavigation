package com.google.ar.core.examples.java.helloar;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** The mapped stair tail, or a short provisional continuation when stair height is unknown. */
final class StairFallbackPath {
  private static final float PROVISIONAL_LENGTH_METERS = 2f;
  private static final float PROVISIONAL_RISE_METERS = 0.6f;
  final Waypoint start;
  final List<Waypoint> waypoints;
  final boolean hasStairHeight;

  private StairFallbackPath(Waypoint start, List<Waypoint> waypoints, boolean hasStairHeight) {
    this.start = start;
    this.waypoints = Collections.unmodifiableList(new ArrayList<>(waypoints));
    this.hasStairHeight = hasStairHeight;
  }

  static StairFallbackPath from(List<Waypoint> route, boolean ascending) {
    if (route == null || route.isEmpty()) throw new IllegalArgumentException("Empty stair route");
    float baseY = route.get(0).y;
    for (int i = 1; i < route.size(); i++) {
      if (Math.abs(route.get(i).y - baseY) > 0.5f) {
        int startIndex = Math.max(0, i - 2);
        return new StairFallbackPath(route.get(startIndex),
            route.subList(startIndex + 1, route.size()), true);
      }
    }
    for (int i = route.size() - 1; i > 0; i--) {
      Waypoint previous = route.get(i - 1);
      Waypoint current = route.get(i);
      float length = current.horizontalDistanceTo(previous.x, previous.z);
      if (length > 0.1f) {
        List<Waypoint> tail = new ArrayList<>(route.subList(i, route.size()));
        Waypoint entrance = route.get(route.size() - 1);
        tail.add(new Waypoint(
            entrance.x + (current.x - previous.x) / length * PROVISIONAL_LENGTH_METERS,
            entrance.y + (ascending ? PROVISIONAL_RISE_METERS : -PROVISIONAL_RISE_METERS),
            entrance.z + (current.z - previous.z) / length * PROVISIONAL_LENGTH_METERS));
        return new StairFallbackPath(previous, tail, false);
      }
    }
    Waypoint origin = new Waypoint(0, baseY, 0);
    Waypoint entrance = route.get(route.size() - 1);
    float length = entrance.horizontalDistanceTo(origin.x, origin.z);
    if (length <= 0.1f) return new StairFallbackPath(origin, route, false);
    List<Waypoint> tail = new ArrayList<>(route);
    tail.add(new Waypoint(
        entrance.x + (entrance.x - origin.x) / length * PROVISIONAL_LENGTH_METERS,
        entrance.y + (ascending ? PROVISIONAL_RISE_METERS : -PROVISIONAL_RISE_METERS),
        entrance.z + (entrance.z - origin.z) / length * PROVISIONAL_LENGTH_METERS));
    return new StairFallbackPath(origin, tail, false);
  }

  static Waypoint entrance(List<Waypoint> route) {
    if (route == null || route.isEmpty()) throw new IllegalArgumentException("Empty stair route");
    float baseY = route.get(0).y;
    for (int i = 1; i < route.size(); i++)
      if (Math.abs(route.get(i).y - baseY) > 0.5f) return route.get(i - 1);
    return route.get(route.size() - 1);
  }

  Waypoint markerPoint() {
    if (hasStairHeight) return waypoints.get(waypoints.size() / 2);
    Waypoint entrance = waypoints.size() > 1
        ? waypoints.get(waypoints.size() - 2) : start;
    Waypoint end = waypoints.get(waypoints.size() - 1);
    return new Waypoint((entrance.x + end.x) / 2f,
        (entrance.y + end.y) / 2f, (entrance.z + end.z) / 2f);
  }
}
