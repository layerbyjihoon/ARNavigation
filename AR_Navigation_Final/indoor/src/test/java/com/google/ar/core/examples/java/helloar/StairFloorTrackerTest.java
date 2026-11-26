package com.google.ar.core.examples.java.helloar;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class StairFloorTrackerTest {
  @Test public void ascentRequiresSustainedHeightBeforeEstimatingNextFloor() {
    StairFloorTracker tracker = new StairFloorTracker("F3", "F4", true, 12f);
    tracker.update(12.9f, 100);
    assertEquals(StairFloorTracker.Phase.BETWEEN_FLOORS, tracker.phase());
    tracker.update(14.6f, 150);
    assertEquals(StairFloorTracker.Phase.BETWEEN_FLOORS, tracker.phase());
    tracker.update(14.7f, 200);
    assertEquals(StairFloorTracker.Phase.BETWEEN_FLOORS, tracker.phase());
    tracker.update(14.8f, 1500);
    assertEquals(StairFloorTracker.Phase.NEXT_FLOOR, tracker.phase());
    assertEquals(2.8f, tracker.climbedMeters(), 0.001f);
    assertEquals("4층", StairFloorTracker.label(tracker.nextFloor));
  }

  @Test public void descentAndBriefPhoneMotionDoNotConfirmAnotherFloor() {
    StairFloorTracker tracker = new StairFloorTracker("F4", "F3", false, 10f);
    tracker.update(7.3f, 100);
    tracker.update(8.9f, 900);
    assertEquals(StairFloorTracker.Phase.BETWEEN_FLOORS, tracker.phase());
    tracker.update(7.3f, 1000);
    tracker.update(7.3f, 1500);
    assertEquals(StairFloorTracker.Phase.BETWEEN_FLOORS, tracker.phase());
    tracker.update(7.2f, 2300);
    assertEquals(StairFloorTracker.Phase.NEXT_FLOOR, tracker.phase());
  }

  @Test public void interruptedTrackingRestartsTheConfirmationWindow() {
    StairFloorTracker tracker = new StairFloorTracker("F2", "F3", true, 0f);
    tracker.update(2.7f, 100);
    tracker.trackingLost();
    tracker.update(2.7f, 2000);
    assertEquals(StairFloorTracker.Phase.BETWEEN_FLOORS, tracker.phase());
    tracker.update(2.7f, 3300);
    assertEquals(StairFloorTracker.Phase.NEXT_FLOOR, tracker.phase());
  }

  @Test public void mappedFourPointFiveMeterFloorWaitsUntilNearItsActualHeight() {
    StairFloorTracker tracker = new StairFloorTracker("F1", "F2", true, 0f, 4.5f);
    tracker.update(2.5f, 100);
    tracker.update(2.5f, 1500);
    assertEquals(StairFloorTracker.Phase.BETWEEN_FLOORS, tracker.phase());
    tracker.update(3.7f, 1600);
    tracker.update(3.7f, 2900);
    assertEquals(StairFloorTracker.Phase.NEXT_FLOOR, tracker.phase());
  }
}
