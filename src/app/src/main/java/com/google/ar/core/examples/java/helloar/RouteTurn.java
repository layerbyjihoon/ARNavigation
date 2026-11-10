package com.google.ar.core.examples.java.helloar;

import java.util.List;

/** An upcoming bend and its distance along the selected, origin-relative route. */
final class RouteTurn {
  enum Direction { LEFT, RIGHT, BACK }

  private static final Waypoint ORIGIN = new Waypoint(0, -0.5f, 0);
  private static final float MIN_SEGMENT = 0.01f;
  private static final float TURN_RADIUS = 1f;
  private static final float DEPARTURE_DISTANCE = 0.35f;

  final Direction direction;
  final float distance;

  private RouteTurn(Direction direction, float distance) {
    this.direction = direction;
    this.distance = distance;
  }

  static RouteTurn next(List<Waypoint> route, int index, float x, float z) {
    Waypoint point = route.get(index);
    int previousIndex = previousDistinctIndex(route, index);
    Waypoint previous = previousIndex < 0 ? ORIGIN : route.get(previousIndex);
    if (previousIndex >= 0 && previous.horizontalDistanceTo(x, z) < TURN_RADIUS) {
      // Keep the cue visible after waypoint arrival until the user enters the outgoing leg.
      float departure = ((x - previous.x) * (point.x - previous.x)
          + (z - previous.z) * (point.z - previous.z))
          / point.horizontalDistanceTo(previous.x, previous.z);
      if (departure < DEPARTURE_DISTANCE) {
        int incomingIndex = previousDistinctIndex(route, previousIndex);
        Waypoint incoming = incomingIndex < 0 ? ORIGIN : route.get(incomingIndex);
        Direction direction = direction(incoming, previous, point);
        if (direction != null) return new RouteTurn(direction, 0);
      }
    }

    float distance = point.horizontalDistanceTo(x, z);
    for (int i = index + 1; i < route.size(); i++) {
      Waypoint next = route.get(i);
      float length = next.horizontalDistanceTo(point.x, point.z);
      if (length < MIN_SEGMENT) continue;
      Direction direction = direction(previous, point, next);
      if (direction != null) return new RouteTurn(direction, distance);
      distance += length;
      previous = point;
      point = next;
    }
    return null;
  }

  private static int previousDistinctIndex(List<Waypoint> route, int index) {
    Waypoint point = route.get(index);
    for (int i = index - 1; i >= 0; i--) {
      Waypoint previous = route.get(i);
      if (point.horizontalDistanceTo(previous.x, previous.z) >= MIN_SEGMENT) return i;
    }
    return -1;
  }

  private static Direction direction(Waypoint before, Waypoint at, Waypoint after) {
    if (at.horizontalDistanceTo(before.x, before.z) < MIN_SEGMENT) return null;
    double inX = at.x - before.x, inZ = at.z - before.z;
    double outX = after.x - at.x, outZ = after.z - at.z;
    double angle = Math.toDegrees(Math.atan2(inX * outZ - inZ * outX, inX * outX + inZ * outZ));
    if (Math.abs(angle) < 35) return null;
    if (Math.abs(angle) >= 150) return Direction.BACK;
    return angle > 0 ? Direction.RIGHT : Direction.LEFT;
  }
}
