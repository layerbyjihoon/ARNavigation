package com.google.ar.core.examples.java.helloar;

import static org.junit.Assert.*;
import android.Manifest;
import android.app.Instrumentation;
import android.content.Intent;
import android.media.MediaExtractor;
import android.opengl.GLSurfaceView;
import android.os.SystemClock;
import android.widget.TextView;
import android.widget.Spinner;
import androidx.appcompat.app.AlertDialog;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.ar.core.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.Test;

/** Exercises real ARCore MP4 recording/playback on the connected phone, without uninstalling. */
public final class ArDatasetIntegrationTest {
  private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
  private HelloArActivity activity;
  private final Set<String> testIds = new HashSet<>();
  private Map<String, ?> originalStartPreferences;

  @Test public void recordPersistReplayPauseReturnLiveAndDelete() throws Exception {
    try {
      open();
      ArDatasetController controller = controller();
      ArRecordingStore store = (ArRecordingStore) field(activity, "recordingStore");
      startAtNode("F4_R406_D1", 3);
      awaitMode(ArDatasetController.Mode.RECORDING);
      ArRecordingStore.Entry first = controller.active(); testIds.add(first.id);
      assertEquals("F4_R406_D1", first.startPoint.nodeId);
      assertEquals("F4", first.startPoint.floorId);
      assertEquals("F4_C007", first.startPoint.direction.nextNodeId);
      assertEquals(MapOrientation.SENSOR_SOURCE,first.mapOrientationSource);
      assertEquals(85.5,first.mapCounterClockwiseDegrees,1e-9);
      onGl(() -> {
        assertNotNull(controller().mapReferenceFor(MapRouteActivity.MUNHWAGWAN_MAP));
        assertNull(controller().mapReferenceFor(MapRouteActivity.TEST_MAP));
      });
      onGl(() -> {
        try { controller().enterPlayback(first); fail("recording -> playback must be rejected"); }
        catch (IOException expected) { }
      });
      SystemClock.sleep(5000);
      assertFrames();
      click(R.id.record_ar_button);
      awaitMode(ArDatasetController.Mode.LIVE);
      ArRecordingStore.Entry saved = store.list().stream().filter(e -> e.id.equals(first.id)).findFirst().get();
      assertTrue(saved.status + ": " + saved.error, saved.playable());
      assertEquals("F4_R406_D1", saved.startPoint.nodeId);
      assertTrue(saved.durationMillis >= 4000); assertTrue(saved.sizeBytes > 1000);
      MediaExtractor extractor = new MediaExtractor();
      try { extractor.setDataSource(saved.path); assertTrue(extractor.getTrackCount() > 1); }
      finally { extractor.release(); }
      instrumentation.runOnMainSync(activity::finish); instrumentation.waitForIdleSync();
      open(); // Reopen the real screen; the library must survive Activity destruction.
      controller = controller();
      store = (ArRecordingStore) field(activity, "recordingStore");
      assertTrue(store.list().stream().anyMatch(e -> e.id.equals(first.id) && e.playable()));
      Session liveSession = (Session) field(activity, "session");
      play(saved);
      awaitMode(ArDatasetController.Mode.PLAYBACK);
      assertNotSame(liveSession, field(activity, "session"));
      instrumentation.runOnMainSync(() -> {
        assertFalse(activity.findViewById(R.id.record_ar_button).isEnabled());
        assertTrue(((TextView) activity.findViewById(R.id.dataset_status)).getText().toString().contains("AR 재생 모드"));
        assertTrue(((TextView) activity.findViewById(R.id.recording_start_point)).getText().toString().contains("F4_R406_D1"));
      });
      SystemClock.sleep(1000);
      assertFrames();
      awaitMapReference();
      assertNull(((HeadingSensor) field(activity, "headingSensor")).at(SystemClock.elapsedRealtimeNanos()));
      Session playbackSession = (Session) field(activity, "session");
      instrumentation.runOnMainSync(() -> instrumentation.callActivityOnPause(activity));
      assertNull(field(activity, "session"));
      SystemClock.sleep(300);
      instrumentation.runOnMainSync(() -> instrumentation.callActivityOnResume(activity));
      awaitMode(ArDatasetController.Mode.PLAYBACK);
      assertNotSame(playbackSession, field(activity, "session"));
      SystemClock.sleep(1000); assertFrames();
      click(R.id.stop_playback_button);
      awaitMode(ArDatasetController.Mode.LIVE);
      SystemClock.sleep(1000); assertFrames();

      // Recording after playback and background finalization.
      click(R.id.record_ar_button); awaitMode(ArDatasetController.Mode.RECORDING);
      ArRecordingStore.Entry second = controller().active(); testIds.add(second.id);
      SystemClock.sleep(1800);
      instrumentation.runOnMainSync(() -> instrumentation.callActivityOnPause(activity));
      assertEquals(ArDatasetController.Mode.LIVE, controller().mode());
      assertTrue(store.list().stream().anyMatch(e -> e.id.equals(second.id) && e.playable()));
      instrumentation.runOnMainSync(() -> instrumentation.callActivityOnResume(activity));

      // A normal MP4 sidecar cannot make invalid dataset bytes playable by ARCore.
      ArRecordingStore.Entry broken = store.create(Long.MAX_VALUE); testIds.add(broken.id);
      try (FileOutputStream out = new FileOutputStream(store.file(broken))) { out.write(new byte[128]); }
      store.finish(broken, 100, null);
      play(broken); awaitMode(ArDatasetController.Mode.LIVE);
      assertNotNull(field(activity, "session"));
      SystemClock.sleep(800); assertFrames();
    } finally { cleanup(); }
  }

