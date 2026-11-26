package com.google.ar.core.examples.java.helloar.map;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Semantic checks beyond the external JSON Schema, with errors separated from survey warnings. */
public final class IndoorMapValidator {
  private final List<MapValidationIssue> issues = new ArrayList<>();
  public MapValidationResult validate(IndoorMap map) {
    issues.clear();
    if (map == null) { error("$", "Map is null"); return result(); }
    if (!"IndoorNavGraph".equals(map.schema)) error("schema", "Expected IndoorNavGraph");
    if (map.version == null || !map.version.matches("1\\.[0-9]+"))
      error("version", "Unsupported version " + map.version + "; supported major version is 1");
    else if (!"1.0".equals(map.version)) warning("version", "New minor version: unknown optional fields ignored");
    if (map.metadata == null || blank(map.metadata.id) || blank(map.metadata.name))
      error("metadata", "id and name are required");
    validateCoordinates(map.coordinateSystem);
    Set<String> floors = new HashSet<>();
    if (map.floors.isEmpty()) error("floors", "At least one floor is required");
    for (Floor floor : map.floors) {
      unique(floors, floor.id, "floors");
      finite(floor.elevation, "floor " + floor.id + ".elevation");
    }
    Map<String, MapNode> nodes = new HashMap<>();
    if (map.nodes.isEmpty()) error("nodes", "At least one node is required");
    Set<String> nodeIds = new HashSet<>();
    for (MapNode node : map.nodes) {
      unique(nodeIds, node.id, "nodes");
      nodes.put(node.id, node);
      floor(floors, node.floorId, "Node " + node.id);
      point(node.position, "Node " + node.id + ".position");
      if (node.type == NodeType.UNKNOWN) warning("Node " + node.id, "Unknown node type");
    }
    Set<String> edgeIds = new HashSet<>();
    Set<String> connected = new HashSet<>();
    for (MapEdge edge : map.edges) {
      String path = "Edge " + edge.id;
      unique(edgeIds, edge.id, "edges");
      MapNode from = nodes.get(edge.from), to = nodes.get(edge.to);
      if (from == null) error(path, "References missing from node " + edge.from);
      if (to == null) error(path, "References missing to node " + edge.to);
      if (edge.from != null && edge.from.equals(edge.to)) error(path, "Self-loop is not a navigation edge");
      if (from != null && to != null) {
        connected.add(edge.from); connected.add(edge.to);
        if (from.position != null && to.position != null
            && !Double.isFinite(from.position.distanceTo(to.position)))
          error(path, "Derived edge distance must be finite");
        if (!from.floorId.equals(to.floorId) && !vertical(edge.type))
          error(path, "Cross-floor edge must be STAIR, ELEVATOR, ESCALATOR or RAMP");
      }
      if (!edge.geometry.isEmpty()) {
        if (edge.geometry.size() < 2) error(path, "Polyline needs at least two points");
        for (MapPosition p : edge.geometry) point(p, path + ".geometry");
        double length = 0;
        boolean finitePoints = true;
        for (MapPosition p : edge.geometry) finitePoints &= p != null && p.isFinite();
        if (finitePoints) {
          for (int i = 1; i < edge.geometry.size(); i++)
            length += edge.geometry.get(i-1).distanceTo(edge.geometry.get(i));
          if (!Double.isFinite(length)) error(path, "Polyline length must be finite");
        }
        if (from != null && from.position != null && !matches(from.position, edge.geometry.get(0)))
          error(path, "Geometry must start at the from node");
        if (to != null && to.position != null && !matches(to.position, edge.geometry.get(edge.geometry.size()-1)))
          error(path, "Geometry must end at the to node");
      }
      if (edge.cost == null) error(path + ".cost", "Cost container is required");
      else { nonnegative(edge.cost.distance, path + ".cost.distance"); nonnegative(edge.cost.time, path + ".cost.time"); }
      if (edge.type == EdgeType.UNKNOWN) warning(path, "Unknown edge type; excluded from routing");
      if (edge.accessibility == null || edge.accessibility.wheelchair == null)
        warning(path + ".accessibility", "Wheelchair accessibility has not been surveyed");
    }
    for (MapNode node : map.nodes)
      if (!connected.contains(node.id)) warning("Node " + node.id, "Node is isolated");
    Set<String> poiIds = new HashSet<>();
    for (Poi poi : map.pois) {
      unique(poiIds, poi.id, "pois");
      floor(floors, poi.floorId, "POI " + poi.id);
      if (poi.nodeIds.isEmpty() || !poi.nodeIds.contains(poi.nodeId))
        error("POI " + poi.id, "nodeIds must include the primary nodeId");
      Set<String> entrances = new HashSet<>();
      for (String nodeId : poi.nodeIds) {
        if (!entrances.add(nodeId)) error("POI " + poi.id, "Duplicate entrance " + nodeId);
        MapNode node = nodes.get(nodeId);
        if (node == null) error("POI " + poi.id, "References missing node " + nodeId);
        else if (!node.floorId.equals(poi.floorId)) error("POI " + poi.id, "POI floor does not match its node");
      }
      if (poi.category == PoiCategory.UNKNOWN) warning("POI " + poi.id, "Unknown category");
    }
    Set<String> anchorIds = new HashSet<>();
    for (MapAnchor anchor : map.anchors) {
      unique(anchorIds, anchor.id, "anchors");
      floor(floors, anchor.floorId, "Anchor " + anchor.id);
      point(anchor.position, "Anchor " + anchor.id + ".position");
      finite(anchor.heading, "Anchor " + anchor.id + ".heading");
      if ((anchor.type == AnchorType.QR || anchor.type == AnchorType.APRILTAG
          || anchor.type == AnchorType.IMAGE) && blank(anchor.value))
        error("Anchor " + anchor.id, "Marker value is required");
      if (anchor.type == AnchorType.UNKNOWN) warning("Anchor " + anchor.id, "Unknown anchor type");
    }
    return result();
  }
  private void validateCoordinates(IndoorCoordinateSystem c) {
    if (c == null) { error("coordinateSystem", "Coordinate system is required"); return; }
    if (!"LOCAL_METERS".equals(c.type) || !"meter".equals(c.unit))
      error("coordinateSystem", "Only LOCAL_METERS / meter is supported");
    Set<Integer> dimensions = new HashSet<>();
    for (String axis : new String[] {c.xAxis, c.yAxis, c.zAxis}) {
      int dimension = axisDimension(axis);
      if (dimension < 0 || !dimensions.add(dimension))
        error("coordinateSystem.axis", "Axes must be orthogonal (EAST/WEST or RIGHT/LEFT, UP/DOWN, NORTH/SOUTH or FORWARD/BACKWARD)");
    }
    if (c.georeference != null) {
      IndoorCoordinateSystem.Georeference g = c.georeference;
      if (!Double.isFinite(g.latitude) || Math.abs(g.latitude) > 90)
        error("georeference.latitude", "Invalid WGS84 latitude");
      if (!Double.isFinite(g.longitude) || Math.abs(g.longitude) > 180)
        error("georeference.longitude", "Invalid WGS84 longitude");
      finite(g.altitude, "georeference.altitude"); finite(g.heading, "georeference.heading");
    }
  }
  static int axisDimension(String axis) {
    if ("EAST".equals(axis) || "WEST".equals(axis) || "RIGHT".equals(axis) || "LEFT".equals(axis)) return 0;
    if ("UP".equals(axis) || "DOWN".equals(axis)) return 1;
    if ("NORTH".equals(axis) || "SOUTH".equals(axis) || "FORWARD".equals(axis) || "BACKWARD".equals(axis)) return 2;
    return -1;
  }
  private static boolean vertical(EdgeType t) {
    return t == EdgeType.STAIR || t == EdgeType.ELEVATOR || t == EdgeType.ESCALATOR || t == EdgeType.RAMP;
  }
  private boolean matches(MapPosition a, MapPosition b) {
    return b != null && a.isFinite() && b.isFinite() && a.distanceTo(b) <= 0.001;
  }
  private void point(MapPosition p, String path) {
    if (p == null || !p.isFinite()) error(path, "Coordinates must be finite");
  }
  private void floor(Set<String> ids, String id, String path) {
    if (!ids.contains(id)) error(path, "References missing floor " + id);
  }
  private void unique(Set<String> ids, String id, String path) {
    if (blank(id)) error(path, "Empty ID");
    else if (!ids.add(id)) error(path, "Duplicate ID " + id);
  }
  private void nonnegative(Double value, String path) {
    finite(value, path);
    if (value != null && value < 0) error(path, "Cost must not be negative");
  }
  private void finite(Double value, String path) {
    if (value != null && !Double.isFinite(value)) error(path, "Value must be finite");
  }
  private static boolean blank(String value) { return value == null || value.trim().isEmpty(); }
  private void error(String path, String text) {
    issues.add(new MapValidationIssue(MapValidationIssue.Severity.ERROR, path, text));
  }
  private void warning(String path, String text) {
    issues.add(new MapValidationIssue(MapValidationIssue.Severity.WARNING, path, text));
  }
  private MapValidationResult result() { return new MapValidationResult(issues); }
}
