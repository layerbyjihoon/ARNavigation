package com.google.ar.core.examples.java.helloar;

import static org.junit.Assert.*;
import java.util.Arrays;
import org.junit.Test;

public final class StairDepthPathTest {
  @Test public void acceptsAscendingAndDescendingDepthProfilesOnly() {
    assertTrue(StairDepthPath.plausible(Arrays.asList(
        new Waypoint(0, 0, 0), new Waypoint(0, 0.2f, 0.4f),
        new Waypoint(0, 0.45f, 0.8f)), true));
    assertTrue(StairDepthPath.plausible(Arrays.asList(
        new Waypoint(0, 0, 0), new Waypoint(0, -0.2f, 0.4f),
        new Waypoint(0, -0.45f, 0.8f)), false));
    assertFalse(StairDepthPath.plausible(Arrays.asList(
        new Waypoint(0, 0, 0), new Waypoint(0, 0, 0.4f),
        new Waypoint(0, 0, 0.8f)), true));
    assertFalse(StairDepthPath.plausible(Arrays.asList(
        new Waypoint(0, 0, 0), new Waypoint(0, -0.3f, 0.4f),
        new Waypoint(0, 0.4f, 0.8f)), true));
  }

  @Test public void selectsStairsOutsideTheCenterOfTheImage() {
    java.util.List<Waypoint> centerFloor = Arrays.asList(
        new Waypoint(0, 0, 0), new Waypoint(0, 0, 0.4f),
        new Waypoint(0, 0, 0.8f));
    java.util.List<Waypoint> rightStairs = Arrays.asList(
        new Waypoint(1, 0, 0), new Waypoint(1, 0.2f, 0.4f),
        new Waypoint(1, 0.45f, 0.8f));
    assertSame(rightStairs, StairDepthPath.bestPlausible(
        Arrays.asList(centerFloor, rightStairs), true));
    assertTrue(StairDepthPath.bestPlausible(
        Arrays.asList(centerFloor), true).isEmpty());
  }

  @Test public void strongDepthProfileCanRecoverMissedImageLabel() {
    assertTrue(StairDepthPath.strong(Arrays.asList(
        new Waypoint(1, 0, 0), new Waypoint(1, 0.15f, 0.25f),
        new Waypoint(1, 0.34f, 0.5f), new Waypoint(1, 0.55f, 0.8f)), true));
    assertFalse(StairDepthPath.strong(Arrays.asList(
        new Waypoint(1, 0, 0), new Waypoint(1, 0.2f, 0.25f),
        new Waypoint(1, 0.45f, 0.5f)), true));
  }
}
