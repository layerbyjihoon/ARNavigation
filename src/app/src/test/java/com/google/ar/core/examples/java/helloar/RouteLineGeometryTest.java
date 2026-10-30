package com.google.ar.core.examples.java.helloar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.FloatBuffer;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

public class RouteLineGeometryTest {
  private RouteLineGeometry route() {
    return new RouteLineGeometry(Arrays.asList(
        new Waypoint(0f, -0.5f, -2f),
        new Waypoint(0f, -0.5f, -4f),
        new Waypoint(2f, -0.5f, -4f)));
  }

  @Test
  public void buildsFullRouteWithMatchingFinitePositionAndColorBuffers() {
    RouteLineGeometry geometry = route();
    assertTrue(geometry.update(0, 0f, 0f));
    assertTrue(geometry.getVertexCount() > 0);
    assertEquals(0, geometry.getVertexCount() % 3);
    assertEquals(geometry.getVertexCount() * 3, geometry.getPositions().limit());
    assertEquals(geometry.getVertexCount() * 4, geometry.getColors().limit());
    FloatBuffer positions = geometry.getPositions();
    for (int i = 0; i < positions.limit(); i++) {
      assertTrue(Float.isFinite(positions.get(i)));
    }
    assertEquals(-0.09f, minimum(positions, 0), 0.0001f);
    assertEquals(2.09f, maximum(positions, 0), 0.0001f);
    assertEquals(-4.09f, minimum(positions, 2), 0.0001f);
    assertEquals(0.09f, maximum(positions, 2), 0.0001f);
  }

  @Test
  public void trimsWalkedPartAndKeepsLineFixedWhenUserMovesSideways() {
    RouteLineGeometry geometry = route();
    geometry.update(0, 0f, -1f);
    assertEquals(-0.91f, maximum(geometry.getPositions(), 2), 0.0001f);
    float[] original = copy(geometry.getPositions());
    assertFalse(geometry.update(0, 3f, -1f));
    for (int i = 0; i < original.length; i++) {
      assertEquals(original[i], geometry.getPositions().get(i), 0f);
    }
  }

  @Test
  public void removesCompletedLegsAndTrimsTheRightTurnAlongX() {
    RouteLineGeometry geometry = route();
    geometry.update(1, 0f, -2.5f);
    assertEquals(-2.41f, maximum(geometry.getPositions(), 2), 0.0001f);
    geometry.update(2, 1f, -4f);
    assertEquals(0.91f, minimum(geometry.getPositions(), 0), 0.0001f);
    assertEquals(-3.91f, maximum(geometry.getPositions(), 2), 0.0001f);
    assertEquals(-4.09f, minimum(geometry.getPositions(), 2), 0.0001f);
  }

  @Test
  public void clampsProgressBeforeStartAndAfterSegmentEnd() {
    RouteLineGeometry geometry = route();
    geometry.update(0, 0f, 10f);
    assertEquals(0.09f, maximum(geometry.getPositions(), 2), 0.0001f);
    geometry.update(2, 10f, -4f);
    assertEquals(0, geometry.getVertexCount());
  }

  @Test
  public void completionEmptiesBuffersAndRestartRebuildsThem() {
    RouteLineGeometry geometry = route();
    geometry.update(0, 0f, 0f);
    assertTrue(geometry.update(3, 2f, -4f));
    assertEquals(0, geometry.getVertexCount());
    assertEquals(0, geometry.getColors().limit());
    assertFalse(geometry.update(3, 2f, -4f));
    assertTrue(geometry.update(0, 0f, 0f));
    assertTrue(geometry.getVertexCount() > 0);
  }

  @Test
  public void invalidationReuploadsAfterGlContextRecreationEvenWhenStationary() {
    RouteLineGeometry geometry = route();
    assertTrue(geometry.update(0, 0f, 0f));
    assertFalse(geometry.update(0, 0f, -0.005f));
    geometry.invalidate();
    assertTrue(geometry.update(0, 0f, -0.005f));
  }

  @Test
  public void handlesEmptyAndDuplicateSegmentsWithoutNanOrBufferOverflow() {
    RouteLineGeometry empty = new RouteLineGeometry(Collections.emptyList());
    empty.update(0, 0f, 0f);
    assertEquals(0, empty.getVertexCount());
    RouteLineGeometry duplicate = new RouteLineGeometry(Arrays.asList(
        new Waypoint(0f, -0.5f, 0f), new Waypoint(0f, -0.5f, -20f)));
    duplicate.update(0, 0f, 0f);
    assertTrue(duplicate.getVertexCount() > 0);
    FloatBuffer positions = duplicate.getPositions();
    for (int i = 0; i < positions.limit(); i++) {
      assertTrue(Float.isFinite(positions.get(i)));
    }
  }

  @Test
  public void arrowTipsPointInTravelDirectionOnStraightAndTurningLegs() {
    RouteLineGeometry geometry = route();
    geometry.update(0, 0f, 0f);
    FloatBuffer positions = geometry.getPositions();
    FloatBuffer colors = geometry.getColors();
    int arrows = 0;
    for (int vertex = 0; vertex < geometry.getVertexCount(); vertex += 3) {
      if (colors.get(vertex * 4) < 0.9f) {
        continue;
      }
      float tipX = positions.get(vertex * 3);
      float tailX = positions.get((vertex + 1) * 3);
      float tipZ = positions.get(vertex * 3 + 2);
      float tailZ = positions.get((vertex + 1) * 3 + 2);
      if (Math.abs(tipX - tailX) > 0.1f) {
        assertTrue(tipX > tailX);
      } else {
        assertTrue(tipZ < tailZ);
      }
      arrows++;
    }
    assertTrue(arrows >= 6);
  }

  private float minimum(FloatBuffer buffer, int axis) {
    float value = Float.POSITIVE_INFINITY;
    for (int i = axis; i < buffer.limit(); i += 3) {
      value = Math.min(value, buffer.get(i));
    }
    return value;
  }

  private float maximum(FloatBuffer buffer, int axis) {
    float value = Float.NEGATIVE_INFINITY;
    for (int i = axis; i < buffer.limit(); i += 3) {
      value = Math.max(value, buffer.get(i));
    }
    return value;
  }

  private float[] copy(FloatBuffer buffer) {
    float[] result = new float[buffer.limit()];
    for (int i = 0; i < result.length; i++) {
      result[i] = buffer.get(i);
    }
    return result;
  }
}
