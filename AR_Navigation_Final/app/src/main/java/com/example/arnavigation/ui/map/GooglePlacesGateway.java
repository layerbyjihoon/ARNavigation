package com.example.arnavigation.ui.map;

import com.example.arnavigation.model.Destination;
import com.example.arnavigation.model.DestinationSource;
import com.example.arnavigation.model.GeoPoint;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.tasks.CancellationTokenSource;
import com.google.android.libraries.places.api.model.AutocompletePrediction;
import com.google.android.libraries.places.api.model.AutocompleteSessionToken;
import com.google.android.libraries.places.api.model.Place;
import com.google.android.libraries.places.api.model.RectangularBounds;
import com.google.android.libraries.places.api.net.FetchPlaceRequest;
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest;
import com.google.android.libraries.places.api.net.PlacesClient;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Places SDK (New) adapter scoped to one autocomplete billing session. */
final class GooglePlacesGateway implements PlaceSearchGateway {
    private static final RectangularBounds CAMPUS_BIAS = RectangularBounds.newInstance(
            new LatLng(37.47, 126.92), new LatLng(37.52, 126.99));
    private static final List<Place.Field> PLACE_FIELDS = Collections.unmodifiableList(Arrays.asList(
            Place.Field.ID, Place.Field.DISPLAY_NAME, Place.Field.LOCATION));

    private final PlacesClient placesClient;
    private AutocompleteSessionToken sessionToken;
    private CancellationTokenSource cancellationTokenSource;

    GooglePlacesGateway(PlacesClient placesClient) {
        this.placesClient = java.util.Objects.requireNonNull(
                placesClient, "placesClient must not be null");
    }

    @Override
    public void startSession() {
        if (sessionToken == null) sessionToken = AutocompleteSessionToken.newInstance();
    }

    @Override
    public void findPredictions(String query, SuggestionsCallback callback) {
        startSession();
        cancelPending();
        cancellationTokenSource = new CancellationTokenSource();
        FindAutocompletePredictionsRequest request =
                FindAutocompletePredictionsRequest.builder()
                        .setQuery(query)
                        .setCountries("KR")
                        .setRegionCode("KR")
                        .setLocationBias(CAMPUS_BIAS)
                        .setSessionToken(sessionToken)
                        .setCancellationToken(cancellationTokenSource.getToken())
                        .build();
        placesClient.findAutocompletePredictions(request)
                .addOnSuccessListener(response -> {
                    ArrayList<PlaceSuggestion> suggestions = new ArrayList<>();
                    for (AutocompletePrediction prediction : response.getAutocompletePredictions()) {
                        suggestions.add(new PlaceSuggestion(
                                prediction.getPlaceId(),
                                prediction.getPrimaryText(null).toString(),
                                prediction.getSecondaryText(null).toString()));
                    }
                    callback.onSuccess(Collections.unmodifiableList(suggestions));
                })
                .addOnFailureListener(error -> callback.onFailure(classify(error)));
    }

    @Override
    public void fetchDestination(String placeId, DestinationCallback callback) {
        startSession();
        cancelPending();
        cancellationTokenSource = new CancellationTokenSource();
        FetchPlaceRequest request = FetchPlaceRequest.builder(placeId, PLACE_FIELDS)
                .setRegionCode("KR")
                .setSessionToken(sessionToken)
                .setCancellationToken(cancellationTokenSource.getToken())
                .build();
        placesClient.fetchPlace(request)
                .addOnSuccessListener(response -> {
                    Place place = response.getPlace();
                    LatLng location = place.getLocation();
                    String id = place.getId();
                    String name = place.getDisplayName();
                    if (location == null || id == null || name == null || name.trim().isEmpty()) {
                        callback.onFailure(PlaceSearchError.INVALID_RESPONSE);
                        return;
                    }
                    callback.onSuccess(new Destination(
                            id,
                            name,
                            new GeoPoint(location.latitude, location.longitude),
                            DestinationSource.PLACES,
                            id));
                })
                .addOnFailureListener(error -> callback.onFailure(classify(error)));
    }

    @Override
    public void cancelPending() {
        if (cancellationTokenSource != null) {
            cancellationTokenSource.cancel();
            cancellationTokenSource = null;
        }
    }

    @Override
    public void endSession() {
        cancelPending();
        sessionToken = null;
    }

    private static PlaceSearchError classify(Exception error) {
        if (!(error instanceof ApiException)) return PlaceSearchError.NETWORK;
        int statusCode = ((ApiException) error).getStatusCode();
        if (statusCode == 7 || statusCode == 15) return PlaceSearchError.NETWORK;
        if (statusCode == 10 || statusCode == 16) return PlaceSearchError.AUTHENTICATION;
        if (statusCode == 8) return PlaceSearchError.QUOTA;
        return PlaceSearchError.UNKNOWN;
    }
}
