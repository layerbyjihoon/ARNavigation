package com.google.ar.core.examples.java.helloar;

import static org.junit.Assert.*;
import android.Manifest;
import android.app.Instrumentation;
import android.content.Intent;
import android.opengl.GLSurfaceView;
import android.os.SystemClock;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.ar.core.Frame;
import com.google.ar.core.Session;
import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;

/** Regression checks using real phone rotation-vector callbacks and real ARCore timestamps. */
public final class HeadingSensorIntegrationTest {
  private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();

  @Test public void phoneRotationVectorIsAcceptedAndStopsAfterPause() {
    HeadingSensor sensor = new HeadingSensor(instrumentation.getTargetContext());
    instrumentation.runOnMainSync(sensor::resume);
    try {
      long deadline = SystemClock.elapsedRealtime() + 8000;
      float[] accepted = null;
      while (SystemClock.elapsedRealtime() < deadline && accepted == null) {
        accepted = sensor.at(SystemClock.elapsedRealtimeNanos());
        if (accepted == null) SystemClock.sleep(30);
      }
      assertNotNull("Real phone rotation vector was rejected", accepted);
      assertEquals(9, accepted.length);
      for (float value : accepted) assertTrue(Float.isFinite(value));
    } finally { instrumentation.runOnMainSync(sensor::pause); }
    assertNull(sensor.at(SystemClock.elapsedRealtimeNanos()));
  }

  @Test public void acceptedHeadingReachesActualNavigationFramePipeline() throws Exception {
    instrumentation.getUiAutomation().grantRuntimePermission(
        instrumentation.getTargetContext().getPackageName(), Manifest.permission.CAMERA);
    HelloArActivity activity = (HelloArActivity) instrumentation.startActivitySync(new Intent(
        instrumentation.getTargetContext(), HelloArActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    try {
      SystemClock.sleep(2500);
      AtomicBoolean ready = new AtomicBoolean();
      long deadline = SystemClock.elapsedRealtime() + 8000;
      while (SystemClock.elapsedRealtime() < deadline && !ready.get()) {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        ((GLSurfaceView)activity.findViewById(R.id.surfaceview)).queueEvent(() -> {
          try {
            Session session = (Session) field(activity,"session");
            ArDatasetController datasets = (ArDatasetController) field(activity,"datasets");
            if (session != null) {
              Frame frame = session.update();
              datasets.onFrame(session,frame);
              float[] heading = datasets.headingForFrame();
              if (frame.getTimestamp() > 0 && heading != null) {
                assertEquals(9,heading.length);
                for (float value : heading) assertTrue(Float.isFinite(value));
                ready.set(true);
              }
            }
          } catch (Throwable error) { failure.set(error); }
          finally { done.countDown(); }
        });
        assertTrue("GL frame check timed out",done.await(5,TimeUnit.SECONDS));
        if (failure.get() != null) throw new AssertionError(failure.get());
        if (!ready.get()) SystemClock.sleep(50);
      }
      assertTrue("No heading reached ARCore navigation frames",ready.get());
    } finally {
      instrumentation.runOnMainSync(activity::finish);
      instrumentation.waitForIdleSync();
    }
  }

  private static Object field(Object object, String name) throws Exception {
    Field field = object.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(object);
  }
}
