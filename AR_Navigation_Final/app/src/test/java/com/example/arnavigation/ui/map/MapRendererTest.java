package com.example.arnavigation.ui.map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import com.example.arnavigation.heading.HeadingStatus;
import com.example.arnavigation.location.LocationStatus;
import com.example.arnavigation.model.Destination;
import com.example.arnavigation.model.DestinationSource;
import com.example.arnavigation.model.GeoPoint;
import com.example.arnavigation.model.ManeuverType;
import com.example.arnavigation.model.OutdoorRoute;
import com.example.arnavigation.model.Waypoint;
import com.example.arnavigation.navigation.NavigationStage;
import com.example.arnavigation.navigation.NavigationUiState;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

public final class MapRendererTest {
    @Test
    public void appliesStateThatArrivesBeforeMapAndDoesNotRedrawUnchangedCandidate() {
        MapRenderer renderer = new MapRenderer();
        Destination destination = destination("a", 37.0, 127.0);
        renderer.render(state(NavigationStage.DESTINATION_SELECTED, destination, null, null));
        FakeSurface surface = new FakeSurface();

        renderer.attachSurface(surface);
        renderer.render(state(NavigationStage.DESTINATION_SELECTED, destination, null, null));

        assertEquals(destination.getPoint(), surface.cameraPoint);
        assertEquals(1, surface.destinationShows);
        assertFalse(surface.lastConfirmed);
        assertEquals(1, surface.moves);
    }

    @Test
    public void routeAndDestinationRenderWithoutWaypointMarkers() {
        MapRenderer renderer = new MapRenderer();
        FakeSurface surface = new FakeSurface();
        renderer.attachSurface(surface);
        Destination first = destination("a", 37.0, 127.0);
        Waypoint waypoint = new Waypoint(
                "w", new GeoPoint(37.001, 127.001), null, ManeuverType.TURN_LEFT, 0);
        OutdoorRoute route = new OutdoorRoute(
                Arrays.asList(first.getPoint(), waypoint.getPoint()),
                Collections.singletonList(waypoint), 100.0, 60L, Collections.emptyList());

        renderer.render(state(NavigationStage.ROUTE_READY, first, route, waypoint));
        renderer.render(state(
                NavigationStage.DESTINATION_SELECTED,
                destination("b", 37.01, 127.01), null, null));

        assertEquals(1, surface.routeShows);
        assertEquals(0, surface.waypointShows);
        assertEquals(2, surface.destinationShows);
        assertEquals(1, surface.routeFits);
        assertFalse(surface.lastConfirmed);
    }

    @Test
    public void locationLayerRemainsDisabledWithoutPermission() {
        MapRenderer renderer = new MapRenderer();
        FakeSurface surface = new FakeSurface();
        renderer.setLocationPermissionGranted(false);
        renderer.attachSurface(surface);

        assertFalse(surface.locationEnabled);
    }

    @Test
    public void movingLocationTrimsSameRouteWithoutRepeatedFitAndArrivalClearsLine() {
        MapRenderer renderer = new MapRenderer();
        FakeSurface surface = new FakeSurface();
        renderer.attachSurface(surface);
        GeoPoint start = new GeoPoint(37,127), bend = new GeoPoint(37.001,127);
        GeoPoint end = new GeoPoint(37.001,127.001);
        Waypoint waypoint = new Waypoint("bend",bend,null,ManeuverType.TURN_RIGHT,0);
        OutdoorRoute route = new OutdoorRoute(Arrays.asList(start,bend,end),
                Collections.singletonList(waypoint),200,120,Collections.emptyList());
        Destination destination = destination("end",end.getLatitude(),end.getLongitude());
        GeoPoint first = new GeoPoint(37.0003,127), second = new GeoPoint(37.0006,127);
        renderer.render(moving(NavigationStage.NAVIGATING,destination,route,waypoint,first));
        assertEquals(first,surface.points.get(0));
        assertEquals(end,surface.points.get(surface.points.size()-1));
        renderer.render(moving(NavigationStage.NAVIGATING,destination,route,waypoint,second));
        assertEquals(second,surface.points.get(0));
        assertFalse(surface.points.contains(start));
        assertEquals(2,surface.routeShows);
        assertEquals(1,surface.routeFits);
        renderer.render(moving(NavigationStage.NAVIGATING,destination,route,waypoint,second));
        assertEquals("Identical GPS/heading state doesn't rebuild map line",2,surface.routeShows);
        renderer.render(moving(NavigationStage.ARRIVED,destination,route,null,end));
        assertEquals(0,surface.points.size());
        assertEquals(1,surface.routeFits);
    }

