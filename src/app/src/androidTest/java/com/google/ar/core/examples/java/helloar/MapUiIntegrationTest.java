package com.google.ar.core.examples.java.helloar;

import static org.junit.Assert.*;
import android.Manifest;
import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.opengl.GLSurfaceView;
import android.os.SystemClock;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.Spinner;
import android.widget.TextView;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.ar.core.examples.java.helloar.map.*;
import com.google.ar.core.examples.java.helloar.routing.*;
import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;

/** Packaging, real POI UI, multilevel routing, and the map-to-existing-AR handoff on a phone. */
public final class MapUiIntegrationTest {
  private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
  @Test public void munhwagwanScreenLoadsAllRoomsChoosesEntranceAndExplainsUnavailableStairs() {
    MapRouteActivity map = (MapRouteActivity) instrumentation.startActivitySync(new Intent(
        instrumentation.getTargetContext(),MapRouteActivity.class)
        .putExtra(MapRouteActivity.EXTRA_MAP_INDEX,0).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    try {
      waitForMap(map);
      instrumentation.runOnMainSync(() -> {
        assertTrue(((TextView)map.findViewById(R.id.map_summary)).getText().toString().contains("Nodes: 206"));
        assertTrue(((TextView)map.findViewById(R.id.map_notice)).getText().toString().contains("현장 미검증"));
        Spinner from=map.findViewById(R.id.start_poi), to=map.findViewById(R.id.destination_poi);
        assertEquals(65,from.getCount());
        selectPoi(from,"105"); selectPoi(to,"106");
        map.findViewById(R.id.find_route).performClick();
        String text=((TextView)map.findViewById(R.id.route_result)).getText().toString();
        assertTrue(text,text.contains("105호 진입 2")); assertTrue(text,text.contains("7.70"));
        assertTrue(text,text.contains("출발 기준:"));
        assertTrue(text,text.contains("85.5°"));
        assertTrue(text,text.contains("F1_C010"));
        try {
          EntryDirection direction = (EntryDirection) field(map,"selectedEntryDirection");
          assertEquals(180,Math.abs(direction.canonicalYawDegrees),1e-6);
        } catch (Exception e) { throw new AssertionError(e); }
        assertTrue(map.findViewById(R.id.apply_ar_route).isEnabled());
        selectPoi(to,"201"); map.findViewById(R.id.find_route).performClick();
        assertFalse(map.findViewById(R.id.apply_ar_route).isEnabled());
        assertTrue(((TextView)map.findViewById(R.id.route_result)).getText().toString().contains("층간 연결 8개"));
      });
    } finally { instrumentation.runOnMainSync(map::finish); }
  }

  private static void selectPoi(Spinner spinner,String id) {
    for(int i=0;i<spinner.getCount();i++) if(((Poi)spinner.getItemAtPosition(i)).id.equals(id)) {
      spinner.setSelection(i); return;
    }
    fail("Missing POI " + id);
  }
  @Test public void packagedAssetProducesStairAndWheelchairRoutes() {
    MapLoadResult loaded = new IndoorMapRepository().loadFromAsset(
        instrumentation.getTargetContext().getAssets()::open, MapRouteActivity.TEST_MAP);
    assertTrue(loaded.validation.issues.toString(), loaded.isSuccess());
    assertEquals(2, loaded.map.floors.size()); assertEquals(10, loaded.map.nodes.size());
    AStarRouteEngine engine = new AStarRouteEngine(loaded.graph);
    NavigationRoute ordinary = engine.findRoute("START","ROOM_B",RouteOptions.DEFAULT).route;
    assertTrue(ordinary.isMultiFloor()); assertEquals(13.8, ordinary.totalDistance, 1e-8);
    NavigationRoute accessible = engine.findRoute("START","ROOM_B",RouteOptions.WHEELCHAIR).route;
    assertTrue(accessible.segments.stream().anyMatch(s -> s.edge.type == EdgeType.ELEVATOR));
    assertFalse(accessible.segments.stream().anyMatch(s -> s.edge.type == EdgeType.STAIR));
  }

  @Test public void poiScreenCalculatesBothFloorRoutesAndRestrictsUnsupportedArTraversal() {
    MapRouteActivity map = (MapRouteActivity) instrumentation.startActivitySync(new Intent(
        instrumentation.getTargetContext(),MapRouteActivity.class)
        .putExtra(MapRouteActivity.EXTRA_MAP_INDEX,1).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    try {
      waitForMap(map);
      instrumentation.runOnMainSync(() -> {
        assertTrue(((TextView)map.findViewById(R.id.map_summary)).getText().toString().contains("Floors: 2"));
        ((Spinner)map.findViewById(R.id.destination_poi)).setSelection(2);
        map.findViewById(R.id.find_route).performClick();
        String text = ((TextView)map.findViewById(R.id.route_result)).getText().toString();
        assertTrue(text,text.contains("STAIR")); assertTrue(text,text.contains("F1 → F2"));
        assertTrue(text,text.contains("목표 AR (2.00, 3.30, -8.00)"));
        assertFalse(map.findViewById(R.id.apply_ar_route).isEnabled());
        ((CheckBox)map.findViewById(R.id.wheelchair_route)).setChecked(true);
        map.findViewById(R.id.find_route).performClick();
        String accessible = ((TextView)map.findViewById(R.id.route_result)).getText().toString();
        assertTrue(accessible,accessible.contains("ELEVATOR"));
        assertFalse(accessible,accessible.contains("· STAIR"));
        ((Spinner)map.findViewById(R.id.destination_poi)).setSelection(1);
        map.findViewById(R.id.find_route).performClick();
        assertTrue(map.findViewById(R.id.apply_ar_route).isEnabled());
      });
    } finally { instrumentation.runOnMainSync(map::finish); }
  }

  @Test public void selectedSameFloorRouteIsAppliedToTheRealArRenderThread() throws Exception {
    instrumentation.getUiAutomation().grantRuntimePermission(
        instrumentation.getTargetContext().getPackageName(),Manifest.permission.CAMERA);
    HelloArActivity ar = (HelloArActivity) instrumentation.startActivitySync(new Intent(
        instrumentation.getTargetContext(),HelloArActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    Instrumentation.ActivityMonitor monitor = instrumentation.addMonitor(MapRouteActivity.class.getName(),null,false);
    Activity map = null;
    try {
      instrumentation.runOnMainSync(() -> ar.findViewById(R.id.map_route_button).performClick());
      map = monitor.waitForActivityWithTimeout(5000);
      assertTrue(map instanceof MapRouteActivity);
      MapRouteActivity screen = (MapRouteActivity) map; waitForMap(screen);
      instrumentation.runOnMainSync(() -> ((Spinner)screen.findViewById(R.id.map_dataset)).setSelection(0));
      instrumentation.waitForIdleSync(); waitForMap(screen);
      instrumentation.runOnMainSync(() -> {
        ((Spinner)screen.findViewById(R.id.destination_poi)).setSelection(1);
        screen.findViewById(R.id.find_route).performClick();
        assertTrue(screen.findViewById(R.id.apply_ar_route).isEnabled());
        screen.findViewById(R.id.apply_ar_route).performClick();
      });
      SystemClock.sleep(2500);
      AtomicReference<Throwable> failure = new AtomicReference<>();
      CountDownLatch done = new CountDownLatch(1);
      ((GLSurfaceView)ar.findViewById(R.id.surfaceview)).queueEvent(() -> {
        try {
          List<?> route = (List<?>) field(ar,"route");
          assertEquals(3,route.size());
          Waypoint last = (Waypoint)route.get(2);
          assertEquals(-4.226,last.x,1e-6); assertEquals(0,last.z,1e-6); assertEquals(-0.5,last.y,1e-6);
          assertTrue(((String)field(ar,"selectedMapRouteName")).contains("102호"));
          assertEquals(0,(Double)field(ar,"routeEntryYawDegrees"),1e-6);
          assertTrue(((String)field(ar,"routeEntryDirectionLabel")).contains("F1_C004"));
          assertFalse((Boolean)field(ar,"routeUseManualAlignment"));
          assertFalse((Boolean)field(ar,"navigationStarted"));
        } catch (Throwable error) { failure.set(error); }
        finally { done.countDown(); }
      });
      assertTrue("AR handoff did not reach the GL thread",done.await(10,TimeUnit.SECONDS));
      if (failure.get()!=null) throw new AssertionError("AR handoff failed",failure.get());
    } finally {
      instrumentation.removeMonitor(monitor);
      if (map != null && !map.isFinishing()) { Activity open=map; instrumentation.runOnMainSync(open::finish); }
      instrumentation.runOnMainSync(ar::finish);
    }
  }
  private void waitForMap(MapRouteActivity activity) {
    long deadline=SystemClock.elapsedRealtime()+5000;
    boolean[] ready={false};
    while(SystemClock.elapsedRealtime()<deadline) {
      instrumentation.runOnMainSync(() -> ready[0]=activity.findViewById(R.id.find_route).isEnabled());
      if (ready[0]) return;
      SystemClock.sleep(50);
    }
    fail("Map did not load");
  }
  private static Object field(Object instance,String name) throws Exception {
    Field field=instance.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(instance);
  }
}
