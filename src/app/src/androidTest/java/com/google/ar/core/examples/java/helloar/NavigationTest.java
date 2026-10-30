package com.google.ar.core.examples.java.helloar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.test.platform.app.InstrumentationRegistry;
import android.Manifest;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.SystemClock;
import android.widget.TextView;
import android.opengl.GLES30;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import com.google.ar.core.examples.java.common.samplerender.Framebuffer;
import com.google.ar.core.examples.java.common.samplerender.Mesh;
import com.google.ar.core.examples.java.common.samplerender.SampleRender;
import com.google.ar.core.examples.java.common.samplerender.Shader;
import com.google.ar.core.examples.java.common.samplerender.VertexBuffer;
import com.google.ar.core.Pose;
import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Before;
import org.junit.Test;

/** Tests real ARCore pose math and the Activity's actual route progression on Android. */
public class NavigationTest {
  private HelloArActivity activity;

  @Before
  public void setUp() {
    // A detached Activity tests route logic without opening a second AR camera session.
    InstrumentationRegistry.getInstrumentation().runOnMainSync(
        () -> activity = new HelloArActivity());
  }

  @Test
  public void distanceUsesMetersAndIgnoresMarkerHeight() {
    Waypoint target = new Waypoint(2f, -0.5f, -4f);
    assertEquals(0f, target.horizontalDistanceTo(2f, -4f), 0.0001f);
    assertEquals(5f, target.horizontalDistanceTo(-1f, 0f), 0.0001f);
  }

  @Test
  public void inactiveNavigationDoesNotAdvance() throws Exception {
    assertFalse(activity.advanceWaypointIfArrived(Pose.makeTranslation(0f, 0f, -2f)));
    assertEquals(0, getField("currentWaypointIndex"));
  }

  @Test
  public void arrivalThresholdIsStrictAndHeightIndependent() throws Exception {
    setField("navigationStarted", true);
    assertFalse(activity.advanceWaypointIfArrived(Pose.makeTranslation(0.8f, 0f, -2f)));
    assertFalse(activity.advanceWaypointIfArrived(Pose.makeTranslation(0.81f, 0f, -2f)));
    assertTrue(activity.advanceWaypointIfArrived(Pose.makeTranslation(0.79f, 10f, -2f)));
    assertEquals(1, getField("currentWaypointIndex"));
  }

  @Test
  public void routeRequiresOrderedWaypointsAndArrivesOnlyAfterThird() throws Exception {
    setField("navigationStarted", true);
    assertFalse(activity.advanceWaypointIfArrived(Pose.makeTranslation(2f, 0f, -4f)));
    assertTrue(activity.advanceWaypointIfArrived(Pose.makeTranslation(0f, 0f, -1.3f)));
    assertEquals(1, getField("currentWaypointIndex"));
    assertFalse(activity.advanceWaypointIfArrived(Pose.makeTranslation(0f, 0f, -1.3f)));
    assertTrue(activity.advanceWaypointIfArrived(Pose.makeTranslation(0f, 0f, -3.3f)));
    assertEquals(2, getField("currentWaypointIndex"));
    assertEquals(false, getField("navigationCompleted"));
    assertTrue(activity.advanceWaypointIfArrived(Pose.makeTranslation(1.3f, 0f, -4f)));
    assertEquals(3, getField("currentWaypointIndex"));
    assertEquals(false, getField("navigationStarted"));
    assertEquals(true, getField("navigationCompleted"));
    // Subsequent frames cannot access route[3] or advance past completion.
    assertFalse(activity.advanceWaypointIfArrived(Pose.makeTranslation(2f, 0f, -4f)));
  }

  @Test
  public void routeRestartResetsProgress() throws Exception {
    setField("navigationStarted", true);
    activity.advanceWaypointIfArrived(Pose.makeTranslation(0f, 0f, -2f));
    java.lang.reflect.Method reset = HelloArActivity.class.getDeclaredMethod("resetNavigation");
    reset.setAccessible(true);
    reset.invoke(activity);
    assertEquals(0, getField("currentWaypointIndex"));
    assertEquals(false, getField("navigationStarted"));
    assertEquals(false, getField("navigationCompleted"));
    setField("navigationStarted", true);
    assertTrue(activity.advanceWaypointIfArrived(Pose.makeTranslation(0f, 0f, -2f)));
  }

  @Test
  public void relativePoseHandlesTranslatedAndRotatedOrigin() {
    // 90 degrees about Y: local -Z is world -X, independent of world origin.
    float halfSqrt = (float) Math.sqrt(0.5);
    Pose origin = new Pose(new float[] {5f, 1f, 7f}, new float[] {0f, halfSqrt, 0f, halfSqrt});
    Pose worldCamera = origin.compose(Pose.makeTranslation(0f, 0f, -1f));
    assertEquals(4f, worldCamera.tx(), 0.0001f);
    Pose relativeCamera = origin.inverse().compose(worldCamera);
    assertTranslation(relativeCamera, 0f, 0f, -1f);
    Pose worldWaypoint = origin.compose(Pose.makeTranslation(0f, -0.5f, -2f));
    assertTranslation(worldWaypoint, 3f, 0.5f, 7f);
  }

