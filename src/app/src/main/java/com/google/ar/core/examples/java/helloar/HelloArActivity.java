/*
 * Copyright 2017 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.ar.core.examples.java.helloar;

import android.content.Intent;
import android.content.SharedPreferences;
import android.text.InputType;
import android.widget.EditText;
import androidx.appcompat.app.AlertDialog;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.ar.core.Anchor;
import com.google.ar.core.ArCoreApk;
import com.google.ar.core.ArCoreApk.Availability;
import com.google.ar.core.Camera;
import com.google.ar.core.Config;
import com.google.ar.core.Frame;
import com.google.ar.core.Pose;
import com.google.ar.core.Plane;
import com.google.ar.core.Session;
import com.google.ar.core.TrackingState;
import com.google.ar.core.examples.java.common.helpers.CameraPermissionHelper;
import com.google.ar.core.examples.java.common.helpers.DisplayRotationHelper;
import com.google.ar.core.examples.java.common.helpers.FullScreenHelper;
import com.google.ar.core.examples.java.common.helpers.SnackbarHelper;
import com.google.ar.core.examples.java.common.helpers.TrackingStateHelper;
import com.google.ar.core.examples.java.common.samplerender.Framebuffer;
import com.google.ar.core.examples.java.common.samplerender.Mesh;
import com.google.ar.core.examples.java.common.samplerender.SampleRender;
import com.google.ar.core.examples.java.common.samplerender.Shader;
import com.google.ar.core.examples.java.common.samplerender.VertexBuffer;
import com.google.ar.core.examples.java.common.samplerender.arcore.BackgroundRenderer;
import com.google.ar.core.exceptions.CameraNotAvailableException;
import com.google.ar.core.exceptions.NotTrackingException;
import com.google.ar.core.exceptions.UnavailableApkTooOldException;
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException;
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException;
import com.google.ar.core.exceptions.UnavailableSdkTooOldException;
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException;
import java.io.IOException;
import java.util.Arrays;
import java.util.Locale;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;
import android.view.View;

/**
 * Indoor relative navigation prototype based on the official hello_ar_java renderer.
 * A single origin anchor renders either the original test route or a selected map route in meters.
 */
public class HelloArActivity extends AppCompatActivity implements SampleRender.Renderer {

  private static final String TAG = HelloArActivity.class.getSimpleName();

  private static final float ARRIVAL_DISTANCE = 0.8f;
  private static final long DEBUG_UPDATE_INTERVAL_MS = 200;

  // Only these requests cross from the UI thread to the GL render thread.
  private volatile boolean startNavigationRequested;
  private volatile boolean recaptureEntryRequested;
  private volatile boolean resetNavigationRequested;
  private final AtomicReference<ArRoutePlan> pendingMapRoute = new AtomicReference<>();
  private static final int MAP_ROUTE_REQUEST = 1001;
  private String selectedMapRouteName;

  // Navigation state and all ARCore pose/anchor operations belong to the GL thread.
  private ArRecordingStore recordingStore;
  private ArDatasetController datasets;
  private AlertDialog recordingStartDialog;
  private static final class RecordingRequest {
    final RecordingStartPoint point;
    RecordingRequest(RecordingStartPoint point) { this.point = point; }
  }
  private final AtomicReference<RecordingRequest> pendingRecording = new AtomicReference<>();
  private String datasetAlignmentOverride;
  private boolean activityResumed;
  private final AtomicBoolean sessionFailurePosted = new AtomicBoolean();
  private HeadingSensor headingSensor;
  private SharedPreferences alignmentPreferences;
  private final AtomicReference<Double> pendingBearing = new AtomicReference<>();
  private volatile boolean calibrateRequested;
  private String alignmentKey = "demo";
  private volatile String uiAlignmentKey = "demo";
  private Anchor directionAnchor;
  private Anchor routeEntryAnchor;
  private Double routeEntryYawDegrees;
  private String routeEntryDirectionLabel;
  private boolean routeUseManualAlignment;
  private Double referenceBearing;
  private String alignmentStatus;
  private Anchor originAnchor;
  private boolean navigationStarted;
  private boolean navigationCompleted;
  private int currentWaypointIndex;
  private long lastDebugUpdateTime;
  private TextView debugText;

  private List<Waypoint> route = Arrays.asList(
      new Waypoint(0.0f, -0.5f, -2.0f),
      new Waypoint(0.0f, -0.5f, -4.0f),
      new Waypoint(2.0f, -0.5f, -4.0f));
  private RouteLineGeometry routeLineGeometry = new RouteLineGeometry(route);

  private static final float Z_NEAR = 0.1f;
  private static final float Z_FAR = 100f;

  // Rendering. The Renderers are created here, and initialized when the GL surface is created.
  private GLSurfaceView surfaceView;

  private boolean installRequested;

  private Session session;
  private final SnackbarHelper messageSnackbarHelper = new SnackbarHelper();
  private DisplayRotationHelper displayRotationHelper;
  private final TrackingStateHelper trackingStateHelper = new TrackingStateHelper(this);
  private SampleRender render;

  private BackgroundRenderer backgroundRenderer;
  private Framebuffer virtualSceneFramebuffer;
  private boolean rendererReady;
  private boolean hasSetTextureNames = false;

  private VertexBuffer routePositionBuffer;
  private VertexBuffer routeColorBuffer;
  private Mesh routeMesh;
  private Shader routeShader;

