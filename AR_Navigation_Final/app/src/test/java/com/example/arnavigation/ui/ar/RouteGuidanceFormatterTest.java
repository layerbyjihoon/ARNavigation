package com.example.arnavigation.ui.ar;
import com.example.arnavigation.model.*;
import com.example.arnavigation.navigation.*;
import com.example.arnavigation.location.LocationStatus;
import com.example.arnavigation.heading.HeadingStatus;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public final class RouteGuidanceFormatterTest {
    private static final double DEG=180/(Math.PI*6371000);
    @Test public void looksPastShapeVerticesAndMeasuresAlongBendsToApiTurn() {
        List<Waypoint> points=Arrays.asList(point("shape0",0,0,null,ManeuverType.UNKNOWN),
            point("bend",0,75,null,ManeuverType.UNKNOWN),
            point("left",100,75,"좌회전 후 120m 이동",ManeuverType.TURN_LEFT),
            point("end",100,200,null,ManeuverType.ARRIVE));
        var guidance=new RouteGuidanceFormatter().format(state(points,0,25,LocationStatus.AVAILABLE));
        assertEquals("200m 앞에서 좌회전하세요",guidance.text);
        assertEquals(200,guidance.distanceMeters,.01);
    }
    @Test public void alreadyPassedTurnIsNotShownAgain() {
        List<Waypoint> points=Arrays.asList(point("old-left",0,0,"좌회전",ManeuverType.TURN_LEFT),
            point("shape",0,20,null,ManeuverType.UNKNOWN),
            point("right",100,20,"우회전",ManeuverType.TURN_RIGHT));
        assertEquals("120m 앞에서 우회전하세요",new RouteGuidanceFormatter()
            .format(state(points,1,18,LocationStatus.AVAILABLE)).text);
    }
    @Test public void apiCrosswalkDescriptionIsPreservedAsPedestrianAction() {
        List<Waypoint> points=Collections.singletonList(point("cross",0,0,"횡단보도를 이용하여 이동",ManeuverType.STRAIGHT));
        assertEquals("50m 앞에서 횡단보도를 건너세요",new RouteGuidanceFormatter()
            .format(state(points,0,50,LocationStatus.AVAILABLE)).text);
    }
    @Test public void unknownGpsDistanceDoesNotInventRemainingMeters() {
        List<Waypoint> points=Collections.singletonList(point("left",0,0,"좌회전",ManeuverType.TURN_LEFT));
        var result=new RouteGuidanceFormatter().format(state(points,0,200,LocationStatus.LOW_ACCURACY));
        assertEquals("좌회전하세요",result.text); assertNull(result.distanceMeters);
    }
    @Test public void customApiDescriptionIsUsedAndGeometryAloneCannotInventTurn() {
        List<Waypoint> custom=Collections.singletonList(point("custom",0,0,"임시 통행로를 이용하세요",ManeuverType.UNKNOWN));
        assertTrue(new RouteGuidanceFormatter().format(state(custom,0,5,LocationStatus.AVAILABLE)).text.contains("임시 통행로"));
        List<Waypoint> geometry=Collections.singletonList(point("shape",0,0,null,ManeuverType.UNKNOWN));
        assertEquals("경로를 따라 이동하세요",new RouteGuidanceFormatter().format(state(geometry,0,5,LocationStatus.AVAILABLE)).text);
    }
    private static Waypoint point(String id,double north,double east,String instruction,ManeuverType maneuver) {
        return new Waypoint(id,new GeoPoint(north*DEG,east*DEG),instruction,maneuver,0);
    }
    private static NavigationUiState state(List<Waypoint> points,int active,double distance,LocationStatus status) {
        List<GeoPoint> path=new ArrayList<>();for(Waypoint point:points) path.add(point.getPoint());
        OutdoorRoute route=new OutdoorRoute(path,points,500,300,Collections.emptyList());
        return new NavigationUiState(NavigationStage.NAVIGATING,
            new Destination("d","문화관",path.get(path.size()-1),DestinationSource.MAP_TAP,null),route,points.get(active),active,
            null,status,null,HeadingStatus.LOW_ACCURACY,distance,null,false,false,null,true,false);
    }
}
