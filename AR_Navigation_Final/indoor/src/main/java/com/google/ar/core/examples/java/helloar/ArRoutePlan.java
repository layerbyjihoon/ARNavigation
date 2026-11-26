package com.google.ar.core.examples.java.helloar;

import com.google.ar.core.examples.java.helloar.localization.SemanticLandmarkMap;
import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable handoff from the map screen to the GL thread. First point is the manual origin. */
final class ArRoutePlan {
  final String name;
  final String referenceId;
  final Double entryYawDegrees;
  final String entryDirectionLabel;
  final String startLocation;
  final List<Waypoint> waypoints;
  final List<Stage> stages;
  final SemanticLandmarkMap semanticMap;

  static final class Stage {
    final String floorId, nextFloorId, transition;
    final List<Waypoint> waypoints;

    private Stage(String floorId, String nextFloorId, String transition, List<Waypoint> waypoints) {
      this.floorId = floorId;
      this.nextFloorId = nextFloorId;
      this.transition = transition;
      this.waypoints = waypoints;
    }
  }

  private ArRoutePlan(String name, List<Stage> stages, String referenceId,
      Double entryYawDegrees, String entryDirectionLabel, String startLocation,
      SemanticLandmarkMap semanticMap) {
    if (entryYawDegrees != null
        && (!Double.isFinite(entryYawDegrees) || Math.abs(entryYawDegrees) > 180))
      throw new IllegalArgumentException("Invalid entry direction");
    this.name = name == null ? "지도 경로" : name;
    this.referenceId = referenceId == null ? "legacy" : referenceId;
    this.entryYawDegrees = entryYawDegrees;
    this.entryDirectionLabel = entryDirectionLabel == null ? "문·진입점 → 복도" : entryDirectionLabel;
    this.startLocation = startLocation;
    this.stages = Collections.unmodifiableList(new ArrayList<>(stages));
    this.waypoints = this.stages.get(0).waypoints;
    this.semanticMap = semanticMap;
  }

  static ArRoutePlan decode(String name, float[] points) {
    return decode(name, points, "legacy");
  }

  static ArRoutePlan decode(String name, float[] points, String referenceId) {
    return decode(name, points, referenceId, null, null, null);
  }

  static ArRoutePlan decode(String name, float[] points, String referenceId,
      Double entryYawDegrees, String entryDirectionLabel, String startLocation) {
    return decode(name, points, referenceId, entryYawDegrees, entryDirectionLabel,
        startLocation, null);
  }

  static ArRoutePlan decode(String name, float[] points, String referenceId,
      Double entryYawDegrees, String entryDirectionLabel, String startLocation,
      String semanticJson) {
    List<Stage> stages = new ArrayList<>();
    stages.add(new Stage("", null, null, decodePoints(points)));
    return new ArRoutePlan(name, stages, referenceId, entryYawDegrees, entryDirectionLabel,
        startLocation, parseSemanticMap(semanticJson, points));
  }

  static ArRoutePlan decodeStages(String name, float[] allPoints, int[] lengths,
      String[] floors, String[] nextFloors, String[] transitions, String referenceId,
      Double entryYawDegrees, String entryDirectionLabel, String startLocation) {
    return decodeStages(name, allPoints, lengths, floors, nextFloors, transitions, referenceId,
        entryYawDegrees, entryDirectionLabel, startLocation, null);
  }

  static ArRoutePlan decodeStages(String name, float[] allPoints, int[] lengths,
      String[] floors, String[] nextFloors, String[] transitions, String referenceId,
      Double entryYawDegrees, String entryDirectionLabel, String startLocation,
      String semanticJson) {
    if (allPoints == null || lengths == null || floors == null || nextFloors == null
        || transitions == null || lengths.length == 0 || lengths.length != floors.length
        || lengths.length != nextFloors.length || lengths.length != transitions.length
        || allPoints.length > 3075)
      throw new IllegalArgumentException("Invalid floor route payload");
    int offset = 0;
    float[] firstStagePoints = null;
    List<Stage> stages = new ArrayList<>();
    for (int i = 0; i < lengths.length; i++) {
      int length = lengths[i];
      if (length < 6 || length % 3 != 0 || length > allPoints.length - offset
          || floors[i] == null || floors[i].isEmpty()
          || (i < lengths.length - 1 && (nextFloors[i] == null || transitions[i] == null))
          || (i < lengths.length - 1 && !nextFloors[i].equals(floors[i + 1]))
          || (i == lengths.length - 1 && (nextFloors[i] != null || transitions[i] != null)))
        throw new IllegalArgumentException("Invalid floor route stage");
      float[] points = new float[length];
      System.arraycopy(allPoints, offset, points, 0, length);
      if (i == 0) firstStagePoints = points;
      stages.add(new Stage(floors[i], nextFloors[i], transitions[i], decodePoints(points)));
      offset += length;
    }
    if (offset != allPoints.length) throw new IllegalArgumentException("Unused floor route points");
    return new ArRoutePlan(name, stages, referenceId, entryYawDegrees, entryDirectionLabel,
        startLocation, parseSemanticMap(semanticJson, firstStagePoints));
  }

  private static List<Waypoint> decodePoints(float[] points) {
    if (points == null || points.length < 6 || points.length % 3 != 0 || points.length > 3075)
      throw new IllegalArgumentException("Invalid AR route points");
    for (float point : points)
      if (!Float.isFinite(point) || Math.abs(point) > 100f)
        throw new IllegalArgumentException("AR preview supports points within 100m of the origin");
    if (Math.abs(points[0]) > 0.001f || Math.abs(points[1] + 0.5f) > 0.001f
        || Math.abs(points[2]) > 0.001f) throw new IllegalArgumentException("Invalid AR route origin");
    List<Waypoint> waypoints = new ArrayList<>();
    for (int i = 3; i < points.length; i += 3)
      waypoints.add(new Waypoint(points[i], points[i + 1], points[i + 2]));
    return Collections.unmodifiableList(waypoints);
  }

  private static SemanticLandmarkMap parseSemanticMap(String json, float[] points) {
    if (json == null || "null".equals(json)) return null;
    if (json.length() > 100_000) throw new IllegalArgumentException("Semantic map too large");
    SemanticLandmarkMap map = new Gson().fromJson(json, SemanticLandmarkMap.class);
    if (map == null || map.floorId == null || map.landmarks == null
        || map.landmarks.size() > 256)
      throw new IllegalArgumentException("Invalid semantic map");
    for (SemanticLandmarkMap.Landmark landmark : map.landmarks)
      if (landmark == null || landmark.id == null || landmark.type == null
          || !Double.isFinite(landmark.x) || !Double.isFinite(landmark.z)
          || Math.abs(landmark.x) > 100 || Math.abs(landmark.z) > 100)
        throw new IllegalArgumentException("Invalid semantic landmark");
    List<SemanticLandmarkMap.RoutePoint> path = new ArrayList<>();
    for (int i = 0; i < points.length; i += 3)
      path.add(new SemanticLandmarkMap.RoutePoint(points[i], points[i + 2]));
    return new SemanticLandmarkMap(map.floorId, map.landmarks,
        map.corridors == null ? Collections.emptyList() : map.corridors, path);
  }
}
