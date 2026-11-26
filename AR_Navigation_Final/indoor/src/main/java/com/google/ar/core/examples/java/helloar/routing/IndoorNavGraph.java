package com.google.ar.core.examples.java.helloar.routing;

import com.google.ar.core.examples.java.helloar.map.*;
import java.util.*;

/** A snapshot of a validated map, with directed adjacency built once at load time. */
public final class IndoorNavGraph {
  public final IndoorMap map;
  public final Map<String, MapNode> nodes;
  public final Map<String, List<GraphEdge>> adjacency;
  /** Global distance lower bound keeps A* admissible even with surveyed cost overrides. */
  final double heuristicScale;
  public IndoorNavGraph(IndoorMap map) {
    if (new IndoorMapValidator().validate(map).hasErrors())
      throw new IllegalArgumentException("Cannot build an invalid IndoorNavGraph");
    this.map = map;
    Map<String, MapNode> index = new LinkedHashMap<>();
    Map<String, List<GraphEdge>> outgoing = new LinkedHashMap<>();
    for (MapNode node : map.nodes) { index.put(node.id, node); outgoing.put(node.id, new ArrayList<>()); }
    double lowerBound = 1;
    for (MapEdge edge : map.edges) {
      MapNode from = index.get(edge.from), to = index.get(edge.to);
      List<MapPosition> geometry = edge.geometry.isEmpty()
          ? Arrays.asList(from.position, to.position) : edge.geometry;
      double distance = edge.cost.distance == null ? polylineLength(geometry) : edge.cost.distance;
      outgoing.get(edge.from).add(new GraphEdge(edge, from, to, geometry, distance));
      if (edge.bidirectional) {
        List<MapPosition> reversed = new ArrayList<>(geometry);
        Collections.reverse(reversed);
        outgoing.get(edge.to).add(new GraphEdge(edge, to, from, reversed, distance));
      }
      double straight = from.position.distanceTo(to.position);
      if (straight > 0) lowerBound = Math.min(lowerBound, distance / straight);
    }
    Map<String, List<GraphEdge>> frozen = new LinkedHashMap<>();
    outgoing.forEach((id, edges) -> frozen.put(id, Collections.unmodifiableList(edges)));
    nodes = Collections.unmodifiableMap(index); adjacency = Collections.unmodifiableMap(frozen);
    heuristicScale = lowerBound;
  }
  public static double polylineLength(List<MapPosition> points) {
    double total = 0;
    for (int i = 1; i < points.size(); i++) total += points.get(i-1).distanceTo(points.get(i));
    return total;
  }
}
