package com.google.ar.core.examples.java.helloar;

import com.google.ar.core.examples.java.helloar.map.EdgeType;
import com.google.ar.core.examples.java.helloar.map.Floor;
import com.google.ar.core.examples.java.helloar.map.IndoorMap;
import com.google.ar.core.examples.java.helloar.map.MapLoadResult;
import com.google.ar.core.examples.java.helloar.map.Poi;
import com.google.ar.core.examples.java.helloar.map.PoiCategory;
import com.google.ar.core.examples.java.helloar.routing.AStarRouteEngine;
import com.google.ar.core.examples.java.helloar.routing.GraphEdge;
import com.google.ar.core.examples.java.helloar.routing.IndoorNavGraph;
import com.google.ar.core.examples.java.helloar.routing.NavigationRoute;
import com.google.ar.core.examples.java.helloar.routing.RouteOptions;
import com.google.ar.core.examples.java.helloar.routing.RouteResult;
import java.util.Collections;

final class MunhwagwanStairRoute {
  final NavigationRoute approach;
  final NavigationRoute destination;
  final EntryDirection exitDirection;
  final String stairName;
  final String destinationFloor;
  final double heightChange;

  private MunhwagwanStairRoute(NavigationRoute approach, NavigationRoute destination,
      EntryDirection exitDirection, String stairName, String destinationFloor, double heightChange) {
    this.approach = approach;
    this.destination = destination;
    this.exitDirection = exitDirection;
    this.stairName = stairName;
    this.destinationFloor = destinationFloor;
    this.heightChange = heightChange;
  }

  static Double heightChange(IndoorMap map, String fromId, String toId) {
    Floor from = floor(map, fromId), to = floor(map, toId);
    if (from == null || to == null || from.level == to.level) return null;
    double height = 0;
    int first = Math.min(from.level, to.level);
    int last = Math.max(from.level, to.level);
    if (first < 1 || last > 5) return null;
    for (int level = first + 1; level <= last; level++) {
      Floor upper = floor(map, "F" + level);
      if (upper == null || upper.level != level || upper.riseFromPreviousFloor == null) return null;
      height += upper.riseFromPreviousFloor;
    }
    if (!Double.isFinite(height)) return null;
    return from.level < to.level ? height : -height;
  }

  static MunhwagwanStairRoute find(MapLoadResult map, Poi from, Poi to) {
    Double height = heightChange(map.map, from.floorId, to.floorId);
    if (height == null) return null;
    Floor startFloor = floor(map.map, from.floorId);
    Floor endFloor = floor(map.map, to.floorId);
    AStarRouteEngine engine = new AStarRouteEngine(map.graph);
    MunhwagwanStairRoute shortest = null;
    // shortcut: stay on one staircase until the app can re-anchor mid-journey.
    for (String stair : new String[] {"A", "B"}) {
      if (!hasStairChain(map.graph, startFloor.level, endFloor.level, stair)) continue;
      String stairName = "계단 " + stair;
      String entry = stairNode(startFloor.level, stair);
      String exit = stairNode(endFloor.level, stair);
      RouteResult approach = engine.findRoute(from,
          new Poi(entry, stairName, PoiCategory.STAIR, entry, from.floorId), RouteOptions.DEFAULT);
      RouteResult destination = engine.findRoute(
          new Poi(exit, stairName, PoiCategory.STAIR, exit, to.floorId), to, RouteOptions.DEFAULT);
      if (!approach.isSuccess() || !destination.isSuccess()
          || approach.route.segments.isEmpty() || destination.route.segments.isEmpty()) continue;
      EntryDirection direction = EntryDirection.resolve(map.map, destination.route.nodes.get(0));
      if (direction == null) continue;
      if (shortest == null || approach.route.totalDistance + destination.route.totalDistance
          < shortest.approach.totalDistance + shortest.destination.totalDistance) {
        shortest = new MunhwagwanStairRoute(approach.route, destination.route, direction,
            stairName, endFloor.name, height);
      }
    }
    return shortest;
  }

  private static boolean hasStairChain(IndoorNavGraph graph, int from, int to, String stair) {
    int step = Integer.compare(to, from);
    for (int level = from; level != to; level += step) {
      String next = stairNode(level + step, stair);
      boolean connected = false;
      for (GraphEdge edge : graph.adjacency.getOrDefault(stairNode(level, stair),
          Collections.emptyList())) {
        if (edge.edge.type == EdgeType.STAIR && edge.toNode.id.equals(next)) {
          connected = true;
          break;
        }
      }
      if (!connected) return false;
    }
    return true;
  }

  private static String stairNode(int level, String stair) {
    return "F" + level + "_STAIR_" + stair;
  }

  private static Floor floor(IndoorMap map, String id) {
    for (Floor floor : map.floors) {
      if (floor.id.equals(id)) return floor;
    }
    return null;
  }
}
