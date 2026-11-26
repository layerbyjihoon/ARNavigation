package com.google.ar.core.examples.java.helloar;

import static org.junit.Assert.*;

import java.util.Arrays;
import org.junit.Test;

public final class StairFallbackPathTest {
  @Test public void measuredStairKeepsMappedThreeDimensionalTail() {
    java.util.List<Waypoint> route = Arrays.asList(
        new Waypoint(0, -0.5f, -2), new Waypoint(0, -0.5f, -4),
        new Waypoint(0, 0.1f, -5), new Waypoint(0, 1.1f, -6));
    StairFallbackPath fallback = StairFallbackPath.from(route, true);
    assertEquals(-4f, StairFallbackPath.entrance(route).z, 0f);
    assertTrue(fallback.hasStairHeight);
    assertEquals(-2f, fallback.start.z, 0f);
    assertEquals(3, fallback.waypoints.size());
    assertEquals(1.1f, fallback.waypoints.get(2).y, 0f);
    assertEquals(-5f, fallback.markerPoint().z, 0f);
    RouteLineGeometry line = new RouteLineGeometry(fallback.waypoints, fallback.start);
    assertTrue(line.update(0, fallback.start.x, fallback.start.z));
    assertTrue(line.getVertexCount() > 0);
  }

  @Test public void unknownStairHeightKeepsUncorrectedEntranceLine() {
    java.util.List<Waypoint> route = Arrays.asList(
        new Waypoint(0, -0.5f, -2), new Waypoint(1, -0.5f, -2),
        new Waypoint(3, -0.5f, -2));
    StairFallbackPath fallback = StairFallbackPath.from(route, true);
    assertEquals(3f, StairFallbackPath.entrance(route).x, 0f);
    assertFalse(fallback.hasStairHeight);
    assertEquals(1f, fallback.start.x, 0f);
    assertEquals(3f, fallback.waypoints.get(0).x, 0f);
    assertEquals(5f, fallback.waypoints.get(1).x, 0f);
    assertEquals(0.1f, fallback.waypoints.get(1).y, 0.00001f);
    assertEquals(4f, fallback.markerPoint().x, 0f);
    RouteLineGeometry line = new RouteLineGeometry(fallback.waypoints, fallback.start);
    assertTrue(line.update(0, fallback.start.x, fallback.start.z));
    assertTrue(line.getVertexCount() > 0);
  }

  @Test public void descendingFallbackContinuesDownFromEntrance() {
    StairFallbackPath fallback = StairFallbackPath.from(Arrays.asList(
        new Waypoint(0, -0.5f, -2), new Waypoint(0, -0.5f, -4)), false);
    assertEquals(-6f, fallback.waypoints.get(1).z, 0f);
    assertEquals(-1.1f, fallback.waypoints.get(1).y, 0.00001f);
  }
}
