package com.google.ar.core.examples.java.helloar;

import android.net.Uri;
import android.os.SystemClock;
import com.google.ar.core.*;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.UUID;
import java.util.function.LongSupplier;

/** Dataset source and recording lifecycle, independent of route/navigation logic.
 * Call on the GL thread, or the main thread only after GLSurfaceView.onPause has returned.
 */
final class ArDatasetController {
  enum Mode { LIVE, RECORDING, PLAYBACK }
  interface Listener { void changed(); void error(String message); }
  static final UUID HEADING_TRACK = UUID.fromString("908c787f-1316-4427-ae34-c76f1a33b3ae");
  static final UUID MAP_ORIENTATION_TRACK = UUID.fromString("908c787f-1316-4427-ae34-c76f1a33b3af");
  private final ArRecordingStore store;
  private final HeadingSensor sensor;
  private final Listener listener;
  private final LongSupplier availableSpace;
  private volatile Mode mode = Mode.LIVE;
  private volatile ArRecordingStore.Entry active;
  private volatile boolean finished;
  private long startedElapsed, lastStorageCheck, lastRecordedFrame;
  private float[] frameHeading;
  private Anchor mapReferenceAnchor;
  private String mapReferenceAsset;
  private long lastMapFrame;

  ArDatasetController(ArRecordingStore store, HeadingSensor sensor, Listener listener) {
    this(store, sensor, listener, store.directory::getUsableSpace);
  }
  ArDatasetController(ArRecordingStore store, HeadingSensor sensor, Listener listener, LongSupplier availableSpace) {
    this.store = store; this.sensor = sensor; this.listener = listener; this.availableSpace = availableSpace;
  }
  Mode mode() { return mode; }
  ArRecordingStore.Entry active() { return active; }
  boolean finished() { return finished; }

  void startRecording(Session session) {
    startRecording(session, null);
  }

  void startRecording(Session session, RecordingStartPoint startPoint) {
    startRecording(session, startPoint, null);
  }

  void startRecording(Session session, RecordingStartPoint startPoint, Frame frame) {
    if (mode != Mode.LIVE) { listener.error("실시간 AR 모드에서만 녹화할 수 있습니다."); return; }
    ArRecordingStore.Entry entry = null;
    Anchor reference = null;
    try {
      if (startPoint != null && MapOrientation.usesSensor(startPoint.mapAsset)) {
        if (frame == null || frame.getCamera().getTrackingState() != TrackingState.TRACKING)
          throw new IOException("위치 추적이 안정된 뒤 다시 녹화를 시작하세요.");
        Double yaw = configuredSensorYaw(startPoint.mapAsset, frame);
        if (yaw == null) throw new IOException("방위 센서가 불안정합니다. 주변 금속·자석에서 떨어져 폰을 천천히 8자로 움직인 뒤 다시 시작하세요.");
        reference = session.createAnchor(referencePose(frame.getCamera().getPose(), yaw));
      } else if (startPoint != null && startPoint.direction != null) {
        if (frame == null || frame.getCamera().getTrackingState() != TrackingState.TRACKING)
          throw new IOException("위치 추적이 안정된 뒤 문에서 복도 쪽을 바라보고 다시 녹화를 시작하세요.");
        float[] camera = new float[16];
        frame.getCamera().getDisplayOrientedPose().toMatrix(camera, 0);
        double cameraYaw;
        try { cameraYaw = MapAlignment.forwardYaw(camera); }
        catch (IllegalArgumentException e) {
          throw new IOException("카메라를 수평에 가깝게 들고 문에서 복도 쪽을 바라보세요.");
        }
        reference = session.createAnchor(referencePose(frame.getCamera().getPose(),
            MapAlignment.referenceYawForEntry(cameraYaw, startPoint.direction.canonicalYawDegrees)));
      }
      entry = store.create(availableSpace.getAsLong(), startPoint);
      Track heading = new Track(session).setId(HEADING_TRACK)
          .setMimeType("application/x-arnav-heading-v1");
      Track orientation = new Track(session).setId(MAP_ORIENTATION_TRACK)
          .setMimeType("application/x-arnav-map-orientation-v1");
      session.startRecording(new RecordingConfig(session)
          .setMp4DatasetUri(Uri.fromFile(store.file(entry))).setAutoStopOnPause(true)
          .addTrack(heading).addTrack(orientation));
      if (reference != null) {
        clearMapReference(); mapReferenceAnchor = reference; mapReferenceAsset = startPoint.mapAsset;
        reference = null;
      }
      active = entry;
      startedElapsed = SystemClock.elapsedRealtime();
      lastStorageCheck = startedElapsed;
      lastRecordedFrame = 0;
      lastMapFrame = 0;
      mode = Mode.RECORDING;
      listener.changed();
    } catch (Exception e) {
      android.util.Log.w("ArDatasetController", "Unable to start dataset recording", e);
      if (reference != null) reference.detach();
      if (entry != null) try { store.finish(entry, 0, e.toString()); } catch (IOException ignored) { }
      listener.error("AR 녹화를 시작하지 못했습니다: " + e.getMessage());
    }
  }