  @Test
  public void sharedWorldCorrectionPreservesRelativeCoordinates() {
    Pose origin = Pose.makeTranslation(5f, 1f, 7f);
    Pose camera = origin.compose(Pose.makeTranslation(0.08f, 0.02f, -1.94f));
    float halfSqrt = (float) Math.sqrt(0.5);
    Pose correction = new Pose(new float[] {-2f, 0.3f, 4f},
        new float[] {0f, halfSqrt, 0f, halfSqrt});
    Pose correctedRelative = correction.compose(origin).inverse().compose(correction.compose(camera));
    assertTranslation(correctedRelative, 0.08f, 0.02f, -1.94f);
  }

  @Test
  public void liveCameraRendererStartsAndSurvivesPauseResume() throws Exception {
    Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    instrumentation.getUiAutomation().grantRuntimePermission(
        instrumentation.getTargetContext().getPackageName(), Manifest.permission.CAMERA);
    HelloArActivity live = (HelloArActivity) instrumentation.startActivitySync(
        new Intent(instrumentation.getTargetContext(), HelloArActivity.class)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    try {
      SystemClock.sleep(4000);
      instrumentation.runOnMainSync(() -> {
        assertFalse(live.isFinishing());
        TextView debug = live.findViewById(R.id.debug_text);
        assertTrue(debug.getText().toString().contains("Tracking:"));
      });
      assertRouteRendersOnGlThread(live);
      // Real GLSurfaceView/ARCore lifecycle; catches stale textures or shaders on resume.
      instrumentation.runOnMainSync(() -> instrumentation.callActivityOnPause(live));
      instrumentation.runOnMainSync(() -> instrumentation.callActivityOnResume(live));
      SystemClock.sleep(3000);
      instrumentation.runOnMainSync(() -> {
        assertFalse(live.isFinishing());
        TextView debug = live.findViewById(R.id.debug_text);
        assertTrue(debug.getText().toString().contains("Tracking:"));
      });
      assertRouteRendersOnGlThread(live);
    } finally {
      instrumentation.runOnMainSync(live::finish);
      instrumentation.waitForIdleSync();
    }
  }

  static void assertRouteRendersOnGlThread(HelloArActivity live) throws Exception {
    CountDownLatch done = new CountDownLatch(1);
    AtomicReference<Throwable> failure = new AtomicReference<>();
    GLSurfaceView surface = live.findViewById(R.id.surfaceview);
    surface.queueEvent(() -> {
      try {
        RouteLineGeometry geometry = (RouteLineGeometry) getField(live, "routeLineGeometry");
        geometry.update(0, 0f, 0f);
        ((VertexBuffer) getField(live, "routePositionBuffer")).set(geometry.getPositions());
        ((VertexBuffer) getField(live, "routeColorBuffer")).set(geometry.getColors());
        float[] projection = new float[16];
        Matrix.perspectiveM(projection, 0, 60f, 1f, 0.1f, 100f);
        Shader shader = (Shader) getField(live, "routeShader");
        shader.setMat4("u_ModelViewProjection", projection);
        ((SampleRender) getField(live, "render")).draw(
            (Mesh) getField(live, "routeMesh"), shader,
            (Framebuffer) getField(live, "virtualSceneFramebuffer"));
        assertEquals(GLES30.GL_NO_ERROR, GLES30.glGetError());
      } catch (Throwable error) {
        failure.set(error);
      } finally {
        done.countDown();
      }
    });
    assertTrue("GL route draw timed out", done.await(10, TimeUnit.SECONDS));
    if (failure.get() != null) {
      throw new AssertionError("Route renderer failed", failure.get());
    }
  }

  private static Object getField(HelloArActivity target, String name) throws Exception {
    Field field = HelloArActivity.class.getDeclaredField(name);
    field.setAccessible(true);
    return field.get(target);
  }

  private void assertTranslation(Pose pose, float x, float y, float z) {
    assertEquals(x, pose.tx(), 0.0001f);
    assertEquals(y, pose.ty(), 0.0001f);
    assertEquals(z, pose.tz(), 0.0001f);
  }

  private Object getField(String name) throws Exception {
    Field field = HelloArActivity.class.getDeclaredField(name);
    field.setAccessible(true);
    return field.get(activity);
  }

  private void setField(String name, Object value) throws Exception {
    Field field = HelloArActivity.class.getDeclaredField(name);
    field.setAccessible(true);
    field.set(activity, value);
  }
}
