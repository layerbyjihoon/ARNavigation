package com.example.arnavigation.ui.map;

import com.example.arnavigation.model.Destination;

public interface PlaceSelectionCallback {
    void onDestination(Destination destination);

    void onError(PlaceSearchError error);
}
