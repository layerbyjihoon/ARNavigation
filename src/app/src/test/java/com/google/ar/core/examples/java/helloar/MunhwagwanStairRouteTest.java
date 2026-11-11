package com.google.ar.core.examples.java.helloar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.ar.core.examples.java.helloar.map.IndoorMapRepository;
import com.google.ar.core.examples.java.helloar.map.MapLoadResult;
import com.google.ar.core.examples.java.helloar.map.Poi;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;

public final class MunhwagwanStairRouteTest {
  @Test
  public void allCrossFloorPoiPairsHaveAnApproachAndAnExitToTheirDestination() throws Exception {
    JsonObject asset = loadAsset();
    MapLoadResult loaded = parse(asset);
    double[] elevations = new double[6];
    for (int level = 2; level <= 5; level++) {
      elevations[level] = elevations[level - 1] + asset.getAsJsonArray("floors")
          .get(level - 1).getAsJsonObject().get("riseFromPreviousFloor").getAsDouble();
    }
    int routeCount = 0;
    Set<String> floorPairs = new HashSet<>();
    Set<String> stairs = new HashSet<>();
    for (Poi start : loaded.map.pois) {
      for (Poi end : loaded.map.pois) {
        if (start.floorId.equals(end.floorId)) continue;
        String pair = start.id + " → " + end.id;
        MunhwagwanStairRoute route = MunhwagwanStairRoute.find(loaded, start, end);
        assertNotNull(pair, route);
        assertTrue(pair, start.nodeIds.contains(route.approach.nodes.get(0).id));
        assertTrue(pair, end.nodeIds.contains(
            route.destination.nodes.get(route.destination.nodes.size() - 1).id));
        String stair = route.stairName.substring(route.stairName.length() - 1);
        assertEquals(pair, start.floorId + "_STAIR_" + stair,
            route.approach.nodes.get(route.approach.nodes.size() - 1).id);
        assertEquals(pair, end.floorId + "_STAIR_" + stair, route.destination.nodes.get(0).id);
        assertTrue(pair, route.exitDirection.nextNodeId.startsWith(end.floorId + "_"));
        int fromLevel = Integer.parseInt(start.floorId.substring(1));
        int toLevel = Integer.parseInt(end.floorId.substring(1));
        assertEquals(pair, elevations[toLevel] - elevations[fromLevel], route.heightChange, 1e-9);
        routeCount++;
        floorPairs.add(start.floorId + end.floorId);
        stairs.add(stair);
      }
    }
    assertEquals(3362, routeCount);
    assertEquals(20, floorPairs.size());
    assertEquals(2, stairs.size());
  }

  @Test
  public void sumsOnlyCrossedIntervalsInEitherDirection() throws Exception {
    JsonObject asset = loadAsset();
    JsonObject second = asset.getAsJsonArray("floors").get(1).getAsJsonObject();
    JsonObject third = asset.getAsJsonArray("floors").get(2).getAsJsonObject();
    second.addProperty("riseFromPreviousFloor", 3.6);
    third.addProperty("riseFromPreviousFloor", 4.2);
    asset.getAsJsonArray("floors").get(3).getAsJsonObject()
        .addProperty("riseFromPreviousFloor", 3.5);
    asset.getAsJsonArray("floors").get(4).getAsJsonObject()
        .addProperty("riseFromPreviousFloor", 3.5);
    MapLoadResult calibrated = parse(asset);
    assertEquals(7.8, MunhwagwanStairRoute.heightChange(calibrated.map, "F1", "F3"), 1e-9);
    assertEquals(-14.8, MunhwagwanStairRoute.heightChange(calibrated.map, "F5", "F1"), 1e-9);
    assertEquals(4.2, MunhwagwanStairRoute.heightChange(calibrated.map, "F2", "F3"), 1e-9);
    third.remove("riseFromPreviousFloor");
    MapLoadResult incomplete = parse(asset);
    assertNull(MunhwagwanStairRoute.heightChange(incomplete.map, "F4", "F2"));
    assertEquals(3.6, MunhwagwanStairRoute.heightChange(incomplete.map, "F1", "F2"), 1e-9);
    assertEquals(-7.0, MunhwagwanStairRoute.heightChange(incomplete.map, "F5", "F3"), 1e-9);
  }

  @Test
  public void ignoresOtherHeightsAndRequiresContinuousSamplesNearTheMeasuredRise() {
    FloorChangeDetector detector = new FloorChangeDetector(3.6);
    assertFalse(detector.observe(0, 0));
    assertFalse(detector.observe(200, 1.8));
    assertFalse(detector.observe(400, 3.6));
    assertFalse(detector.observe(800, 3.5));
    assertFalse(detector.observe(1200, Double.NaN));
    assertFalse(detector.observe(1400, 3.6));
    assertFalse(detector.observe(1800, 3.6));
    assertFalse(detector.observe(2400, 3.6));
    assertFalse(detector.observe(2800, 3.6));
    assertFalse(detector.observe(3200, 3.7));
    assertTrue(detector.observe(3400, 3.6));
    assertFalse(detector.observe(3500, 3.1));
  }

  @Test
  public void doesNotSwitchAtAnIntermediateFloorInEitherDirection() {
    for (int sign : new int[] {1, -1}) {
      FloorChangeDetector detector = new FloorChangeDetector(sign * 14.0);
      for (int time = 0; time <= 1200; time += 400) {
        assertFalse(detector.observe(time, sign * 10.5));
      }
      assertFalse(detector.observe(1500, -sign * 14.0));
      assertFalse(detector.observe(1800, sign * 13.9));
      assertFalse(detector.observe(2200, sign * 14.1));
      assertFalse(detector.observe(2600, sign * 14.0));
      assertTrue(detector.observe(2800, sign * 14.0));
    }
  }

  private JsonObject loadAsset() throws Exception {
    try (InputStream stream = getClass().getClassLoader()
        .getResourceAsStream("munhwagwan.indoor.json")) {
      assertNotNull(stream);
      return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
          .getAsJsonObject();
    }
  }

  private MapLoadResult parse(JsonObject asset) {
    MapLoadResult loaded = new IndoorMapRepository().loadFromInputStream(
        new ByteArrayInputStream(asset.toString().getBytes(StandardCharsets.UTF_8)));
    assertTrue(loaded.validation.issues.toString(), loaded.isSuccess());
    return loaded;
  }
}
