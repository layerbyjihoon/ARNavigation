package com.google.ar.core.examples.java.helloar.routing;

import com.google.ar.core.examples.java.helloar.map.*;
import java.util.*;

/** Pure Java distance-based A*. No JSON traversal and no Android or ARCore API dependencies. */
public final class AStarRouteEngine {
  private final IndoorNavGraph graph;
  public AStarRouteEngine(IndoorNavGraph graph) { this.graph = Objects.requireNonNull(graph); }
  /** Compare every entrance pair so rooms with several doors do not route via an arbitrary door. */
  public RouteResult findRoute(Poi start, Poi destination, RouteOptions options) {
    if (start == null || destination == null)
      return new RouteResult(RouteResult.Status.INVALID_ENDPOINT, null, "출발지 또는 목적지가 없습니다.");
    RouteResult shortest = null;
    for (String from : start.nodeIds) for (String to : destination.nodeIds) {
      RouteResult candidate = findRoute(from, to, options);
      if (candidate.isSuccess() && (shortest == null
          || candidate.route.totalDistance < shortest.route.totalDistance)) shortest = candidate;
    }
    return shortest != null ? shortest : new RouteResult(RouteResult.Status.NO_ROUTE, null,
        options.wheelchairOnly ? "접근성이 확인된 경로가 없습니다." : "연결된 경로가 없습니다.");
  }
  public RouteResult findRoute(String startId, String destinationId, RouteOptions options) {
    MapNode start = graph.nodes.get(startId), target = graph.nodes.get(destinationId);
    if (start == null || target == null)
      return new RouteResult(RouteResult.Status.INVALID_ENDPOINT, null, "출발지 또는 목적지 노드가 없습니다.");
    Map<String, Double> best = new HashMap<>();
    Map<String, GraphEdge> previous = new HashMap<>();
    PriorityQueue<Entry> queue = new PriorityQueue<>(
        Comparator.comparingDouble((Entry e) -> e.estimate).thenComparing(e -> e.id));
    best.put(startId, 0.0);
    queue.add(new Entry(startId, 0, heuristic(start, target)));
    while (!queue.isEmpty()) {
      Entry current = queue.remove();
      if (current.distance > best.getOrDefault(current.id, Double.POSITIVE_INFINITY)) continue;
      if (current.id.equals(destinationId)) {
        LinkedList<RouteSegment> segments = new LinkedList<>();
        LinkedList<MapNode> nodes = new LinkedList<>();
        String cursor = destinationId; nodes.addFirst(target);
        while (!cursor.equals(startId)) {
          GraphEdge edge = previous.get(cursor);
          segments.addFirst(new RouteSegment(edge)); nodes.addFirst(edge.fromNode);
          cursor = edge.fromNode.id;
        }
        return new RouteResult(RouteResult.Status.SUCCESS, new NavigationRoute(nodes, segments), "");
      }
      for (GraphEdge edge : graph.adjacency.get(current.id)) {
        // Same-floor journeys must not take a shortcut through another floor.
        if (start.floorId.equals(target.floorId) && !edge.toNode.floorId.equals(start.floorId)) continue;
        if (edge.edge.type == EdgeType.UNKNOWN) continue;
        if (options.wheelchairOnly && (edge.edge.type == EdgeType.STAIR
            || edge.edge.type == EdgeType.ESCALATOR
            || !Boolean.TRUE.equals(edge.edge.accessibility.wheelchair))) continue;
        double next = current.distance + edge.distance;
        if (next < best.getOrDefault(edge.toNode.id, Double.POSITIVE_INFINITY)) {
          best.put(edge.toNode.id, next); previous.put(edge.toNode.id, edge);
          queue.add(new Entry(edge.toNode.id, next, next + heuristic(edge.toNode, target)));
        }
      }
    }
    return new RouteResult(RouteResult.Status.NO_ROUTE, null,
        options.wheelchairOnly ? "접근성이 확인된 경로가 없습니다." : "연결된 경로가 없습니다.");
  }
  private double heuristic(MapNode from, MapNode to) {
    if (graph.heuristicScale == 0) return 0;
    return graph.heuristicScale * from.position.distanceTo(to.position);
  }
  private static final class Entry {
    final String id; final double distance, estimate;
    Entry(String id, double distance, double estimate) {
      this.id = id; this.distance = distance; this.estimate = estimate;
    }
  }
}
