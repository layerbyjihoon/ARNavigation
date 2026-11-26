package com.google.ar.core.examples.java.helloar.coordinates;

import com.google.ar.core.examples.java.helloar.map.*;
import java.util.HashSet;
import java.util.Set;

/**
 * Axis normalization, uniform scale, right-hand yaw about AR +Y, and translation.
 * EAST/UP/NORTH maps to AR +X/+Y/-Z. Yaw 0 faces map north; yaw is in degrees.
 */
public final class LocalMapCoordinateTransformer implements MapCoordinateTransformer {
  private final MapPosition mapOrigin, arOrigin;
  private final double scale, cos, sin;
  private final double[][] basis = new double[3][3];
  public LocalMapCoordinateTransformer(IndoorCoordinateSystem coordinates, MapPosition mapOrigin,
      MapPosition arOrigin, double yawDegrees) {
    this(coordinates, mapOrigin, arOrigin, yawDegrees, 1.0);
  }
  public LocalMapCoordinateTransformer(IndoorCoordinateSystem coordinates, MapPosition mapOrigin,
      MapPosition arOrigin, double yawDegrees, double scale) {
    if (coordinates == null || !"LOCAL_METERS".equals(coordinates.type)
        || !"meter".equals(coordinates.unit) || mapOrigin == null || !mapOrigin.isFinite()
        || arOrigin == null || !arOrigin.isFinite() || !Double.isFinite(yawDegrees)
        || !Double.isFinite(scale) || scale <= 0)
      throw new IllegalArgumentException("Invalid meter transform");
    this.mapOrigin = mapOrigin; this.arOrigin = arOrigin; this.scale = scale;
    cos = Math.cos(Math.toRadians(yawDegrees)); sin = Math.sin(Math.toRadians(yawDegrees));
    String[] axes = {coordinates.xAxis, coordinates.yAxis, coordinates.zAxis};
    Set<Integer> dimensions = new HashSet<>();
    for (int i = 0; i < 3; i++) {
      int dimension; double sign;
      switch (axes[i]) {
        case "RIGHT": case "EAST": dimension = 0; sign = 1; break;
        case "LEFT": case "WEST": dimension = 0; sign = -1; break;
        case "UP": dimension = 1; sign = 1; break;
        case "DOWN": dimension = 1; sign = -1; break;
        case "FORWARD": case "NORTH": dimension = 2; sign = -1; break;
        case "BACKWARD": case "SOUTH": dimension = 2; sign = 1; break;
        default: throw new IllegalArgumentException("Unsupported coordinate axis " + axes[i]);
      }
      if (!dimensions.add(dimension)) throw new IllegalArgumentException("Axes are not orthogonal");
      basis[dimension][i] = sign;
    }
  }
  @Override public MapPosition mapToAr(MapPosition position) {
    check(position);
    double[] local = {position.x - mapOrigin.x, position.y - mapOrigin.y, position.z - mapOrigin.z};
    double[] canonical = multiply(local, false);
    return finite(arOrigin.x + scale * (cos * canonical[0] + sin * canonical[2]),
        arOrigin.y + scale * canonical[1],
        arOrigin.z + scale * (-sin * canonical[0] + cos * canonical[2]));
  }
  @Override public MapPosition arToMap(MapPosition position) {
    check(position);
    double x = (position.x - arOrigin.x) / scale, y = (position.y - arOrigin.y) / scale,
        z = (position.z - arOrigin.z) / scale;
    double[] local = multiply(new double[] {cos*x - sin*z, y, sin*x + cos*z}, true);
    return finite(mapOrigin.x + local[0], mapOrigin.y + local[1], mapOrigin.z + local[2]);
  }
  private double[] multiply(double[] vector, boolean transpose) {
    double[] result = new double[3];
    for (int i = 0; i < 3; i++) for (int j = 0; j < 3; j++)
      result[i] += (transpose ? basis[j][i] : basis[i][j]) * vector[j];
    return result;
  }
  private static void check(MapPosition p) {
    if (p == null || !p.isFinite()) throw new IllegalArgumentException("Non-finite coordinate");
  }
  private static MapPosition finite(double x, double y, double z) {
    MapPosition result = new MapPosition(x,y,z); check(result); return result;
  }
}
