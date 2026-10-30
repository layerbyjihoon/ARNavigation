package com.google.ar.core.examples.java.helloar.map;

public final class IndoorCoordinateSystem {
  public final String type, unit;
  public final String xAxis, yAxis, zAxis;
  public final Georeference georeference;
  public IndoorCoordinateSystem(String type, String unit, String xAxis, String yAxis,
      String zAxis, Georeference georeference) {
    this.type = type; this.unit = unit; this.xAxis = xAxis; this.yAxis = yAxis;
    this.zAxis = zAxis; this.georeference = georeference;
  }
  /** Optional WGS84 origin; never used as an ARCore world pose or as a GPS measurement. */
  public static final class Georeference {
    public final double latitude, longitude;
    public final Double altitude, heading;
    public Georeference(double latitude, double longitude, Double altitude, Double heading) {
      this.latitude = latitude; this.longitude = longitude;
      this.altitude = altitude; this.heading = heading;
    }
  }
}
