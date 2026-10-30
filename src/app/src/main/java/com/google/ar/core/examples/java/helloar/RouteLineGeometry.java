package com.google.ar.core.examples.java.helloar;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;

/** Reusable triangle geometry for a world-space navigation ribbon, in origin-local meters. */
final class RouteLineGeometry {
  private static final float HALF_WIDTH = 0.06f;
  private static final float BORDER_HALF_WIDTH = 0.09f;
  private static final float ARROW_SPACING = 0.6f;
  private static final float ARROW_LENGTH = 0.16f;
  private static final float ARROW_HALF_WIDTH = 0.045f;
  private static final float MIN_LENGTH = 0.0001f;
  private static final float UPDATE_DISTANCE = 0.02f;
  private static final int CAP_SIDES = 12;
  private static final float[] BORDER = {0.015f, 0.12f, 0.16f, 1f};
  private static final float[] ACTIVE = {0.04f, 0.88f, 1f, 1f};
  private static final float[] FUTURE = {0.04f, 0.42f, 0.60f, 1f};
  private static final float[] ARROW = {0.92f, 1f, 1f, 1f};

  private final List<Waypoint> route;
  private final Waypoint start;
  private final FloatBuffer positions;
  private final FloatBuffer colors;
  private int lastWaypointIndex = -1;
  private float lastProgress;

  RouteLineGeometry(List<Waypoint> route) {
    this.route = new ArrayList<>(route);
    start = new Waypoint(0f, route.isEmpty() ? -0.5f : route.get(0).y, 0f);
    int maxVertices = 0;
    Waypoint previous = start;
    for (Waypoint waypoint : route) {
      float length = waypoint.horizontalDistanceTo(previous.x, previous.z);
      // Border + center strip, rounded ends, and a direction triangle every 0.6m.
      maxVertices += 12 + CAP_SIDES * 12
          + 3 * ((int) Math.ceil(length / ARROW_SPACING) + 1);
      previous = waypoint;
    }
    positions = buffer(maxVertices * 3);
    colors = buffer(maxVertices * 4);
    positions.limit(0);
    colors.limit(0);
  }

  void invalidate() {
    lastWaypointIndex = -1;
  }

  /** Returns true only when GPU buffers need uploading. Sideways motion never moves the path. */
  boolean update(int waypointIndex, float cameraX, float cameraZ) {
    if (waypointIndex < 0 || waypointIndex >= route.size()) {
      boolean changed = positions.limit() != 0 || lastWaypointIndex != waypointIndex;
      positions.limit(0);
      colors.limit(0);
      lastWaypointIndex = waypointIndex;
      return changed;
    }
    Waypoint from = waypointIndex == 0 ? start : route.get(waypointIndex - 1);
    Waypoint to = route.get(waypointIndex);
    float dx = to.x - from.x;
    float dz = to.z - from.z;
    float length = (float) Math.sqrt(dx * dx + dz * dz);
    float progress = length < MIN_LENGTH ? 0f
        : Math.max(0f, Math.min(length,
            ((cameraX - from.x) * dx + (cameraZ - from.z) * dz) / length));
    if (lastWaypointIndex == waypointIndex && Math.abs(progress - lastProgress) < UPDATE_DISTANCE) {
      return false;
    }
    lastWaypointIndex = waypointIndex;
    lastProgress = progress;
    positions.clear();
    colors.clear();
    for (int i = waypointIndex; i < route.size(); i++) {
      from = i == 0 ? start : route.get(i - 1);
      to = route.get(i);
      addSegment(from, to, i == waypointIndex ? progress : 0f,
          i == waypointIndex ? ACTIVE : FUTURE);
    }
    positions.flip();
    colors.flip();
    return true;
  }

  int getVertexCount() {
    return positions.limit() / 3;
  }

  FloatBuffer getPositions() {
    positions.rewind();
    return positions;
  }

  FloatBuffer getColors() {
    colors.rewind();
    return colors;
  }

  private void addSegment(Waypoint from, Waypoint to, float progress, float[] color) {
    float dx = to.x - from.x;
    float dz = to.z - from.z;
    float length = (float) Math.sqrt(dx * dx + dz * dz);
    if (length - progress < MIN_LENGTH) {
      return;
    }
    dx /= length;
    dz /= length;
    float startX = from.x + dx * progress;
    float startZ = from.z + dz * progress;
    float startY = from.y + (to.y - from.y) * progress / length;
    addRibbon(startX, startY, startZ, to.x, to.y, to.z, -dz, dx, BORDER_HALF_WIDTH, BORDER);
    addCap(startX, startY, startZ, BORDER_HALF_WIDTH, BORDER);
    addCap(to.x, to.y, to.z, BORDER_HALF_WIDTH, BORDER);
    // Small height offsets prevent z-fighting between the border, fill, and arrows.
    addRibbon(startX, startY + 0.002f, startZ, to.x, to.y + 0.002f, to.z,
        -dz, dx, HALF_WIDTH, color);
    addCap(startX, startY + 0.002f, startZ, HALF_WIDTH, color);
    addCap(to.x, to.y + 0.002f, to.z, HALF_WIDTH, color);
    for (float d = ARROW_SPACING; d < length - 0.12f; d += ARROW_SPACING) {
      // Never leave an arrow extending into the already-traveled part of a segment.
      if (d - ARROW_LENGTH < progress) {
        continue;
      }
      float tipX = from.x + dx * d;
      float tipZ = from.z + dz * d;
      float tailX = tipX - dx * ARROW_LENGTH;
      float tailZ = tipZ - dz * ARROW_LENGTH;
      float tipY = from.y + (to.y - from.y) * d / length + 0.004f;
      float tailY = from.y + (to.y - from.y) * (d - ARROW_LENGTH) / length + 0.004f;
      vertex(tipX, tipY, tipZ, ARROW);
      vertex(tailX - dz * ARROW_HALF_WIDTH, tailY, tailZ + dx * ARROW_HALF_WIDTH, ARROW);
      vertex(tailX + dz * ARROW_HALF_WIDTH, tailY, tailZ - dx * ARROW_HALF_WIDTH, ARROW);
    }
  }

  private void addRibbon(float x1, float y1, float z1, float x2, float y2, float z2,
      float sideX, float sideZ, float halfWidth, float[] color) {
    float ox = sideX * halfWidth;
    float oz = sideZ * halfWidth;
    vertex(x1 + ox, y1, z1 + oz, color);
    vertex(x1 - ox, y1, z1 - oz, color);
    vertex(x2 + ox, y2, z2 + oz, color);
    vertex(x2 + ox, y2, z2 + oz, color);
    vertex(x1 - ox, y1, z1 - oz, color);
    vertex(x2 - ox, y2, z2 - oz, color);
  }

  private void addCap(float x, float y, float z, float radius, float[] color) {
    for (int i = 0; i < CAP_SIDES; i++) {
      double a = i * 2 * Math.PI / CAP_SIDES;
      double b = (i + 1) * 2 * Math.PI / CAP_SIDES;
      vertex(x, y, z, color);
      vertex(x + radius * (float) Math.cos(a), y, z + radius * (float) Math.sin(a), color);
      vertex(x + radius * (float) Math.cos(b), y, z + radius * (float) Math.sin(b), color);
    }
  }

  private void vertex(float x, float y, float z, float[] color) {
    positions.put(x).put(y).put(z);
    colors.put(color);
  }

  private static FloatBuffer buffer(int floats) {
    return ByteBuffer.allocateDirect(floats * Float.BYTES).order(ByteOrder.nativeOrder()).asFloatBuffer();
  }
}
