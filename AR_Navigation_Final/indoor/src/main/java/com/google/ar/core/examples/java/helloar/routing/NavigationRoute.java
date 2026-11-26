package com.google.ar.core.examples.java.helloar.routing;

import com.google.ar.core.examples.java.helloar.map.*;
import java.util.*;
public final class NavigationRoute {
  public final List<MapNode> nodes;
  public final List<RouteSegment> segments;
  public final double totalDistance;
  NavigationRoute(List<MapNode> nodes, List<RouteSegment> segments) {
    this.nodes = Collections.unmodifiableList(new ArrayList<>(nodes));
    this.segments = Collections.unmodifiableList(new ArrayList<>(segments));
    double total = 0; for (RouteSegment segment : segments) total += segment.distance;
    totalDistance = total;
  }
  public boolean isMultiFloor() {
    for (RouteSegment segment : segments) if (segment.isFloorTransition()) return true;
    return false;
  }
  /** Ordered polyline in map coordinates; reversed edges have already been reversed. */
  public List<MapPosition> geometry() {
    List<MapPosition> points = new ArrayList<>();
    points.add(nodes.get(0).position);
    for (RouteSegment segment : segments) {
      for (MapPosition point : segment.geometry)
        if (points.get(points.size()-1).distanceTo(point) > 0.000001) points.add(point);
    }
    return Collections.unmodifiableList(points);
  }
}
