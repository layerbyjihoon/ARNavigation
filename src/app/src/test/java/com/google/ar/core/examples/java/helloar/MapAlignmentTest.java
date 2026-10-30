package com.google.ar.core.examples.java.helloar;

import static org.junit.Assert.*;
import org.junit.Test;

public final class MapAlignmentTest {
  @Test public void northIsIndependentOfYawPitchRollAndSessionOrientation() {
    for (double sessionYaw : new double[] {0, 0.7, -2.8})
      for (double yaw : new double[] {0, Math.PI/2, Math.PI, -Math.PI/2})
        for (double pitch : new double[] {0, 0.5, Math.PI/2, -Math.PI/2})
          for (double roll : new double[] {0, 0.6, Math.PI/2, Math.PI}) {
            double[][] rotation = multiply(rotate(2, yaw),
                multiply(rotate(0, pitch), rotate(1, roll)));
            float[] sensor = new float[9], ar = new float[16];
            double[][] enuToAr = {{1,0,0}, {0,0,1}, {0,-1,0}};
            double[][] world = multiply(rotate(1, sessionYaw), multiply(enuToAr, rotation));
            for (int r = 0; r < 3; r++) for (int c = 0; c < 3; c++) {
              sensor[r*3+c] = (float) rotation[r][c];
              ar[c*4+r] = (float) world[r][c];
            }
            double north = MapAlignment.northYaw(sensor, ar);
            assertEquals(0, MapAlignment.wrap(north - sessionYaw), 1e-6);
            assertEquals(0, MapAlignment.wrap(MapOrientation.yawFromNorth(north)
                - sessionYaw - Math.toRadians(85.5)), 1e-6);
            // A building bearing of 90 degrees always points east, for every phone attitude.
            assertEquals(0, MapAlignment.wrap(
                MapAlignment.yawForBearing(north, 90) - (sessionYaw - Math.PI/2)), 1e-6);
          }
  }

  @Test public void manualForwardIgnoresPitchAndRejectsVerticalCamera() {
    for (double yaw : new double[] {0, Math.PI/2, Math.PI}) {
      double[][] rotation = multiply(rotate(1, yaw), rotate(0, 0.6));
      float[] camera = new float[16];
      for (int r=0;r<3;r++) for(int c=0;c<3;c++) camera[c*4+r]=(float)rotation[r][c];
      assertEquals(0, MapAlignment.wrap(MapAlignment.forwardYaw(camera)-yaw), 1e-6);
    }
    float[] vertical = new float[16]; vertical[9] = 1;
    try { MapAlignment.forwardYaw(vertical); fail(); }
    catch (IllegalArgumentException expected) { }
  }

  @Test public void bearingsWrapWithoutChangingDirection() {
    assertEquals(MapAlignment.yawForBearing(0, 0), MapAlignment.yawForBearing(0, 360), 1e-6);
    assertEquals(-Math.PI/2, MapAlignment.yawForBearing(0, 90), 1e-6);
  }

  private static double[][] rotate(int axis, double angle) {
    double[][] r = {{1,0,0},{0,1,0},{0,0,1}};
    int a=(axis+1)%3, b=(axis+2)%3;
    r[a][a]=r[b][b]=Math.cos(angle); r[a][b]=-Math.sin(angle); r[b][a]=Math.sin(angle);
    return r;
  }
  private static double[][] multiply(double[][] a, double[][] b) {
    double[][] r = new double[3][3];
    for(int i=0;i<3;i++) for(int j=0;j<3;j++) for(int k=0;k<3;k++) r[i][j]+=a[i][k]*b[k][j];
    return r;
  }
}
