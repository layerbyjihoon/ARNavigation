package com.google.ar.core.examples.java.helloar;

import com.google.ar.core.examples.java.helloar.localization.CaptureGeometry;
import com.google.ar.core.examples.java.helloar.localization.Rigid2D;
import com.google.ar.core.examples.java.helloar.segmentation.FrameSnapshot;
import com.google.ar.core.examples.java.helloar.segmentation.SegmentationResult;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Heading and lateral correction from stable wall/floor contacts in the route frame. */
final class CorridorCenterEstimator {
  private static final float FLOOR_Y = -0.52f;
  static final float SINGLE_WALL_DISTANCE_METERS = 1f;

  static final class Observation {
    final float x, z, width, yaw, pivotX, pivotZ;
    final boolean singleWall;
    final long timestamp;

    Observation(float x, float z, float width, long timestamp) {
      this(x, z, width, timestamp, false);
    }

    Observation(float x, float z, float width, long timestamp, boolean singleWall) {
      this(x, z, width, timestamp, singleWall, 0, 0, 0);
    }

    Observation(float x, float z, float width, long timestamp, boolean singleWall,
        float yaw, float pivotX, float pivotZ) {
      this.x = x;
      this.z = z;
      this.width = width;
      this.timestamp = timestamp;
      this.singleWall = singleWall;
      this.yaw = yaw;
      this.pivotX = pivotX;
      this.pivotZ = pivotZ;
    }
  }

  Observation estimate(SegmentationResult result, List<float[]> boundaries,
      List<Waypoint> route, int waypointIndex, Rigid2D alignment) {
    if (result == null || boundaries == null || boundaries.isEmpty()) return null;
    FrameSnapshot frame = result.frame;
    if (!frame.tracking || frame.worldToRoute == null) return null;
    List<float[]> contacts = new ArrayList<>();
    for (float[] line : boundaries) {
      for (int i = 2; i < line.length; i += 2) {
        float u0 = line[i - 2], v0 = line[i - 1];
        float u1 = line[i], v1 = line[i + 1];
        int steps = Math.min(40, Math.max(1, (int) (Math.hypot(
            (u1 - u0) * result.width, (v1 - v0) * result.height) / 8)));
        for (int step = 0; step <= steps; step++) {
          float t = step / (float) steps;
          CaptureGeometry.Ray ray = CaptureGeometry.ray(frame,
              u0 + (u1 - u0) * t, v0 + (v1 - v0) * t);
          if (ray == null || ray.origin[1] - FLOOR_Y < 0.6f
              || ray.origin[1] - FLOOR_Y > 2.5f || ray.direction[1] >= -0.04f)
            continue;
          float distance = (FLOOR_Y - ray.origin[1]) / ray.direction[1];
          if (distance < 0.5f || distance > 8f) continue;
          contacts.add(new float[] {
              ray.origin[0] + distance * ray.direction[0],
              ray.origin[2] + distance * ray.direction[2]});
        }
      }
    }
    float[] camera = FrameSnapshot.transform(frame.worldToRoute,
        frame.cameraToWorld[12], frame.cameraToWorld[13], frame.cameraToWorld[14]);
    return fit(contacts, camera[0], camera[2], route, waypointIndex, alignment,
        frame.timestamp);
  }

