package com.example.arnavigation.ui;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.example.arnavigation.ui.map.MapFragment;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public final class NavigationRestoreTest {
    @Test public void restoringMapReattachesToInitializedNavigationSession() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                activity.openMapTab();
                activity.getSupportFragmentManager().executePendingTransactions();
                activity.getSupportFragmentManager().beginTransaction()
                    .add(com.example.arnavigation.R.id.main_fragment_container,
                        new com.example.arnavigation.ui.ar.ArNavigationFragment(), "restore-ar")
                    .commitNow();
            });
            scenario.recreate();
            scenario.onActivity(activity -> {
                assertNotNull(activity.getNavigationViewModel());
                assertNotNull(activity.getNavigationViewModel().getState().getValue());
                boolean mapRestored = false;
                for (var fragment : activity.getSupportFragmentManager().getFragments()) {
                    if (fragment instanceof MapFragment && fragment.getView() != null) mapRestored = true;
                }
                assertTrue("Map view was restored with a navigation session", mapRestored);
                var ar = activity.getSupportFragmentManager().findFragmentByTag("restore-ar");
                assertNotNull(ar);
                assertNotNull("Outdoor AR view also restores with the shared session", ar.getView());
            });
        }
    }
}
