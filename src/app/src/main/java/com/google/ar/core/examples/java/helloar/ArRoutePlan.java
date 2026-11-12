package com.google.ar.core.examples.java.helloar;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import com.google.gson.Gson;
import com.google.ar.core.examples.java.helloar.localization.SemanticLandmarkMap;
/** Immutable handoff from the map screen to the GL thread. First point is the manual origin. */
final class ArRoutePlan {
  final String name;
  final String referenceId;
  final Double entryYawDegrees;
  final String entryDirectionLabel;
  final String startLocation;
  final List<Waypoint> waypoints;
  final SemanticLandmarkMap semanticMap;
  private ArRoutePlan(String name, List<Waypoint> waypoints, String referenceId,
      Double entryYawDegrees, String entryDirectionLabel, String startLocation,SemanticLandmarkMap semanticMap) {
    this.semanticMap=semanticMap;
    this.entryYawDegrees = entryYawDegrees;
    this.entryDirectionLabel = entryDirectionLabel;
    this.startLocation = startLocation;
    this.referenceId = referenceId == null ? "legacy" : referenceId;
    this.name = name; this.waypoints = Collections.unmodifiableList(waypoints);
  }
  static ArRoutePlan decode(String name, float[] points) {
    return decode(name, points, "legacy");
  }
  static ArRoutePlan decode(String name, float[] points, String referenceId) {
    return decode(name, points, referenceId, null, null, null);
  }
  static ArRoutePlan decode(String name, float[] points, String referenceId,
      Double entryYawDegrees, String entryDirectionLabel, String startLocation) {
    return decode(name,points,referenceId,entryYawDegrees,entryDirectionLabel,startLocation,null);
  }
  static ArRoutePlan decode(String name,float[] points,String referenceId,Double entryYawDegrees,
      String entryDirectionLabel,String startLocation,String semanticJson) {
    SemanticLandmarkMap semanticMap=null;
    if(semanticJson!=null) {
      if(semanticJson.length()>100_000)throw new IllegalArgumentException("Semantic map too large");
      semanticMap=new Gson().fromJson(semanticJson,SemanticLandmarkMap.class);
      if(semanticMap==null||semanticMap.floorId==null||semanticMap.landmarks==null||semanticMap.landmarks.size()>256)
        throw new IllegalArgumentException("Invalid semantic map");
      for(SemanticLandmarkMap.Landmark p:semanticMap.landmarks)
        if(p==null||p.id==null||p.type==null||!Double.isFinite(p.x)||!Double.isFinite(p.z)||Math.abs(p.x)>100||Math.abs(p.z)>100)
          throw new IllegalArgumentException("Invalid semantic landmark");
    }
    if (entryYawDegrees != null && (!Double.isFinite(entryYawDegrees) || Math.abs(entryYawDegrees) > 180))
      throw new IllegalArgumentException("Invalid entry direction");
    if (points == null || points.length < 6 || points.length % 3 != 0 || points.length > 3075)
      throw new IllegalArgumentException("Invalid AR route points");
    for (float point : points)
      if (!Float.isFinite(point) || Math.abs(point) > 100f)
        throw new IllegalArgumentException("AR preview supports points within 100m of the origin");
    if (Math.abs(points[0]) > 0.001f || Math.abs(points[1] + 0.5f) > 0.001f
        || Math.abs(points[2]) > 0.001f) throw new IllegalArgumentException("Invalid AR route origin");
    List<Waypoint> waypoints = new ArrayList<>();
    for (int i = 3; i < points.length; i += 3)
      waypoints.add(new Waypoint(points[i], points[i+1], points[i+2]));
    if(semanticMap!=null){
      List<SemanticLandmarkMap.RoutePoint> path=new ArrayList<>();
      for(int i=0;i<points.length;i+=3)path.add(new SemanticLandmarkMap.RoutePoint(points[i],points[i+2]));
      semanticMap=new SemanticLandmarkMap(semanticMap.floorId,semanticMap.landmarks,
          semanticMap.corridors==null?Collections.emptyList():semanticMap.corridors,path);
    }
    return new ArRoutePlan(name == null ? "지도 경로" : name, waypoints, referenceId,
        entryYawDegrees, entryDirectionLabel == null ? "문·진입점 → 복도" : entryDirectionLabel,
        startLocation,semanticMap);
  }
}
