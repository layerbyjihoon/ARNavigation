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

public final class IndoorMapRepositoryTest {
  private JsonObject sample;
  private final IndoorMapRepository repository = new IndoorMapRepository();
  @Before public void loadFixture() throws Exception {
    try (InputStream input = getClass().getClassLoader().getResourceAsStream("test_building.indoor.json")) {
      assertNotNull("Bundled map must be included in JVM tests", input);
      sample = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
    }
  }
  private MapLoadResult load(JsonObject map) { return load(map.toString()); }
  private MapLoadResult load(String json) {
    return repository.loadFromInputStream(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
  }
  private MapLoadResult valid() {
    MapLoadResult result = load(sample);
    assertTrue(result.validation.issues.toString(), result.isSuccess()); return result;
  }
  private void error(JsonObject map, String text) {
    MapLoadResult result = load(map);
    assertFalse(result.isSuccess()); assertNull(result.graph);
    assertTrue(result.validation.issues.toString(),
        result.validation.issues.stream().anyMatch(i -> i.severity == MapValidationIssue.Severity.ERROR
            && i.toString().contains(text)));
  }
  private JsonObject item(String collection, int index) {
    return sample.getAsJsonArray(collection).get(index).getAsJsonObject();
  }
  @Test public void parsesRealAssetAndBuildsImmutableAdjacency() {
    MapLoadResult map = valid();
    assertEquals(2, map.map.floors.size()); assertEquals(10, map.map.nodes.size());
    assertEquals(11, map.map.edges.size()); assertEquals(4, map.map.pois.size());
    assertEquals(1, map.map.anchors.size()); assertEquals(0, map.validation.warningCount());
    assertEquals("QR", map.map.anchors.get(0).type.name());
    try { map.graph.adjacency.get("START").clear(); fail(); } catch (UnsupportedOperationException expected) {}
    try { map.map.nodes.get(2).properties.put("name","changed"); fail(); }
    catch (UnsupportedOperationException expected) {}
  }
  @Test public void sameFloorRouteCannotShortcutThroughAnotherFloor() {
    JsonObject first = new JsonObject();
    first.addProperty("id","shortcut1"); first.addProperty("from","START");
    first.addProperty("to","ROOM_B"); first.addProperty("type","ELEVATOR");
    JsonObject cost = new JsonObject(); cost.addProperty("distance",0.1); first.add("cost",cost);
    JsonObject second = first.deepCopy(); second.addProperty("id","shortcut2");
    second.addProperty("from","ROOM_B"); second.addProperty("to","ROOM_A");
    sample.getAsJsonArray("edges").add(first); sample.getAsJsonArray("edges").add(second);
    MapLoadResult loaded = valid();
    NavigationRoute route = new AStarRouteEngine(loaded.graph)
        .findRoute("START","ROOM_A",RouteOptions.DEFAULT).route;
    assertEquals(6,route.totalDistance,1e-9); assertFalse(route.isMultiFloor());
  }
  @Test public void missingFromAndToReferencesAreErrors() {
    item("edges",0).addProperty("from","MISSING_FROM");
    item("edges",0).addProperty("to","MISSING_TO");
    error(sample,"MISSING_FROM"); error(sample,"MISSING_TO");
  }
  @Test public void duplicateIdentifiersAcrossEntityCollectionsAreErrors() {
    for (String collection : Arrays.asList("floors","nodes","edges","pois")) {
      JsonObject copy = sample.deepCopy();
      JsonArray array = copy.getAsJsonArray(collection);
      array.add(array.get(0).deepCopy()); error(copy,"Duplicate ID");
    }
    sample.getAsJsonArray("anchors").add(item("anchors",0).deepCopy()); error(sample,"Duplicate ID");
  }
  @Test public void danglingFloorPoiAndAnchorReferencesAreErrors() {
    item("nodes",0).addProperty("floorId","missing"); error(sample,"missing floor");
    loadFixtureUnchecked(); item("pois",0).addProperty("nodeId","missing"); error(sample,"missing node");
    loadFixtureUnchecked(); item("anchors",0).addProperty("floorId","missing"); error(sample,"missing floor");
  }
  private void loadFixtureUnchecked() { try { loadFixture(); } catch(Exception e) { throw new AssertionError(e); } }
  @Test public void poiMustBeOnItsAssociatedNodeFloor() {
    item("pois",1).addProperty("floorId","F2"); error(sample,"does not match");
  }
  @Test public void nonfiniteNumbersAndNegativeCostsAreRejected() {
    item("nodes",0).getAsJsonObject("position")
        .add("x",new JsonPrimitive(new java.math.BigDecimal("1e400")));
    error(sample,"finite");
    loadFixtureUnchecked();
    item("edges",0).add("cost",JsonParser.parseString("{\"distance\":-1}"));
    error(sample,"negative");
  }
  @Test public void malformedGeometryAndEndpointMismatchAreRejected() {
    item("edges",0).add("geometry",JsonParser.parseString("[]")); error(sample,"two points");
    item("edges",0).add("geometry",JsonParser.parseString(
        "[{\"x\":1,\"y\":0,\"z\":0},{\"x\":0,\"y\":0,\"z\":2}]"));
    error(sample,"start at");
  }
  @Test public void selfLoopsAndInvalidCrossFloorCorridorsAreRejected() {
    item("edges",0).addProperty("to","START"); error(sample,"Self-loop");
    loadFixtureUnchecked(); item("edges",4).addProperty("type","CORRIDOR"); error(sample,"Cross-floor");
  }
  @Test public void isolatedNodesAreWarningsNotFatal() {
    JsonObject node = item("nodes",0).deepCopy(); node.addProperty("id","ISOLATED");
    sample.getAsJsonArray("nodes").add(node);
    MapLoadResult result = valid(); assertTrue(result.validation.warningCount() > 0);
    assertTrue(result.validation.issues.toString().contains("isolated"));
  }
  @Test public void unknownOptionalKeysAndMinorVersionsRemainReadable() {
    sample.addProperty("version","1.1");
    sample.add("futureExtension",JsonParser.parseString("{\"nested\":[1,2,3]}"));
    item("nodes",0).addProperty("type","FUTURE_NODE");
    item("pois",0).addProperty("category","FUTURE_POI");
    MapLoadResult result = valid();
    assertEquals(NodeType.UNKNOWN,result.map.nodes.get(0).type);
    assertEquals(PoiCategory.UNKNOWN,result.map.pois.get(0).category);
    assertTrue(result.validation.warningCount() >= 3);
  }
  @Test public void emptyOptionalMetadataAndFutureAnchorsAreReadable() {
    sample.getAsJsonObject("metadata").addProperty("description","");
    item("floors",0).addProperty("name","");
    item("anchors",0).addProperty("type","FUTURE_MARKER");
    item("anchors",0).remove("value");
    MapLoadResult result=valid();
    assertEquals("",result.map.metadata.description);
    assertEquals(AnchorType.UNKNOWN,result.map.anchors.get(0).type);
  }
  @Test public void badMajorVersionAndSchemaFailClearly() {
    sample.addProperty("version","2.0"); error(sample,"Unsupported version");
    sample.addProperty("schema","GeoJSON"); error(sample,"IndoorNavGraph");
  }
  @Test public void malformedJsonNullAndWrongFieldTypesDoNotThrow() {
    for (String bad : Arrays.asList("{", "null", "[]", sample + " {}", "//comment\n" + sample))
      assertFalse(load(bad).isSuccess());
    item("nodes",0).getAsJsonObject("position").addProperty("x","1.5"); error(sample,"number");
    assertFalse(repository.loadFromInputStream(null).isSuccess());
  }
  @Test public void negativeFloorLevelsAndOptionalGeoreferenceAreSupported() {
    item("floors",0).addProperty("level",-2);
    sample.getAsJsonObject("coordinateSystem").add("georeference",JsonParser.parseString(
        "{\"latitude\":37.123456,\"longitude\":127.123456,\"altitude\":32.5,\"heading\":15}"));
    MapLoadResult result = valid(); assertEquals(-2,result.map.floors.get(0).level);
    assertEquals(15,result.map.coordinateSystem.georeference.heading,0);
  }
  @Test public void invalidGeoreferenceAndCollinearAxesAreErrors() {
    sample.getAsJsonObject("coordinateSystem").getAsJsonObject("axis").addProperty("z","EAST");
    error(sample,"orthogonal");
    loadFixtureUnchecked();
    sample.getAsJsonObject("coordinateSystem").add("georeference",
        JsonParser.parseString("{\"latitude\":91,\"longitude\":0}"));
    error(sample,"latitude");
  }
  @Test public void inputStreamOwnershipAndIoFailureAreHandled() {
    final boolean[] closed={false};
    ByteArrayInputStream caller = new ByteArrayInputStream(sample.toString().getBytes(StandardCharsets.UTF_8)) {
      @Override public void close() { closed[0]=true; }
    };
    assertTrue(repository.loadFromInputStream(caller).isSuccess()); assertFalse(closed[0]);
    caller.reset(); assertTrue(repository.loadFromAsset(path->caller,"test").isSuccess()); assertTrue(closed[0]);
    assertFalse(repository.loadFromAsset(path->{throw new IOException("not found");},"missing").isSuccess());
  }
  private void simpleDirectedMap(boolean bidirectional) {
    JsonArray nodes = new JsonArray(); nodes.add(item("nodes",0)); nodes.add(item("nodes",1));
    JsonArray edges = new JsonArray(); JsonObject edge = item("edges",0);
    edge.addProperty("bidirectional",bidirectional); edges.add(edge);
    sample.add("nodes",nodes); sample.add("edges",edges); sample.add("pois",new JsonArray());
  }
  @Test public void bidirectionalEdgesReverseGeometryAndPermitBothDirections() {
    simpleDirectedMap(true); MapLoadResult loaded=valid();
    AStarRouteEngine engine=new AStarRouteEngine(loaded.graph);
    assertTrue(engine.findRoute("START","N1",RouteOptions.DEFAULT).isSuccess());
    NavigationRoute reversed=engine.findRoute("N1","START",RouteOptions.DEFAULT).route;
    assertEquals(2,reversed.totalDistance,1e-9);
    assertEquals(2,reversed.segments.get(0).geometry.get(0).z,0);
    assertEquals(0,reversed.segments.get(0).geometry.get(1).z,0);
  }
  @Test public void oneWayEdgesCannotBeTraversedBackwards() {
    simpleDirectedMap(false); AStarRouteEngine engine=new AStarRouteEngine(valid().graph);
    assertTrue(engine.findRoute("START","N1",RouteOptions.DEFAULT).isSuccess());
    assertEquals(RouteResult.Status.NO_ROUTE,engine.findRoute("N1","START",RouteOptions.DEFAULT).status);
  }
  @Test public void zeroCostEdgesAndCyclesStillTerminateOptimally() {
    simpleDirectedMap(true);
    item("edges",0).add("cost",JsonParser.parseString("{\"distance\":0}"));
    NavigationRoute route=new AStarRouteEngine(valid().graph).findRoute("START","N1",RouteOptions.DEFAULT).route;
    assertEquals(0,route.totalDistance,0); assertEquals(1,route.segments.size());
  }
  @Test public void expectedShortestRoutesIncludeSegmentsAndStairsAcrossFloors() {
    AStarRouteEngine engine=new AStarRouteEngine(valid().graph);
    NavigationRoute roomA=engine.findRoute("START","ROOM_A",RouteOptions.DEFAULT).route;
    assertEquals(Arrays.asList("START","N1","J1","ROOM_A"),ids(roomA)); assertEquals(6,roomA.totalDistance,1e-9);
    NavigationRoute roomB=engine.findRoute("START","ROOM_B",RouteOptions.DEFAULT).route;
    assertEquals(Arrays.asList("START","N1","J1","STAIR_F1","STAIR_F2","J2","ROOM_B"),ids(roomB));
    assertEquals(13.8,roomB.totalDistance,1e-9);
    assertTrue(roomB.isMultiFloor()); assertEquals(6,roomB.segments.size());
    assertEquals("F1",roomB.segments.get(3).floorId); assertEquals("F2",roomB.segments.get(3).toFloorId);
    assertEquals(EdgeType.STAIR,roomB.segments.get(3).edge.type);
  }
  @Test public void wheelchairPolicyUsesLiftAndExcludesStairsAndUnknownAccess() {
    MapLoadResult loaded=valid();
    NavigationRoute route=new AStarRouteEngine(loaded.graph)
        .findRoute("START","ROOM_B",RouteOptions.WHEELCHAIR).route;
    assertNotNull(route); assertTrue(route.segments.stream().anyMatch(s->s.edge.type==EdgeType.ELEVATOR));
    assertFalse(route.segments.stream().anyMatch(s->s.edge.type==EdgeType.STAIR));
    item("edges",0).remove("accessibility");
    assertEquals(RouteResult.Status.NO_ROUTE,new AStarRouteEngine(valid().graph)
        .findRoute("START","ROOM_B",RouteOptions.WHEELCHAIR).status);
  }
  @Test public void fullPolylineLengthAndExplicitDistanceAreRespected() {
    MapLoadResult map=valid();
    GraphEdge corridor=map.graph.adjacency.get("J1").stream().filter(e->e.edge.id.equals("E08")).findFirst().get();
    assertEquals(1+Math.sqrt(5),corridor.distance,1e-9);
    GraphEdge lift=map.graph.adjacency.get("LIFT_F1").stream().filter(e->e.edge.type==EdgeType.ELEVATOR).findFirst().get();
    assertEquals(8,lift.distance,0); assertEquals(15,lift.edge.cost.time,0);
  }
  @Test public void cheaperThanEuclideanCostDoesNotBreakAStarOptimality() {
    JsonArray nodes=new JsonArray();
    for (int index : new int[]{0,1,3}) nodes.add(item("nodes",index));
    nodes.get(1).getAsJsonObject().add("position",JsonParser.parseString("{\"x\":100,\"y\":0,\"z\":0}"));
    nodes.get(2).getAsJsonObject().add("position",JsonParser.parseString("{\"x\":1,\"y\":0,\"z\":0}"));
    sample.add("nodes",nodes); sample.add("pois",new JsonArray());
    sample.add("edges",JsonParser.parseString(
        "[{\"id\":\"direct\",\"from\":\"START\",\"to\":\"ROOM_A\",\"type\":\"CORRIDOR\",\"cost\":{\"distance\":10}},"+
        "{\"id\":\"cheap1\",\"from\":\"START\",\"to\":\"N1\",\"type\":\"CORRIDOR\",\"cost\":{\"distance\":1}},"+
        "{\"id\":\"cheap2\",\"from\":\"N1\",\"to\":\"ROOM_A\",\"type\":\"CORRIDOR\",\"cost\":{\"distance\":1}}]"));
    NavigationRoute result=new AStarRouteEngine(valid().graph).findRoute("START","ROOM_A",RouteOptions.DEFAULT).route;
    assertEquals(2,result.totalDistance,0); assertEquals(Arrays.asList("START","N1","ROOM_A"),ids(result));
  }
  @Test public void sameNodeMissingNodeAndDisconnectedDestinationReturnStructuredResults() {
    AStarRouteEngine engine=new AStarRouteEngine(valid().graph);
    NavigationRoute same=engine.findRoute("START","START",RouteOptions.DEFAULT).route;
    assertEquals(0,same.totalDistance,0); assertTrue(same.segments.isEmpty()); assertEquals(1,same.geometry().size());
    assertEquals(RouteResult.Status.INVALID_ENDPOINT,engine.findRoute("missing","START",RouteOptions.DEFAULT).status);
    item("edges",0).addProperty("type","FUTURE_EDGE");
    assertEquals(RouteResult.Status.NO_ROUTE,new AStarRouteEngine(valid().graph)
        .findRoute("START","ROOM_A",RouteOptions.DEFAULT).status);
  }
  @Test public void multiFloorGeometryCanBeTransformedWithoutArCore() {
    MapLoadResult loaded=valid();
    NavigationRoute route=new AStarRouteEngine(loaded.graph).findRoute("START","ROOM_B",RouteOptions.DEFAULT).route;
    List<MapPosition> ar=RouteCoordinateAdapter.toAr(route,new LocalMapCoordinateTransformer(
        loaded.map.coordinateSystem,route.nodes.get(0).position,new MapPosition(0,-0.5,0),0));
    MapPosition last=ar.get(ar.size()-1);
    assertEquals(2,last.x,1e-9); assertEquals(3.3,last.y,1e-9); assertEquals(-8,last.z,1e-9);
  }
  private static List<String> ids(NavigationRoute route) {
    List<String> ids=new ArrayList<>(); for(MapNode node:route.nodes) ids.add(node.id); return ids;
  }
}
