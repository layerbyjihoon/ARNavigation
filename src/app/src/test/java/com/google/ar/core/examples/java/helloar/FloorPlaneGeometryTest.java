package com.google.ar.core.examples.java.helloar;

import static org.junit.Assert.*;
import java.nio.FloatBuffer;
import org.junit.Test;

public final class FloorPlaneGeometryTest {
  private FloatBuffer square() {
    return FloatBuffer.wrap(new float[] {-1,-1, 1,-1, 1,1, -1,1}).asReadOnlyBuffer();
  }

  @Test public void floorSeenAheadCanStartWithoutCameraProjectionInsidePolygon() {
    float distance = FloorPlaneGeometry.distanceToPolygon(square(), 0, 2.5f);
    assertEquals(1.5f, distance, 1e-6f);
    assertTrue(FloorPlaneGeometry.isNearbyFloor(1.4f, distance));
  }

  @Test public void insideEdgesAndCornersHaveCorrectDistance() {
    assertEquals(0, FloorPlaneGeometry.distanceToPolygon(square(), 0, 0), 0);
    assertEquals(0, FloorPlaneGeometry.distanceToPolygon(square(), 1, 0), 0);
    assertEquals(0, FloorPlaneGeometry.distanceToPolygon(square(), -1, -1), 0);
    assertEquals(Math.sqrt(2), FloorPlaneGeometry.distanceToPolygon(square(), 2, 2), 1e-6);
  }

  @Test public void rejectDistantAndWrongHeightSurfaces() {
    assertFalse(FloorPlaneGeometry.isNearbyFloor(1.4f,
        FloorPlaneGeometry.distanceToPolygon(square(), 0, 5)));
    assertFalse(FloorPlaneGeometry.isNearbyFloor(-1, 0));
    assertFalse(FloorPlaneGeometry.isNearbyFloor(0.3f, 0));
    assertFalse(FloorPlaneGeometry.isNearbyFloor(3, 0));
    assertFalse(FloorPlaneGeometry.isNearbyFloor(1.4f, Float.POSITIVE_INFINITY));
  }

  @Test public void polygonWindingAndBufferPositionDoNotAffectDistance() {
    FloatBuffer polygon = square();
    polygon.position(4);
    assertEquals(2, FloorPlaneGeometry.distanceToPolygon(polygon, 3, 0), 0);
    assertEquals(4, polygon.position());
    FloatBuffer reverse = FloatBuffer.wrap(new float[] {-1,1, 1,1, 1,-1, -1,-1});
    assertEquals(0, FloorPlaneGeometry.distanceToPolygon(reverse, 0, 0), 0);
    assertEquals(2, FloorPlaneGeometry.distanceToPolygon(reverse, 3, 0), 0);
  }

  @Test public void missingOrInvalidPolygonCannotStartNavigation() {
    assertEquals(Float.POSITIVE_INFINITY,
        FloorPlaneGeometry.distanceToPolygon(FloatBuffer.allocate(0), 0, 0), 0);
    assertEquals(Float.POSITIVE_INFINITY,
        FloorPlaneGeometry.distanceToPolygon(square(), Float.NaN, 0), 0);
  }
}