    @Test
    public void reattachingMapRestoresRemainingRouteOnNewSurface() {
        MapRenderer renderer = new MapRenderer();
        FakeSurface first = new FakeSurface();
        renderer.attachSurface(first);
        Destination dest = destination("end",37.001,127);
        Waypoint waypoint = new Waypoint("end",dest.getPoint(),null,ManeuverType.ARRIVE,0);
        OutdoorRoute route = new OutdoorRoute(Arrays.asList(new GeoPoint(37,127),dest.getPoint()),
                Collections.singletonList(waypoint),100,60,Collections.emptyList());
        renderer.render(moving(NavigationStage.NAVIGATING,dest,route,waypoint,new GeoPoint(37.0005,127)));
        renderer.detach();
        FakeSurface second = new FakeSurface();
        renderer.attachSurface(second);
        assertEquals(first.points,second.points);
        assertEquals(1,second.routeShows);
    }

    @Test public void mapEntryWaitsForLocationThenCentersOnceUntilRequestedAgain() {
        MapRenderer renderer=new MapRenderer(); FakeSurface surface=new FakeSurface();
        renderer.setLocationPermissionGranted(true);
        renderer.centerOnCurrentLocation(); renderer.attachSurface(surface);
        renderer.render(state(NavigationStage.NO_DESTINATION,null,null,null));
        GeoPoint first=new GeoPoint(37.496,126.954),second=new GeoPoint(37.4961,126.954);
        renderer.render(moving(NavigationStage.NO_DESTINATION,null,null,null,first));
        assertEquals(first,surface.cameraPoint);
        int moves=surface.moves;
        renderer.render(moving(NavigationStage.NO_DESTINATION,null,null,null,second));
        assertEquals("Normal GPS updates preserve user map panning",moves,surface.moves);
        renderer.centerOnCurrentLocation();
        assertEquals(second,surface.cameraPoint); assertEquals(moves+1,surface.moves);
    }

    @Test public void pendingMapEntryCenterTakesPriorityOverInitialRouteFit() {
        MapRenderer renderer=new MapRenderer(); FakeSurface surface=new FakeSurface();
        GeoPoint current=new GeoPoint(37.496,126.954);
        Destination destination=destination("d",37.499,126.96);
        OutdoorRoute path=new OutdoorRoute(Arrays.asList(current,destination.getPoint()),
            Collections.emptyList(),500,300,Collections.emptyList());
        renderer.setLocationPermissionGranted(true);
        renderer.render(moving(NavigationStage.ROUTE_READY,destination,path,null,current));
        renderer.centerOnCurrentLocation(); renderer.attachSurface(surface);
        assertEquals(current,surface.cameraPoint); assertEquals("move",surface.lastCameraAction);
    }

    @Test public void destinationConfirmationFocusSurvivesMapEntryAndGpsUpdates() {
        MapRenderer renderer=new MapRenderer(); FakeSurface surface=new FakeSurface();
        GeoPoint current=new GeoPoint(37.496,126.954);
        Destination building=destination("building",37.499,126.96);
        renderer.setLocationPermissionGranted(true); renderer.attachSurface(surface);
        renderer.centerOnCurrentLocation();
        renderer.render(moving(NavigationStage.DESTINATION_SELECTED,building,null,null,current));
        renderer.onMapOpened();
        assertEquals(building.getPoint(),surface.cameraPoint);
        int moves=surface.moves;
        renderer.render(moving(NavigationStage.DESTINATION_SELECTED,building,null,null,
            new GeoPoint(37.4961,126.954)));
        assertEquals(building.getPoint(),surface.cameraPoint); assertEquals(moves,surface.moves);
        Destination other=destination("other",37.495,126.959);
        renderer.render(moving(NavigationStage.DESTINATION_SELECTED,other,null,null,current));
        assertEquals(other.getPoint(),surface.cameraPoint);
    }

