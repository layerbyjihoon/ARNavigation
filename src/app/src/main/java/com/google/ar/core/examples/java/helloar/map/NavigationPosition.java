package com.google.ar.core.examples.java.helloar.map;

/** A position need not coincide with a graph vertex. Snapping/localization are future modules. */
public final class NavigationPosition {
  public final MapPosition position;
  public final String floorId, nearestNodeId, nearestEdgeId;
  public NavigationPosition(MapPosition position, String floorId, String nearestNodeId,
      String nearestEdgeId) {
    this.position = position; this.floorId = floorId;
    this.nearestNodeId = nearestNodeId; this.nearestEdgeId = nearestEdgeId;
  }
}
