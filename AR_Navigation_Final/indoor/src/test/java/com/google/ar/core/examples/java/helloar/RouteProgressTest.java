package com.google.ar.core.examples.java.helloar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public final class RouteProgressTest {
  private final List<Waypoint> route = Arrays.asList(
      new Waypoint(0, -0.5f, -4), new Waypoint(2, -0.5f, -4));

  @Test public void remainingLengthDropsAlongTheLegAndIgnoresSidewaysMotion() {
    assertEquals(4f, RouteProgress.distanceToNext(route, 0, 0, 0), 0.001f);
    assertEquals(2f, RouteProgress.distanceToNext(route, 0, 0.9f, -2), 0.001f);
    assertEquals(1f, RouteProgress.distanceToNext(route, 0, -0.8f, -3), 0.001f);
    assertEquals(0f, RouteProgress.distanceToNext(route, 0, 0.5f, -5), 0.001f);
  }

  @Test public void onlyAnIntermediatePointCanBePassedWithinTheRouteCorridor() {
    assertFalse(RouteProgress.passedIntermediate(route, 0, 0, -3.9f));
    assertTrue(RouteProgress.passedIntermediate(route, 0, 0.9f, -4.3f));
    assertFalse(RouteProgress.passedIntermediate(route, 0, 2f, -4.3f));
    assertFalse(RouteProgress.passedIntermediate(route, 1, 3f, -4f));
  }
}
