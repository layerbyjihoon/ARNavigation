package com.google.ar.core.examples.java.helloar.routing;

import com.google.ar.core.examples.java.helloar.map.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
public final class GraphEdge {
  public final MapEdge edge;
  public final MapNode fromNode, toNode;
  public final List<MapPosition> geometry;
  public final double distance;
  GraphEdge(MapEdge edge, MapNode from, MapNode to, List<MapPosition> geometry, double distance) {
    this.edge = edge; this.fromNode = from; this.toNode = to; this.distance = distance;
    this.geometry = Collections.unmodifiableList(new ArrayList<>(geometry));
  }
}