  static Observation fit(List<float[]> contacts, float cameraX, float cameraZ,
      List<Waypoint> route, int waypointIndex, Rigid2D alignment, long timestamp) {
    if (route == null || waypointIndex < 0 || waypointIndex >= route.size()
        || alignment == null) return null;
    Waypoint from = waypointIndex == 0
        ? new Waypoint(0, route.get(0).y, 0) : route.get(waypointIndex - 1);
    Waypoint to = route.get(waypointIndex);
    double[] a = alignment.apply(from.x, from.z), b = alignment.apply(to.x, to.z);
    float dx = (float) (b[0] - a[0]), dz = (float) (b[1] - a[1]);
    float length = (float) Math.hypot(dx, dz);
    if (length < 2f) return null;
    dx /= length;
    dz /= length;
    float sideX = -dz, sideZ = dx;
    List<float[]> left = new ArrayList<>(), right = new ArrayList<>();
    for (float[] point : contacts) {
      float x = point[0] - cameraX, z = point[1] - cameraZ;
      float ahead = x * dx + z * dz;
      float side = x * sideX + z * sideZ;
      if (ahead < 0.6f || ahead > 5f || Math.abs(side) < 0.3f
          || Math.abs(side) > 3.5f) continue;
      (side < 0 ? left : right).add(new float[] {ahead, side});
    }
    float[] l = stableSide(left), r = stableSide(right);
    float targetSide, width, slope;
    boolean singleWall;
    if (l != null && r != null) {
      width = r[0] - l[0];
      if (width < 1.4f || width > 5.5f
          || Math.min(l[2], r[2]) - Math.max(l[1], r[1]) < 0.8f) return null;
      slope = (l[4] + r[4]) * 0.5f;
      if (Math.abs(l[4] - r[4]) > 0.2f) return null;
      singleWall = false;
    } else if (l != null && right.size() < 3) {
      width = Float.NaN;
      slope = l[4];
      singleWall = true;
    } else if (r != null && left.size() < 3) {
      width = Float.NaN;
      slope = r[4];
      singleWall = true;
    } else return null;
    if (Math.abs(slope) > Math.tan(Math.toRadians(20))) return null;
    float yaw = -(float) Math.atan(slope);
    float cos = (float) Math.cos(yaw), sin = (float) Math.sin(yaw);
    float rotatedSideX = cos * sideX + sin * sideZ;
    float rotatedSideZ = -sin * sideX + cos * sideZ;
    if (singleWall) {
      float[] wall = l != null ? l : r;
      float wallSide = wall[3] * cos;
      if (Math.signum(wallSide) != Math.signum(wall[0])) return null;
      targetSide = wallSide + (l != null ? SINGLE_WALL_DISTANCE_METERS
          : -SINGLE_WALL_DISTANCE_METERS);
    } else {
      targetSide = (l[3] + r[3]) * cos * 0.5f;
      width = (r[3] - l[3]) * cos;
    }
    float routeSide = (float) ((a[0] - cameraX) * sideX + (a[1] - cameraZ) * sideZ);
    float correction = targetSide - routeSide;
    if (Math.abs(correction) > (singleWall ? 2f : 1.2f)) return null;
    return new Observation(correction * rotatedSideX, correction * rotatedSideZ,
        width, timestamp, singleWall, yaw, cameraX, cameraZ);
  }

  private static float[] stableSide(List<float[]> points) {
    if (points.size() < 3) return null;
    List<Float> sides = new ArrayList<>();
    for (float[] point : points) sides.add(point[1]);
    Collections.sort(sides);
    float median = sides.get(sides.size() / 2);
    int count = 0;
    float minAhead = Float.POSITIVE_INFINITY, maxAhead = Float.NEGATIVE_INFINITY;
    for (float[] point : points) {
      if (Math.abs(point[1] - median) > 0.3f) continue;
      count++;
      minAhead = Math.min(minAhead, point[0]);
      maxAhead = Math.max(maxAhead, point[0]);
    }
    if (count < 3 || count * 2 < points.size() || maxAhead - minAhead < 1f)
      return null;
    float meanAhead = 0, meanSide = 0;
    for (float[] point : points) if (Math.abs(point[1] - median) <= 0.3f) {
      meanAhead += point[0]; meanSide += point[1];
    }
    meanAhead /= count; meanSide /= count;
    float covariance = 0, variance = 0;
    for (float[] point : points) if (Math.abs(point[1] - median) <= 0.3f) {
      float ahead = point[0] - meanAhead;
      covariance += ahead * (point[1] - meanSide);
      variance += ahead * ahead;
    }
    if (variance < 0.05f) return null;
    float slope = covariance / variance;
    return new float[] {median, minAhead, maxAhead, meanSide - slope * meanAhead, slope};
  }
}
