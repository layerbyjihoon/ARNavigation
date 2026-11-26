package com.example.arnavigation.ui.map;

import java.util.List;

public interface PlaceSearchCallback {
    void onSuggestions(List<PlaceSuggestion> suggestions);

    void onError(PlaceSearchError error);
}