  @Test public void lowSpaceRejectsStartAndFinalizesAnActiveRecording() throws Exception {
    try {
      open();
      File isolated = new File(activity.getCacheDir(), "dataset-test-" + UUID.randomUUID());
      ArRecordingStore store = new ArRecordingStore(isolated);
      AtomicLong space = new AtomicLong(0);
      List<String> errors = new CopyOnWriteArrayList<>();
      ArDatasetController controller = new ArDatasetController(store,
          (HeadingSensor) field(activity, "headingSensor"), new ArDatasetController.Listener() {
            public void changed() { }
            public void error(String message) { errors.add(message); }
          }, space::get);
      onGl(() -> controller.startRecording((Session) field(activity, "session")));
      assertEquals(ArDatasetController.Mode.LIVE, controller.mode()); assertTrue(store.list().isEmpty());
      assertFalse(errors.isEmpty());
      space.set(Long.MAX_VALUE);
      onGl(() -> controller.startRecording((Session) field(activity, "session")));
      assertEquals(ArDatasetController.Mode.RECORDING, controller.mode());
      ArRecordingStore.Entry recording = controller.active();
      space.set(0); SystemClock.sleep(1500);
      onGl(() -> {
        Session session = (Session) field(activity, "session");
        controller.onFrame(session, session.update());
      });
      assertEquals(ArDatasetController.Mode.LIVE, controller.mode());
      assertTrue(store.list().get(0).playable()); assertTrue(errors.size() >= 2);
      store.delete(recording);
      assertTrue(store.list().isEmpty());
    } finally { cleanup(); }
  }