    @Test public void confirmingDestinationCentersCurrentLocationAndRouteResponseKeepsThatFocus() {
        MapRenderer renderer=new MapRenderer(); FakeSurface surface=new FakeSurface();
        GeoPoint current=new GeoPoint(37.496,126.954);
        Destination building=destination("building",37.499,126.96);
        renderer.setLocationPermissionGranted(true); renderer.attachSurface(surface);
        renderer.render(moving(NavigationStage.DESTINATION_SELECTED,building,null,null,current));
        assertEquals(building.getPoint(),surface.cameraPoint);
        renderer.onDestinationConfirmed();
        assertEquals(current,surface.cameraPoint);
        renderer.render(moving(NavigationStage.ROUTE_LOADING,building,null,null,current));
        OutdoorRoute path=new OutdoorRoute(Arrays.asList(current,building.getPoint()),
            Collections.emptyList(),500,300,Collections.emptyList());
        renderer.render(moving(NavigationStage.ROUTE_READY,building,path,null,current));
        assertEquals(current,surface.cameraPoint); assertEquals(0,surface.routeFits);
        int moves=surface.moves;
        renderer.render(moving(NavigationStage.ROUTE_READY,building,path,null,
            new GeoPoint(37.4961,126.954)));
        assertEquals(moves,surface.moves);
    }

    private static NavigationUiState moving(NavigationStage stage, Destination destination,
            OutdoorRoute route, Waypoint waypoint, GeoPoint location) {
        return new NavigationUiState(stage,destination,route,waypoint,0,
                new com.example.arnavigation.model.LocationSample(location,2,null,0,1),
                LocationStatus.AVAILABLE,null,HeadingStatus.UNAVAILABLE,
                null,null,false,false,null,false,false);
    }

    private static Destination destination(String id, double latitude, double longitude) {
        return new Destination(
                id, id, new GeoPoint(latitude, longitude), DestinationSource.MAP_TAP, null);
    }

    private static NavigationUiState state(
            NavigationStage stage,
            Destination destination,
            OutdoorRoute route,
            Waypoint waypoint) {
        return new NavigationUiState(
                stage, destination, route, waypoint, 0,
                null, LocationStatus.UNAVAILABLE,
                null, HeadingStatus.UNAVAILABLE,
                null, null, false, false, null, false, false);
    }

    private static final class FakeSurface implements MapSurface {
        boolean locationEnabled;
        boolean lastConfirmed;
        int destinationShows;
        int routeShows;
        int waypointShows;
        int moves;
        int routeFits;
        GeoPoint cameraPoint;
        String lastCameraAction;
        List<GeoPoint> points = Collections.emptyList();
        @Override public void setMyLocationEnabled(boolean enabled) { locationEnabled = enabled; }
        @Override public void showDestination(GeoPoint point, String title, boolean confirmed) {
            destinationShows++;
            lastConfirmed = confirmed;
        }
        @Override public void clearDestination() {}
        @Override public void showRoute(List<GeoPoint> points) { routeShows++; this.points = points; }
        @Override public void clearRoute() { points = Collections.emptyList(); }
        @Override public void showActiveWaypoint(GeoPoint point) { waypointShows++; }
        @Override public void clearActiveWaypoint() {}
        @Override public void moveTo(GeoPoint point, float zoom) { moves++; cameraPoint=point; lastCameraAction="move"; }
        @Override public void fitRoute(List<GeoPoint> points) { routeFits++; lastCameraAction="fit"; }
    }
}
