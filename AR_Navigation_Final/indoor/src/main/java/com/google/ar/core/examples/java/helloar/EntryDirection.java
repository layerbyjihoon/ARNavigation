package com.google.ar.core.examples.java.helloar;

import com.google.ar.core.examples.java.helloar.coordinates.LocalMapCoordinateTransformer;
import com.google.ar.core.examples.java.helloar.map.*;
import java.util.*;

/** Outgoing, same-floor graph path from an entry to a corridor. No floor/height guesses. */
final class EntryDirection {
  final String nextNodeId, corridorNodeId;
  final double canonicalYawDegrees;

  private EntryDirection(String next, String corridor, double yaw) {
    nextNodeId = next; corridorNodeId = corridor; canonicalYawDegrees = yaw;
  }

  static EntryDirection resolve(IndoorMap map, MapNode start) {
    if (start.type != NodeType.DOOR && start.type != NodeType.ENTRANCE && start.type != NodeType.STAIR)
      return null;
    Map<String, MapNode> nodes = new HashMap<>();
    Map<String, List<MapEdge>> outgoing = new HashMap<>();
    for (MapNode node : map.nodes) nodes.put(node.id, node);
    for (MapEdge edge : map.edges) {
      outgoing.computeIfAbsent(edge.from, k -> new ArrayList<>()).add(edge);
      if (edge.bidirectional) outgoing.computeIfAbsent(edge.to, k -> new ArrayList<>()).add(edge);
    }
    Map<String, Double> distance = new HashMap<>();
    Map<String, String> parent = new HashMap<>();
    Map<String, MapEdge> incoming = new HashMap<>();
    PriorityQueue<State> pending = new PriorityQueue<>(Comparator.comparingDouble((State s) -> s.distance)
        .thenComparing(s -> s.id));
    distance.put(start.id, 0.0); pending.add(new State(start.id, 0));
    EntryDirection selected = null;
    double nearest = Double.POSITIVE_INFINITY;
    while (!pending.isEmpty()) {
      State current = pending.remove();
      if (current.distance > distance.get(current.id) + 1e-6) continue;
      if (current.distance > nearest + 1e-6) break;
      MapNode node = nodes.get(current.id);
      if (!node.id.equals(start.id) && isCorridor(node)) {
        EntryDirection candidate = alongPath(map, start, node, nodes, parent, incoming);
        if (candidate == null) continue;
        if (selected != null && Math.abs(MapAlignment.wrap(Math.toRadians(
            selected.canonicalYawDegrees - candidate.canonicalYawDegrees))) > 0.01) return null;
        selected = candidate; nearest = current.distance;
        continue;
      }
      for (MapEdge edge : outgoing.getOrDefault(current.id, Collections.emptyList())) {
        String targetId = edge.from.equals(current.id) ? edge.to : edge.from;
        MapNode target = nodes.get(targetId);
        if (target == null || !target.floorId.equals(start.floorId)) continue;
        double weight = edge.cost == null || edge.cost.distance == null
            ? geometryLength(edge, nodes) : edge.cost.distance;
        if (!Double.isFinite(weight) || weight <= 0) continue;
        double cost = current.distance + weight;
        if (cost < distance.getOrDefault(targetId, Double.POSITIVE_INFINITY) - 1e-6) {
          distance.put(targetId, cost); parent.put(targetId, current.id); incoming.put(targetId, edge);
          pending.add(new State(targetId, cost));
        }
      }
    }
    return selected;
  }

  private static boolean isCorridor(MapNode node) {
    Object type = node.properties.get("type");
    return "corridor".equals(type) || (type == null
        && (node.type == NodeType.WAYPOINT || node.type == NodeType.JUNCTION));
  }

  private static double geometryLength(MapEdge edge, Map<String, MapNode> nodes) {
    MapPosition previous = nodes.get(edge.from).position;
    double length = 0;
    for (MapPosition point : edge.geometry) { length += previous.distanceTo(point); previous = point; }
    return length + previous.distanceTo(nodes.get(edge.to).position);
  }

  private static EntryDirection alongPath(IndoorMap map, MapNode start, MapNode corridor,
      Map<String, MapNode> nodes, Map<String, String> parent, Map<String, MapEdge> incoming) {
    List<String> path = new ArrayList<>();
    for (String id = corridor.id; !id.equals(start.id); id = parent.get(id)) {
      if (id == null) return null;
      path.add(id);
    }
    Collections.reverse(path);
    LocalMapCoordinateTransformer transform = new LocalMapCoordinateTransformer(map.coordinateSystem,
        start.position, new MapPosition(0, 0, 0), 0);
    String previous = start.id;
    for (String id : path) {
      MapEdge edge = incoming.get(id);
      List<MapPosition> points = new ArrayList<>(edge.geometry);
      if (!edge.from.equals(previous)) Collections.reverse(points);
      points.add(nodes.get(id).position);
      for (MapPosition position : points) {
        MapPosition p = transform.mapToAr(position);
        if (Math.hypot(p.x, p.z) > 0.001)
          return new EntryDirection(id, corridor.id, Math.toDegrees(Math.atan2(-p.x, -p.z)));
      }
      previous = id;
    }
    return null;
  }

  String description() {
    return "문·진입점 → 복도 방향 (다음 " + nextNodeId
        + (nextNodeId.equals(corridorNodeId) ? "" : ", 복도 " + corridorNodeId) + ")";
  }
  private static final class State {
    final String id; final double distance;
    State(String id, double distance) { this.id = id; this.distance = distance; }
  }
}