  private void open() {
    instrumentation.getUiAutomation().grantRuntimePermission(
        instrumentation.getTargetContext().getPackageName(), Manifest.permission.CAMERA);
    activity = (HelloArActivity) instrumentation.startActivitySync(new Intent(
        instrumentation.getTargetContext(), HelloArActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    if (originalStartPreferences == null)
      originalStartPreferences = activity.getSharedPreferences("recording_start", 0).getAll();
    SystemClock.sleep(2500);
  }
  private ArDatasetController controller() throws Exception { return (ArDatasetController) field(activity, "datasets"); }
  private void click(int id) {
    instrumentation.runOnMainSync(() -> {
      activity.findViewById(id).performClick();
      try {
        AlertDialog dialog = (AlertDialog) field(activity, "recordingStartDialog");
        if (id == R.id.record_ar_button && dialog != null && dialog.isShowing())
          dialog.getButton(AlertDialog.BUTTON_NEUTRAL).performClick();
      } catch (Exception e) { throw new AssertionError(e); }
    });
  }

  private void startAtNode(String nodeId, int floorIndex) throws Exception {
    instrumentation.runOnMainSync(() -> activity.findViewById(R.id.record_ar_button).performClick());
    AlertDialog dialog = (AlertDialog) field(activity, "recordingStartDialog");
    assertNotNull(dialog);
    instrumentation.runOnMainSync(() -> ((Spinner) dialog.findViewById(R.id.record_start_map)).setSelection(0));
    long deadline = SystemClock.elapsedRealtime() + 5000;
    AtomicBoolean ready = new AtomicBoolean();
    while (SystemClock.elapsedRealtime() < deadline) {
      instrumentation.runOnMainSync(() -> {
        Spinner floors = dialog.findViewById(R.id.record_start_floor);
        ready.set(floors.isEnabled() && floors.getCount() == 5);
      });
      if (ready.get()) break;
      SystemClock.sleep(50);
    }
    assertTrue("Recording map did not load", ready.get());
    instrumentation.runOnMainSync(() -> ((Spinner) dialog.findViewById(R.id.record_start_floor)).setSelection(floorIndex));
    ready.set(false);
    while (SystemClock.elapsedRealtime() < deadline) {
      instrumentation.runOnMainSync(() -> {
        Spinner nodes = dialog.findViewById(R.id.record_start_node);
        for (int i = 0; i < nodes.getCount(); i++) if (nodes.getItemAtPosition(i).toString().contains("[" + nodeId + "]")) {
          nodes.setSelection(i); ready.set(true); return;
        }
      });
      if (ready.get()) break;
      SystemClock.sleep(50);
    }
    assertTrue("Missing recording start node " + nodeId, ready.get());
    instrumentation.waitForIdleSync();
    instrumentation.runOnMainSync(() -> {
      assertTrue(dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled());
      assertTrue(((TextView) dialog.findViewById(R.id.record_start_info)).getText().toString().contains(nodeId));
      dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
    });
  }
  private void play(ArRecordingStore.Entry entry) {
    instrumentation.runOnMainSync(() -> {
      try {
        Method method = HelloArActivity.class.getDeclaredMethod("switchDataset", ArRecordingStore.Entry.class);
        method.setAccessible(true); method.invoke(activity, entry);
      } catch (Exception e) { throw new AssertionError(e); }
    });
  }
  private void awaitMode(ArDatasetController.Mode expected) throws Exception {
    long deadline = SystemClock.elapsedRealtime() + 7000;
    while (SystemClock.elapsedRealtime() < deadline) {
      if (controller().mode() == expected) return;
      SystemClock.sleep(50);
    }
    assertEquals(expected, controller().mode());
  }
  private void assertFrames() throws Exception {
    onGl(() -> {
      Session session = (Session) field(activity, "session");
      Frame frame = session.update();
      assertTrue("ARCore frame timestamp", frame.getTimestamp() > 0);
      float[] pose = frame.getCamera().getPose().getTranslation();
      for (float coordinate : pose) assertTrue(Float.isFinite(coordinate));
      assertNotNull(field(activity, "routeShader"));
      assertNotNull(field(activity, "routeMesh"));
      if (controller().mode() == ArDatasetController.Mode.PLAYBACK)
        assertNotEquals(PlaybackStatus.NONE, session.getPlaybackStatus());
      android.util.Log.i("ArDatasetTest", "mode=" + controller().mode() + " tracking="
          + frame.getCamera().getTrackingState() + " timestamp=" + frame.getTimestamp());
    });
    NavigationTest.assertRouteRendersOnGlThread(activity);
  }

  private void awaitMapReference() throws Exception {
    long deadline = SystemClock.elapsedRealtime() + 5000;
    AtomicBoolean restored = new AtomicBoolean();
    while (SystemClock.elapsedRealtime() < deadline) {
      onGl(() -> restored.set(controller().mapReferenceFor(MapRouteActivity.MUNHWAGWAN_MAP) != null));
      if (restored.get()) return;
      SystemClock.sleep(100);
    }
    fail("Recorded door-to-corridor map alignment did not restore during ARCore playback");
  }

  @Test public void recordedSensorRelationRestoresMapOrientationAfterWorldRotation() {
    float half = (float) Math.sqrt(.5);
    Pose map = Pose.makeRotation(0, half, 0, half);
    Pose sensor = Pose.makeRotation(half, 0, 0, half).compose(Pose.makeRotation(0, 0, half, half));
    Pose relative = map.inverse().compose(sensor);
    Pose playbackWorld = Pose.makeRotation(0, -half, 0, half);
    Pose restored = playbackWorld.compose(sensor).compose(relative.inverse());
    float[] expected = playbackWorld.compose(map).getRotationQuaternion();
    float[] actual = restored.getRotationQuaternion();
    for (int i = 0; i < 4; i++) assertEquals(expected[i], actual[i], 1e-5);
  }
  private interface CheckedRunnable { void run() throws Exception; }
  private void onGl(CheckedRunnable action) throws Exception {
    CountDownLatch done = new CountDownLatch(1); AtomicReference<Throwable> failure = new AtomicReference<>();
    ((GLSurfaceView) activity.findViewById(R.id.surfaceview)).queueEvent(() -> {
      try { action.run(); } catch (Throwable e) { failure.set(e); } finally { done.countDown(); }
    });
    assertTrue("GL command timed out", done.await(10, TimeUnit.SECONDS));
    if (failure.get() != null) throw new AssertionError(failure.get());
  }
  private void cleanup() throws Exception {
    if (activity == null) return;
    instrumentation.runOnMainSync(activity::finish); instrumentation.waitForIdleSync();
    ArRecordingStore store = new ArRecordingStore(instrumentation.getTargetContext().getFilesDir());
    for (ArRecordingStore.Entry entry : store.list()) if (testIds.contains(entry.id)) store.delete(entry);
    if (originalStartPreferences != null) {
      android.content.SharedPreferences.Editor editor = instrumentation.getTargetContext()
          .getSharedPreferences("recording_start", 0).edit().clear();
      for (Map.Entry<String, ?> value : originalStartPreferences.entrySet())
        if (value.getValue() instanceof String) editor.putString(value.getKey(), (String) value.getValue());
      editor.commit();
    }
  }
  private static Object field(Object object, String name) throws Exception {
    Field field = object.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(object);
  }
}
