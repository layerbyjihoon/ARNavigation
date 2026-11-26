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
import android.widget.CheckBox;
import com.google.ar.core.examples.java.helloar.segmentation.SegmentationController;
import com.google.ar.core.examples.java.helloar.localization.*;
import com.google.ar.core.examples.java.helloar.coordinates.*;
import com.google.ar.core.examples.java.helloar.map.*;
import com.google.ar.core.examples.java.helloar.routing.*;
import com.google.ar.core.examples.java.helloar.debug.SegmentationDebugOverlay;
import android.content.SharedPreferences;
import android.content.Context;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.text.InputType;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
import android.widget.EditText;
import android.widget.SeekBar;
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
import com.google.ar.core.CameraConfig;
import com.google.ar.core.CameraConfigFilter;
import com.google.ar.core.Config;
import com.google.ar.core.Frame;
import com.google.ar.core.FutureState;
import com.google.ar.core.Pose;
import com.google.ar.core.Plane;
import com.google.ar.core.ResolveCloudAnchorFuture;
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
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;
import android.view.View;
import android.view.Surface;
import com.google.ar.core.DepthPoint;
import com.google.ar.core.HitResult;
import java.util.ArrayList;

/**
 * Indoor relative navigation prototype based on the official hello_ar_java renderer.
 * A single origin anchor renders either the original test route or a selected map route in meters.
 */
public class HelloArActivity extends AppCompatActivity implements SampleRender.Renderer {

  public static final String EXTRA_AUTO_START_ON_FLOOR = "auto_start_on_floor";
  public static final String EXTRA_OUTDOOR_PICKER = "outdoor_picker";
  public static final String EXTRA_OUTDOOR_DESTINATION = "outdoor_destination";
  public static final String EXTRA_BEGIN_OUTDOOR_DEPARTURE = "begin_outdoor_departure";
  private static final int OUTDOOR_DESTINATION_REQUEST = 1002;
  private static final int EXIT_ROUTE_REQUEST = 1003;
  private static final String TAG = HelloArActivity.class.getSimpleName();
  private static final long ENTRANCE_RESOLVE_TIMEOUT_MS = 35_000;
  private static final double ENTRANCE_START_RADIUS_METERS = 1.5;

  private EntranceCloudAnchor entranceCloudAnchor;
  private AlertDialog cloudNotice;
  private boolean cloudConsentGranted;
  private boolean cloudModeEnabled;
  private boolean cloudStartPending;
  private long cloudDeadline;
  private ResolveCloudAnchorFuture cloudResolve;
  private Anchor resolvedEntrance;
  private Bundle pendingOutdoorDestination;
  private Bundle outdoorDestination;
  private Bundle exitRouteData;
  private String destinationPoi;
  private ArRoutePlan exitPlan;
  private final OutdoorExitGate outdoorExitGate = new OutdoorExitGate();
  private long outdoorCheckStart;
  private volatile long outdoorDepartureFrame;

  private static final float ARRIVAL_DISTANCE = 0.8f;
  private static final long DEBUG_UPDATE_INTERVAL_MS = 200;
  private static final String EXPERIMENT_RECORDING_ID =
      "30f9c2f2-bbe1-4ed4-9e8c-bca748f8098b";
  private static final float EXPERIMENT_CAMERA_HEIGHT_METERS = 1.1f;

  // Only these requests cross from the UI thread to the GL render thread.
  private final AtomicReference<Boolean> startFromUi = new AtomicReference<>();
  private volatile boolean resetNavigationRequested;
  private final AtomicReference<ArRoutePlan> pendingMapRoute = new AtomicReference<>();
  private final AtomicReference<Boolean> autoStartOnNextRoute = new AtomicReference<>(false);
  private final AtomicBoolean experimentLoading = new AtomicBoolean();
  private volatile boolean experimentReplay;
  private volatile boolean experimentAutoStartPending;
  private volatile double debugMapAngleDegrees = MapOrientation.MUNHWAGWAN_CCW_DEGREES;
  private View angleDebugControls;
  private TextView angleDebugValue;
  private static final int MAP_ROUTE_REQUEST = 1001;
  private String selectedMapRouteName;
  private ArRoutePlan activeMapPlan;
  private int activeStageIndex;
  private boolean waitingForFloorChange;
  private StairVisionDetector stairVision;
  private RouteLineGeometry observedStairLine;
  private Waypoint observedStairMarker;
  private StairFallbackPath fallbackStairPath;
  private RouteLineGeometry fallbackStairLine;
  private RouteLineGeometry stairApproachLine;
  private int stairApproachWaypointCount;
  private RouteLineGeometry lastRenderedLine;
  private StairFloorTracker stairFloorTracker;
  private volatile boolean stairMarkerMode;
  private boolean stairMarkerPostedVisible;
  private boolean floorEstimatePostedVisible;
  private long lastStairMarkerPostAt;
  private long lastFloorEstimatePostAt;
  private long lastStairDepthScanAt;
  private long lastStairStatusLogAt;
  private long lastVisionDebugPostAt;
  private int lastStairDepthPointCount;
  private volatile boolean visionDebugEnabled;
  private int surfaceWidth, surfaceHeight;
  private int imageSensorOrientation = -1;
  private float routeCameraHeight;

