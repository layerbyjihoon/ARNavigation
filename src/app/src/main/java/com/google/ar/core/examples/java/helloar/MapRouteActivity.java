package com.google.ar.core.examples.java.helloar;

import android.content.Intent;
import android.os.Bundle;
import android.os.Build;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import com.google.ar.core.examples.java.helloar.coordinates.*;
import com.google.ar.core.examples.java.helloar.map.*;
import com.google.ar.core.examples.java.helloar.routing.*;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import com.google.gson.Gson;
import com.google.ar.core.examples.java.helloar.localization.SemanticLandmarkMap;

/** Repository owns loading, engine owns routing, adapter owns coordinates; this is a test UI. */
public final class MapRouteActivity extends AppCompatActivity {
  public static final String EXTRA_POINTS = "map_route_points", EXTRA_NAME = "map_route_name";
  public static final String EXTRA_REFERENCE = "map_reference_id";
  public static final String EXTRA_SEMANTIC_MAP = "semantic_route_map";
  public static final String EXTRA_START_LOCATION = "map_start_location";
  public static final String EXTRA_ENTRY_YAW = "route_entry_yaw", EXTRA_ENTRY_DIRECTION = "route_entry_direction";
  public static final String EXTRA_STAGE_LENGTHS = "map_route_stage_lengths";
  public static final String EXTRA_FLOORS = "map_route_floors";
  public static final String EXTRA_NEXT_FLOORS = "map_route_next_floors";
  public static final String EXTRA_TRANSITIONS = "map_route_transitions";
  public static final String TEST_MAP = "maps/test_building.indoor.json";
  public static final String MUNHWAGWAN_MAP = "maps/munhwagwan.indoor.json";
  public static final String EXTRA_MAP_INDEX = "map_index";
  private static final String[] MAP_ASSETS = {MUNHWAGWAN_MAP, TEST_MAP};
  private final ExecutorService worker = Executors.newSingleThreadExecutor();
  private TextView summary, output, notice, routeSummary;
  private Spinner maps, start, destination;
  private CheckBox wheelchair;
  private EditText yaw;
  private Button find, apply;
  private MapLoadResult loaded;
  private NavigationRoute selectedRoute;
  private float[] selectedPoints;
  private int[] selectedStageLengths;
  private String[] selectedFloors, selectedNextFloors, selectedTransitions;
  private EntryDirection selectedEntryDirection;
  private SemanticLandmarkMap selectedSemanticMap;
  private int savedStart, savedDestination = 1;
  private boolean savedWheelchair;
  private String savedYaw = "0";
  private boolean useDefaultYaw;
  private int selectedMap, loadGeneration;

