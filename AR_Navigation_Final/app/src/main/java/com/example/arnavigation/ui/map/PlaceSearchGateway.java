package com.example.arnavigation.ui.map;

import com.example.arnavigation.model.Destination;
import java.util.List;

interface PlaceSearchGateway {
    interface SuggestionsCallback {
        void onSuccess(List<PlaceSuggestion> suggestions);
        void onFailure(PlaceSearchError error);
    }

    interface DestinationCallback {
        void onSuccess(Destination destination);
        void onFailure(PlaceSearchError error);
    }

    default List<PlaceSuggestion> initialSuggestions() { return java.util.Collections.emptyList(); }

    void startSession();
    void findPredictions(String query, SuggestionsCallback callback);
    void fetchDestination(String placeId, DestinationCallback callback);
    void cancelPending();
    void endSession();
}
