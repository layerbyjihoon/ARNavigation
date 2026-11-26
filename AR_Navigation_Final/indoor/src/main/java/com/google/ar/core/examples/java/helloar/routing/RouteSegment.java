package com.google.ar.core.examples.java.helloar.routing;

import com.google.ar.core.examples.java.helloar.map.*;
import java.util.List;
public final class RouteSegment {
  public final MapNode fromNode, toNode;
  public final MapEdge edge;
  public final List<MapPosition> geometry;
  public final double distance;
  public final String floorId, toFloorId;
  RouteSegment(GraphEdge traversed) {
    fromNode = traversed.fromNode; toNode = traversed.toNode; edge = traversed.edge;
    geometry = traversed.geometry; distance = traversed.distance;
    floorId = fromNode.floorId; toFloorId = toNode.floorId;
  }
  public boolean isFloorTransition() { return !floorId.equals(toFloorId); }
}