  @Override protected void onCreate(Bundle saved) {
    super.onCreate(saved);
    setContentView(R.layout.activity_map_route);
    // Android 15+ enforces edge-to-edge: keep controls clear of system bars and the keyboard.
    android.view.View content = findViewById(R.id.map_root);
    content.setOnApplyWindowInsetsListener((view, insets) -> {
      if (Build.VERSION.SDK_INT >= 30) {
        android.graphics.Insets safe = insets.getInsets(android.view.WindowInsets.Type.systemBars()
            | android.view.WindowInsets.Type.displayCutout() | android.view.WindowInsets.Type.ime());
        view.setPadding(safe.left, safe.top, safe.right, safe.bottom);
      } else {
        view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
            insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
      }
      return insets;
    });
    content.requestApplyInsets();
    summary = findViewById(R.id.map_summary); output = findViewById(R.id.route_result);
    notice = findViewById(R.id.map_notice); maps = findViewById(R.id.map_dataset);
    routeSummary = findViewById(R.id.route_summary);
    View details = findViewById(R.id.map_details_panel);
    Button detailsToggle = findViewById(R.id.map_details_toggle);
    detailsToggle.setOnClickListener(v -> {
      boolean open = details.getVisibility() != View.VISIBLE;
      details.setVisibility(open ? View.VISIBLE : View.GONE);
      detailsToggle.setText(open ? R.string.nav_details_close : R.string.nav_details_open);
    });
    start = findViewById(R.id.start_poi); destination = findViewById(R.id.destination_poi);
    wheelchair = findViewById(R.id.wheelchair_route); yaw = findViewById(R.id.map_yaw);
    find = findViewById(R.id.find_route); apply = findViewById(R.id.apply_ar_route);
    if (saved != null) {
      savedStart = saved.getInt("start"); savedDestination = saved.getInt("destination", 1);
      savedWheelchair = saved.getBoolean("wheelchair"); savedYaw = saved.getString("yaw", "0");
    }
    useDefaultYaw = saved == null;
    selectedMap = saved != null ? saved.getInt("map") : getIntent().getIntExtra(EXTRA_MAP_INDEX,
        getPreferences(MODE_PRIVATE).getInt("map", 0));
    selectedMap = Math.max(0, Math.min(selectedMap, MAP_ASSETS.length - 1));
    yaw.setText(savedYaw); wheelchair.setChecked(savedWheelchair);
    find.setEnabled(false); setPreviewAvailable(false);
    find.setOnClickListener(v -> findRoute());
    apply.setOnClickListener(v -> {
      findRoute(); // POI, policy or yaw may have changed since the previous calculation.
      if (selectedRoute != null && selectedPoints != null) {
        Poi from = (Poi) start.getSelectedItem(), to = (Poi) destination.getSelectedItem();
        MapNode first = selectedRoute.nodes.get(0);
        Intent result = new Intent().putExtra(EXTRA_POINTS, selectedPoints)
              .putExtra(EXTRA_SEMANTIC_MAP,
                  selectedSemanticMap == null ? null : new Gson().toJson(selectedSemanticMap))
            .putExtra(EXTRA_STAGE_LENGTHS, selectedStageLengths)
            .putExtra(EXTRA_FLOORS, selectedFloors)
            .putExtra(EXTRA_NEXT_FLOORS, selectedNextFloors)
            .putExtra(EXTRA_TRANSITIONS, selectedTransitions)
            .putExtra(EXTRA_REFERENCE, MAP_ASSETS[selectedMap])
            .putExtra(EXTRA_START_LOCATION, startLocation(from, first))
            .putExtra(EXTRA_NAME, from.name + " [" + endpointLabel(first)
                + "] → " + to.name + " ["
                + endpointLabel(selectedRoute.nodes.get(selectedRoute.nodes.size()-1)) + "]");
        if (selectedEntryDirection != null) result
            .putExtra(EXTRA_ENTRY_YAW, selectedEntryDirection.canonicalYawDegrees)
            .putExtra(EXTRA_ENTRY_DIRECTION, selectedEntryDirection.description());
        setResult(RESULT_OK, result);
        finish();
      }
    });
    findViewById(R.id.return_ar).setOnClickListener(v -> finish());
    ArrayAdapter<String> mapAdapter = new ArrayAdapter<>(this, R.layout.map_spinner_item,
        getResources().getStringArray(R.array.map_datasets));
    mapAdapter.setDropDownViewResource(R.layout.map_spinner_item);
    maps.setAdapter(mapAdapter); maps.setSelection(selectedMap);
    maps.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
      @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        if (position != selectedMap) {
          selectedMap = position; savedStart = 0; savedDestination = 1;
          useDefaultYaw = true;
          getPreferences(MODE_PRIVATE).edit().putInt("map", position).apply();
          loadMap();
        }
      }
      @Override public void onNothingSelected(AdapterView<?> parent) {}
    });
    loadMap();
  }

  private void loadMap() {
    final int generation = ++loadGeneration;
    final int mapIndex = selectedMap;
    final String asset = MAP_ASSETS[selectedMap];
    loaded = null; selectedRoute = null; selectedPoints = null;
    find.setEnabled(false); setPreviewAvailable(false); start.setEnabled(false); destination.setEnabled(false);
    summary.setText(R.string.map_loading); notice.setText(""); output.setText("");
    routeSummary.setText(R.string.map_loading);
    ((TextView)findViewById(R.id.map_yaw_caption)).setText(selectedMap == 0
        ? R.string.map_plan_yaw_label : R.string.map_yaw_label);
    apply.setText(selectedMap == 0 ? R.string.map_apply_preview : R.string.map_apply_ar);
    worker.execute(() -> {
      MapLoadResult result = new IndoorMapRepository().loadFromAsset(getAssets()::open, asset);
      Double defaultAngle = null;
      String angleError = null;
      if (result.isSuccess() && mapIndex == 0) {
        try { result = MunhwagwanDemoFloors.add(getAssets()::open, asset, result); }
        catch (java.io.IOException | RuntimeException e) { angleError = e.getMessage(); }
      }
      if (result.isSuccess() && TEST_MAP.equals(asset)) {
        try { defaultAngle = new BuildingAngleRepository().load(getAssets()::open, result.map.metadata.id); }
        catch (java.io.IOException | RuntimeException e) { angleError = e.getMessage(); }
      }
      final MapLoadResult configuredMap = result;
      final Double configuredAngle = defaultAngle;
      final String configurationError = angleError;
      runOnUiThread(() -> {
        if (isFinishing() || isDestroyed() || generation != loadGeneration) return;
        loaded = configuredMap;
        if (!configuredMap.isSuccess()) {
          summary.setText(R.string.map_load_failed); output.setText(issues(configuredMap.validation));
          routeSummary.setText(R.string.map_load_failed);
          return;
        }
        if (configurationError != null) {
          summary.setText(R.string.map_load_failed);
          output.setText(getString(R.string.map_angle_error, configurationError));
          routeSummary.setText(R.string.map_load_failed);
          return;
        }
        if (useDefaultYaw) {
          yaw.setText(configuredAngle == null ? "0" : java.math.BigDecimal.valueOf(configuredAngle)
              .stripTrailingZeros().toPlainString());
          useDefaultYaw = false;
        }
        IndoorMap map = configuredMap.map;
        summary.setText(getString(R.string.map_summary_format, map.metadata.name, map.floors.size(),
            map.nodes.size(), map.edges.size(), map.pois.size(), configuredMap.validation.warningCount()));
        notice.setText(map.metadata.description == null ? "" : map.metadata.description);
        ArrayAdapter<Poi> adapter = new ArrayAdapter<>(this,
            R.layout.map_spinner_item, map.pois);
        adapter.setDropDownViewResource(R.layout.map_spinner_item);
        start.setAdapter(adapter); destination.setAdapter(adapter);
        if (!map.pois.isEmpty()) {
          start.setSelection(Math.max(0, Math.min(savedStart, map.pois.size()-1)));
          destination.setSelection(Math.max(0, Math.min(savedDestination, map.pois.size()-1)));
          find.setEnabled(true);
          start.setEnabled(true); destination.setEnabled(true);
        }
        routeSummary.setText(R.string.map_select_prompt);
        output.setText(getString(R.string.map_select_prompt) + (selectedMap == 0
            ? "\n" + getString(R.string.map_munhwagwan_prompt) : issues(configuredMap.validation)));
      });
    });
  }

  private void findRoute() {
    selectedRoute = null; selectedPoints = null; selectedEntryDirection = null;
    selectedStageLengths = null; selectedFloors = selectedNextFloors = selectedTransitions = null;
    setPreviewAvailable(false);
    if (loaded == null || !loaded.isSuccess() || start.getSelectedItem() == null) return;
    Poi from = (Poi) start.getSelectedItem(), to = (Poi) destination.getSelectedItem();
    RouteResult result = new AStarRouteEngine(loaded.graph).findRoute(from, to,
        wheelchair.isChecked() ? RouteOptions.WHEELCHAIR : RouteOptions.DEFAULT);
    if (!result.isSuccess()) {
      output.setText(result.message);
      routeSummary.setText(result.message);
      return;
    }
    selectedRoute = result.route;
    boolean cultureFloorChange = selectedMap == 0 && result.route.isMultiFloor();
    StringBuilder text = new StringBuilder(String.format(Locale.getDefault(),
        "%s → %s\n%s: %.2f m · 구간: %d\n", from.name, to.name,
        cultureFloorChange ? getString(R.string.map_horizontal_distance) : getString(R.string.map_total_distance),
        result.route.totalDistance, result.route.segments.size()));
    if (cultureFloorChange) text.append(getString(R.string.map_munhwagwan_demo_stairs)).append("\n");
    MapNode firstNode = result.route.nodes.get(0);
    selectedEntryDirection = EntryDirection.resolve(loaded.map, firstNode);
    MapNode lastNode = result.route.nodes.get(result.route.nodes.size()-1);
    text.append("출발 진입점: ").append(endpointLabel(firstNode)).append("\n")
        .append("도착 진입점: ").append(endpointLabel(lastNode)).append("\n");
    if (selectedMap == 0) {
      text.append(String.format(Locale.getDefault(), "출발 도면 좌표 (%.3f, %.3f) m\n",
          firstNode.position.x, firstNode.position.z));
      java.util.Set<String> notes = new java.util.LinkedHashSet<>();
      for (MapNode node : result.route.nodes) {
        Object note = node.properties.get("note");
        if (note instanceof String && !((String)note).isEmpty()) notes.add((String)note);
      }
      for (String note : notes) text.append(note).append("\n");
    }
    for (RouteSegment segment : result.route.segments) {
      text.append(segment.fromNode.id).append(" → ").append(segment.toNode.id)
          .append(" · ").append(segment.edge.type).append(" · ").append(segment.floorId)
          .append(segment.isFloorTransition() ? " → " + segment.toFloorId : "")
          .append(String.format(Locale.getDefault(), " · %.2f m\n", segment.distance));
    }
    try {
      double angle = Double.parseDouble(yaw.getText().toString().trim());
      MapCoordinateTransformer transformer = new LocalMapCoordinateTransformer(
          loaded.map.coordinateSystem, result.route.nodes.get(0).position,
          new MapPosition(0, -0.5, 0), angle);
      List<MapPosition> ar = RouteCoordinateAdapter.toAr(result.route, transformer);
      selectedSemanticMap=SemanticLandmarkMap.fromIndoorMap(loaded.map,firstNode.floorId,transformer).nearRoute(ar,3);
      MapPosition last = ar.get(ar.size()-1);
      text.append(String.format(Locale.getDefault(),
          "\n변환: meter → AR 원점 기준\n목표 AR (%.2f, %.2f, %.2f) m\n", last.x, last.y, last.z));
      if (result.route.segments.isEmpty()) {
        text.append(getString(R.string.map_same_poi));
        routeSummary.setText(R.string.map_same_poi);
      } else {
        List<FloorRoutePlanner.Leg> legs = FloorRoutePlanner.split(result.route);
        selectedStageLengths = new int[legs.size()];
        selectedFloors = new String[legs.size()];
        selectedNextFloors = new String[legs.size()];
        selectedTransitions = new String[legs.size()];
        java.util.List<float[]> stagePoints = new java.util.ArrayList<>();
        int total = 0;
        for (int i = 0; i < legs.size(); i++) {
          FloorRoutePlanner.Leg leg = legs.get(i);
          MapCoordinateTransformer legTransformer = new LocalMapCoordinateTransformer(
              loaded.map.coordinateSystem, leg.points.get(0), new MapPosition(0, -0.5, 0), angle);
          float[] points = new float[leg.points.size() * 3];
          for (int j = 0; j < leg.points.size(); j++) {
            MapPosition point = legTransformer.mapToAr(leg.points.get(j));
            points[j*3] = (float) point.x; points[j*3+1] = (float) point.y;
            points[j*3+2] = (float) point.z;
          }
          stagePoints.add(points); selectedStageLengths[i] = points.length; total += points.length;
          selectedFloors[i] = leg.floorId; selectedNextFloors[i] = leg.nextFloorId;
          selectedTransitions[i] = leg.transition == null ? null
              : leg.transition == EdgeType.STAIR
                  ? (leg.ascending ? "STAIR_UP" : "STAIR_DOWN") : leg.transition.name();
          if (leg.transition != null) text.append("\n").append(leg.floorId)
              .append(" → ").append(leg.transition).append(" → ").append(leg.nextFloorId);
        }
        selectedPoints = new float[total];
        int offset = 0;
        for (float[] points : stagePoints) {
          System.arraycopy(points, 0, selectedPoints, offset, points.length);
          offset += points.length;
        }
        ArRoutePlan.decodeStages("", selectedPoints, selectedStageLengths,
            selectedFloors, selectedNextFloors, selectedTransitions, MAP_ASSETS[selectedMap],
            null, null, null);
        setPreviewAvailable(true);
        if (result.route.isMultiFloor()) text.append("\n").append(getString(R.string.map_multifloor_notice));
        routeSummary.setText(getString(selectedMap == 0
                ? R.string.nav_route_ready_plan : R.string.nav_route_ready,
            from.name, to.name, result.route.totalDistance, startLocation(from, firstNode)));
        if (selectedEntryDirection != null) text.append("\n출발 기준: ")
            .append(selectedEntryDirection.description()).append("\n");
        if (MapOrientation.usesSensor(MAP_ASSETS[selectedMap]))
          text.append(getString(R.string.map_sensor_alignment_instruction));
        else if (selectedEntryDirection != null) text
            .append(getString(R.string.map_entry_alignment_instruction));
        else text.append(getString(selectedMap == 0 ? R.string.map_plan_alignment_instruction
            : R.string.map_alignment_instruction));
      }
    } catch (IllegalArgumentException e) {
      text.append("\nAR 변환을 적용할 수 없습니다: ").append(e.getMessage());
      routeSummary.setText(R.string.nav_route_unavailable);
    }
    output.setText(text.toString());
  }

  private void setPreviewAvailable(boolean available) {
    apply.setEnabled(available);
    apply.setVisibility(available ? View.VISIBLE : View.GONE);
  }

  private static String endpointLabel(MapNode node) {
    Object label = node.properties.get("label");
    return label instanceof String ? (String)label : node.id;
  }

  private String startLocation(Poi from, MapNode node) {
    Object label = node.properties.get("label");
    String entry = label instanceof String && !((String) label).trim().isEmpty()
        ? (String) label : from.name;
    for (Floor floor : loaded.map.floors) {
      if (floor.id.equals(node.floorId)) {
        return entry.startsWith(floor.name + " ") ? entry
            : getString(R.string.nav_location_with_floor, floor.name, entry);
      }
    }
    return entry;
  }

  private static String issues(MapValidationResult validation) {
    StringBuilder text = new StringBuilder();
    int shown = 0;
    for (MapValidationIssue issue : validation.issues) {
      if (shown++ >= 5) { text.append("\n… 총 ").append(validation.issues.size()).append("건"); break; }
      text.append("\n").append(issue);
    }
    return text.toString();
  }
  @Override protected void onSaveInstanceState(Bundle saved) {
    super.onSaveInstanceState(saved);
    saved.putInt("map", selectedMap);
    saved.putInt("start", start.getSelectedItemPosition());
    saved.putInt("destination", destination.getSelectedItemPosition());
    saved.putBoolean("wheelchair", wheelchair.isChecked());
    saved.putString("yaw", yaw.getText().toString());
  }
  @Override protected void onDestroy() { worker.shutdownNow(); super.onDestroy(); }
}