  // Temporary matrix allocated here to reduce number of allocations for each frame.
  private final float[] modelMatrix = new float[16];
  private final float[] viewMatrix = new float[16];
  private final float[] projectionMatrix = new float[16];
  private final float[] modelViewMatrix = new float[16]; // view x model
  private final float[] modelViewProjectionMatrix = new float[16]; // projection x view x model
  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
    surfaceView = findViewById(R.id.surfaceview);
    debugText = findViewById(R.id.debug_text);
    headingSensor = new HeadingSensor(this);
    try {
      recordingStore = new ArRecordingStore(getFilesDir());
      recordingStore.recoverInterrupted();
      datasets = new ArDatasetController(recordingStore, headingSensor, new ArDatasetController.Listener() {
        @Override public void changed() { runOnUiThread(() -> updateDatasetControls()); }
        @Override public void error(String message) {
          runOnUiThread(() -> Toast.makeText(HelloArActivity.this, message, Toast.LENGTH_LONG).show());
        }
      });
      findViewById(R.id.record_ar_button).setOnClickListener(v -> toggleRecording());
      findViewById(R.id.recordings_button).setOnClickListener(v ->
          ArRecordingDialogs.show(this, recordingStore, datasets, this::switchDataset));
      findViewById(R.id.stop_playback_button).setOnClickListener(v -> switchDataset(null));
    } catch (IOException e) {
      Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
    }
    updateDatasetControls();
    alignmentPreferences = getSharedPreferences("map_alignment", MODE_PRIVATE);
    loadReferenceBearing();
    findViewById(R.id.calibrate_button).setOnClickListener(v -> showAlignmentDialog());
    Button startButton = findViewById(R.id.start_nav_button);
    Button resetButton = findViewById(R.id.reset_nav_button);
    startButton.setOnClickListener(v -> {
      recaptureEntryRequested = true;
      startNavigationRequested = true;
    });
    resetButton.setOnClickListener(v -> resetNavigationRequested = true);
    findViewById(R.id.map_route_button).setOnClickListener(v ->
        startActivityForResult(new Intent(this, MapRouteActivity.class), MAP_ROUTE_REQUEST));
    displayRotationHelper = new DisplayRotationHelper(/* context= */ this);

    // Set up renderer.
    render = new SampleRender(surfaceView, this, getAssets());

