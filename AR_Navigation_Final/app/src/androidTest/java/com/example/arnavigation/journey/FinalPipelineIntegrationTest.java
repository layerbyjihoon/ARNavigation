package com.example.arnavigation.journey;

import android.app.Activity;
import android.content.Intent;
import android.os.SystemClock;
import android.widget.Button;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.ar.core.examples.java.helloar.MapRouteActivity;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public final class FinalPipelineIntegrationTest {
    @Test public void cultureRoomSelectionCompilesAndReturnsIndoorRouteForTransfer() {
        Intent intent = new Intent(ApplicationProvider.getApplicationContext(), MapRouteActivity.class)
            .putExtra("final_pick_room", true).putExtra("map_index", 0);
        try (ActivityScenario<MapRouteActivity> scenario = ActivityScenario.launchActivityForResult(intent)) {
            boolean[] ready = {false};
            for (int i=0; i<80 && !ready[0]; i++) {
                scenario.onActivity(activity -> ready[0] = ((Button) activity.findViewById(
                    com.google.ar.core.examples.java.helloar.R.id.find_route)).isEnabled());
                if (!ready[0]) SystemClock.sleep(100);
            }
            assertTrue("Bundled culture map loads", ready[0]);
            scenario.onActivity(activity -> {
                android.widget.Spinner start = activity.findViewById(
                    com.google.ar.core.examples.java.helloar.R.id.start_poi);
                assertFalse("Departure is pinned to the fixed point one meter right of the corridor midpoint", start.isEnabled());
                assertEquals("F1_START_C006_C007", ((com.google.ar.core.examples.java.helloar.map.Poi)
                    start.getSelectedItem()).id);
                activity.findViewById(com.google.ar.core.examples.java.helloar.R.id.find_route).performClick();
                Button apply = activity.findViewById(com.google.ar.core.examples.java.helloar.R.id.apply_ar_route);
                assertTrue("Route can be applied", apply.isEnabled());
                apply.performClick();
            });
            var result = scenario.getResult();
            assertEquals(Activity.RESULT_OK, result.getResultCode());
            Intent data = result.getResultData();
            assertNotNull(data);
            assertNotNull(data.getStringExtra("final_room_name"));
            assertEquals(MapRouteActivity.MUNHWAGWAN_MAP, data.getStringExtra(MapRouteActivity.EXTRA_REFERENCE));
            assertTrue(data.getFloatArrayExtra(MapRouteActivity.EXTRA_POINTS).length >= 6);
            assertNotNull(data.getIntArrayExtra(MapRouteActivity.EXTRA_STAGE_LENGTHS));
            assertNotNull(data.getStringExtra(MapRouteActivity.EXTRA_SEMANTIC_MAP));
        }
    }
    @Test public void roomSearchFiltersAndSelectingVisibleRowEnablesGuidance() {
        Intent intent = new Intent(ApplicationProvider.getApplicationContext(), MapRouteActivity.class)
            .putExtra("final_pick_room", true).putExtra("map_index", 0);
        try (ActivityScenario<MapRouteActivity> scenario = ActivityScenario.launchActivityForResult(intent)) {
            boolean[] ready = {false};
            for (int i=0; i<80 && !ready[0]; i++) {
                scenario.onActivity(activity -> ready[0] = activity.findViewById(
                    com.google.ar.core.examples.java.helloar.R.id.final_room_list) != null);
                if (!ready[0]) SystemClock.sleep(100);
            }
            assertTrue(ready[0]);
            String[] selectedName = {null};
            scenario.onActivity(activity -> {
                android.widget.ListView list = activity.findViewById(
                    com.google.ar.core.examples.java.helloar.R.id.final_room_list);
                android.widget.EditText search = activity.findViewById(
                    com.google.ar.core.examples.java.helloar.R.id.final_room_search);
                Button apply = activity.findViewById(com.google.ar.core.examples.java.helloar.R.id.apply_ar_route);
                assertTrue(list.getCount() > 0);
                search.setText("NO_SUCH_ROOM_98765");
                assertEquals(0, list.getCount()); assertFalse(apply.isEnabled());
                search.setText("");
                selectedName[0] = ((com.google.ar.core.examples.java.helloar.map.Poi)list.getItemAtPosition(0)).name;
                list.performItemClick(null, 0, list.getItemIdAtPosition(0));
                assertTrue("Tapping a visible room computes a usable route",apply.isEnabled());
                apply.performClick();
            });
            assertEquals(Activity.RESULT_OK, scenario.getResult().getResultCode());
            assertEquals(selectedName[0], scenario.getResult().getResultData().getStringExtra("final_room_name"));
        }
    }

    @Test public void selectingCultureOpensRoomsThenKeepsIndoorGuideInactiveUntilEntry() {
        try (ActivityScenario<com.example.arnavigation.ui.MainActivity> scenario =
                ActivityScenario.launch(com.example.arnavigation.ui.MainActivity.class)) {
            scenario.onActivity(activity -> activity.selectDestination(culture()));
            MapRouteActivity picker = awaitRoomPicker();
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                android.widget.ListView rooms = picker.findViewById(
                    com.google.ar.core.examples.java.helloar.R.id.final_room_list);
                assertTrue(rooms.getCount() > 0);
                android.widget.Spinner start=picker.findViewById(
                    com.google.ar.core.examples.java.helloar.R.id.start_poi);
                assertFalse(start.isEnabled());
                assertEquals(MapRouteActivity.MUNHWAGWAN_START_ID,
                    ((com.google.ar.core.examples.java.helloar.map.Poi)start.getSelectedItem()).id);
                rooms.performItemClick(null, 0, rooms.getItemIdAtPosition(0));
                picker.findViewById(com.google.ar.core.examples.java.helloar.R.id.apply_ar_route).performClick();
            });
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.onActivity(activity -> {
                JourneyState journey = activity.getJourney();
                assertNotNull("Indoor route is stored before entering the building",journey.route());
                var state = activity.getNavigationViewModel().getState().getValue();
                assertEquals(journey.destinationId(),state.getDestination().getId());
                assertEquals("Room journey keeps the selected front door for routing and proximity",
                    new com.example.arnavigation.model.GeoPoint(37.49637,126.95442),state.getDestination().getPoint());
                assertTrue(state.getDestination().getName().startsWith("문화관 "));
                assertFalse(journey.gate.isHandingOff()); assertFalse(journey.isLaunchReady());
                assertNotEquals(com.example.arnavigation.navigation.NavigationStage.NAVIGATING,state.getStage());
                assertTrue(activity.getSupportFragmentManager().getPrimaryNavigationFragment()
                    instanceof com.example.arnavigation.ui.map.MapFragment);
                activity.startIndoorGuide();
                assertFalse("Preset room does not enable the indoor activity",journey.isLaunchReady());
            });
        }
    }

    @Test public void cancelingRoomPickerPreservesPreviousDestination() {
        try (ActivityScenario<com.example.arnavigation.ui.MainActivity> scenario =
                ActivityScenario.launch(com.example.arnavigation.ui.MainActivity.class)) {
            var previous = new com.example.arnavigation.model.Destination("library","중앙도서관",
                new com.example.arnavigation.model.GeoPoint(37.4963,126.9574),
                com.example.arnavigation.model.DestinationSource.MAP_TAP,null);
            scenario.onActivity(activity -> {
                activity.selectDestination(previous); activity.selectDestination(culture());
            });
            MapRouteActivity picker = awaitRoomPicker();
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().runOnMainSync(picker::finish);
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.onActivity(activity -> {
                assertEquals(previous,activity.getNavigationViewModel().getState().getValue().getDestination());
                assertNull(activity.getJourney().route());
            });
        }
    }

    private static com.example.arnavigation.model.Destination culture() {
        return new com.example.arnavigation.model.Destination("culture","문화관",
            new com.example.arnavigation.model.GeoPoint(37.4963,126.9574),
            com.example.arnavigation.model.DestinationSource.PLACES,"culture");
    }
    private static MapRouteActivity awaitRoomPicker() {
        var instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation();
        MapRouteActivity[] result = {null};
        for (int i=0; i<80 && result[0] == null; i++) {
            instrumentation.runOnMainSync(() -> {
                for (Activity activity : androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
                        .getInstance().getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED)) {
                    if (activity instanceof MapRouteActivity && activity.findViewById(
                            com.google.ar.core.examples.java.helloar.R.id.final_room_list) != null)
                        result[0] = (MapRouteActivity)activity;
                }
            });
            if (result[0] == null) SystemClock.sleep(100);
        }
        assertNotNull("Culture selection immediately opens the room picker",result[0]); return result[0];
    }

}
