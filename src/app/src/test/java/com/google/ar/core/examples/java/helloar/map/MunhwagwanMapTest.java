package com.google.ar.core.examples.java.helloar.map;

import static org.junit.Assert.*;
import com.google.ar.core.examples.java.helloar.coordinates.*;
import com.google.ar.core.examples.java.helloar.routing.*;
import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.Before;
import org.junit.Test;

public final class MunhwagwanMapTest {
  private JsonObject json;
  private MapLoadResult loaded;
  @Before public void load() throws Exception {
    try (InputStream stream = getClass().getClassLoader().getResourceAsStream("munhwagwan.indoor.json")) {
      assertNotNull(stream);
      json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
    }
    loaded = parse(json);
    assertTrue(loaded.validation.issues.toString(), loaded.isSuccess());
  }
  private MapLoadResult parse(JsonObject data) {
    return new IndoorMapRepository().loadFromInputStream(
        new ByteArrayInputStream(data.toString().getBytes(StandardCharsets.UTF_8)));
  }
  private Poi poi(String id) {
    return loaded.map.pois.stream().filter(p -> id.equals(p.id)).findFirst().get();
  }
  private NavigationRoute route(String from, String to) {
    RouteResult result = new AStarRouteEngine(loaded.graph).findRoute(poi(from),poi(to),RouteOptions.DEFAULT);
    assertTrue(result.message, result.isSuccess()); return result.route;
  }
  @Test public void preservesDatasetAndUsesOnlyEligibleSameFloorEdges() {
    assertEquals(5,loaded.map.floors.size()); assertEquals(206,loaded.map.nodes.size());
    assertEquals(201,loaded.map.edges.size()); assertEquals(65,loaded.map.pois.size());
    assertEquals(201,loaded.validation.warningCount()); // accessibility remains unknown, not true/false
    assertEquals(8,json.getAsJsonObject("sourceData").getAsJsonArray("excludedEdges").size());
    assertEquals(64,json.getAsJsonObject("sourceData").getAsJsonArray("rooms").size());
    for (Floor f:loaded.map.floors) assertNull(f.elevation);
    for (MapNode n:loaded.map.nodes) {
      assertEquals(0,n.position.y,0);
      assertEquals(false,n.properties.get("field_verified"));
      assertNull(n.properties.get("z_m"));
    }
    for (MapEdge e:loaded.map.edges) {
      assertEquals(loaded.graph.nodes.get(e.from).floorId,loaded.graph.nodes.get(e.to).floorId);
      assertTrue(e.cost.distance>0); assertNull(e.accessibility.wheelchair);
    }
  }
  @Test public void choosesBestEntranceOnBothEndsAndKeepsRoomIdsAsStrings() {
    NavigationRoute selected=route("105","106");
    assertEquals(7.702,selected.totalDistance,1e-9);
    assertEquals("F1_R105_D2",selected.nodes.get(0).id);
    assertEquals("F1_R106_D1",selected.nodes.get(selected.nodes.size()-1).id);
    assertEquals(2,poi("105").nodeIds.size());
    assertEquals("406-1",poi("406-1").id);
    try { poi("105").nodeIds.clear(); fail(); } catch (UnsupportedOperationException expected) {}
  }
  @Test public void internalRoomAccessRemainsAnActualPathThrough406() {
    NavigationRoute selected=route("401","406-1");
    assertEquals(31.287,selected.totalDistance,1e-9);
    assertTrue(selected.nodes.stream().anyMatch(n->n.id.equals("F4_R406_D1")));
    assertTrue(selected.nodes.stream().anyMatch(n->n.id.equals("F4_INT406_1")));
    assertTrue(selected.nodes.stream().anyMatch(n->n.id.equals("F4_INT406_2")));
  }
  @Test public void missingStairsAndUnknownAccessibilityNeverBecomeRoutable() {
    AStarRouteEngine engine = new AStarRouteEngine(loaded.graph);
    assertEquals(RouteResult.Status.NO_ROUTE,engine.findRoute(poi("101"),poi("201"),RouteOptions.DEFAULT).status);
    assertEquals(RouteResult.Status.NO_ROUTE,engine.findRoute(poi("101"),poi("102"),RouteOptions.WHEELCHAIR).status);
  }
  @Test public void drawingAxesMapToArWithoutFlippingPlanYOrInventingFloorHeight() {
    NavigationRoute selected=route("101","102");
    assertEquals(5.616,selected.totalDistance,1e-9);
    MapPosition origin=selected.nodes.get(0).position;
    assertEquals(8.399,origin.x,0); assertEquals(4.436,origin.z,0);
    LocalMapCoordinateTransformer transform=new LocalMapCoordinateTransformer(
        loaded.map.coordinateSystem,origin,new MapPosition(0,-0.5,0),0);
    List<MapPosition> ar=RouteCoordinateAdapter.toAr(selected,transform);
    MapPosition last=ar.get(ar.size()-1);
    assertEquals(-4.226,last.x,1e-9); assertEquals(-0.5,last.y,1e-9); assertEquals(0,last.z,1e-9);
    MapPosition forward=transform.mapToAr(new MapPosition(origin.x,0,origin.z+1));
    assertEquals(-1,forward.z,1e-9);
    MapPosition inverse=transform.arToMap(last);
    assertEquals(4.173,inverse.x,1e-9); assertEquals(4.436,inverse.z,1e-9);
  }
  @Test public void validatesEveryEntranceReferenceNotOnlyThePrimary() {
    JsonObject first=json.getAsJsonArray("pois").get(0).getAsJsonObject();
    for (String extra:new String[]{"missing","F2_R201_D1","F1_R101_D1"}) {
      JsonArray entrances=new JsonArray(); entrances.add("F1_R101_D1"); entrances.add(extra);
      first.add("nodeIds",entrances); assertFalse(parse(json).isSuccess());
    }
    first.add("nodeIds",new JsonArray()); assertFalse(parse(json).isSuccess());
    JsonArray missingPrimary=new JsonArray(); missingPrimary.add("F1_R102_D1");
    first.add("nodeIds",missingPrimary); assertFalse(parse(json).isSuccess());
    JsonArray wrongType=new JsonArray(); wrongType.add(101);
    first.add("nodeIds",wrongType); assertFalse(parse(json).isSuccess());
    first.remove("nodeIds"); assertTrue(parse(json).isSuccess());
  }
  @Test public void allRoomPairsMatchAnIndependentFloydWarshallOracle() {
    List<MapNode> nodes=loaded.map.nodes;
    Map<String,Integer> index=new HashMap<>();
    int n=nodes.size(); double[][] distance=new double[n][n];
    for (int i=0;i<n;i++) { index.put(nodes.get(i).id,i); Arrays.fill(distance[i],Double.POSITIVE_INFINITY); distance[i][i]=0; }
    for (MapEdge e:loaded.map.edges) {
      int a=index.get(e.from), b=index.get(e.to);
      distance[a][b]=Math.min(distance[a][b],e.cost.distance);
      if (e.bidirectional) distance[b][a]=Math.min(distance[b][a],e.cost.distance);
    }
    for (int k=0;k<n;k++) for(int i=0;i<n;i++) for(int j=0;j<n;j++)
      distance[i][j]=Math.min(distance[i][j],distance[i][k]+distance[k][j]);
    AStarRouteEngine engine=new AStarRouteEngine(loaded.graph);
    for (Poi a:loaded.map.pois) for(Poi b:loaded.map.pois) {
      double expected=Double.POSITIVE_INFINITY;
      for(String from:a.nodeIds) for(String to:b.nodeIds)
        expected=Math.min(expected,distance[index.get(from)][index.get(to)]);
      RouteResult actual=engine.findRoute(a,b,RouteOptions.DEFAULT);
      if(Double.isInfinite(expected)) assertFalse(a.id+" -> "+b.id,actual.isSuccess());
      else { assertTrue(a.id+" -> "+b.id,actual.isSuccess()); assertEquals(expected,actual.route.totalDistance,1e-8); }
    }
  }
}

