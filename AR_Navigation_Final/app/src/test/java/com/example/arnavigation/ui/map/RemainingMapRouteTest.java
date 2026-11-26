package com.example.arnavigation.ui.map;

import static org.junit.Assert.*;
import com.example.arnavigation.model.GeoPoint;
import java.util.*;
import org.junit.Test;

public final class RemainingMapRouteTest {
    @Test public void removesTravelledVerticesAndRetainsUpcomingBends() {
        GeoPoint a=new GeoPoint(37,127), b=new GeoPoint(37.001,127), c=new GeoPoint(37.001,127.001);
        GeoPoint location=new GeoPoint(37.0005,127.0001);
        List<GeoPoint> remaining=new RemainingMapRoute().update(Arrays.asList(a,b,c),location,b);
        assertEquals(location,remaining.get(0));
        assertEquals(127,remaining.get(1).getLongitude(),1e-9);
        assertEquals(location.getLatitude(),remaining.get(1).getLatitude(),1e-9);
        assertFalse(remaining.contains(a));
        assertEquals(b,remaining.get(2));
        assertEquals(c,remaining.get(3));
    }
    @Test public void nearbyFutureLoopDoesNotSkipUpcomingWaypoint() {
        GeoPoint a=new GeoPoint(37,127), b=new GeoPoint(37.001,127);
        GeoPoint c=new GeoPoint(37.001,127.001), d=new GeoPoint(37,127.001);
        GeoPoint location=new GeoPoint(37.0001,127.0009);
        List<GeoPoint> remaining=new RemainingMapRoute().update(Arrays.asList(a,b,c,d,a),location,b);
        assertTrue(remaining.contains(b));
        assertTrue(remaining.contains(c));
    }
    @Test public void advancingDoesNotRestoreEarlierSegmentsAndNewRouteHasFreshCursor() {
        List<GeoPoint> path=Arrays.asList(new GeoPoint(37,127),new GeoPoint(37.001,127),
                new GeoPoint(37.001,127.001));
        RemainingMapRoute clip=new RemainingMapRoute();
        clip.update(path,new GeoPoint(37.001,127.0005),path.get(2));
        assertEquals(1,clip.segment);
        clip.update(path,path.get(0),null);
        assertEquals(1,clip.segment);
        RemainingMapRoute fresh=new RemainingMapRoute();
        fresh.update(path,path.get(0),path.get(1));
        assertEquals(0,fresh.segment);
    }
}
