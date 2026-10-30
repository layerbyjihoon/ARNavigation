package com.google.ar.core.examples.java.helloar;

import static org.junit.Assert.*;
import com.google.ar.core.examples.java.helloar.coordinates.LocalMapCoordinateTransformer;
import com.google.ar.core.examples.java.helloar.map.*;
import java.io.InputStream;
import org.junit.Test;

public final class MapOrientationTest {
  @Test public void counterClockwise85Point5MeansClockwiseBearing274Point5() {
    assertEquals(274.5, MapOrientation.mapBearing(), 1e-9);
    assertEquals(MapAlignment.yawForBearing(0, 274.5), MapOrientation.yawFromNorth(0), 1e-9);
    double yaw = MapOrientation.yawFromNorth(0);
    // Looking down from above: graph up/north rotates toward west, not east.
    assertEquals(-0.9969173337, -Math.sin(yaw), 1e-9);
    assertEquals(-0.0784590957, -Math.cos(yaw), 1e-9);
  }

  @Test public void arbitraryArSessionNorthHasSameFixedBuildingOffset() {
    for (double north : new double[] {0,Math.PI/2,Math.PI,-2.9,2.8})
      assertEquals(Math.toRadians(85.5), MapAlignment.wrap(MapOrientation.yawFromNorth(north)-north), 1e-9);
    assertTrue(MapOrientation.usesSensor("maps/munhwagwan.indoor.json"));
    assertFalse(MapOrientation.usesSensor("maps/test_building.indoor.json"));
    assertFalse(MapOrientation.usesSensor("demo"));
  }

  @Test public void nodeGeometryIsNotPreRotatedSoFixedOffsetIsAppliedOnce() throws Exception {
    IndoorMap map;
    try (InputStream input = getClass().getClassLoader().getResourceAsStream("munhwagwan.indoor.json")) {
      map = new IndoorMapRepository().loadFromInputStream(input).map;
    }
    LocalMapCoordinateTransformer transform = new LocalMapCoordinateTransformer(map.coordinateSystem,
        new MapPosition(0,0,0),new MapPosition(0,0,0),0);
    MapPosition forward = transform.mapToAr(new MapPosition(0,0,1));
    assertEquals(0,forward.x,0); assertEquals(-1,forward.z,0);
    double yaw = MapOrientation.yawFromNorth(0);
    double x = Math.cos(yaw)*forward.x+Math.sin(yaw)*forward.z;
    double z = -Math.sin(yaw)*forward.x+Math.cos(yaw)*forward.z;
    assertEquals(Math.toRadians(85.5),Math.atan2(-x,-z),1e-9);
  }

  @Test public void exitBearingsAreDerivedFromSensorOrientedMapRatherThanCamera() {
    assertEquals(274.5,MapOrientation.entryBearing(0),1e-9);
    assertEquals(94.5,MapOrientation.entryBearing(180),1e-9);
    assertEquals(4.5,MapOrientation.entryBearing(-90),1e-9);
  }

  @Test public void legacyDoorOrientationIsNotAcceptedAsTheSensorBuildingHeading() {
    ArRecordingStore.Entry entry = new ArRecordingStore.Entry();
    assertFalse(MapOrientation.matchesRecording(entry));
    entry.mapOrientationSource = MapOrientation.SENSOR_SOURCE;
    entry.mapCounterClockwiseDegrees = 85.5;
    assertTrue(MapOrientation.matchesRecording(entry));
    entry.mapCounterClockwiseDegrees = 171.0;
    assertFalse(MapOrientation.matchesRecording(entry));
  }
}