  // Navigation state and all ARCore pose/anchor operations belong to the GL thread.
  private ArRecordingStore recordingStore;
  private ArDatasetController datasets;
  private AlertDialog recordingStartDialog;
  private static final class RecordingRequest {
    final RecordingStartPoint point;
    final long createdAt = SystemClock.elapsedRealtime();
    RecordingRequest(RecordingStartPoint point) { this.point = point; }
  }
  private final AtomicReference<RecordingRequest> pendingRecording = new AtomicReference<>();
  private String datasetAlignmentOverride;
  private boolean activityResumed;
  private final AtomicBoolean sessionFailurePosted = new AtomicBoolean();
  private HeadingSensor headingSensor;
  private SegmentationController segmentation;
  private SegmentationDebugOverlay semanticOverlay;
  private final RouteAlignmentManager semanticAlignment=new RouteAlignmentManager();
  private final CorridorCenterEstimator corridorCenterEstimator = new CorridorCenterEstimator();
  private final CorridorCenterTracker corridorCenter = new CorridorCenterTracker();
  private double lastCenterRouteYaw = Double.NaN;
  private long lastCorridorEvidenceMs;
  private float lastCorridorWidth;
  private boolean lastCorridorSingleWall;
  private volatile boolean semanticCorrectionEnabled=true,compareSemanticRoutes;
  private long lastSemanticUi;
  private boolean lastCorrectionAccepted;
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
  private String startLocation;
  private Anchor originAnchor;
  private Anchor requestedStartAnchor;
  private boolean captureStartOnTracking;
  private boolean startNavigationRequested;
  private boolean navigationStarted;
  private boolean navigationCompleted;
  private boolean returnToStartRequired;
  private int currentWaypointIndex;
  private long lastDebugUpdateTime;
  private TextView debugText;
  private TextView visionDebugText;
  private TextView stairArMarker;
  private TextView stairFloorEstimate;
  private TextView navigationStatus;
  private TextView navigationStartLocation;
  private Button navigationStartButton;
  private boolean confirmStartOnUi;

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
    setContentView(R.layout.activity_indoor_ar);
    surfaceView = findViewById(R.id.surfaceview);
    debugText = findViewById(R.id.debug_text);
    visionDebugText = findViewById(R.id.vision_debug_status);
    stairArMarker = findViewById(R.id.stair_ar_marker);
    stairFloorEstimate = findViewById(R.id.stair_floor_estimate);
    navigationStatus = findViewById(R.id.navigation_status);
    navigationStartLocation = findViewById(R.id.navigation_start_location);
    View toolsPanel = findViewById(R.id.ar_tools_panel);
    Button toolsToggle = findViewById(R.id.ar_tools_toggle);
    toolsToggle.setOnClickListener(v -> {
      boolean open = toolsPanel.getVisibility() != View.VISIBLE;
      toolsPanel.setVisibility(open ? View.VISIBLE : View.GONE);
      toolsToggle.setText(open ? R.string.nav_tools_close : R.string.nav_tools_open);
    });
    angleDebugControls = findViewById(R.id.angle_debug_controls);
    angleDebugValue = findViewById(R.id.angle_debug_value);
    EditText angleInput = findViewById(R.id.angle_debug_input);
    SeekBar angleSeek = findViewById(R.id.angle_debug_seek);
    angleInput.setText(String.format(Locale.US, "%.1f", debugMapAngleDegrees));
    angleSeek.setProgress((int) Math.round(debugMapAngleDegrees * 2));
    angleSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
      @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
        if (fromUser) angleInput.setText(String.format(Locale.US, "%.1f", progress / 2.0));
      }
      @Override public void onStartTrackingTouch(SeekBar bar) { }
      @Override public void onStopTrackingTouch(SeekBar bar) { }
    });
    angleInput.addTextChangedListener(new TextWatcher() {
      @Override public void beforeTextChanged(CharSequence text, int start, int count, int after) { }
      @Override public void onTextChanged(CharSequence text, int start, int before, int count) {
        try {
          double degrees = Double.parseDouble(text.toString().replace(',', '.'));
          if (degrees < 0 || degrees > 360) return;
          debugMapAngleDegrees = degrees;
          angleDebugValue.setText(getString(R.string.angle_debug_value_format, degrees));
          angleSeek.setProgress((int) Math.round(degrees * 2));
        } catch (NumberFormatException ignored) { }
      }
      @Override public void afterTextChanged(Editable text) { }
    });
    Button visionDebugButton = findViewById(R.id.vision_debug_button);
    visionDebugButton.setOnClickListener(v -> {
      visionDebugEnabled = !visionDebugEnabled;
      visionDebugText.setVisibility(visionDebugEnabled ? View.VISIBLE : View.GONE);
      visionDebugButton.setText(visionDebugEnabled
          ? R.string.vision_debug_off : R.string.vision_debug_on);
      if (visionDebugEnabled) visionDebugText.setText(R.string.vision_debug_wait);
    });
    Button stairDisplayToggle = findViewById(R.id.stair_display_toggle);
    stairDisplayToggle.setOnClickListener(v -> {
      stairMarkerMode = !stairMarkerMode;
      stairDisplayToggle.setText(stairMarkerMode
          ? R.string.stair_display_marker : R.string.stair_display_line);
      if (!stairMarkerMode) stairArMarker.setVisibility(View.GONE);
      surfaceView.queueEvent(() -> {
        lastRenderedLine = null;
        routeLineGeometry.invalidate();
        if (stairApproachLine != null) stairApproachLine.invalidate();
        if (fallbackStairLine != null) fallbackStairLine.invalidate();
        if (observedStairLine != null) observedStairLine.invalidate();
      });
    });
    headingSensor = new HeadingSensor(this);
    segmentation=new SegmentationController(getAssets());
    semanticOverlay=findViewById(R.id.semantic_overlay);
    CheckBox runModel=findViewById(R.id.semantic_enable),correct=findViewById(R.id.semantic_correction);
    CheckBox mask=findViewById(R.id.semantic_mask),landmarks=findViewById(R.id.semantic_landmarks),
        boundaries=findViewById(R.id.semantic_boundaries),status=findViewById(R.id.semantic_status);
    runModel.setChecked(true);
    correct.setChecked(true);
    segmentation.setEnabled(true);
    semanticCorrectionEnabled = true;
    runModel.setOnCheckedChangeListener((button,checked)->segmentation.setEnabled(checked));
    correct.setOnCheckedChangeListener((button,checked)->semanticCorrectionEnabled=checked);
    android.widget.CompoundButton.OnCheckedChangeListener display=(button,checked)->
        semanticOverlay.configure(mask.isChecked(),landmarks.isChecked(),boundaries.isChecked(),status.isChecked());
    mask.setOnCheckedChangeListener(display);landmarks.setOnCheckedChangeListener(display);
    boundaries.setOnCheckedChangeListener(display);status.setOnCheckedChangeListener(display);
    semanticOverlay.configure(mask.isChecked(),landmarks.isChecked(),boundaries.isChecked(),status.isChecked());
    ((CheckBox)findViewById(R.id.semantic_compare)).setOnCheckedChangeListener((button,checked)->compareSemanticRoutes=checked);
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
    findViewById(R.id.experiment_replay_button).setOnClickListener(v -> startExperimentReplay());
    alignmentPreferences = getSharedPreferences("map_alignment", MODE_PRIVATE);
    loadReferenceBearing();
    findViewById(R.id.calibrate_button).setOnClickListener(v -> showAlignmentDialog());
    navigationStartButton = findViewById(R.id.start_nav_button);
    Button resetButton = findViewById(R.id.reset_nav_button);
    navigationStartButton.setOnClickListener(v -> {
      if (confirmStartOnUi) {
        new AlertDialog.Builder(this)
            .setTitle(R.string.nav_restart_title)
            .setMessage(R.string.nav_restart_message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.nav_restart_confirm, (dialog, which) -> startFromUi.set(true))
            .show();
      } else {
        startFromUi.set(false);
      }
    });
    resetButton.setOnClickListener(v -> resetNavigationRequested = true);
    findViewById(R.id.map_route_button).setOnClickListener(v ->
        startActivityForResult(new Intent(this, MapRouteActivity.class), MAP_ROUTE_REQUEST));
    View outdoorButton = findViewById(R.id.choose_outdoor_destination);
    outdoorButton.setVisibility(getIntent().hasExtra(EXTRA_OUTDOOR_PICKER) ? View.VISIBLE : View.GONE);
    outdoorButton.setOnClickListener(v -> chooseOutdoorDestination());
    displayRotationHelper = new DisplayRotationHelper(/* context= */ this);

    // Set up renderer.
    render = new SampleRender(surfaceView, this, getAssets());

    if (savedInstanceState != null) {
      pendingOutdoorDestination = savedInstanceState.getBundle("pending_outdoor_destination");
      outdoorDestination = savedInstanceState.getBundle(EXTRA_OUTDOOR_DESTINATION);
      exitRouteData = savedInstanceState.getBundle("exit_route");
      destinationPoi = savedInstanceState.getString("indoor_destination_poi");
    }
    if (exitRouteData != null && outdoorDestination != null) {
      applyMapRoute(new Intent().putExtras(exitRouteData), true);
    } else if (getIntent().hasExtra(MapRouteActivity.EXTRA_POINTS)) {
      autoStartOnNextRoute.set(savedInstanceState == null
          && getIntent().getBooleanExtra(EXTRA_AUTO_START_ON_FLOOR, false));
      if (autoStartOnNextRoute.get()) {
        entranceCloudAnchor = EntranceCloudAnchor.fromRoute(
            getIntent().getBundleExtra(EntranceCloudAnchor.EXTRA));
      }
      applyMapRoute(getIntent(), false);
    }
    installRequested = false;
    if (savedInstanceState == null && getIntent().getBooleanExtra(EXTRA_BEGIN_OUTDOOR_DEPARTURE, false)) {
      chooseOutdoorDestination();
    }
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    if (requestCode == OUTDOOR_DESTINATION_REQUEST && resultCode == RESULT_OK && data != null) {
      pendingOutdoorDestination = data.getBundleExtra(EXTRA_OUTDOOR_DESTINATION);
      if (pendingOutdoorDestination != null) {
        startActivityForResult(new Intent(this, MapRouteActivity.class)
            .putExtra(MapRouteActivity.EXTRA_PICK_EXIT_ROUTE, true)
            .putExtra(MapRouteActivity.EXTRA_PREFERRED_START_POI, destinationPoi), EXIT_ROUTE_REQUEST);
      }
    } else if (requestCode == EXIT_ROUTE_REQUEST) {
      if (resultCode == RESULT_OK && data != null && pendingOutdoorDestination != null
          && MapRouteActivity.MUNHWAGWAN_EXIT_ID.equals(data.getStringExtra(MapRouteActivity.EXTRA_DESTINATION_POI))
          && applyMapRoute(data, true)) {
        outdoorDestination = pendingOutdoorDestination;
        exitRouteData = data.getExtras();
      }
      pendingOutdoorDestination = null;
    } else if (requestCode == MAP_ROUTE_REQUEST && resultCode == RESULT_OK && data != null) {
      if (applyMapRoute(data, false)) {
        outdoorDestination = null;
        exitRouteData = null;
      }
    }
  }

  private boolean applyMapRoute(Intent data, boolean isExit) {
      try {
        ArRoutePlan plan = ArRoutePlan.decodeStages(data.getStringExtra(MapRouteActivity.EXTRA_NAME),
            data.getFloatArrayExtra(MapRouteActivity.EXTRA_POINTS),
            data.getIntArrayExtra(MapRouteActivity.EXTRA_STAGE_LENGTHS),
            data.getStringArrayExtra(MapRouteActivity.EXTRA_FLOORS),
            data.getStringArrayExtra(MapRouteActivity.EXTRA_NEXT_FLOORS),
            data.getStringArrayExtra(MapRouteActivity.EXTRA_TRANSITIONS),
            data.getStringExtra(MapRouteActivity.EXTRA_REFERENCE),
            data.hasExtra(MapRouteActivity.EXTRA_ENTRY_YAW)
                ? data.getDoubleExtra(MapRouteActivity.EXTRA_ENTRY_YAW, Double.NaN) : null,
            data.getStringExtra(MapRouteActivity.EXTRA_ENTRY_DIRECTION),
            data.getStringExtra(MapRouteActivity.EXTRA_START_LOCATION),
            data.getStringExtra(MapRouteActivity.EXTRA_SEMANTIC_MAP));
        exitPlan = isExit ? plan : null;
        if (!isExit) destinationPoi = data.getStringExtra(MapRouteActivity.EXTRA_DESTINATION_POI);
        outdoorDepartureFrame = 0;
        pendingMapRoute.set(plan);
        return true;
      } catch (IllegalArgumentException e) {
        autoStartOnNextRoute.set(false);
        Toast.makeText(this, "지도 경로를 적용할 수 없습니다: " + e.getMessage(), Toast.LENGTH_LONG).show();
        return false;
      }
  }

  private void chooseOutdoorDestination() {
    Intent picker = getIntent().getParcelableExtra(EXTRA_OUTDOOR_PICKER);
    if (picker != null) startActivityForResult(picker, OUTDOOR_DESTINATION_REQUEST);
  }

  private void completeOutdoorDeparture() {
    outdoorDepartureFrame = 0;
    if (session != null) {
      displayRotationHelper.onPause();
      surfaceView.onPause();
      headingSensor.pause();
      if (datasets != null) datasets.stopRecording(session);
      session.pause();
      releaseArSession();
    }
    setResult(RESULT_OK, new Intent().putExtra(EXTRA_OUTDOOR_DESTINATION, outdoorDestination));
    finish();
  }

  @Override protected void onSaveInstanceState(Bundle state) {
    super.onSaveInstanceState(state);
    state.putBundle("pending_outdoor_destination", pendingOutdoorDestination);
    state.putBundle(EXTRA_OUTDOOR_DESTINATION, outdoorDestination);
    state.putBundle("exit_route", exitRouteData);
    state.putString("indoor_destination_poi", destinationPoi);
  }

  @Override
  protected void onDestroy() {
    if (cloudNotice != null) cloudNotice.dismiss();
    if (segmentation != null) segmentation.close();
    if (stairVision != null) { stairVision.close(); stairVision = null; }
    if (session != null) {
      // onPause has already stopped the GL thread before Activity destruction.
      clearCloudResolve();
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
    if (entranceCloudAnchor != null && !cloudConsentGranted) {
      if (cloudNotice == null) showCloudNotice();
      return;
    }
    resumeArSession();
  }

  private void showCloudNotice() {
    cloudNotice = new AlertDialog.Builder(this)
        .setTitle(R.string.cloud_entrance_title)
        .setMessage(getText(R.string.cloud_entrance_notice))
        .setPositiveButton(R.string.cloud_entrance_accept, (dialog, which) -> chooseCloud(true))
        .setNegativeButton(R.string.cloud_entrance_decline, (dialog, which) -> chooseCloud(false))
        .setOnCancelListener(dialog -> chooseCloud(false))
        .show();
    TextView message = cloudNotice.findViewById(android.R.id.message);
    if (message != null) message.setMovementMethod(LinkMovementMethod.getInstance());
  }

  private void chooseCloud(boolean accepted) {
    cloudNotice = null;
    cloudConsentGranted = accepted;
    if (!accepted) entranceCloudAnchor = null;
    if (activityResumed) resumeArSession();
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
      if (newSession && (datasets == null || datasets.mode() != ArDatasetController.Mode.PLAYBACK))
        logArCameraOptions();
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
    if(segmentation!=null)segmentation.invalidate();
    pendingRecording.set(null);
    if (recordingStartDialog != null) recordingStartDialog.dismiss();
    headingSensor.pause();
    if (session != null) {
      // Note that the order matters - GLSurfaceView is paused first so that it does not try
      // to query the session. If Session is paused before GLSurfaceView, GLSurfaceView may
      // still call session.update() and get a SessionPausedException.
      displayRotationHelper.onPause();
      surfaceView.onPause();
      clearCloudResolve();
      resetOutdoorObservation();
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
      routeShader.setFloat("u_Baseline",0f);
      // A recreated GL context has empty GPU buffers even if CPU geometry is cached.
      routeLineGeometry.invalidate();
      lastRenderedLine = null;
      rendererReady = true;
    } catch (IOException e) {
      Log.e(TAG, "Failed to read a required asset file", e);
      messageSnackbarHelper.showError(this, "Failed to read a required asset file: " + e);
    }
  }

  @Override
  public void onSurfaceChanged(SampleRender render, int width, int height) {
    surfaceWidth = width; surfaceHeight = height;
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
    processSemanticFrame(frame);
    updateNavigation(camera);
    processStairVision(frame, camera);
    updateStairFloorEstimate(camera);

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
      hideStairMarker();
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
    if ((!navigationStarted && !(waitingForFloorChange
            && (observedStairLine != null || fallbackStairLine != null)))
        || originAnchor == null
        || originAnchor.getTrackingState() != TrackingState.TRACKING) {
      hideStairMarker();
      return;
    }
    Pose relativePose = getRelativeCameraPose(camera);
    if (relativePose == null) {
      hideStairMarker();
      return;
    }
    boolean markerModeAtStairs = stairMarkerMode && fallbackStairPath != null
        && activeMapPlan != null && activeStageIndex < activeMapPlan.stages.size()
        && activeMapPlan.stages.get(activeStageIndex).transition != null
        && activeMapPlan.stages.get(activeStageIndex).transition.startsWith("STAIR");
    if (markerModeAtStairs) updateStairMarker();
    else hideStairMarker();
    // A detected stair surface replaces the mapped tail immediately, not only after
    // the entrance waypoint is marked complete.
    if (markerModeAtStairs && currentWaypointIndex >= stairApproachWaypointCount) return;
    RouteLineGeometry line = markerModeAtStairs
        ? stairApproachLine == null ? routeLineGeometry : stairApproachLine
        : observedStairLine != null ? observedStairLine
        : fallbackStairLine != null ? fallbackStairLine : routeLineGeometry;
    int index = line == routeLineGeometry ? currentWaypointIndex : 0;
    if (markerModeAtStairs) index = currentWaypointIndex;
    // The provisional path stays visible at its initial position while depth is missing.
    float progressX = line == fallbackStairLine ? fallbackStairPath.start.x : relativePose.tx();
    float progressZ = line == fallbackStairLine ? fallbackStairPath.start.z : relativePose.tz();
    if (lastRenderedLine != line) {
      line.invalidate();
      lastRenderedLine = line;
    }
    if (line.update(index, progressX, progressZ)) {
      routePositionBuffer.set(line.getPositions());
      routeColorBuffer.set(line.getColors());
    }
    if (line.getVertexCount() == 0) {
      return;
    }

    // Geometry stays relative to the anchor, never attached to the moving camera.
    if(compareSemanticRoutes) {
      levelAnchorPose().toMatrix(modelMatrix,0);
      Matrix.multiplyMM(modelViewMatrix,0,viewMatrix,0,modelMatrix,0);
      Matrix.multiplyMM(modelViewProjectionMatrix,0,projectionMatrix,0,modelViewMatrix,0);
      routeShader.setFloat("u_Baseline",1f).setMat4("u_ModelViewProjection",modelViewProjectionMatrix);
      render.draw(routeMesh,routeShader,virtualSceneFramebuffer);
    }
    correctedRoutePose().toMatrix(modelMatrix, 0);
    Matrix.multiplyMM(modelViewMatrix, 0, viewMatrix, 0, modelMatrix, 0);
    Matrix.multiplyMM(modelViewProjectionMatrix, 0, projectionMatrix, 0, modelViewMatrix, 0);
    routeShader.setFloat("u_Baseline",0f).setMat4("u_ModelViewProjection", modelViewProjectionMatrix);
    render.draw(routeMesh, routeShader, virtualSceneFramebuffer);
  }

  private void updateStairMarker() {
    Waypoint point = observedStairMarker;
    if (point == null && fallbackStairPath != null && !fallbackStairPath.waypoints.isEmpty())
      point = fallbackStairPath.markerPoint();
    if (point == null || surfaceWidth <= 0 || surfaceHeight <= 0) {
      hideStairMarker();
      return;
    }
    long now = SystemClock.elapsedRealtime();
    if (now - lastStairMarkerPostAt < 100) return;
    lastStairMarkerPostAt = now;
    float[] world = correctedRoutePose().transformPoint(
        new float[] {point.x, point.y + 0.3f, point.z});
    float[] view = new float[4], clip = new float[4];
    Matrix.multiplyMV(view, 0, viewMatrix, 0,
        new float[] {world[0], world[1], world[2], 1f}, 0);
    Matrix.multiplyMV(clip, 0, projectionMatrix, 0, view, 0);
    if (clip[3] <= 0.1f || Math.abs(clip[0] / clip[3]) > 1f
        || Math.abs(clip[1] / clip[3]) > 1f) {
      hideStairMarker();
      return;
    }
    stairMarkerPostedVisible = true;
    float screenX = (clip[0] / clip[3] + 1f) * surfaceWidth / 2f;
    float screenY = (1f - clip[1] / clip[3]) * surfaceHeight / 2f;
    ArRoutePlan.Stage stage = activeMapPlan.stages.get(activeStageIndex);
    int label = "STAIR_UP".equals(stage.transition)
        ? R.string.stair_marker_up : R.string.stair_marker_down;
    stairArMarker.post(() -> {
      stairArMarker.setText(label);
      stairArMarker.setContentDescription(getString(label));
      stairArMarker.setVisibility(View.VISIBLE);
      stairArMarker.setX(Math.max(surfaceView.getLeft(),
          Math.min(surfaceView.getRight() - stairArMarker.getWidth(),
              surfaceView.getLeft() + screenX - stairArMarker.getWidth() / 2f)));
      stairArMarker.setY(Math.max(surfaceView.getTop(),
          Math.min(surfaceView.getBottom() - stairArMarker.getHeight(),
              surfaceView.getTop() + screenY - stairArMarker.getHeight() - 16f)));
    });
  }

  private void hideStairMarker() {
    if (!stairMarkerPostedVisible) return;
    stairMarkerPostedVisible = false;
    stairArMarker.post(() -> stairArMarker.setVisibility(View.GONE));
  }

  private void updateStairFloorEstimate(Camera camera) {
    if (stairFloorTracker == null || (!navigationStarted && !waitingForFloorChange)) {
      if (floorEstimatePostedVisible) {
        floorEstimatePostedVisible = false;
        stairFloorEstimate.post(() -> stairFloorEstimate.setVisibility(View.GONE));
      }
      return;
    }
    if (camera.getTrackingState() != TrackingState.TRACKING) {
      stairFloorTracker.trackingLost();
      return;
    }
    long now = SystemClock.elapsedRealtime();
    stairFloorTracker.update(camera.getPose().ty(), now);
    if (stairFloorTracker.phase() == StairFloorTracker.Phase.NEXT_FLOOR
        && activeMapPlan != null && activeStageIndex + 1 < activeMapPlan.stages.size()) {
      // Height confirmation must advance the route even if the stair entrance waypoint
      // was missed while the user was climbing.
      if (!waitingForFloorChange && awaitNextFloor())
        Log.i(TAG, "AR height confirmed floor transition to " + stairFloorTracker.nextFloor);
      if (waitingForFloorChange) startNavigationRequested = true;
    }
    if (now - lastFloorEstimatePostAt < 250) return;
    lastFloorEstimatePostAt = now;
    floorEstimatePostedVisible = true;
    String from = StairFloorTracker.label(stairFloorTracker.startFloor);
    String to = StairFloorTracker.label(stairFloorTracker.nextFloor);
    float height = stairFloorTracker.verticalDeltaMeters();
    String status = stairFloorTracker.phase() == StairFloorTracker.Phase.NEXT_FLOOR
        ? getString(R.string.stair_floor_next, to, height)
        : stairFloorTracker.phase() == StairFloorTracker.Phase.BETWEEN_FLOORS
            ? getString(R.string.stair_floor_between, from, to, height)
            : getString(R.string.stair_floor_start, from, height);
    stairFloorEstimate.post(() -> {
      stairFloorEstimate.setText(status);
      stairFloorEstimate.setVisibility(View.VISIBLE);
    });
  }

  private void processStairVision(Frame frame, Camera camera) {
    updateVisionDebug(frame, camera);
    if (camera.getTrackingState() != TrackingState.TRACKING || activeMapPlan == null
        || activeStageIndex >= activeMapPlan.stages.size() || originAnchor == null
        || originAnchor.getTrackingState() != TrackingState.TRACKING) return;
    ArRoutePlan.Stage stage = activeMapPlan.stages.get(activeStageIndex);
    if (stage.transition == null || !stage.transition.startsWith("STAIR")
        || (!navigationStarted && !waitingForFloorChange)) return;
    if (!waitingForFloorChange) {
      Pose relative = getRelativeCameraPose(camera);
      Waypoint entrance = StairFallbackPath.entrance(route);
      if (relative == null || entrance.horizontalDistanceTo(relative.tx(), relative.tz()) > 3f)
        return;
    }
    prepareStairFallback(stage);
    if (stairVision == null) stairVision = new StairVisionDetector();
    stairVision.submit(frame, imageRotationDegrees());
    long now = SystemClock.elapsedRealtime();
    if (!session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) {
      if (now - lastStairStatusLogAt > 3000) {
        Log.i(TAG, "Stair correction unavailable: depth unsupported");
        lastStairStatusLogAt = now;
      }
      return;
    }
    if (now - lastStairDepthScanAt < 700) return;
    lastStairDepthScanAt = now;
    boolean visionDetected = stairVision.seesStairs();
    boolean ascending = "STAIR_UP".equals(stage.transition);
    List<Waypoint> observed = observedStairPoints(
        frame, camera, ascending);
    lastStairDepthPointCount = observed.size();
    if (observed.isEmpty() || (!visionDetected && !StairDepthPath.strong(observed, ascending))) {
      if (now - lastStairStatusLogAt > 3000) {
        Log.i(TAG, "Stair correction waiting: vision=" + visionDetected
            + ", depthPoints=" + observed.size());
        lastStairStatusLogAt = now;
      }
      return;
    }
    observedStairLine = new RouteLineGeometry(observed.subList(1, observed.size()), observed.get(0));
    observedStairMarker = observed.get(observed.size() / 2);
    observedStairLine.invalidate();
    Log.i(TAG, "Stair ribbon aligned to " + observed.size()
        + " depth observations; vision=" + visionDetected);
  }

  private void updateVisionDebug(Frame frame, Camera camera) {
    if (!visionDebugEnabled) return;
    if (camera.getTrackingState() == TrackingState.TRACKING) {
      if (stairVision == null) stairVision = new StairVisionDetector();
      stairVision.submit(frame, imageRotationDegrees());
    }
    long now = SystemClock.elapsedRealtime();
    if (now - lastVisionDebugPostAt < 300) return;
    lastVisionDebugPostAt = now;
    String model = stairVision == null ? "계단 모델: 결과 대기" : stairVision.debugSummary();
    String depth = session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)
        ? "깊이: 지원 · 유효 계단 점 " + lastStairDepthPointCount + "개"
        : "깊이: 미지원";
    String display = stairMarkerMode ? "표식" : "경로선";
    String line = observedStairLine != null ? display + ": 깊이 보정 위치"
        : fallbackStairLine != null ? display + ": 임시 위치"
        : display + ": 계단 관측 전";
    SegmentationController.Completed latestSemantic = segmentation.latest();
    int boundaryLines = latestSemantic == null ? 0 : latestSemantic.wallBoundaries.size();
    String corridor = String.format(Locale.getDefault(),
        "\n벽·바닥 경계 %d선 · 복도 보정: %s · 각도 %+.1f° X %+.2f / Z %+.2f m",
        boundaryLines,
        now - lastCorridorEvidenceMs < (lastCorridorSingleWall ? 4000 : 1800)
            ? lastCorridorSingleWall ? "한쪽 벽에서 1.0m"
                : String.format(Locale.getDefault(), "양쪽 벽 중앙(폭 %.1f m)", lastCorridorWidth)
            : "관측 부족",
        Math.toDegrees(corridorCenter.yaw()), corridorCenter.x(), corridorCenter.z());
    String status = (camera.getTrackingState() == TrackingState.TRACKING ? "" : "추적 대기\n")
        + model + "\n" + depth + "\n" + line + corridor;
    visionDebugText.post(() -> visionDebugText.setText(status));
  }

  private void prepareStairFallback(ArRoutePlan.Stage stage) {
    if (fallbackStairLine != null) return;
    corridorCenter.reset();
    lastCorridorEvidenceMs = 0;
    fallbackStairPath = StairFallbackPath.from(route, "STAIR_UP".equals(stage.transition));
    fallbackStairLine = new RouteLineGeometry(
        fallbackStairPath.waypoints, fallbackStairPath.start);
    stairApproachWaypointCount = route.size();
    for (int i = 0; i < route.size(); i++) {
      if (Math.abs(route.get(i).y - route.get(0).y) > 0.5f) {
        stairApproachWaypointCount = i;
        break;
      }
    }
    stairApproachLine = stairApproachWaypointCount < route.size()
        ? new RouteLineGeometry(route.subList(0, stairApproachWaypointCount)) : null;
    Log.i(TAG, "Stair fallback ready: mappedHeight=" + fallbackStairPath.hasStairHeight
        + ", points=" + fallbackStairPath.waypoints.size());
  }

  private List<Waypoint> observedStairPoints(Frame frame, Camera camera, boolean ascending) {
    List<List<Waypoint>> profiles = new ArrayList<>();
    if (surfaceWidth <= 0 || surfaceHeight <= 0) return java.util.Collections.emptyList();
    Pose inverse = correctedRoutePose().inverse();
    for (float xFraction : new float[] {0.8f, 0.65f, 0.5f, 0.35f, 0.2f}) {
      List<Waypoint> points = new ArrayList<>();
      for (float yFraction : new float[] {0.7f, 0.62f, 0.54f, 0.48f,
          0.42f, 0.36f, 0.3f, 0.24f}) {
        for (HitResult hit : frame.hitTest(surfaceWidth * xFraction, surfaceHeight * yFraction)) {
          if (!(hit.getTrackable() instanceof DepthPoint)) continue;
          Pose world = hit.getHitPose();
          float dx = world.tx() - camera.getPose().tx();
          float dy = world.ty() - camera.getPose().ty();
          float dz = world.tz() - camera.getPose().tz();
          float distance = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
          if (distance < 0.3f || distance > 6f) break;
          Pose local = inverse.compose(world);
          Waypoint point = new Waypoint(local.tx(), local.ty() + 0.02f, local.tz());
          if (points.isEmpty() || point.horizontalDistanceTo(
              points.get(points.size() - 1).x, points.get(points.size() - 1).z) > 0.08f)
            points.add(point);
          break;
        }
      }
      profiles.add(points);
    }
    return StairDepthPath.bestPlausible(profiles, ascending);
  }

  private int imageRotationDegrees() {
    try {
      if (imageSensorOrientation < 0) {
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        Integer degrees = manager.getCameraCharacteristics(session.getCameraConfig().getCameraId())
            .get(CameraCharacteristics.SENSOR_ORIENTATION);
        imageSensorOrientation = degrees == null ? 0 : degrees;
      }
      int display = getWindowManager().getDefaultDisplay().getRotation() * 90;
      return (imageSensorOrientation - display + 360) % 360;
    } catch (Exception unavailable) {
      return 0;
    }
  }

  private void processNavigationRequests(Frame frame) {
    Camera camera = frame.getCamera();
    Boolean confirmed = startFromUi.getAndSet(null);
    if (confirmed != null && !navigationStarted && !startNavigationRequested
        && (confirmed || (!returnToStartRequired && !navigationCompleted))) {
      detachRouteEntryAnchor();
      detachRequestedStartAnchor();
      alignmentStatus = null;
      returnToStartRequired |= navigationCompleted;
      navigationCompleted = false;
      startNavigationRequested = true;
      captureStartOnTracking = true;
    }
    ArRoutePlan plan = pendingMapRoute.getAndSet(null);
    if (plan != null) {
      resetNavigation();
      // Initial app handoff waits for tracking, yaw and floor without pinning an early pose.
      startNavigationRequested = autoStartOnNextRoute.getAndSet(false);
      cloudStartPending = startNavigationRequested && cloudConsentGranted
          && MapRouteActivity.MUNHWAGWAN_MAP.equals(plan.referenceId)
          && "F1".equals(plan.stages.get(0).floorId);
      activeMapPlan = plan;
      activeStageIndex = 0;
      waitingForFloorChange = false;
      observedStairLine = null;
      observedStairMarker = null;
      fallbackStairPath = null;
      fallbackStairLine = null;
      stairApproachLine = null;
      stairFloorTracker = null;
      route = plan.waypoints;
      segmentation.setMap(plan.semanticMap);
      startLocation = plan.startLocation;
      returnToStartRequired = false;
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
      calibrateRequested = false;
      pendingBearing.set(null);
      routeLineGeometry = new RouteLineGeometry(route);
      Log.i(TAG, "Map route applied: " + plan.name + ", waypoints=" + route.size());
      if (experimentReplay && experimentAutoStartPending) {
        experimentAutoStartPending = false;
        startNavigationRequested = true;
      }
    }
    if (resetNavigationRequested) {
      resetNavigationRequested = false;
      startNavigationRequested = false;
      resetNavigation();
      waitingForFloorChange = false;
      observedStairLine = null;
      observedStairMarker = null;
      fallbackStairPath = null;
      fallbackStairLine = null;
      stairApproachLine = null;
      stairFloorTracker = null;
      if (activeMapPlan != null) {
        activeStageIndex = 0;
        route = activeMapPlan.stages.get(0).waypoints;
        routeLineGeometry = new RouteLineGeometry(route);
        segmentation.setMap(activeMapPlan.semanticMap);
      }
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

    if (cloudStartPending) {
      startFromCloud(frame, camera);
      return;
    }

    // Keep a start request pending until a reliable camera pose becomes available.
    if (!startNavigationRequested || camera.getTrackingState() != TrackingState.TRACKING) {
      return;
    }
    try {
      boolean nextFloorStart = waitingForFloorChange;
      if (captureStartOnTracking && requestedStartAnchor == null)
        requestedStartAnchor = session.createAnchor(camera.getPose());
      if (requestedStartAnchor != null
          && requestedStartAnchor.getTrackingState() != TrackingState.TRACKING) {
        if (requestedStartAnchor.getTrackingState() == TrackingState.STOPPED) {
          resetNavigation();
          startNavigationRequested = false;
          alignmentStatus = "출발 위치 추적이 끊겼습니다. 실제 출발점에서 다시 시작하세요.";
        } else {
          alignmentStatus = "안내 시작 위치의 추적을 복구하는 중입니다.";
        }
        return;
      }
      Float expectedNextFloorY = null;
      Double yaw = null;
      if (nextFloorStart && originAnchor != null
          && originAnchor.getTrackingState() == TrackingState.TRACKING) {
        Pose previousRoute = correctedRoutePose();
        float[] axis = previousRoute.getZAxis();
        yaw = Math.atan2(axis[0], axis[2]);
        if (stairFloorTracker != null)
          expectedNextFloorY = originAnchor.getPose().ty() - 0.52f
              + stairFloorTracker.floorRiseMeters();
      }
      if (yaw == null && datasets != null && !routeUseManualAlignment
          && !alignmentKey.equals(datasetAlignmentOverride)) {
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
                : "방위 센서 데이터를 기다리고 있어요. 폰을 잠시 안정적으로 들어 주세요. 계속 대기하면 방향 보정에서 기준을 지정할 수 있어요.";
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
      Pose playbackStart = experimentReplay && datasets != null
          ? datasets.playbackStartCameraPose() : null;
      if (experimentReplay && playbackStart == null) {
        alignmentStatus = "녹화의 첫 추적 위치를 복원하는 중입니다.";
        return;
      }
      // The stair A→B experiment uses its recorded first camera pose. Plane estimates
      // during replay vary, so hold the route at an assumed 1.1 m camera height.
      Pose startPose = experimentReplay ? playbackStart
          : requestedStartAnchor != null ? requestedStartAnchor.getPose() : camera.getPose();
      final Float expectedFloorY = expectedNextFloorY;
      Float floorY = FloorPlaneGeometry.navigationFloor(experimentReplay,
          playbackStart == null ? 0f : playbackStart.ty() - EXPERIMENT_CAMERA_HEIGHT_METERS,
          () -> findFloor(startPose, expectedFloorY));
      if (floorY == null && nextFloorStart && expectedNextFloorY != null
          && stairFloorTracker != null
          && stairFloorTracker.phase() == StairFloorTracker.Phase.NEXT_FLOOR
          && FloorPlaneGeometry.isNearbyFloor(
              camera.getPose().ty() - expectedNextFloorY, 0f)) {
        // ARCore Y confirmed the floor change, but the exit floor plane may not
        // have been detected yet. Use the expected floor height until it is.
        floorY = expectedNextFloorY;
      }
      if (floorY == null) {
        alignmentStatus = "가까운 앞쪽 바닥을 천천히 비춰 수평면을 인식시켜 주세요.";
        return;
      }
      // Existing route vertices are at y=-0.5; place the ribbon 2cm above the floor.
      Pose origin = yawPose(startPose.tx(), floorY + 0.52f,
          startPose.tz(), yaw);
      routeCameraHeight = experimentReplay ? EXPERIMENT_CAMERA_HEIGHT_METERS
          : startPose.ty() - floorY;
      Anchor newOrigin = session.createAnchor(origin);
      detachOriginAnchor();
      originAnchor = newOrigin;
      detachRouteEntryAnchor();
      detachRequestedStartAnchor();
      if (nextFloorStart) {
        observedStairLine = null;
        observedStairMarker = null;
        fallbackStairPath = null;
        fallbackStairLine = null;
        stairApproachLine = null;
        stairFloorTracker = null;
        activeStageIndex++;
        route = activeMapPlan.stages.get(activeStageIndex).waypoints;
        routeLineGeometry = new RouteLineGeometry(route);
        // Corrections from the previous floor's landmarks must not move this stage.
        semanticAlignment.reset();
        corridorCenter.reset();
        lastCenterRouteYaw = Double.NaN;
        lastCorridorEvidenceMs = 0;
        segmentation.setMap(null);
        waitingForFloorChange = false;
        returnToStartRequired = false;
        startLocation = activeMapPlan.stages.get(activeStageIndex).floorId + " 출구";
        Log.i(TAG, "Next floor stage anchored: " + startLocation);
      }
      finishNavigationStart(frame, camera);
    } catch (NotTrackingException e) {
      // Tracking may change while ARCore processes the request. Retry next frame.
      Log.w(TAG, "Waiting for tracking before creating the origin", e);
    }
  }

  private void startFromCloud(Frame frame, Camera camera) {
    long now = SystemClock.elapsedRealtime();
    if (!cloudModeEnabled || (cloudDeadline != 0 && now >= cloudDeadline)) {
      fallbackFromCloud();
      return;
    }
    if (cloudDeadline == 0) cloudDeadline = now + ENTRANCE_RESOLVE_TIMEOUT_MS;
    alignmentStatus = getString(R.string.cloud_entrance_scan);
    try {
      if (cloudResolve == null && resolvedEntrance == null
          && camera.getTrackingState() == TrackingState.TRACKING) {
        cloudResolve = session.resolveCloudAnchorAsync(entranceCloudAnchor.cloudId, null);
      }
      if (cloudResolve != null && cloudResolve.getState() == FutureState.DONE) {
        Anchor.CloudAnchorState state = cloudResolve.getResultCloudAnchorState();
        resolvedEntrance = cloudResolve.getResultAnchor();
        cloudResolve = null;
        if (state != Anchor.CloudAnchorState.SUCCESS || resolvedEntrance == null) {
          Log.w(TAG, "Entrance Cloud Anchor resolve failed: " + state);
          fallbackFromCloud();
          return;
        }
      }
      if (resolvedEntrance == null || resolvedEntrance.getTrackingState() == TrackingState.PAUSED
          || camera.getTrackingState() != TrackingState.TRACKING) return;
      Pose origin = resolvedEntrance.getTrackingState() == TrackingState.TRACKING
          ? entranceCloudAnchor.routeOrigin(resolvedEntrance.getPose()) : null;
      if (origin == null) {
        fallbackFromCloud();
        return;
      }
      Pose cameraPose = camera.getPose();
      float cameraHeight = cameraPose.ty() - (origin.ty() + route.get(0).y);
      double distance = Math.hypot(cameraPose.tx() - origin.tx(), cameraPose.tz() - origin.tz());
      if (distance > ENTRANCE_START_RADIUS_METERS
          || !FloorPlaneGeometry.isNearbyFloor(cameraHeight, 0)) {
        alignmentStatus = getString(R.string.cloud_entrance_move_to_start);
        return;
      }
      originAnchor = session.createAnchor(origin);
      routeCameraHeight = cameraHeight;
      clearCloudResolve();
      cloudStartPending = false;
      finishNavigationStart(frame, camera);
    } catch (NotTrackingException ignored) {
      // Tracking may change before ARCore creates the route origin. Retry on the next frame.
    } catch (RuntimeException failure) {
      Log.w(TAG, "Entrance Cloud Anchor unavailable", failure);
      fallbackFromCloud();
    }
  }

  private void fallbackFromCloud() {
    clearCloudResolve();
    cloudStartPending = false;
    startNavigationRequested = false;
    returnToStartRequired = true;
    alignmentStatus = getString(R.string.cloud_entrance_fallback);
  }

  private void clearCloudResolve() {
    if (cloudResolve != null) {
      if (!cloudResolve.cancel()) {
        Anchor result = cloudResolve.getResultAnchor();
        if (result != null) result.detach();
      }
      cloudResolve = null;
    }
    if (resolvedEntrance != null) {
      resolvedEntrance.detach();
      resolvedEntrance = null;
    }
    cloudDeadline = 0;
  }

  private void finishNavigationStart(Frame frame, Camera camera) {
    currentWaypointIndex = 0;
    navigationStarted = true;
    if (activeMapPlan != null) {
      ArRoutePlan.Stage stage = activeMapPlan.stages.get(activeStageIndex);
      stairFloorTracker = stage.transition != null && stage.transition.startsWith("STAIR")
          ? new StairFloorTracker(stage.floorId, stage.nextFloorId,
              "STAIR_UP".equals(stage.transition), camera.getPose().ty(),
              Math.abs(route.get(route.size() - 1).y - route.get(0).y)) : null;
    }
    returnToStartRequired = false;
    alignmentStatus = null;
    navigationCompleted = false;
    startNavigationRequested = false;
    routeLineGeometry.invalidate();
    lastDebugUpdateTime = 0;
    Log.i(TAG, "Navigation started: frame=" + frame.getTimestamp()
        + " origin=" + originAnchor.getPose() + " target=WP1");
  }

  private void resetNavigation() {
    clearCloudResolve();
    resetOutdoorObservation();
    cloudStartPending = false;
    semanticAlignment.reset();
    corridorCenter.reset();
    lastCenterRouteYaw = Double.NaN;
    lastCorridorEvidenceMs = 0;
    if (segmentation != null) segmentation.invalidate();
    observedStairLine = null;
    observedStairMarker = null;
    fallbackStairPath = null;
    fallbackStairLine = null;
    stairApproachLine = null;
    stairFloorTracker = null;
    lastRenderedLine = null;
    lastStairDepthScanAt = 0;
    lastStairStatusLogAt = 0;
    lastStairDepthPointCount = 0;
    detachOriginAnchor();
    detachRouteEntryAnchor();
    detachRequestedStartAnchor();
    returnToStartRequired |= navigationStarted || navigationCompleted;
    navigationStarted = false;
    navigationCompleted = false;
    alignmentStatus = null;
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

  private void detachRequestedStartAnchor() {
    if (requestedStartAnchor != null) {
      requestedStartAnchor.detach();
      requestedStartAnchor = null;
    }
    captureStartOnTracking = false;
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
    return correctedRoutePose().inverse().compose(camera.getDisplayOrientedPose());
  }

  private void updateNavigation(Camera camera) {
    boolean tracking = camera.getTrackingState() == TrackingState.TRACKING;
    if (navigationStarted && (originAnchor == null
        || originAnchor.getTrackingState() == TrackingState.STOPPED)) {
      resetNavigation();
    }
    if (returnToStartRequired && !startNavigationRequested) {
      postDebugText(getString(R.string.navigation_origin_lost),
          getString(R.string.nav_status_restart), tracking);
      return;
    }
    if (navigationCompleted) {
      if (awaitNextFloor()) return;
      postDebugText(getString(R.string.navigation_arrived, route.size()),
          getString(R.string.nav_status_arrived), tracking);
      return;
    }
    if (waitingForFloorChange) {
      ArRoutePlan.Stage stage = activeMapPlan.stages.get(activeStageIndex);
      postDebugText(stairMarkerMode && stage.transition != null
              && stage.transition.startsWith("STAIR")
              ? getString(R.string.navigation_stair_marker,
                  getString("STAIR_UP".equals(stage.transition)
                      ? R.string.stair_marker_up : R.string.stair_marker_down),
                  stage.nextFloorId)
          : observedStairLine == null && fallbackStairPath != null
              ? getString(fallbackStairPath.hasStairHeight
                  ? R.string.navigation_stair_scan_mapped
                  : R.string.navigation_stair_scan, stage.nextFloorId)
              : observedStairLine != null
                  ? getString(R.string.navigation_stair_observed, stage.nextFloorId)
              : getString(R.string.navigation_floor_change,
                  transitionLabel(stage.transition), stage.nextFloorId),
          getString(R.string.nav_status_next_floor, stage.nextFloorId), tracking);
      return;
    }
    if (!tracking) {
      postDebugText(getString(R.string.navigation_tracking,
          camera.getTrackingState().name(), camera.getTrackingFailureReason().name()),
           cloudStartPending && alignmentStatus != null ? alignmentStatus
               : getString(navigationStarted ? R.string.nav_status_recovering : R.string.nav_status_tracking),
          false);
      return;
    }
    if (!navigationStarted) {
      if (SystemClock.elapsedRealtime() - lastDebugUpdateTime < DEBUG_UPDATE_INTERVAL_MS) return;
      boolean alignWithCorridor = selectedMapRouteName != null && routeEntryYawDegrees != null
          && !routeUseManualAlignment && !MapOrientation.usesSensor(alignmentKey);
      boolean canStart = !startNavigationRequested
          && (selectedMapRouteName == null || findFloor(camera.getPose()) != null);
      String status = alignmentStatus;
      if (status == null && selectedMapRouteName != null && !startNavigationRequested && !canStart) {
        status = getString(R.string.nav_status_find_floor);
      } else if (status == null) {
        if (startNavigationRequested) status = getString(R.string.nav_status_preparing);
        else if (selectedMapRouteName == null) status = getString(R.string.nav_status_choose_route);
        else status = getString(alignWithCorridor
            ? R.string.nav_status_ready_aligned : R.string.nav_status_ready);
      }
      postDebugText(alignmentStatus != null ? alignmentStatus
          : selectedMapRouteName != null && MapOrientation.usesSensor(alignmentKey) && !routeUseManualAlignment
              ? getString(R.string.navigation_ready_sensor, selectedMapRouteName)
          : selectedMapRouteName != null && routeEntryYawDegrees != null && !routeUseManualAlignment
              ? getString(R.string.navigation_ready_entry, selectedMapRouteName, routeEntryDirectionLabel)
          : selectedMapRouteName == null ? getString(R.string.navigation_ready)
          : getString(R.string.navigation_ready_map, selectedMapRouteName), status, canStart);
      return;
    }

    Pose relativePose = getRelativeCameraPose(camera);
    if (relativePose == null) {
      postDebugText(getString(R.string.navigation_origin_paused),
          getString(R.string.nav_status_recovering), false);
      return;
    }
    float distance = RouteProgress.distanceToNext(route, currentWaypointIndex,
        relativePose.tx(), relativePose.tz());

    if (advanceWaypointIfArrived(relativePose)) {
      lastDebugUpdateTime = 0;
      Log.i(TAG, String.format(Locale.US,
          "Waypoint %d reached: x=%.2f z=%.2f distance=%.2f",
          currentWaypointIndex, relativePose.tx(), relativePose.tz(), distance));
      if (navigationCompleted) {
        if (awaitNextFloor()) return;
        detachOriginAnchor();
        postDebugText(getString(R.string.navigation_arrived, route.size()),
            getString(R.string.nav_status_arrived), true);
        Log.i(TAG, "Navigation arrived: all " + route.size() + " waypoints reached");
        return;
      }
      distance = RouteProgress.distanceToNext(route, currentWaypointIndex,
          relativePose.tx(), relativePose.tz());
    }
    updateDebugText(relativePose.tx(), relativePose.ty(), relativePose.tz(),
        distance, currentWaypointIndex);
  }

  private boolean awaitNextFloor() {
    if (activeMapPlan == null || activeStageIndex + 1 >= activeMapPlan.stages.size()) return false;
    ArRoutePlan.Stage stage = activeMapPlan.stages.get(activeStageIndex);
    navigationStarted = false;
    navigationCompleted = false;
    waitingForFloorChange = true;
    returnToStartRequired = false;
    if (stage.transition != null && stage.transition.startsWith("STAIR")) {
      prepareStairFallback(stage);
    } else {
      detachOriginAnchor();
    }
    postDebugText(getString(R.string.navigation_floor_change,
        transitionLabel(stage.transition), stage.nextFloorId),
        getString(R.string.nav_status_next_floor, stage.nextFloorId), true);
    return true;
  }

  private static String transitionLabel(String transition) {
    if ("STAIR_UP".equals(transition)) return "계단(올라감)";
    if ("STAIR_DOWN".equals(transition)) return "계단(내려감)";
    if ("ELEVATOR".equals(transition)) return "엘리베이터";
    if ("ESCALATOR".equals(transition)) return "에스컬레이터";
    if ("RAMP".equals(transition)) return "경사로";
    return transition == null ? "층간 통로" : transition;
  }

  // Kept independent of the camera/UI so the real route progression can be tested.
  boolean advanceWaypointIfArrived(Pose relativePose) {
    if (!navigationStarted || currentWaypointIndex >= route.size()) {
      return false;
    }
    Waypoint target = route.get(currentWaypointIndex);
    float distance = target.horizontalDistanceTo(relativePose.tx(), relativePose.tz());
    boolean stairHeightRequired = activeMapPlan != null
        && activeMapPlan.stages.get(activeStageIndex).transition != null
        && activeMapPlan.stages.get(activeStageIndex).transition.startsWith("STAIR")
        && Math.abs(target.y - route.get(0).y) > 0.5f;
    if (stairHeightRequired) {
      float dy = target.y - (relativePose.ty() - routeCameraHeight);
      distance = (float) Math.hypot(distance, dy);
    }
    boolean passed = !stairHeightRequired && RouteProgress.passedIntermediate(route,
        currentWaypointIndex, relativePose.tx(), relativePose.tz());
    if (distance >= ARRIVAL_DISTANCE && !passed) {
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
    RouteTurn turn = RouteTurn.next(route, waypointIndex, x, z);
    String status = getString(R.string.nav_status_active, distance);
    if (turn != null) {
      int directionText;
      switch (turn.direction) {
        case LEFT: directionText = R.string.nav_turn_left; break;
        case RIGHT: directionText = R.string.nav_turn_right; break;
        default: directionText = R.string.nav_turn_back; break;
      }
      String direction = getString(directionText);
      status = turn.distance <= ARRIVAL_DISTANCE ? getString(R.string.nav_turn_here, direction)
          : getString(R.string.nav_turn_ahead, turn.distance, direction);
    }
    postDebugText(String.format(Locale.getDefault(),
        getString(R.string.navigation_debug), x, y, z, waypointIndex + 1, route.size(), distance),
        status, false);
  }

  private void postDebugText(String text, String status, boolean canStart) {
    long now = SystemClock.elapsedRealtime();
    if (now - lastDebugUpdateTime < DEBUG_UPDATE_INTERVAL_MS) {
      return;
    }
    lastDebugUpdateTime = now;
    String location = startLocation == null ? "" : getString(R.string.nav_ar_start_location, startLocation);
    boolean confirmStart = !waitingForFloorChange && (returnToStartRequired || navigationCompleted);
    boolean exitReached = waitingToConfirmOutside();
    int action = exitReached ? R.string.outdoor_checking
        : navigationStarted ? R.string.nav_start_active
        : startNavigationRequested ? R.string.nav_start_preparing
        : waitingForFloorChange ? R.string.navigation_next_floor
        : confirmStart ? R.string.nav_start_confirm_location
        : selectedMapRouteName == null ? R.string.nav_start_sample : R.string.navigation_start;
    // Capture the state on the render thread; the UI never reads mutable navigation state.
    debugText.post(() -> {
      debugText.setText(text);
      String routeStatus = exitReached ? getString(R.string.outdoor_confirm_location) : status;
      if (!routeStatus.contentEquals(navigationStatus.getText())) navigationStatus.setText(routeStatus);
      if (!location.contentEquals(navigationStartLocation.getText())) {
        navigationStartLocation.setText(location);
        navigationStartLocation.setVisibility(location.isEmpty() ? View.GONE : View.VISIBLE);
      }
      confirmStartOnUi = confirmStart;
      String actionText = getString(action);
      if (!actionText.contentEquals(navigationStartButton.getText())) {
        navigationStartButton.setText(actionText);
      }
      navigationStartButton.setEnabled(!exitReached && canStart);
    });
  }

  private void updateDatasetControls() {
    ArDatasetController.Mode mode = datasets == null ? ArDatasetController.Mode.LIVE : datasets.mode();
    boolean playback = mode == ArDatasetController.Mode.PLAYBACK;
    Button record = findViewById(R.id.record_ar_button);
    record.setEnabled(datasets != null && !playback);
    record.setText(mode == ArDatasetController.Mode.RECORDING
        ? R.string.ar_record_stop : R.string.ar_record_start);
    findViewById(R.id.recordings_button).setEnabled(datasets != null);
    findViewById(R.id.experiment_replay_button).setEnabled(
        datasets != null && mode != ArDatasetController.Mode.RECORDING && !experimentLoading.get());
    findViewById(R.id.stop_playback_button).setVisibility(playback ? View.VISIBLE : View.GONE);
    TextView status = findViewById(R.id.dataset_status);
    status.setVisibility(playback || mode == ArDatasetController.Mode.RECORDING
        ? View.VISIBLE : View.GONE);
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
    if (session == null || !activityResumed || datasets == null) {
      Toast.makeText(this, "AR 세션이 준비된 뒤 녹화를 시작하세요.", Toast.LENGTH_LONG).show();
      return;
    }
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
    RecordingRequest request = pendingRecording.get();
    if (request == null || datasets == null || datasets.mode() != ArDatasetController.Mode.LIVE) return;
    if (request.point != null && (request.point.direction != null
        || MapOrientation.usesSensor(request.point.mapAsset))) {
      boolean ready = frame.getCamera().getTrackingState() == TrackingState.TRACKING;
      if (ready && MapOrientation.usesSensor(request.point.mapAsset))
        ready = datasets.configuredSensorYaw(request.point.mapAsset, frame) != null;
      if (!ready) {
        if (SystemClock.elapsedRealtime() - request.createdAt > 20_000
            && pendingRecording.compareAndSet(request, null))
          runOnUiThread(() -> Toast.makeText(this,
              "위치 추적과 방위 센서가 준비되지 않아 녹화를 시작하지 못했습니다.",
              Toast.LENGTH_LONG).show());
        return;
      }
    }
    if (!pendingRecording.compareAndSet(request, null)) return;
    datasets.startRecording(session, request.point, frame);
    if (datasets.mode() == ArDatasetController.Mode.RECORDING && request.point != null
        && (request.point.direction != null || MapOrientation.usesSensor(request.point.mapAsset))) {
      if (request.point.mapAsset.equals(datasetAlignmentOverride)) datasetAlignmentOverride = null;
      if (request.point.mapAsset.equals(alignmentKey)) {
        routeUseManualAlignment = false;
        resetNavigation();
        startNavigationRequested = false;
        alignmentStatus = MapOrientation.usesSensor(request.point.mapAsset)
            ? "폰 방위 센서와 반시계 70°로 문화관 방향을 적용했습니다. 길안내 시작을 누르세요."
            : "문에서 복도 쪽을 바라보는 출발 방향을 적용했습니다. 길안내 시작을 누르세요.";
      }
    }
  }

  private void startExperimentReplay() {
    if (datasets == null || recordingStore == null
        || datasets.mode() == ArDatasetController.Mode.RECORDING
        || !experimentLoading.compareAndSet(false, true)) return;
    ArRecordingStore.Entry recording = null;
    for (ArRecordingStore.Entry entry : recordingStore.list()) {
      if (entry.playable() && EXPERIMENT_RECORDING_ID.equals(entry.id)
          && entry.startPoint != null
          && MapRouteActivity.MUNHWAGWAN_MAP.equals(entry.startPoint.mapAsset)
          && "F3_STAIR_A".equals(entry.startPoint.nodeId)) {
        recording = entry;
        break;
      }
    }
    if (recording == null) {
      experimentLoading.set(false);
      Toast.makeText(this, R.string.experiment_replay_missing, Toast.LENGTH_LONG).show();
      return;
    }
    Button button = findViewById(R.id.experiment_replay_button);
    button.setEnabled(false);
    ArRecordingStore.Entry selected = recording;
    new Thread(() -> {
      try {
        ArRoutePlan plan = buildExperimentRoute();
        runOnUiThread(() -> {
          experimentLoading.set(false);
          button.setEnabled(true);
          if (isFinishing() || !activityResumed) return;
          switchDataset(selected);
          if (datasets.mode() != ArDatasetController.Mode.PLAYBACK) return;
          experimentReplay = true;
          experimentAutoStartPending = true;
          pendingMapRoute.set(plan);
          angleDebugControls.setVisibility(View.VISIBLE);
          findViewById(R.id.ar_tools_panel).setVisibility(View.GONE);
          ((Button) findViewById(R.id.ar_tools_toggle)).setText(R.string.nav_tools_open);
          Log.i(TAG, "Experiment replay started with live map angle " + debugMapAngleDegrees);
        });
      } catch (Exception error) {
        Log.e(TAG, "Experiment route unavailable", error);
        runOnUiThread(() -> {
          experimentLoading.set(false);
          button.setEnabled(true);
          if (!isFinishing()) Toast.makeText(this,
              "실험 경로를 열 수 없습니다: " + error.getMessage(), Toast.LENGTH_LONG).show();
        });
      }
    }, "ar-experiment-route").start();
  }

  private ArRoutePlan buildExperimentRoute() throws IOException {
    MapLoadResult loaded = new IndoorMapRepository().loadFromAsset(
        getAssets()::open, MapRouteActivity.MUNHWAGWAN_MAP);
    if (!loaded.isSuccess()) throw new IOException("문화관 지도를 불러오지 못했습니다.");
    NavigationRoute path = new AStarRouteEngine(loaded.graph)
        .findRoute("F3_STAIR_A", "F3_STAIR_B", RouteOptions.DEFAULT).route;
    if (path == null || path.nodes.size() < 2)
      throw new IOException("계단 A→B 경로를 찾지 못했습니다.");
    MapCoordinateTransformer transformer = new LocalMapCoordinateTransformer(
        loaded.map.coordinateSystem, path.nodes.get(0).position,
        new MapPosition(0, -0.5, 0), 0);
    List<MapPosition> points = RouteCoordinateAdapter.toAr(path, transformer);
    float[] packed = new float[points.size() * 3];
    for (int i = 0; i < points.size(); i++) {
      packed[i * 3] = (float) points.get(i).x;
      packed[i * 3 + 1] = (float) points.get(i).y;
      packed[i * 3 + 2] = (float) points.get(i).z;
    }
    return ArRoutePlan.decode("계단 A→B 각도 시험", packed,
        MapRouteActivity.MUNHWAGWAN_MAP, null, null, "3층 계단 A");
  }

  // Stop the GL thread before changing the session's source. Both sources feed the same renderer.
  private void switchDataset(ArRecordingStore.Entry entry) {
    if (datasets == null || !activityResumed) return;
    if (datasets.mode() == ArDatasetController.Mode.RECORDING) {
      Toast.makeText(this, "녹화를 먼저 종료하세요.", Toast.LENGTH_LONG).show(); return;
    }
    experimentReplay = false;
    experimentAutoStartPending = false;
    angleDebugControls.setVisibility(View.GONE);
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
    startFromUi.set(null);
    routeUseManualAlignment = false;
    if (datasets != null) datasets.clearMapReference();
    startNavigationRequested = false;
    resetNavigationRequested = false;
    calibrateRequested = false;
    pendingBearing.set(null);
    resetNavigation();
    detachDirectionAnchor();
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
            ? "문화관 기본 정렬은 폰 센서의 자북에서 반시계 70°(방위각 290°)입니다. 아래 수동 설정은 기본 정렬 대신 사용하며 70°를 추가로 더하지 않습니다. 현재 방향은 도면 위쪽을 바라볼 때만 사용하세요. 설정하면 안내가 초기화됩니다."
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

  private Float findFloor(Pose camera) { return findFloor(camera, null); }

  private Float findFloor(Pose camera, Float expectedY) {
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
          && (expectedY == null || Math.abs(y - expectedY) <= 0.5f)
          && (height == null || (expectedY == null ? y < height
              : Math.abs(y - expectedY) < Math.abs(height - expectedY)))) height = y;
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

  private Pose correctedRoutePose() {
    Rigid2D transform=semanticAlignment.current();
    double angleDelta = experimentReplay && MapOrientation.usesSensor(alignmentKey)
        && !routeUseManualAlignment
        ? Math.toRadians(debugMapAngleDegrees - MapOrientation.MUNHWAGWAN_CCW_DEGREES) : 0;
    return levelAnchorPose().compose(yawPose(
        corridorCenter.poseX((float) transform.x, (float) transform.z), 0,
        corridorCenter.poseZ((float) transform.x, (float) transform.z),
        transform.yaw + corridorCenter.yaw() + angleDelta));
  }

  private void processSemanticFrame(Frame frame) {
    boolean tracking=frame.getCamera().getTrackingState()==TrackingState.TRACKING;
    boolean anchored=navigationStarted&&originAnchor!=null&&originAnchor.getTrackingState()==TrackingState.TRACKING;
    SegmentationController.Completed completed=segmentation.takeCompleted();
    if(completed!=null&&semanticCorrectionEnabled&&!experimentReplay
        &&segmentation.isEnabled()&&tracking&&anchored)
      lastCorrectionAccepted=semanticAlignment.accept(completed.match,frame.getTimestamp());
    semanticAlignment.tick(frame.getTimestamp(),tracking&&anchored&&semanticCorrectionEnabled
        &&!experimentReplay&&segmentation.isEnabled());
    boolean checkingExit = waitingToConfirmOutside();
    observeOutdoorScene(frame.getTimestamp(), completed, checkingExit);
    segmentation.onFrame(frame,anchored?levelAnchorPose():null,tracking&&anchored,
        semanticAlignment.current(), checkingExit);
    long now=SystemClock.elapsedRealtime();
    updateCorridorCenter(completed, anchored && tracking, now);
    if(now-lastSemanticUi<500)return;
    lastSemanticUi=now;
    SegmentationController.Completed latest=segmentation.latest();
    Rigid2D applied=semanticAlignment.current();
    SemanticLandmarkMap map=segmentation.map();
    StringBuilder message=new StringBuilder(segmentation.state());
    message.append("\n지도 기준점: ").append(map==null?0:map.verifiedCount()).append("개 실측 / ")
        .append(map==null?0:map.usableCount()).append("개 도면 사용");
    if(latest!=null) {
      int doors=0,stairs=0,depth=0;
      for(LandmarkObservation ob:latest.observations){if(ob.type.equals("DOOR"))doors++;if(ob.type.equals("STAIRS"))stairs++;if(ob.metricValid)depth++;}
      message.append(String.format(Locale.US,"\n%.0f ms · door %d stairs %d · 3D %d",latest.segmentation.inferenceMillis,doors,stairs,depth));
      message.append("\n").append(latest.match.status).append(" ").append(latest.match.reason);
      message.append("\n누적 문·계단 ").append(latest.accumulatedCount).append(" · 복도 방향 ").append(latest.corridorYaw==null?"대기":"합의");
      message.append("\n확인 추적 ").append(latest.confirmedTracks).append(" · 경로 2D 추적 ").append(latest.bearingTracks);
      message.append(String.format(Locale.US,"\ncandidates %d inliers %d residual %.3f %s",latest.match.candidates,latest.match.inliers,latest.match.residual,latest.match.residualUnit));
      float[] pose=latest.segmentation.frame.cameraToWorld;
      message.append(String.format(Locale.US,"\nAR camera %.2f %.2f %.2f",pose[12],pose[13],pose[14]));
    }
    message.append(String.format(Locale.US,"\n보정 전 (0,0,0°) → (%.2f,%.2f,%.1f°) 적용 %s",applied.x,applied.z,Math.toDegrees(applied.yaw),lastCorrectionAccepted));
    String text=message.toString();
    runOnUiThread(()->{
      semanticOverlay.display(latest,text);
      ((TextView)findViewById(R.id.semantic_model_state)).setText(segmentation.state());
    });
  }

  private boolean waitingToConfirmOutside() {
    return navigationCompleted && !waitingForFloorChange && exitPlan != null
        && activeMapPlan == exitPlan && (datasets == null || datasets.mode() != ArDatasetController.Mode.PLAYBACK);
  }

  private void resetOutdoorObservation() {
    outdoorCheckStart = 0;
    outdoorDepartureFrame = 0;
    outdoorExitGate.reset();
  }

  private void observeOutdoorScene(long now, SegmentationController.Completed completed, boolean checking) {
    if (!checking) {
      if (outdoorCheckStart != 0) resetOutdoorObservation();
      return;
    }
    if (outdoorCheckStart == 0) outdoorCheckStart = now;
    if (completed == null || outdoorDepartureFrame != 0) return;
    long timestamp = completed.segmentation.frame.timestamp;
    if (timestamp < outdoorCheckStart || !outdoorExitGate.observe(completed.segmentation, now)) return;
    outdoorDepartureFrame = timestamp;
    Log.i(TAG, "Outdoor departure confirmed: frame=" + timestamp);
    runOnUiThread(() -> {
      if (activityResumed && !isFinishing() && outdoorDestination != null
          && outdoorDepartureFrame == timestamp) completeOutdoorDeparture();
    });
  }

  private void updateCorridorCenter(SegmentationController.Completed completed,
      boolean anchored, long nowMs) {
    if (!anchored || activeMapPlan == null || !segmentation.isEnabled()
        || experimentReplay
        || fallbackStairPath != null
        || currentWaypointIndex >= route.size()) {
      corridorCenter.reset();
      lastCenterRouteYaw = Double.NaN;
      lastCorridorEvidenceMs = 0;
      return;
    }
    Waypoint from = currentWaypointIndex == 0
        ? new Waypoint(0, route.get(0).y, 0) : route.get(currentWaypointIndex - 1);
    Waypoint to = route.get(currentWaypointIndex);
    double routeYaw = Math.atan2(to.x - from.x, to.z - from.z);
    if (Double.isFinite(lastCenterRouteYaw)
        && Math.abs(Rigid2D.wrap(routeYaw - lastCenterRouteYaw)) > Math.toRadians(20)) {
      corridorCenter.reset();
      lastCorridorEvidenceMs = 0;
    }
    lastCenterRouteYaw = routeYaw;
    if (completed != null && completed.segmentation != null) {
      CorridorCenterEstimator.Observation observation = corridorCenterEstimator.estimate(
          completed.segmentation, completed.wallBoundaries, route, currentWaypointIndex,
          semanticAlignment.current());
      if (observation != null) {
        lastCorridorEvidenceMs = nowMs;
        lastCorridorWidth = observation.width;
        lastCorridorSingleWall = observation.singleWall;
      }
      corridorCenter.observe(observation, nowMs);
    }
    corridorCenter.tick(nowMs);
  }

  /** Configures the session with feature settings. */
  private void configureSession() {
    Config config = session.getConfig();
    config.setPlaneFindingMode(Config.PlaneFindingMode.HORIZONTAL);
    config.setLightEstimationMode(Config.LightEstimationMode.DISABLED);
    config.setDepthMode(session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)
        ? Config.DepthMode.AUTOMATIC : Config.DepthMode.DISABLED);
    config.setInstantPlacementMode(Config.InstantPlacementMode.DISABLED);
    cloudModeEnabled = cloudConsentGranted
        && (datasets == null || datasets.mode() != ArDatasetController.Mode.PLAYBACK);
    if (cloudModeEnabled) {
      config.setCloudAnchorMode(Config.CloudAnchorMode.ENABLED);
      try {
        session.configure(config);
        return;
      } catch (RuntimeException failure) {
        Log.w(TAG, "Could not enable Cloud Anchors", failure);
        cloudModeEnabled = false;
      }
    }
    config.setCloudAnchorMode(Config.CloudAnchorMode.DISABLED);
    session.configure(config);
  }

  private void logArCameraOptions() {
    try {
      CameraConfigFilter filter = new CameraConfigFilter(session);
      filter.setFacingDirection(CameraConfig.FacingDirection.BACK);
      CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
      Set<String> seen = new HashSet<>();
      Log.i(TAG, "ARCore active camera ID=" + session.getCameraConfig().getCameraId());
      for (CameraConfig option : session.getSupportedCameraConfigs(filter)) {
        String id = option.getCameraId();
        if (!seen.add(id)) continue;
        String fov = "unknown";
        try {
          CameraCharacteristics camera = manager.getCameraCharacteristics(id);
          android.util.SizeF sensor = camera.get(
              CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE);
          float[] focal = camera.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS);
          if (sensor != null && focal != null && focal.length > 0 && focal[0] > 0)
            fov = String.format(Locale.US, "%.1f°", Math.toDegrees(
                2 * Math.atan(sensor.getWidth() / (2 * focal[0]))));
        } catch (Exception ignored) { }
        Log.i(TAG, "ARCore rear camera option ID=" + id + " horizontal FOV≈" + fov
            + " image=" + option.getImageSize() + " depth=" + option.getDepthSensorUsage());
      }
    } catch (Exception e) {
      Log.w(TAG, "ARCore camera options unavailable", e);
    }
  }
}
