package com.example.arnavigation.ui.map;

import android.content.Context;
import com.google.android.libraries.places.api.Places;
import java.util.Objects;

/** Debounced Places autocomplete and detail-selection coordinator. */
public final class PlaceSearchController {
    static final long DEFAULT_DEBOUNCE_MILLIS = 300L;
    private final PlaceSearchGateway gateway;
    private final SearchScheduler scheduler;
    private final long debounceMillis;
    private SearchScheduler.ScheduledTask pendingDebounce;
    private long generation;
    private boolean sessionActive;

    public PlaceSearchController(Context context) {
        this(
                new CampusBuildingGateway(new GooglePlacesGateway(Places.createClient(
                        Objects.requireNonNull(context, "context must not be null")
                                .getApplicationContext()))),
                new AndroidSearchScheduler(),
                DEFAULT_DEBOUNCE_MILLIS);
    }

    PlaceSearchController(
            PlaceSearchGateway gateway, SearchScheduler scheduler, long debounceMillis) {
        this.gateway = Objects.requireNonNull(gateway, "gateway must not be null");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler must not be null");
        if (debounceMillis < 0L) throw new IllegalArgumentException("debounceMillis must not be negative");
        this.debounceMillis = debounceMillis;
    }

    public void onQueryChanged(String query, PlaceSearchCallback callback) {
        Objects.requireNonNull(callback, "callback must not be null");
        cancelDebounce();
        gateway.cancelPending();
        long request = ++generation;
        String normalized = query == null ? "" : query.trim();
        if (normalized.isEmpty()) {
            endSession();
            callback.onSuggestions(gateway.initialSuggestions());
            return;
        }
        if (!sessionActive) {
            gateway.startSession();
            sessionActive = true;
        }
        pendingDebounce = scheduler.schedule(debounceMillis, () -> {
            pendingDebounce = null;
            gateway.findPredictions(normalized, new PlaceSearchGateway.SuggestionsCallback() {
                @Override
                public void onSuccess(java.util.List<PlaceSuggestion> suggestions) {
                    if (request == generation) callback.onSuggestions(suggestions);
                }

                @Override
                public void onFailure(PlaceSearchError error) {
                    if (request == generation) callback.onError(error);
                }
            });
        });
    }

    public void selectSuggestion(
            PlaceSuggestion suggestion, PlaceSelectionCallback callback) {
        Objects.requireNonNull(suggestion, "suggestion must not be null");
        Objects.requireNonNull(callback, "callback must not be null");
        cancelDebounce();
        long request = ++generation;
        gateway.fetchDestination(
                suggestion.getPlaceId(), new PlaceSearchGateway.DestinationCallback() {
                    @Override
                    public void onSuccess(com.example.arnavigation.model.Destination destination) {
                        if (request != generation) return;
                        endSession();
                        callback.onDestination(destination);
                    }

                    @Override
                    public void onFailure(PlaceSearchError error) {
                        if (request != generation) return;
                        endSession();
                        callback.onError(error);
                    }
                });
    }

    public void close() {
        generation++;
        cancelDebounce();
        endSession();
    }

    private void endSession() {
        if (sessionActive) gateway.endSession();
        sessionActive = false;
    }

    private void cancelDebounce() {
        if (pendingDebounce != null) {
            pendingDebounce.cancel();
            pendingDebounce = null;
        }
    }
}