    installRequested = false;
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    if (requestCode == MAP_ROUTE_REQUEST && resultCode == RESULT_OK && data != null) {
      try {
        pendingMapRoute.set(ArRoutePlan.decode(data.getStringExtra(MapRouteActivity.EXTRA_NAME),
            data.getFloatArrayExtra(MapRouteActivity.EXTRA_POINTS),
            data.getStringExtra(MapRouteActivity.EXTRA_REFERENCE),
            data.hasExtra(MapRouteActivity.EXTRA_ENTRY_YAW)
                ? data.getDoubleExtra(MapRouteActivity.EXTRA_ENTRY_YAW, Double.NaN) : null,
            data.getStringExtra(MapRouteActivity.EXTRA_ENTRY_DIRECTION)));
      } catch (IllegalArgumentException e) {
        Toast.makeText(this, "지도 경로를 적용할 수 없습니다: " + e.getMessage(), Toast.LENGTH_LONG).show();
      }
    }
  }

  @Override
  protected void onDestroy() {
    if (session != null) {
      // onPause has already stopped the GL thread before Activity destruction.
      detachOriginAnchor();
      detachRouteEntryAnchor();
      detachDirectionAnchor();
      if (datasets != null) datasets.clearMapReference();
      // Explicitly close ARCore Session to release native resources.
      // Review the API reference for important considerations before calling close() in apps with
      // more complicated lifecycle requirements:
      // https://developers.google.com/ar/reference/java/arcore/reference/com/google/ar/core/Session#close()
      session.close();
      session = null;
    }

    super.onDestroy();
  }

  @Override
  protected void onResume() {
    super.onResume();
    activityResumed = true;
    resumeArSession();
  }

  private void resumeArSession() {
    boolean newSession = session == null;
    if (session == null) {
      Exception exception = null;
      String message = null;
      try {
        // Always check the latest availability.
        Availability availability = ArCoreApk.getInstance().checkAvailability(this);

        // In all other cases, try to install ARCore and handle installation failures.
        if (availability != Availability.SUPPORTED_INSTALLED) {
          switch (ArCoreApk.getInstance().requestInstall(this, !installRequested)) {
            case INSTALL_REQUESTED:
              installRequested = true;
              return;
            case INSTALLED:
              break;
          }
        }

        // ARCore requires camera permissions to operate. If we did not yet obtain runtime
        // permission on Android M and above, now is a good time to ask the user for it.
        if (!CameraPermissionHelper.hasCameraPermission(this)) {
          CameraPermissionHelper.requestCameraPermission(this);
          return;
        }

        // Create the session.
        detachOriginAnchor();
        detachDirectionAnchor();
        navigationStarted = false;
        navigationCompleted = false;
        session = new Session(/* context= */ this);
        hasSetTextureNames = false;
      } catch (UnavailableArcoreNotInstalledException
          | UnavailableUserDeclinedInstallationException e) {
        message = "Please install ARCore";
        exception = e;
      } catch (UnavailableApkTooOldException e) {
        message = "Please update ARCore";
        exception = e;
      } catch (UnavailableSdkTooOldException e) {
        message = "Please update this app";
        exception = e;
      } catch (UnavailableDeviceNotCompatibleException e) {
        message = "This device does not support AR";
        exception = e;
      } catch (Exception e) {
        message = "Failed to create AR session";
        exception = e;
      }

      if (message != null) {
        messageSnackbarHelper.showError(this, message);
        Log.e(TAG, "Exception creating session", exception);
        return;
      }
    }

    // Note that order matters - see the note in onPause(), the reverse applies here.
    try {
      configureSession();
      if (newSession && datasets != null) datasets.configureNewSession(session);
      session.resume();
    } catch (Exception e) {
      boolean playback = datasets != null && datasets.mode() == ArDatasetController.Mode.PLAYBACK;
      if (datasets != null) datasets.stopRecording(session);
      releaseArSession();
      if (playback) {
        datasets.exitPlayback();
        Toast.makeText(this, "녹화 파일을 재생하지 못했습니다. 실시간 AR로 돌아갑니다: "
            + e.getMessage(), Toast.LENGTH_LONG).show();
        resumeArSession();
      } else {
        messageSnackbarHelper.showError(this, "AR 세션을 시작하지 못했습니다: " + e.getMessage());
      }
      return;
    }

    if (datasets == null || datasets.mode() != ArDatasetController.Mode.PLAYBACK) headingSensor.resume();
    surfaceView.onResume();
    displayRotationHelper.onResume();
  }

  @Override
  public void onPause() {
    super.onPause();
    activityResumed = false;
    pendingRecording.set(null);
    if (recordingStartDialog != null) recordingStartDialog.dismiss();
    headingSensor.pause();
    if (session != null) {
      // Note that the order matters - GLSurfaceView is paused first so that it does not try
      // to query the session. If Session is paused before GLSurfaceView, GLSurfaceView may
      // still call session.update() and get a SessionPausedException.
      displayRotationHelper.onPause();
      surfaceView.onPause();
      if (datasets != null) datasets.stopRecording(session);
      session.pause();
      // Playback discards frames while paused. Reopen from the start on resume.
      if (datasets != null && datasets.mode() == ArDatasetController.Mode.PLAYBACK) releaseArSession();
    }
  }

  @Override
  public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
    super.onRequestPermissionsResult(requestCode, permissions, results);
    if (!CameraPermissionHelper.hasCameraPermission(this)) {
      // Use toast instead of snackbar here since the activity will exit.
      Toast.makeText(this, "Camera permission is needed to run this application", Toast.LENGTH_LONG)
          .show();
      if (!CameraPermissionHelper.shouldShowRequestPermissionRationale(this)) {
        // Permission denied with checking "Do not ask again".
        CameraPermissionHelper.launchPermissionSettings(this);
      }
      finish();
    }
  }

  @Override
  public void onWindowFocusChanged(boolean hasFocus) {
    super.onWindowFocusChanged(hasFocus);
    FullScreenHelper.setFullScreenOnWindowFocusChanged(this, hasFocus);
  }

  @Override
  public void onSurfaceCreated(SampleRender render) {
    rendererReady = false;
    // Rebind textures if Android has recreated the OpenGL context after a pause.
    hasSetTextureNames = false;
    // Prepare the rendering objects. This involves reading shaders and 3D model files, so may throw
    // an IOException.
    try {
      backgroundRenderer = new BackgroundRenderer(render);
      // These calls also create the shaders, even with both depth options disabled.
      backgroundRenderer.setUseDepthVisualization(render, false);
      backgroundRenderer.setUseOcclusion(render, false);
      virtualSceneFramebuffer = new Framebuffer(render, /* width= */ 1, /* height= */ 1);

      routePositionBuffer = new VertexBuffer(render, 3, null);
      routeColorBuffer = new VertexBuffer(render, 4, null);
      routeMesh = new Mesh(render, Mesh.PrimitiveMode.TRIANGLES, null,
          new VertexBuffer[] {routePositionBuffer, routeColorBuffer});
      routeShader = Shader.createFromAssets(
          render, "shaders/navigation_route.vert", "shaders/navigation_route.frag", null)
          .setCullFace(false)
          .setDepthTest(true)
          .setDepthWrite(true);
      // A recreated GL context has empty GPU buffers even if CPU geometry is cached.
      routeLineGeometry.invalidate();
      rendererReady = true;
    } catch (IOException e) {
      Log.e(TAG, "Failed to read a required asset file", e);
      messageSnackbarHelper.showError(this, "Failed to read a required asset file: " + e);
    }
  }

  @Override
  public void onSurfaceChanged(SampleRender render, int width, int height) {
    displayRotationHelper.onSurfaceChanged(width, height);
    if (virtualSceneFramebuffer != null) {
      virtualSceneFramebuffer.resize(width, height);
    }
  }

  @Override
  public void onDrawFrame(SampleRender render) {
    if (session == null || !rendererReady) {
      return;
    }

    // Texture names should only be set once on a GL thread unless they change. This is done during
    // onDrawFrame rather than onSurfaceCreated since the session is not guaranteed to have been
    // initialized during the execution of onSurfaceCreated.
    if (!hasSetTextureNames) {
      session.setCameraTextureNames(
          new int[] {backgroundRenderer.getCameraColorTexture().getTextureId()});
      hasSetTextureNames = true;
    }

    // -- Update per-frame state

    // Notify ARCore session that the view size changed so that the perspective matrix and
    // the video background can be properly adjusted.
    displayRotationHelper.updateSessionIfNeeded(session);

    // Obtain the current frame from the AR Session. When the configuration is set to
    // UpdateMode.BLOCKING (it is by default), this will throttle the rendering to the
    // camera framerate.
    Frame frame;
    try {
      frame = session.update();
    } catch (CameraNotAvailableException e) {
      handleSessionFailure(e);
      return;
    } catch (RuntimeException e) {
      handleSessionFailure(e);
      return;
    }
    try {
      if (datasets != null) datasets.onFrame(session, frame);
    } catch (RuntimeException e) {
      handleSessionFailure(e);
      return;
    }
    sessionFailurePosted.set(false);
    Camera camera = frame.getCamera();

    // Retain the sample camera background without depth/occlusion settings.
    backgroundRenderer.updateDisplayGeometry(frame);
    processRecordingStart(frame);
    processNavigationRequests(frame);
    updateNavigation(camera);

    // Keep the screen unlocked while tracking, but allow it to lock when tracking stops.
    trackingStateHelper.updateKeepScreenOnFlag(camera.getTrackingState());

    // -- Draw background

    if (frame.getTimestamp() != 0) {
      // Suppress rendering if the camera did not produce the first frame yet. This is to avoid
      // drawing possible leftover data from previous sessions if the texture is reused.
      backgroundRenderer.drawBackground(render);
    }

    // If not tracking, don't draw 3D objects.
    if (camera.getTrackingState() != TrackingState.TRACKING) {
      return;
    }

    // Get projection matrix.
    camera.getProjectionMatrix(projectionMatrix, 0, Z_NEAR, Z_FAR);

    // Get camera matrix and draw.
    camera.getViewMatrix(viewMatrix, 0);

    // A navigation ribbon in origin-local coordinates, recomposed into world space each frame.
    render.clear(virtualSceneFramebuffer, 0f, 0f, 0f, 0f);
    drawNavigationRoute(render, camera);

    // Compose the virtual scene with the background.
    backgroundRenderer.drawVirtualScene(render, virtualSceneFramebuffer, Z_NEAR, Z_FAR);
  }

  private void drawNavigationRoute(SampleRender render, Camera camera) {
    if (!navigationStarted || originAnchor == null
        || originAnchor.getTrackingState() != TrackingState.TRACKING) {
      return;
    }
    Pose relativePose = getRelativeCameraPose(camera);
    if (relativePose == null) {
      return;
    }
    if (routeLineGeometry.update(currentWaypointIndex, relativePose.tx(), relativePose.tz())) {
      routePositionBuffer.set(routeLineGeometry.getPositions());
      routeColorBuffer.set(routeLineGeometry.getColors());
    }
    if (routeLineGeometry.getVertexCount() == 0) {
      return;
    }

    // Geometry stays relative to the anchor, never attached to the moving camera.
    levelAnchorPose().toMatrix(modelMatrix, 0);
    Matrix.multiplyMM(modelViewMatrix, 0, viewMatrix, 0, modelMatrix, 0);
    Matrix.multiplyMM(modelViewProjectionMatrix, 0, projectionMatrix, 0, modelViewMatrix, 0);
    routeShader.setMat4("u_ModelViewProjection", modelViewProjectionMatrix);
    render.draw(routeMesh, routeShader, virtualSceneFramebuffer);
  }

  private void processNavigationRequests(Frame frame) {
    Camera camera = frame.getCamera();
    if (recaptureEntryRequested) {
      recaptureEntryRequested = false;
      detachRouteEntryAnchor();
    }
    ArRoutePlan plan = pendingMapRoute.getAndSet(null);
    if (plan != null) {
      resetNavigation();
      startNavigationRequested = false;
      route = plan.waypoints;
      routeEntryYawDegrees = plan.entryYawDegrees;
      routeEntryDirectionLabel = plan.entryDirectionLabel;
      routeUseManualAlignment = false;
      selectedMapRouteName = plan.name;
      if (!alignmentKey.equals(plan.referenceId)) {
        detachDirectionAnchor();
        alignmentKey = plan.referenceId;
        loadReferenceBearing();
      }
      uiAlignmentKey = alignmentKey;
      alignmentStatus = null;
      calibrateRequested = false;
      pendingBearing.set(null);
      routeLineGeometry = new RouteLineGeometry(route);
      Log.i(TAG, "Map route applied: " + plan.name + ", waypoints=" + route.size());
    }
    if (resetNavigationRequested) {
      resetNavigationRequested = false;
      startNavigationRequested = false;
      resetNavigation();
      Log.i(TAG, "Navigation reset");
    }

    Double bearing = pendingBearing.getAndSet(null);
    if (bearing != null) {
      routeUseManualAlignment = true;
      datasetAlignmentOverride = alignmentKey;
      referenceBearing = bearing;
      detachDirectionAnchor();
      alignmentPreferences.edit().putFloat(alignmentKey, bearing.floatValue()).apply();
      resetNavigation();
      startNavigationRequested = false;
      alignmentStatus = "기준 방위를 저장했습니다. 바닥을 비춘 뒤 시작하세요.";
    }
    if (calibrateRequested) {
      calibrateRequested = false;
      if (camera.getTrackingState() != TrackingState.TRACKING) {
        alignmentStatus = "위치 추적이 안정된 뒤 방향 보정을 다시 누르세요.";
      } else {
        float[] matrix = new float[16];
        camera.getDisplayOrientedPose().toMatrix(matrix, 0);
        try {
          double manualWorldYaw = MapAlignment.forwardYaw(matrix);
          Pose position = camera.getPose();
          Anchor newDirection = session.createAnchor(yawPose(
              position.tx(), position.ty(), position.tz(), manualWorldYaw));
          detachDirectionAnchor();
          directionAnchor = newDirection;
          routeUseManualAlignment = true;
          datasetAlignmentOverride = alignmentKey;
          Double north = northYaw(frame);
          referenceBearing = north == null ? null :
              (Math.toDegrees(MapAlignment.wrap(north - manualWorldYaw)) + 360) % 360;
          SharedPreferences.Editor editor = alignmentPreferences.edit();
          if (referenceBearing == null) editor.remove(alignmentKey);
          else editor.putFloat(alignmentKey, referenceBearing.floatValue());
          editor.apply();
          resetNavigation();
          startNavigationRequested = false;
          alignmentStatus = referenceBearing == null
              ? "현재 세션의 기준 방향을 고정했습니다. 앱을 다시 열면 재보정하세요."
              : "기준 방향을 보정하고 저장했습니다.";
        } catch (NotTrackingException e) {
          alignmentStatus = "위치 추적이 안정된 뒤 방향 보정을 다시 누르세요.";
        } catch (IllegalArgumentException e) {
          alignmentStatus = "카메라를 수평에 가까운 복도 기준 방향으로 향하고 다시 보정하세요.";
        }
      }
      final String feedback = alignmentStatus;
      runOnUiThread(() -> Toast.makeText(this, feedback, Toast.LENGTH_LONG).show());
    }

    // Keep a start request pending until a reliable camera pose becomes available.
    if (!startNavigationRequested || camera.getTrackingState() != TrackingState.TRACKING) {
      return;
    }
    try {
      Double yaw = null;
      if (datasets != null && !routeUseManualAlignment && !alignmentKey.equals(datasetAlignmentOverride)) {
        Pose recordedReference = datasets.mapReferenceFor(alignmentKey);
        if (recordedReference != null) {
          float[] axis = recordedReference.getZAxis();
          yaw = Math.atan2(axis[0], axis[2]);
        } else if (datasets.hasMapReference(alignmentKey) || datasets.expectsMapReference(alignmentKey)) {
          alignmentStatus = "녹화된 출발 방향을 추적·복원하는 중입니다. 복원되지 않으면 방향 보정에서 다시 맞춰주세요.";
          return;
        }
      }
      if (yaw == null && MapOrientation.usesSensor(alignmentKey) && !routeUseManualAlignment) {
        if (routeEntryAnchor == null) {
          Double north = northYaw(frame);
          if (north == null) {
            alignmentStatus = datasets != null && datasets.mode() == ArDatasetController.Mode.PLAYBACK
                ? "녹화된 방위 데이터를 기다리는 중입니다. 데이터가 없으면 방향 보정으로 기준을 지정하세요."
                : "방위 센서가 불안정합니다. 금속·자석에서 떨어져 폰을 천천히 8자로 움직여 주세요. 문화관은 자북 기준 반시계 85.5°를 적용합니다.";
            return;
          }
          routeEntryAnchor = session.createAnchor(yawPose(camera.getPose().tx(), camera.getPose().ty(),
              camera.getPose().tz(), MapOrientation.yawFromNorth(north)));
        }
        if (routeEntryAnchor.getTrackingState() != TrackingState.TRACKING) {
          if (routeEntryAnchor.getTrackingState() == TrackingState.STOPPED) detachRouteEntryAnchor();
          alignmentStatus = "센서로 정렬한 지도 기준의 추적을 복구하는 중입니다.";
          return;
        }
        float[] axis = routeEntryAnchor.getPose().getZAxis();
        yaw = Math.atan2(axis[0], axis[2]);
      }
      if (yaw == null && routeEntryYawDegrees != null && !routeUseManualAlignment
          && !MapOrientation.usesSensor(alignmentKey)) {
        if (routeEntryAnchor == null) {
          float[] cameraMatrix = new float[16];
          camera.getDisplayOrientedPose().toMatrix(cameraMatrix, 0);
          try {
            double mapYaw = MapAlignment.referenceYawForEntry(
                MapAlignment.forwardYaw(cameraMatrix), routeEntryYawDegrees);
            routeEntryAnchor = session.createAnchor(yawPose(camera.getPose().tx(), camera.getPose().ty(),
                camera.getPose().tz(), mapYaw));
          } catch (IllegalArgumentException e) {
            startNavigationRequested = false;
            alignmentStatus = "출발 문에서 표시된 복도 쪽을 바라보고 카메라를 수평에 가깝게 든 뒤 길안내 시작을 다시 누르세요.";
            return;
          }
        }
        if (routeEntryAnchor.getTrackingState() != TrackingState.TRACKING) {
          if (routeEntryAnchor.getTrackingState() == TrackingState.STOPPED) detachRouteEntryAnchor();
          alignmentStatus = "출발 방향 추적을 복구하는 중입니다. 주변을 천천히 비춰주세요.";
          return;
        }
        float[] axis = routeEntryAnchor.getPose().getZAxis();
        yaw = Math.atan2(axis[0], axis[2]);
      }
      if (yaw == null && directionAnchor != null) {
        if (directionAnchor.getTrackingState() == TrackingState.PAUSED) {
          alignmentStatus = "기준 방향 앵커 추적을 복구하는 중입니다. 주변을 천천히 비춰주세요.";
          return;
        }
        if (directionAnchor.getTrackingState() == TrackingState.TRACKING) {
          float[] axis = directionAnchor.getPose().getZAxis();
          yaw = Math.atan2(axis[0], axis[2]);
        } else {
          detachDirectionAnchor();
        }
      }
      if (yaw == null && referenceBearing != null) {
        Double north = northYaw(frame);
        if (north != null) yaw = MapAlignment.yawForBearing(north, referenceBearing);
      }
      if (yaw == null) {
        alignmentStatus = referenceBearing == null
            ? "방향 보정에서 건물 기준 방향을 먼저 설정하세요."
            : "방위 센서가 불안정합니다. 기기를 천천히 8자로 움직이거나 복도 방향으로 재보정하세요.";
        return;
      }
      Float floorY = findFloor(camera.getPose());
      if (floorY == null) {
        alignmentStatus = "가까운 앞쪽 바닥을 천천히 비춰 수평면을 인식시켜 주세요.";
        return;
      }
      // Existing route vertices are at y=-0.5; place the ribbon 2cm above the floor.
      Pose origin = yawPose(camera.getPose().tx(), floorY + 0.52f,
          camera.getPose().tz(), yaw);
      Anchor newOrigin = session.createAnchor(origin);
      detachOriginAnchor();
      originAnchor = newOrigin;
      detachRouteEntryAnchor();
      currentWaypointIndex = 0;
      navigationStarted = true;
      alignmentStatus = null;
      navigationCompleted = false;
      startNavigationRequested = false;
      routeLineGeometry.invalidate();
      lastDebugUpdateTime = 0;
      Log.i(TAG, "Navigation started: origin=(0,0,0), target=WP1");
    } catch (NotTrackingException e) {
      // Tracking may change while ARCore processes the request. Retry next frame.
      Log.w(TAG, "Waiting for tracking before creating the origin", e);
    }
  }

  private void resetNavigation() {
    detachOriginAnchor();
    detachRouteEntryAnchor();
    navigationStarted = false;
    navigationCompleted = false;
    currentWaypointIndex = 0;
    routeLineGeometry.invalidate();
    lastDebugUpdateTime = 0;
  }

  private void detachDirectionAnchor() {
    if (directionAnchor != null) { directionAnchor.detach(); directionAnchor = null; }
  }

  private void detachRouteEntryAnchor() {
    if (routeEntryAnchor != null) { routeEntryAnchor.detach(); routeEntryAnchor = null; }
  }

  private void detachOriginAnchor() {
    if (originAnchor != null) {
      originAnchor.detach();
      originAnchor = null;
    }
  }

  private Pose getRelativeCameraPose(Camera camera) {
    if (originAnchor == null
        || originAnchor.getTrackingState() != TrackingState.TRACKING
        || camera.getTrackingState() != TrackingState.TRACKING) {
      return null;
    }
    return levelAnchorPose().inverse().compose(camera.getDisplayOrientedPose());
  }

  private void updateNavigation(Camera camera) {
    if (camera.getTrackingState() != TrackingState.TRACKING) {
      postDebugText(getString(R.string.navigation_tracking,
          camera.getTrackingState().name(), camera.getTrackingFailureReason().name()));
      return;
    }
    if (navigationCompleted) {
      postDebugText(getString(R.string.navigation_arrived, route.size()));
      return;
    }
    if (!navigationStarted) {
      postDebugText(alignmentStatus != null ? alignmentStatus
          : selectedMapRouteName != null && MapOrientation.usesSensor(alignmentKey) && !routeUseManualAlignment
              ? getString(R.string.navigation_ready_sensor, selectedMapRouteName)
          : selectedMapRouteName != null && routeEntryYawDegrees != null && !routeUseManualAlignment
              ? getString(R.string.navigation_ready_entry, selectedMapRouteName, routeEntryDirectionLabel)
          : selectedMapRouteName == null ? getString(R.string.navigation_ready)
          : getString(R.string.navigation_ready_map, selectedMapRouteName));
      return;
    }
    if (originAnchor == null || originAnchor.getTrackingState() == TrackingState.STOPPED) {
      resetNavigation();
      postDebugText(getString(R.string.navigation_origin_lost));
      return;
    }

    Pose relativePose = getRelativeCameraPose(camera);
    if (relativePose == null) {
      postDebugText(getString(R.string.navigation_origin_paused));
      return;
    }
    float distance = route.get(currentWaypointIndex)
        .horizontalDistanceTo(relativePose.tx(), relativePose.tz());

    if (advanceWaypointIfArrived(relativePose)) {
      lastDebugUpdateTime = 0;
      Log.i(TAG, String.format(Locale.US,
          "Waypoint %d reached: x=%.2f z=%.2f distance=%.2f",
          currentWaypointIndex, relativePose.tx(), relativePose.tz(), distance));
      if (navigationCompleted) {
        detachOriginAnchor();
        postDebugText(getString(R.string.navigation_arrived, route.size()));
        Log.i(TAG, "Navigation arrived: all " + route.size() + " waypoints reached");
        return;
      }
      distance = route.get(currentWaypointIndex)
          .horizontalDistanceTo(relativePose.tx(), relativePose.tz());
    }
    updateDebugText(relativePose.tx(), relativePose.ty(), relativePose.tz(),
        distance, currentWaypointIndex);
  }

  // Kept independent of the camera/UI so the real route progression can be tested.
  boolean advanceWaypointIfArrived(Pose relativePose) {
    if (!navigationStarted || currentWaypointIndex >= route.size()) {
      return false;
    }
    Waypoint target = route.get(currentWaypointIndex);
    if (target.horizontalDistanceTo(relativePose.tx(), relativePose.tz()) >= ARRIVAL_DISTANCE) {
      return false;
    }
    currentWaypointIndex++;
    if (currentWaypointIndex == route.size()) {
      navigationStarted = false;
      navigationCompleted = true;
    }
    return true;
  }

  private void updateDebugText(float x, float y, float z, float distance, int waypointIndex) {
    long now = SystemClock.elapsedRealtime();
    if (now - lastDebugUpdateTime < DEBUG_UPDATE_INTERVAL_MS) {
      return;
    }
    postDebugText(String.format(Locale.getDefault(),
        getString(R.string.navigation_debug), x, y, z, waypointIndex + 1, route.size(), distance));
  }

  private void postDebugText(String text) {
    long now = SystemClock.elapsedRealtime();
    if (now - lastDebugUpdateTime < DEBUG_UPDATE_INTERVAL_MS) {
      return;
    }
    lastDebugUpdateTime = now;
    // Capture the text on the render thread; the UI never reads mutable navigation state.
    debugText.post(() -> debugText.setText(text));
  }

  private void updateDatasetControls() {
    ArDatasetController.Mode mode = datasets == null ? ArDatasetController.Mode.LIVE : datasets.mode();
    boolean playback = mode == ArDatasetController.Mode.PLAYBACK;
    Button record = findViewById(R.id.record_ar_button);
    record.setEnabled(datasets != null && !playback);
    record.setText(mode == ArDatasetController.Mode.RECORDING
        ? R.string.ar_record_stop : R.string.ar_record_start);
    findViewById(R.id.recordings_button).setEnabled(datasets != null);
    findViewById(R.id.stop_playback_button).setVisibility(playback ? View.VISIBLE : View.GONE);
    TextView status = findViewById(R.id.dataset_status);
    status.setText(playback ? (datasets.finished() ? R.string.ar_playback_finished : R.string.ar_playback_mode)
        : mode == ArDatasetController.Mode.RECORDING ? R.string.ar_recording : R.string.ar_live_mode);
    status.setTextColor(playback || mode == ArDatasetController.Mode.RECORDING ? 0xFFFFCC66 : 0xFFFFFFFF);
    TextView startPoint = findViewById(R.id.recording_start_point);
    ArRecordingStore.Entry active = datasets == null ? null : datasets.active();
    startPoint.setVisibility(active == null ? View.GONE : View.VISIBLE);
    startPoint.setText(active == null ? "" : "출발: " + RecordingStartPoint.describe(active.startPoint)
        + (active.startPoint == null ? "" : "\n기준: " + active.startPoint.directionDescription()));
  }

  private void toggleRecording() {
    if (session == null || !activityResumed || datasets == null) return;
    if (datasets.mode() == ArDatasetController.Mode.LIVE) {
      if (recordingStartDialog == null || !recordingStartDialog.isShowing())
        recordingStartDialog = RecordingStartDialog.show(this, uiAlignmentKey, this::startRecordingAt);
      return;
    }
    surfaceView.queueEvent(() -> {
      if (session == null) return;
      if (datasets.mode() == ArDatasetController.Mode.RECORDING) datasets.stopRecording(session);
    });
  }

  private void startRecordingAt(RecordingStartPoint point) {
    if (!activityResumed || session == null || datasets.mode() != ArDatasetController.Mode.LIVE) return;
    pendingRecording.set(new RecordingRequest(point));
  }

  private void processRecordingStart(Frame frame) {
    RecordingRequest request = pendingRecording.getAndSet(null);
    if (request == null || datasets == null || datasets.mode() != ArDatasetController.Mode.LIVE) return;
    datasets.startRecording(session, request.point, frame);
    if (datasets.mode() == ArDatasetController.Mode.RECORDING && request.point != null
        && (request.point.direction != null || MapOrientation.usesSensor(request.point.mapAsset))) {
      if (request.point.mapAsset.equals(datasetAlignmentOverride)) datasetAlignmentOverride = null;
      if (request.point.mapAsset.equals(alignmentKey)) {
        routeUseManualAlignment = false;
        resetNavigation();
        startNavigationRequested = false;
        alignmentStatus = MapOrientation.usesSensor(request.point.mapAsset)
            ? "폰 방위 센서와 반시계 85.5°로 문화관 방향을 적용했습니다. 길안내 시작을 누르세요."
            : "문에서 복도 쪽을 바라보는 출발 방향을 적용했습니다. 길안내 시작을 누르세요.";
      }
    }
  }

  // Stop the GL thread before changing the session's source. Both sources feed the same renderer.
  private void switchDataset(ArRecordingStore.Entry entry) {
    if (datasets == null || !activityResumed) return;
    if (datasets.mode() == ArDatasetController.Mode.RECORDING) {
      Toast.makeText(this, "녹화를 먼저 종료하세요.", Toast.LENGTH_LONG).show(); return;
    }
    displayRotationHelper.onPause();
    surfaceView.onPause();
    headingSensor.pause();
    if (session != null) session.pause();
    releaseArSession();
    datasets.exitPlayback();
    if (entry != null) {
      try { datasets.enterPlayback(entry); }
      catch (IOException e) { Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show(); }
    }
    resumeArSession();
    updateDatasetControls();
  }

  private void releaseArSession() {
    pendingRecording.set(null);
    datasetAlignmentOverride = null;
    recaptureEntryRequested = false;
    routeUseManualAlignment = false;
    if (datasets != null) datasets.clearMapReference();
    startNavigationRequested = false;
    resetNavigationRequested = false;
    calibrateRequested = false;
    pendingBearing.set(null);
    resetNavigation();
    detachDirectionAnchor();
    alignmentStatus = null;
    if (session != null) { session.close(); session = null; }
    hasSetTextureNames = false;
  }

  private void handleSessionFailure(Exception error) {
    Log.e(TAG, "AR dataset/session failed", error);
    if (!sessionFailurePosted.compareAndSet(false, true)) return;
    runOnUiThread(() -> {
      if (!activityResumed) return;
      displayRotationHelper.onPause();
      surfaceView.onPause();
      headingSensor.pause();
      if (datasets != null) datasets.stopRecording(session);
      if (session != null) session.pause();
      releaseArSession();
      if (datasets != null) datasets.exitPlayback();
      Toast.makeText(this, "AR 세션 오류로 실시간 AR를 다시 시작합니다: "
          + error.getMessage(), Toast.LENGTH_LONG).show();
      resumeArSession();
    });
  }

  private void loadReferenceBearing() {
    referenceBearing = alignmentPreferences.contains(alignmentKey)
        ? (double) alignmentPreferences.getFloat(alignmentKey, 0f) : null;
    alignmentStatus = null;
  }

  private void showAlignmentDialog() {
    final String dialogKey = uiAlignmentKey;
    EditText input = new EditText(this);
    input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL
        | InputType.TYPE_NUMBER_FLAG_SIGNED);
    input.setHint("0~360° · 자북 기준 시계 방향");
    if (alignmentPreferences.contains(dialogKey))
      input.setText(String.valueOf(alignmentPreferences.getFloat(dialogKey, 0f)));
    else if (MapOrientation.usesSensor(dialogKey)) input.setText(String.valueOf(MapOrientation.mapBearing()));
    AlertDialog dialog = new AlertDialog.Builder(this)
        .setTitle("건물 기준 방향 보정")
        .setMessage(MapOrientation.usesSensor(dialogKey)
            ? "문화관 기본 정렬은 폰 센서의 자북에서 반시계 85.5°(방위각 274.5°)입니다. 아래 수동 설정은 기본 정렬 대신 사용하며 85.5°를 추가로 더하지 않습니다. 현재 방향은 도면 위쪽을 바라볼 때만 사용하세요. 설정하면 안내가 초기화됩니다."
            : "지도 회전각 0°의 기준을 설정합니다. 측정한 자북 방위를 입력하거나 카메라 정면을 실제 기준 방향으로 향한 뒤 현재 방향을 누르세요. 설정하면 안내가 초기화됩니다.")
        .setView(input)
        .setPositiveButton("방위 저장", null)
        .setNeutralButton("현재 방향", (d, which) -> calibrateRequested = true)
        .setNegativeButton("취소", null).create();
    dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        .setOnClickListener(v -> {
          try {
            double value = Double.parseDouble(input.getText().toString().trim());
            if (!Double.isFinite(value) || value < 0 || value > 360)
              throw new IllegalArgumentException();
            pendingBearing.set(value);
            dialog.dismiss();
          } catch (IllegalArgumentException e) { input.setError("0~360 사이 방위를 입력하세요."); }
        }));
    dialog.show();
  }

  private Double northYaw(Frame frame) {
    float[] sensor = datasets == null ? headingSensor.at(frame.getTimestamp()) : datasets.headingForFrame();
    if (sensor == null) return null;
    float[] ar = new float[16];
    frame.getAndroidSensorPose().toMatrix(ar, 0);
    try { return MapAlignment.northYaw(sensor, ar); }
    catch (IllegalArgumentException e) { return null; }
  }

  private Float findFloor(Pose camera) {
    Float height = null;
    for (Plane plane : session.getAllTrackables(Plane.class)) {
      if (plane.getTrackingState() != TrackingState.TRACKING || plane.getSubsumedBy() != null
          || plane.getType() != Plane.Type.HORIZONTAL_UPWARD_FACING) continue;
      Pose planePose = plane.getCenterPose();
      float y = planePose.ty();
      float below = camera.ty() - y;
      // A forward-looking camera normally observes floor ahead, not directly underneath itself.
      // Use that nearby observed surface's height without moving the route's manual start point.
      float[] localCamera = planePose.inverse().transformPoint(
          new float[] {camera.tx(), y, camera.tz()});
      float distance = FloorPlaneGeometry.distanceToPolygon(
          plane.getPolygon(), localCamera[0], localCamera[2]);
      if (FloorPlaneGeometry.isNearbyFloor(below, distance)
          && (height == null || y < height)) height = y;
    }
    return height;
  }

  private static Pose yawPose(float x, float y, float z, double yaw) {
    return new Pose(new float[] {x, y, z},
        new float[] {0, (float) Math.sin(yaw / 2), 0, (float) Math.cos(yaw / 2)});
  }

  // ARCore may refine anchor orientation. Strip pitch/roll in both rendering and localization.
  private Pose levelAnchorPose() {
    Pose pose = originAnchor.getPose();
    float[] forward = pose.getZAxis();
    return yawPose(pose.tx(), pose.ty(), pose.tz(), Math.atan2(forward[0], forward[2]));
  }

  /** Configures the session with feature settings. */
  private void configureSession() {
    Config config = session.getConfig();
    config.setPlaneFindingMode(Config.PlaneFindingMode.HORIZONTAL);
    config.setLightEstimationMode(Config.LightEstimationMode.DISABLED);
    config.setDepthMode(Config.DepthMode.DISABLED);
    config.setInstantPlacementMode(Config.InstantPlacementMode.DISABLED);
    session.configure(config);
  }
}
