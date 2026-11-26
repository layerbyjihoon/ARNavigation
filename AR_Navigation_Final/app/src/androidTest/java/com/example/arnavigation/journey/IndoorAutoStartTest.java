package com.example.arnavigation.journey;

import android.Manifest;
import android.content.Intent;
import android.opengl.GLSurfaceView;
import android.os.SystemClock;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.ar.core.examples.java.helloar.HelloArActivity;
import com.google.ar.core.examples.java.helloar.MapRouteActivity;
import java.util.concurrent.*;
import java.lang.reflect.Field;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public final class IndoorAutoStartTest {
    @Test public void handoffQueuesStartWithoutTapAndResetDoesNotAutomaticallyRestart() throws Exception {
        grantCamera();
        try(ActivityScenario<HelloArActivity> scenario=ActivityScenario.launch(intent(true))) {
            await(scenario, activity -> field(activity,"selectedMapRouteName")!=null
                && (flag(activity,"startNavigationRequested") || flag(activity,"navigationStarted")));
            // Keep real AR frames running while the initial floor may still be unknown.
            SystemClock.sleep(2000);
            await(scenario, activity -> flag(activity,"startNavigationRequested") || flag(activity,"navigationStarted"));
            scenario.onActivity(activity -> {
                activity.findViewById(com.google.ar.core.examples.java.helloar.R.id.reset_nav_button).performClick();
            });
            await(scenario, activity -> !flag(activity,"resetNavigationRequested")
                && !flag(activity,"startNavigationRequested") && !flag(activity,"navigationStarted"));
            SystemClock.sleep(300);
            await(scenario, activity -> !flag(activity,"startNavigationRequested") && !flag(activity,"navigationStarted"));
        }
    }
    @Test public void normalRouteOpeningStillWaitsForManualStart() throws Exception {
        grantCamera();
        try(ActivityScenario<HelloArActivity> scenario=ActivityScenario.launch(intent(false))) {
            await(scenario, activity -> field(activity,"selectedMapRouteName")!=null);
            await(scenario, activity -> !flag(activity,"startNavigationRequested") && !flag(activity,"navigationStarted"));
        }
    }
    @Test public void indoorOpeningEnablesVisualPositionCorrection() throws Exception {
        grantCamera();
        try(ActivityScenario<HelloArActivity> scenario=ActivityScenario.launch(intent(true))) {
            scenario.onActivity(activity -> {
                assertTrue(((android.widget.CheckBox)activity.findViewById(
                        com.google.ar.core.examples.java.helloar.R.id.semantic_enable)).isChecked());
                assertTrue(((android.widget.CheckBox)activity.findViewById(
                        com.google.ar.core.examples.java.helloar.R.id.semantic_correction)).isChecked());
            });
            await(scenario, activity -> flag(activity,"semanticCorrectionEnabled"));
        }
    }

    private static Intent intent(boolean auto) {
        return new Intent(ApplicationProvider.getApplicationContext(),HelloArActivity.class)
            .putExtra(HelloArActivity.EXTRA_AUTO_START_ON_FLOOR,auto)
            .putExtra(MapRouteActivity.EXTRA_NAME,"실내 자동 시작 검증")
            .putExtra(MapRouteActivity.EXTRA_REFERENCE,MapRouteActivity.MUNHWAGWAN_MAP)
            .putExtra(MapRouteActivity.EXTRA_POINTS,new float[]{0,-.5f,0,0,-.5f,-3})
            .putExtra(MapRouteActivity.EXTRA_STAGE_LENGTHS,new int[]{6})
            .putExtra(MapRouteActivity.EXTRA_FLOORS,new String[]{"F1"})
            .putExtra(MapRouteActivity.EXTRA_NEXT_FLOORS,new String[]{null})
            .putExtra(MapRouteActivity.EXTRA_TRANSITIONS,new String[]{null});
    }
    private static void grantCamera() {
        var i=InstrumentationRegistry.getInstrumentation();
        i.getUiAutomation().grantRuntimePermission(i.getTargetContext().getPackageName(),Manifest.permission.CAMERA);
    }
    private interface Check {boolean matches(HelloArActivity activity) throws Exception;}
    private static void await(ActivityScenario<HelloArActivity> scenario,Check check) throws Exception {
        for(int attempt=0;attempt<100;attempt++) {
            CountDownLatch done=new CountDownLatch(1); boolean[] okay={false}; Throwable[] failure={null};
            scenario.onActivity(activity -> ((GLSurfaceView)activity.findViewById(
                com.google.ar.core.examples.java.helloar.R.id.surfaceview)).queueEvent(() -> {
                    try {okay[0]=check.matches(activity);} catch(Throwable e) {failure[0]=e;}
                    finally {done.countDown();}
                }));
            assertTrue("AR render thread responds",done.await(3,TimeUnit.SECONDS));
            if(failure[0]!=null) throw new AssertionError(failure[0]);
            if(okay[0]) return;
            SystemClock.sleep(100);
        }
        fail("Indoor automatic-start state did not arrive");
    }
    private static boolean flag(Object object,String name) throws Exception {return (Boolean)field(object,name);}
    private static Object field(Object object,String name) throws Exception {
        Field field=HelloArActivity.class.getDeclaredField(name);field.setAccessible(true);return field.get(object);
    }
}
