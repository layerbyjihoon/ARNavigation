package com.google.ar.core.examples.java.helloar.coordinates;

import static org.junit.Assert.*;
import com.google.ar.core.examples.java.helloar.map.*;
import org.junit.Test;
public final class MapCoordinateTransformerTest {
  private final IndoorCoordinateSystem coordinates = new IndoorCoordinateSystem(
      "LOCAL_METERS","meter","EAST","UP","NORTH",null);
  private static void point(MapPosition p,double x,double y,double z) {
    assertEquals(x,p.x,1e-8); assertEquals(y,p.y,1e-8); assertEquals(z,p.z,1e-8);
  }
  @Test public void mapOriginEastAndNorthUseCorrectArAxes() {
    MapCoordinateTransformer t=new LocalMapCoordinateTransformer(coordinates,
        new MapPosition(0,0,0),new MapPosition(5,1,7),0);
    point(t.mapToAr(new MapPosition(0,0,0)),5,1,7);
    point(t.mapToAr(new MapPosition(1,0,0)),6,1,7);
    point(t.mapToAr(new MapPosition(0,0,2)),5,1,5);
  }
  @Test public void yawAndScaleHaveKnownExpectedPositionsAndInverse() {
    MapCoordinateTransformer t=new LocalMapCoordinateTransformer(coordinates,
        new MapPosition(10,3,20),new MapPosition(5,1,7),90,2);
    point(t.mapToAr(new MapPosition(11,3,20)),5,1,5);
    point(t.mapToAr(new MapPosition(10,3,21)),3,1,7);
    point(t.arToMap(t.mapToAr(new MapPosition(12,4,23))),12,4,23);
  }
  @Test public void permutationsAndOppositeAxisDirectionsWork() {
    IndoorCoordinateSystem swapped=new IndoorCoordinateSystem("LOCAL_METERS","meter","NORTH","WEST","DOWN",null);
    MapCoordinateTransformer t=new LocalMapCoordinateTransformer(swapped,
        new MapPosition(0,0,0),new MapPosition(0,0,0),0);
    point(t.mapToAr(new MapPosition(1,2,3)),-2,-3,-1);
    point(t.arToMap(new MapPosition(-2,-3,-1)),1,2,3);
  }
  @Test public void arbitraryYawRoundTripsManyPositions() {
    MapCoordinateTransformer t=new LocalMapCoordinateTransformer(coordinates,
        new MapPosition(5,1,9),new MapPosition(-2,3,7),37.3,1.7);
    for(int i=-10;i<=10;i++) {
      MapPosition p=new MapPosition(i*0.71,i*0.12,-i*0.31);
      point(t.arToMap(t.mapToAr(p)),p.x,p.y,p.z);
    }
  }
  @Test public void invalidScaleAxisOrNonfinitePositionIsRejected() {
    for(double scale:new double[]{0,-1,Double.NaN,Double.POSITIVE_INFINITY}) {
      try { new LocalMapCoordinateTransformer(coordinates,new MapPosition(0,0,0),
          new MapPosition(0,0,0),0,scale); fail(); } catch(IllegalArgumentException expected) {}
    }
    try { new LocalMapCoordinateTransformer(
        new IndoorCoordinateSystem("LOCAL_METERS","meter","EAST","UP","EAST",null),
        new MapPosition(0,0,0),new MapPosition(0,0,0),0); fail(); }
    catch(IllegalArgumentException expected) {}
    try { new LocalMapCoordinateTransformer(coordinates,new MapPosition(0,0,0),
        new MapPosition(0,0,0),0).mapToAr(new MapPosition(Double.NaN,0,0)); fail(); }
    catch(IllegalArgumentException expected) {}
  }
}
