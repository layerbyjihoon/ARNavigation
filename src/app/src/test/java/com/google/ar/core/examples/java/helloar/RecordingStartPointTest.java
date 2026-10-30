package com.google.ar.core.examples.java.helloar;

import static org.junit.Assert.*;
import com.google.ar.core.examples.java.helloar.map.*;
import java.io.*;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public final class RecordingStartPointTest {
  @Rule public TemporaryFolder temp = new TemporaryFolder();
  private IndoorMap map() throws Exception {
    try (InputStream input = getClass().getClassLoader().getResourceAsStream("munhwagwan.indoor.json")) {
      MapLoadResult result = new IndoorMapRepository().loadFromInputStream(input);
      assertTrue(result.isSuccess()); return result.map;
    }
  }

  @Test public void recordingRetainsMapFloorNodeAndCoordinatesAfterReopen() throws Exception {
    IndoorMap map = map();
    RecordingStartPoint point = RecordingStartPoint.fromNode("maps/munhwagwan.indoor.json", map, "F1_R101_D1");
    ArRecordingStore store = new ArRecordingStore(temp.getRoot());
    ArRecordingStore.Entry entry = store.create(Long.MAX_VALUE, point);
    try (FileOutputStream out = new FileOutputStream(store.file(entry))) { out.write(new byte[64]); }
    store.finish(entry, 500, null);
    RecordingStartPoint saved = new ArRecordingStore(temp.getRoot()).list().get(0).startPoint;
    assertEquals(map.metadata.id, saved.mapId); assertEquals(map.metadata.name, saved.mapName);
    assertEquals("maps/munhwagwan.indoor.json", saved.mapAsset);
    assertEquals("F1", saved.floorId); assertEquals("1층", saved.floorName);
    assertEquals("F1_R101_D1", saved.nodeId); assertEquals("101호 진입 1", saved.label);
    assertEquals(8.399, saved.x, 1e-9); assertEquals(0, saved.y, 0); assertEquals(4.436, saved.z, 1e-9);
    assertEquals("F1_C004", saved.direction.corridorNodeId);
    assertEquals(0, saved.direction.canonicalYawDegrees, 1e-6);
    ArRecordingStore.Entry savedEntry = new ArRecordingStore(temp.getRoot()).list().get(0);
    assertEquals(MapOrientation.SENSOR_SOURCE,savedEntry.mapOrientationSource);
    assertEquals(85.5,savedEntry.mapCounterClockwiseDegrees,1e-9);
  }

  @Test public void allPackagedNodesCanBeChosenIncludingCorridorsAndStairs() throws Exception {
    IndoorMap map = map();
    for (MapNode node : map.nodes) {
      RecordingStartPoint point = RecordingStartPoint.fromNode("maps/munhwagwan.indoor.json", map, node.id);
      assertEquals(node.floorId, point.floorId); assertEquals(node.position.z, point.z, 0);
    }
  }

  @Test public void unknownNodeIsRejected() throws Exception {
    try { RecordingStartPoint.fromNode("map", map(), "F99_MISSING"); fail(); }
    catch (IllegalArgumentException expected) { }
  }

  @Test public void existingAndUnassignedRecordingsStillLoad() throws Exception {
    ArRecordingStore store = new ArRecordingStore(temp.getRoot());
    store.create(Long.MAX_VALUE);
    assertNull(new ArRecordingStore(temp.getRoot()).list().get(0).startPoint);
    assertEquals("미지정", RecordingStartPoint.describe(null));
  }
}
