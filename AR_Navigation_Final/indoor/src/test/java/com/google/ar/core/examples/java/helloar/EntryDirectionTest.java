package com.google.ar.core.examples.java.helloar;

import static org.junit.Assert.*;
import com.google.ar.core.examples.java.helloar.map.*;
import java.io.InputStream;
import java.util.*;
import org.junit.Test;

public final class EntryDirectionTest {
  private IndoorMap map() throws Exception {
    try (InputStream in = getClass().getClassLoader().getResourceAsStream("munhwagwan.indoor.json")) {
      return new IndoorMapRepository().loadFromInputStream(in).map;
    }
  }
  private RecordingStartPoint point(String id) throws Exception {
    return RecordingStartPoint.fromNode("maps/munhwagwan.indoor.json", map(), id);
  }

  @Test public void oppositeSidesOfCorridorHaveOppositeOutgoingDirections() throws Exception {
    EntryDirection north = point("F1_R101_D1").direction;
    EntryDirection south = point("F1_R105_D1").direction;
    assertEquals("F1_C004", north.nextNodeId); assertEquals(0, north.canonicalYawDegrees, 1e-6);
    assertEquals("F1_C009", south.nextNodeId);
    assertEquals(Math.PI, Math.abs(Math.toRadians(south.canonicalYawDegrees)), 1e-6);
  }

  @Test public void room406UsesCorridorNotItsInternalAccessBranch() throws Exception {
    EntryDirection direction = point("F4_R406_D1").direction;
    assertEquals("F4_C007", direction.nextNodeId); assertEquals("F4_C007", direction.corridorNodeId);
    assertEquals(180, Math.abs(direction.canonicalYawDegrees), 1e-6);
  }

  @Test public void innerRoomUsesFirstPathSegmentRatherThanLineThroughWalls() throws Exception {
    EntryDirection direction = point("F4_R406-1_D1").direction;
    assertEquals("F4_INT406_2", direction.nextNodeId);
    assertEquals("F4_C007", direction.corridorNodeId);
    assertEquals(0, direction.canonicalYawDegrees, 1e-6);
  }

  @Test public void everyPackagedEntryResolvesButCorridorsDoNotGetArbitraryHeadings() throws Exception {
    IndoorMap map = map();
    for (MapNode node : map.nodes) {
      EntryDirection direction = EntryDirection.resolve(map, node);
      if (node.type == NodeType.DOOR || node.type == NodeType.ENTRANCE || node.type == NodeType.STAIR)
        assertNotNull(node.id, direction);
      else assertNull(node.id, direction);
    }
  }

  @Test public void ambiguousExitsAndWrongWayEdgesDoNotProduceAGuess() throws Exception {
    MapNode start = node("D", NodeType.DOOR, 0, 0), east = node("E", NodeType.WAYPOINT, 1, 0),
        west = node("W", NodeType.WAYPOINT, -1, 0);
    assertNull(EntryDirection.resolve(fixture(Arrays.asList(start,east,west),
        Arrays.asList(edge("D","E",true),edge("D","W",true))), start));
    assertNull(EntryDirection.resolve(fixture(Arrays.asList(start,east),
        Collections.singletonList(edge("E","D",false))), start));
  }

  @Test public void polylineFirstLegDefinesFacingAndCameraYawIsConvertedToMapReference() throws Exception {
    MapNode start = node("D", NodeType.DOOR, 0, 0), east = node("E", NodeType.WAYPOINT, 1, 0);
    MapEdge curved = new MapEdge("curve","D","E",true,EdgeType.DOOR,
        Arrays.asList(new MapPosition(0,0,0),new MapPosition(0,0,1)),new EdgeCost(null,null),new Accessibility(null));
    EntryDirection direction = EntryDirection.resolve(fixture(Arrays.asList(start,east),Collections.singletonList(curved)),start);
    assertEquals(0, direction.canonicalYawDegrees, 1e-6);
    for (double cameraYaw : new double[] {0,Math.PI/2,-2.7})
      for (double entryYaw : new double[] {0,90,180,-90}) {
        double reference = MapAlignment.referenceYawForEntry(cameraYaw,entryYaw);
        assertEquals(0,MapAlignment.wrap(reference+Math.toRadians(entryYaw)-cameraYaw),1e-6);
      }
  }

  private MapNode node(String id, NodeType type, double x, double z) {
    return new MapNode(id,"F1",type,new MapPosition(x,0,z),Collections.emptyMap());
  }
  private MapEdge edge(String from, String to, boolean both) {
    return new MapEdge(from+to,from,to,both,EdgeType.DOOR,Collections.emptyList(),new EdgeCost(1.0,null),new Accessibility(null));
  }
  private IndoorMap fixture(List<MapNode> nodes, List<MapEdge> edges) throws Exception {
    IndoorMap base = map();
    return new IndoorMap(base.schema,base.version,base.metadata,base.coordinateSystem,base.floors,
        nodes,edges,Collections.emptyList(),Collections.emptyList());
  }
}
