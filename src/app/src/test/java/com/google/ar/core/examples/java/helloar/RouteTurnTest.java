package com.google.ar.core.examples.java.helloar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public final class RouteTurnTest {
  @Test public void looksPastStraightAndDuplicateWaypointsForRightTurn() {
    List<Waypoint> route = Arrays.asList(point(0, -2), point(0, -2), point(0, -5), point(2, -5));
    RouteTurn turn = RouteTurn.next(route, 0, 0, -1);
    assertEquals(RouteTurn.Direction.RIGHT, turn.direction);
    assertEquals(4f, turn.distance, 0.001f);
  }

  @Test public void detectsLeftTurnAndKeepsItVisibleAtTheCorner() {
    List<Waypoint> route = Arrays.asList(point(0, -4), point(-2, -4));
    assertEquals(RouteTurn.Direction.LEFT, RouteTurn.next(route, 0, 0, -2).direction);
    RouteTurn atCorner = RouteTurn.next(route, 1, 0, -3.3f);
    assertEquals(RouteTurn.Direction.LEFT, atCorner.direction);
    assertEquals(0f, atCorner.distance, 0f);
    assertNull(RouteTurn.next(route, 1, -0.5f, -4));
  }

  @Test public void keepsLeftAndRightRelativeToTheApproachDirection() {
    List<Waypoint> route = Arrays.asList(point(2, 0), point(2, 3));
    assertEquals(RouteTurn.Direction.RIGHT, RouteTurn.next(route, 0, 0, 0).direction);
  }

  @Test public void reversalsGetTheirOwnCue() {
    List<Waypoint> route = Arrays.asList(point(0, -3), point(0, -1));
    assertEquals(RouteTurn.Direction.BACK, RouteTurn.next(route, 0, 0, 0).direction);
  }

  @Test public void smallBendsAndTheFinalStraightLegHaveNoTurnCue() {
    List<Waypoint> route = Arrays.asList(point(0, -2), point(0.1f, -5));
    assertNull(RouteTurn.next(route, 0, 0, 0));
    assertNull(RouteTurn.next(route, 1, 0, -2));
  }

  private static Waypoint point(float x, float z) {
    return new Waypoint(x, -0.5f, z);
  }
}
