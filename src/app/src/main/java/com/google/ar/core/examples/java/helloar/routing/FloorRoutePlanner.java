package com.google.ar.core.examples.java.helloar.routing;

import com.google.ar.core.examples.java.helloar.map.EdgeType;
import com.google.ar.core.examples.java.helloar.map.MapPosition;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Splits a routed path at mapped stairs, elevators, escalators and ramps. */
public final class FloorRoutePlanner {
  private FloorRoutePlanner() {}

  public static final class Leg {
    public final String floorId, nextFloorId;
    public final EdgeType transition;
    public final boolean ascending;
    public final List<MapPosition> points;

    private Leg(String floorId, String nextFloorId, EdgeType transition,
        boolean ascending, List<MapPosition> points) {
      this.floorId = floorId;
      this.nextFloorId = nextFloorId;
      this.transition = transition;
      this.ascending = ascending;
      this.points = Collections.unmodifiableList(new ArrayList<>(points));
    }
  }

  public static List<Leg> split(NavigationRoute route) {
    if (route == null || route.nodes.isEmpty()) throw new IllegalArgumentException("Route is empty");
    List<Leg> legs = new ArrayList<>();
    List<MapPosition> points = new ArrayList<>();
    points.add(route.nodes.get(0).position);
    String floor = route.nodes.get(0).floorId;
    for (RouteSegment segment : route.segments) {
      if (segment.isFloorTransition()) {
        if (segment.edge.type != EdgeType.STAIR && segment.edge.type != EdgeType.ELEVATOR
            && segment.edge.type != EdgeType.ESCALATOR && segment.edge.type != EdgeType.RAMP)
          throw new IllegalArgumentException("Unsupported floor transition");
        // A surveyed stair polyline belongs to the departing stage so the AR ribbon
        // remains visible while climbing. Unknown-height demo links stay text-only.
        if (segment.edge.type == EdgeType.STAIR
            && Math.abs(segment.toNode.position.y - segment.fromNode.position.y) >= 0.5) {
          for (MapPosition point : segment.geometry)
            if (points.get(points.size() - 1).distanceTo(point) > 0.000001) points.add(point);
        }
        ensureTwoPoints(points);
        legs.add(new Leg(floor, segment.toFloorId, segment.edge.type,
            ascending(segment), points));
        floor = segment.toFloorId;
        points = new ArrayList<>();
        points.add(segment.toNode.position);
      } else {
        if (!floor.equals(segment.floorId))
          throw new IllegalArgumentException("Route floor sequence is inconsistent");
        for (MapPosition point : segment.geometry)
          if (points.get(points.size() - 1).distanceTo(point) > 0.000001) points.add(point);
      }
    }
    ensureTwoPoints(points);
    legs.add(new Leg(floor, null, null, false, points));
    return Collections.unmodifiableList(legs);
  }

  private static void ensureTwoPoints(List<MapPosition> points) {
    if (points.size() == 1) points.add(points.get(0));
  }

  private static boolean ascending(RouteSegment segment) {
    if (segment.toNode.position.y != segment.fromNode.position.y)
      return segment.toNode.position.y > segment.fromNode.position.y;
    try {
      return Integer.parseInt(segment.toFloorId.substring(1))
          > Integer.parseInt(segment.floorId.substring(1));
    } catch (NumberFormatException | IndexOutOfBoundsException e) {
      return false;
    }
  }
}