  void stopRecording(Session session) { stopRecording(session, null); }

  private void stopRecording(Session session, String failure) {
    if (mode != Mode.RECORDING) return;
    ArRecordingStore.Entry entry = active;
    try { session.stopRecording(); }
    catch (Exception e) { failure = "녹화 파일을 정상 종료하지 못했습니다: " + e.getMessage(); }
    try { store.finish(entry, SystemClock.elapsedRealtime() - startedElapsed, failure); }
    catch (IOException e) { failure = "녹화 정보를 저장하지 못했습니다: " + e.getMessage(); }
    active = null; mode = Mode.LIVE;
    listener.changed();
    if (failure != null) listener.error(failure);
  }

  void enterPlayback(ArRecordingStore.Entry entry) throws IOException {
    if (mode != Mode.LIVE) throw new IOException("녹화 또는 재생을 먼저 종료하세요.");
    if (!entry.playable() || !store.file(entry).isFile()) throw new IOException("정상 종료된 녹화 파일이 아닙니다.");
    clearMapReference();
    active = entry; finished = false; frameHeading = null; mode = Mode.PLAYBACK;
    listener.changed();
  }
  void exitPlayback() {
    if (mode != Mode.PLAYBACK) return;
    clearMapReference();
    active = null; finished = false; frameHeading = null; mode = Mode.LIVE;
    listener.changed();
  }
  void configureNewSession(Session session) throws IOException {
    clearMapReference();
    if (mode == Mode.PLAYBACK) session.setPlaybackDatasetUri(Uri.fromFile(store.file(active)));
    finished = false; frameHeading = null;
  }

  void onFrame(Session session, Frame frame) {
    if (mode == Mode.PLAYBACK) {
      // Never fall back to live IMU values while replaying a recorded camera/pose.
      frameHeading = null;
      for (TrackData data : frame.getUpdatedTrackData(HEADING_TRACK)) {
        ByteBuffer bytes = data.getData().duplicate().order(ByteOrder.LITTLE_ENDIAN);
        if (bytes.remaining() != 9 * Float.BYTES) continue;
        float[] matrix = new float[9];
        boolean valid = true;
        for (int i = 0; i < 9; i++) { matrix[i] = bytes.getFloat(); valid &= Float.isFinite(matrix[i]); }
        if (valid && Math.abs(data.getFrameTimestamp() - frame.getTimestamp()) <= 50_000_000L)
          frameHeading = matrix;
      }
      restoreMapReference(session, frame);
      if (!finished && session.getPlaybackStatus() == PlaybackStatus.FINISHED) {
        finished = true; listener.changed();
      }
      return;
    }
    frameHeading = sensor.at(frame.getTimestamp());
    if (mode != Mode.RECORDING) return;
    if (session.getRecordingStatus() != RecordingStatus.OK) {
      stopRecording(session, "ARCore 녹화 오류가 발생했습니다. 저장 공간을 확인하세요."); return;
    }
    long now = SystemClock.elapsedRealtime();
    if (now - lastStorageCheck >= 1000) {
      lastStorageCheck = now;
      if (availableSpace.getAsLong() < ArRecordingStore.STOP_RESERVE_BYTES) {
        stopRecording(session);
        listener.error("저장 공간이 부족하여 녹화를 종료했습니다."); return;
      }
    }
    if (frameHeading != null && frame.getTimestamp() != 0 && frame.getTimestamp() != lastRecordedFrame) {
      ByteBuffer bytes = ByteBuffer.allocateDirect(9 * Float.BYTES).order(ByteOrder.LITTLE_ENDIAN);
      for (float value : frameHeading) bytes.putFloat(value);
      bytes.flip();
      try { frame.recordTrackData(HEADING_TRACK, bytes); lastRecordedFrame = frame.getTimestamp(); }
      catch (Exception e) { stopRecording(session, "방위 데이터 녹화에 실패했습니다: " + e.getMessage()); }
    }
    if (mode == Mode.RECORDING && hasOrientation(active)
        && mapReferenceAnchor != null && mapReferenceAnchor.getTrackingState() == TrackingState.TRACKING
        && frame.getCamera().getTrackingState() == TrackingState.TRACKING
        && frame.getTimestamp() != 0 && frame.getTimestamp() != lastMapFrame) {
      float[] relativeSensor = mapReferenceAnchor.getPose().inverse()
          .compose(frame.getAndroidSensorPose()).getRotationQuaternion();
      ByteBuffer bytes = ByteBuffer.allocateDirect(4 * Float.BYTES).order(ByteOrder.LITTLE_ENDIAN);
      for (float value : relativeSensor) bytes.putFloat(value);
      bytes.flip();
      try { frame.recordTrackData(MAP_ORIENTATION_TRACK, bytes); lastMapFrame = frame.getTimestamp(); }
      catch (Exception e) { stopRecording(session, "출발 방향 기록에 실패했습니다: " + e.getMessage()); }
    }
  }

