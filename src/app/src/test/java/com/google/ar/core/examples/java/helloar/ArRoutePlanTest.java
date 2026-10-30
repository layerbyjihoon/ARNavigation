package com.google.ar.core.examples.java.helloar;

import static org.junit.Assert.*;
import org.junit.Test;
public final class ArRoutePlanTest {
  @Test public void mapPolylineBecomesExistingWaypointRoute() {
    ArRoutePlan plan=ArRoutePlan.decode("START → Room A",
        new float[]{0,-.5f,0, 0,-.5f,-2, 0,-.5f,-4, 2,-.5f,-4});
    assertEquals(3,plan.waypoints.size()); assertEquals(2,plan.waypoints.get(2).x,0);
    assertNull(plan.entryYawDegrees);
    RouteLineGeometry geometry=new RouteLineGeometry(plan.waypoints);
    assertTrue(geometry.update(0,0,0)); assertTrue(geometry.getVertexCount()>0);
  }
  @Test public void selectedDoorDirectionSurvivesHandoffWithoutRotatingWaypointsTwice() {
    ArRoutePlan plan = ArRoutePlan.decode("105 → 106",
        new float[]{0,-.5f,0, 0,-.5f,1.39f, 2,-.5f,1.39f},
        "maps/munhwagwan.indoor.json", 180.0, "F1_R105_D1 → F1_C009");
    assertEquals(180, plan.entryYawDegrees, 0);
    assertTrue(plan.entryDirectionLabel.contains("F1_C009"));
    assertEquals(1.39f, plan.waypoints.get(0).z, 1e-6);
    assertEquals(2, plan.waypoints.get(1).x, 0);
  }
  @Test public void invalidEntryDirectionsCannotReachRenderer() {
    for (double angle : new double[]{Double.NaN,Double.POSITIVE_INFINITY,181,-181}) {
      try { ArRoutePlan.decode("bad",new float[]{0,-.5f,0,0,-.5f,-2},"map",angle,"bad"); fail(); }
      catch (IllegalArgumentException expected) { }
    }
  }
  @Test public void invalidPayloadsAndOutOfBoundsPointsCannotReachGlRenderer() {
    for(float[] input:new float[][]{null,new float[]{0,0,0},new float[]{0,0,0,1,0,0},
        new float[]{0,-.5f,0,Float.NaN,0,0},new float[]{0,-.5f,0,101,0,0}}) {
      try { ArRoutePlan.decode("bad",input); fail(); } catch(IllegalArgumentException expected) {}
    }
  }
}
