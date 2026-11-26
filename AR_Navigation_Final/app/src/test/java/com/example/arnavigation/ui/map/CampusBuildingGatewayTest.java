package com.example.arnavigation.ui.map;

import com.example.arnavigation.model.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public final class CampusBuildingGatewayTest {
    @Test public void blankQueryShowsOnlyOfficialBuildingsAndPartialQueryFiltersThem() {
        assertEquals(24, CampusBuildingGateway.buildings("").size());
        assertEquals(24, CampusBuildingGateway.buildings("숭실대학교").size());
        assertEquals("정보과학관", CampusBuildingGateway.buildings("정보").get(0).getPrimaryText());
        assertEquals("조만식기념관", CampusBuildingGateway.buildings("숭실대 조만식").get(0).getPrimaryText());
        assertEquals("글로벌브레인홀", CampusBuildingGateway.buildings("Global Brain Hall").get(0).getPrimaryText());
        assertTrue(CampusBuildingGateway.buildings("카페").isEmpty());
        assertTrue(CampusBuildingGateway.buildings("숭실대입구역").isEmpty());
        assertTrue(CampusBuildingGateway.buildings("대운동장").isEmpty());
    }
    @Test public void selectionSkipsCafeAndResolvesActualBuildingPlaceId() {
        FakePlaces places = new FakePlaces(); CampusBuildingGateway gateway = new CampusBuildingGateway(places);
        Result result = new Result(); gateway.fetchDestination("ssu-building:정보과학관", result);
        assertEquals("숭실대학교 정보과학관", places.query);
        places.predictions.onSuccess(Arrays.asList(new PlaceSuggestion("cafe", "정보과학관 카페", "숭실대학교"),
            new PlaceSuggestion("building", "숭실대학교 정보과학관", "서울특별시 동작구")));
        assertEquals("building",places.fetchedId);
        places.detail.onSuccess(destination(37.4963,126.9574));
        assertEquals("정보과학관", result.destination.getName()); assertNull(result.error);
    }
    @Test public void otherCampusAndUniversityCentroidAreRejected() {
        FakePlaces places = new FakePlaces(); CampusBuildingGateway gateway = new CampusBuildingGateway(places);
        Result result = new Result(); gateway.fetchDestination("ssu-building:정보과학관", result);
        places.predictions.onSuccess(Collections.singletonList(new PlaceSuggestion("uni", "숭실대학교", "서울")));
        assertEquals(PlaceSearchError.INVALID_RESPONSE,result.error); assertNull(places.fetchedId);
        result = new Result(); gateway.fetchDestination("ssu-building:정보과학관", result);
        places.predictions.onSuccess(Collections.singletonList(new PlaceSuggestion("building", "정보과학관", "서울")));
        places.detail.onSuccess(destination(37.6,127.1));
        assertEquals(PlaceSearchError.INVALID_RESPONSE,result.error); assertNull(result.destination);
    }
    @Test public void canceledSelectionCannotStartAnotherDetailRequest() {
        FakePlaces places = new FakePlaces(); CampusBuildingGateway gateway = new CampusBuildingGateway(places);
        Result result = new Result(); gateway.fetchDestination("ssu-building:정보과학관", result);
        var stale = places.predictions; gateway.cancelPending();
        stale.onSuccess(Collections.singletonList(new PlaceSuggestion("building", "정보과학관", "숭실대학교")));
        assertNull(places.fetchedId); assertNull(result.destination); assertNull(result.error);
    }
    @Test public void cultureUsesUserConfirmedFrontDoorInsteadOfPlacesRepresentativePoint() {
        FakePlaces places = new FakePlaces(); CampusBuildingGateway gateway = new CampusBuildingGateway(places);
        Result result = new Result(); gateway.fetchDestination("ssu-building:문화관", result);
        if (result.destination == null) {
            places.predictions.onSuccess(Collections.singletonList(
                new PlaceSuggestion("building", "문화관", "숭실대학교")));
            places.detail.onSuccess(destination(37.4963,126.9574));
        }
        assertNotNull(result.destination);
        assertEquals(new GeoPoint(37.49637,126.95442), result.destination.getPoint());
    }
    private static Destination destination(double lat,double lng) {
        return new Destination("building","문화관",new GeoPoint(lat,lng),DestinationSource.PLACES,"building");
    }
    private static final class Result implements PlaceSearchGateway.DestinationCallback {
        Destination destination; PlaceSearchError error;
        public void onSuccess(Destination value) { destination = value; }
        public void onFailure(PlaceSearchError value) { error = value; }
    }
    private static final class FakePlaces implements PlaceSearchGateway {
        String query,fetchedId; SuggestionsCallback predictions; DestinationCallback detail;
        public void startSession() {} public void cancelPending() {} public void endSession() {}
        public void findPredictions(String value,SuggestionsCallback callback) {query=value;predictions=callback;}
        public void fetchDestination(String value,DestinationCallback callback) {fetchedId=value;detail=callback;}
    }
}