  float[] headingForFrame() { return frameHeading; }

  Double configuredSensorYaw(String asset, Frame frame) {
    if (!MapOrientation.usesSensor(asset) || frameHeading == null) return null;
    float[] ar = new float[16];
    frame.getAndroidSensorPose().toMatrix(ar, 0);
    try { return MapOrientation.yawFromNorth(MapAlignment.northYaw(frameHeading, ar)); }
    catch (IllegalArgumentException e) { return null; }
  }

  Pose mapReferenceFor(String asset) {
    if (mapReferenceAnchor == null || !asset.equals(mapReferenceAsset)) return null;
    if (mapReferenceAnchor.getTrackingState() == TrackingState.STOPPED) { clearMapReference(); return null; }
    return mapReferenceAnchor.getTrackingState() == TrackingState.TRACKING ? mapReferenceAnchor.getPose() : null;
  }
  boolean hasMapReference(String asset) { return mapReferenceAnchor != null && asset.equals(mapReferenceAsset); }
  boolean expectsMapReference(String asset) {
    return mode == Mode.PLAYBACK && active != null && active.startPoint != null
        && hasOrientation(active) && asset.equals(active.startPoint.mapAsset)
        && (!MapOrientation.usesSensor(asset) || MapOrientation.matchesRecording(active));
  }
  void clearMapReference() {
    if (mapReferenceAnchor != null) { mapReferenceAnchor.detach(); mapReferenceAnchor = null; }
    mapReferenceAsset = null;
  }

  private void restoreMapReference(Session session, Frame frame) {
    if (mapReferenceAnchor != null || active == null || active.startPoint == null
        || !hasOrientation(active) || frame.getCamera().getTrackingState() != TrackingState.TRACKING) return;
    // Old door-facing recordings are not a measurement of the new magnetic-north building heading.
    // Recompute from their recorded magnetic track, never from the current phone's live sensors.
    if (MapOrientation.usesSensor(active.startPoint.mapAsset) && !MapOrientation.matchesRecording(active)) {
      Double yaw = configuredSensorYaw(active.startPoint.mapAsset, frame);
      if (yaw != null) {
        mapReferenceAnchor = session.createAnchor(referencePose(frame.getCamera().getPose(), yaw));
        mapReferenceAsset = active.startPoint.mapAsset;
        listener.changed();
      }
      return;
    }
    for (TrackData data : frame.getUpdatedTrackData(MAP_ORIENTATION_TRACK)) {
      if (Math.abs(data.getFrameTimestamp() - frame.getTimestamp()) > 50_000_000L) continue;
      ByteBuffer bytes = data.getData().duplicate().order(ByteOrder.LITTLE_ENDIAN);
      if (bytes.remaining() != 4 * Float.BYTES) continue;
      float[] rotation = new float[4];
      double norm = 0;
      for (int i = 0; i < 4; i++) { rotation[i] = bytes.getFloat(); norm += rotation[i] * rotation[i]; }
      if (!Double.isFinite(norm) || norm < 0.99 || norm > 1.01) continue;
      Pose map = frame.getAndroidSensorPose().compose(Pose.makeRotation(rotation).inverse());
      float[] axis = map.getZAxis();
      mapReferenceAnchor = session.createAnchor(referencePose(frame.getCamera().getPose(), Math.atan2(axis[0], axis[2])));
      mapReferenceAsset = active.startPoint.mapAsset;
      listener.changed();
      break;
    }
  }

  private static boolean hasOrientation(ArRecordingStore.Entry entry) {
    return entry != null && entry.startPoint != null
        && (entry.startPoint.direction != null || MapOrientation.matchesRecording(entry));
  }

  private static Pose referencePose(Pose position, double yaw) {
    return new Pose(position.getTranslation(),
        new float[] {0, (float) Math.sin(yaw / 2), 0, (float) Math.cos(yaw / 2)});
  }
}
