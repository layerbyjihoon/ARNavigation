package com.example.arnavigation.ui.map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.example.arnavigation.model.Destination;
import com.example.arnavigation.model.DestinationSource;
import com.example.arnavigation.model.GeoPoint;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

public final class PlaceSearchControllerTest {
    @Test
    public void debouncesQueriesAndIgnoresStaleResults() {
        FakeGateway gateway = new FakeGateway();
        FakeScheduler scheduler = new FakeScheduler();
        PlaceSearchController controller = new PlaceSearchController(gateway, scheduler, 300L);
        RecordingSearchCallback callback = new RecordingSearchCallback();

        controller.onQueryChanged("숭", callback);
        controller.onQueryChanged("숭실대", callback);
        scheduler.runAll();

        assertEquals(1, gateway.queries.size());
        assertEquals("숭실대", gateway.queries.get(0));
        gateway.suggestionCallbacks.get(0).onSuccess(Collections.singletonList(
                new PlaceSuggestion("id", "숭실대학교", "서울")));
        assertEquals(1, callback.lastSuggestions.size());
        assertEquals(1, gateway.sessionStarts);
    }

    @Test
    public void emptyQueryEndsSessionAndReturnsEmptyResults() {
        FakeGateway gateway = new FakeGateway();
        FakeScheduler scheduler = new FakeScheduler();
        PlaceSearchController controller = new PlaceSearchController(gateway, scheduler, 300L);
        RecordingSearchCallback callback = new RecordingSearchCallback();
        controller.onQueryChanged("test", callback);

        controller.onQueryChanged("  ", callback);

        assertTrue(callback.lastSuggestions.isEmpty());
        assertEquals(1, gateway.sessionEnds);
    }

    @Test
    public void selectionUsesCurrentSessionAndAlwaysEndsIt() {
        FakeGateway gateway = new FakeGateway();
        FakeScheduler scheduler = new FakeScheduler();
        PlaceSearchController controller = new PlaceSearchController(gateway, scheduler, 0L);
        controller.onQueryChanged("test", new RecordingSearchCallback());
        scheduler.runAll();
        Destination expected = new Destination(
                "id", "Place", new GeoPoint(37.0, 127.0), DestinationSource.PLACES, "id");
        final Destination[] selected = new Destination[1];

        controller.selectSuggestion(new PlaceSuggestion("id", "Place", ""),
                new PlaceSelectionCallback() {
                    @Override public void onDestination(Destination destination) {
                        selected[0] = destination;
                    }
                    @Override public void onError(PlaceSearchError error) {}
                });
        gateway.destinationCallback.onSuccess(expected);

        assertEquals(expected, selected[0]);
        assertEquals(1, gateway.sessionEnds);
    }

    private static final class RecordingSearchCallback implements PlaceSearchCallback {
        List<PlaceSuggestion> lastSuggestions = Collections.emptyList();
        PlaceSearchError error;
        @Override public void onSuggestions(List<PlaceSuggestion> suggestions) {
            lastSuggestions = suggestions;
        }
        @Override public void onError(PlaceSearchError error) { this.error = error; }
    }

    private static final class FakeScheduler implements SearchScheduler {
        final List<FakeTask> tasks = new ArrayList<>();
        @Override public ScheduledTask schedule(long delayMillis, Runnable action) {
            FakeTask task = new FakeTask(action);
            tasks.add(task);
            return task;
        }
        void runAll() {
            for (FakeTask task : new ArrayList<>(tasks)) if (!task.canceled) task.action.run();
            tasks.clear();
        }
        private static final class FakeTask implements ScheduledTask {
            final Runnable action;
            boolean canceled;
            FakeTask(Runnable action) { this.action = action; }
            @Override public void cancel() { canceled = true; }
        }
    }

    private static final class FakeGateway implements PlaceSearchGateway {
        int sessionStarts;
        int sessionEnds;
        int cancels;
        final List<String> queries = new ArrayList<>();
        final List<SuggestionsCallback> suggestionCallbacks = new ArrayList<>();
        DestinationCallback destinationCallback;
        @Override public void startSession() { sessionStarts++; }
        @Override public void findPredictions(String query, SuggestionsCallback callback) {
            queries.add(query);
            suggestionCallbacks.add(callback);
        }
        @Override public void fetchDestination(String placeId, DestinationCallback callback) {
            destinationCallback = callback;
        }
        @Override public void cancelPending() { cancels++; }
        @Override public void endSession() { sessionEnds++; }
    }
}
