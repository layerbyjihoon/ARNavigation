package com.example.arnavigation.ui.map;

import com.example.arnavigation.config.AppConfig;
import com.example.arnavigation.model.Destination;
import java.util.*;

/** Recommendations from SSU's official campus map; Places resolves coordinates only on selection.
 * Source: https://ssu.ac.kr/학교소개/캠퍼스안내/캠퍼스맵/ (2026-11-25).
 */
final class CampusBuildingGateway implements PlaceSearchGateway {
    private static final String PREFIX = "ssu-building:";
    private static final String[] NAMES = {
        "베어드홀", "숭덕경상관", "문화관", "안익태기념관", "형남공학관", "교육관", "백마관",
        "한경직기념관", "신양관", "벤처중소기업센터", "진리관", "조만식기념관", "한국기독교박물관",
        "중앙도서관", "연구관", "창신관", "글로벌브레인홀", "레지던스홀", "전산관", "미래관",
        "정보과학관", "웨스트민스터홀", "학생회관", "창의관"
    };
    private final PlaceSearchGateway places;
    private long generation;
    CampusBuildingGateway(PlaceSearchGateway places) { this.places = Objects.requireNonNull(places); }

    @Override public List<PlaceSuggestion> initialSuggestions() { return buildings(""); }
    @Override public void startSession() { places.startSession(); }
    @Override public void cancelPending() { generation++; places.cancelPending(); }
    @Override public void endSession() { generation++; places.endSession(); }
    @Override public void findPredictions(String query, SuggestionsCallback callback) {
        callback.onSuccess(buildings(query));
    }
    static List<PlaceSuggestion> buildings(String query) {
        String needle = normalized(query);
        List<PlaceSuggestion> result = new ArrayList<>();
        for (String name : NAMES) {
            if (normalized(name).contains(needle))
                result.add(new PlaceSuggestion(PREFIX + name, name, "숭실대학교 · 캠퍼스 건물"));
        }
        return Collections.unmodifiableList(result);
    }
    private static String normalized(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("\\s+", "")
            .replace("숭실대학교", "").replace("숭실대", "").replace("서울캠퍼스", "")
            .replace("globalbrainhall", "글로벌브레인홀").replace("residencehall", "레지던스홀");
    }
    @Override public void fetchDestination(String id, DestinationCallback callback) {
        String name = null;
        for (String candidate : NAMES) if ((PREFIX + candidate).equals(id)) { name = candidate; break; }
        if (name == null) { callback.onFailure(PlaceSearchError.INVALID_RESPONSE); return; }
        final String building = name;
        final long request = ++generation;
        places.cancelPending();
        if ("문화관".equals(building)) {
            callback.onSuccess(com.example.arnavigation.entrance.CultureHallEntrance.destination());
            return;
        }
        places.findPredictions("숭실대학교 " + building, new SuggestionsCallback() {
            @Override public void onSuccess(List<PlaceSuggestion> values) {
                if (request != generation) return;
                // Do not silently navigate to the university centroid, an ATM, a cafe or another building.
                for (PlaceSuggestion value : values) {
                    if (!normalized(value.getPrimaryText()).equals(normalized(building))) continue;
                    places.fetchDestination(value.getPlaceId(), new DestinationCallback() {
                        @Override public void onSuccess(Destination destination) {
                            if (request != generation) return;
                            double lat = destination.getPoint().getLatitude();
                            double lng = destination.getPoint().getLongitude();
                            double north = (lat - AppConfig.CAMPUS_CENTER_LATITUDE) * 111320;
                            double east = (lng - AppConfig.CAMPUS_CENTER_LONGITUDE) * 111320
                                * Math.cos(Math.toRadians(AppConfig.CAMPUS_CENTER_LATITUDE));
                            if (Math.hypot(north, east) > 1000) {
                                callback.onFailure(PlaceSearchError.INVALID_RESPONSE); return;
                            }
                            callback.onSuccess(new Destination(destination.getId(), building,
                                destination.getPoint(), destination.getSource(), destination.getPlaceId()));
                        }
                        @Override public void onFailure(PlaceSearchError error) { if (request == generation) callback.onFailure(error); }
                    });
                    return;
                }
                callback.onFailure(PlaceSearchError.INVALID_RESPONSE);
            }
            @Override public void onFailure(PlaceSearchError error) { if (request == generation) callback.onFailure(error); }
        });
    }
}
