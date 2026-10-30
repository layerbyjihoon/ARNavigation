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

/** Repository owns loading, engine owns routing, adapter owns coordinates; this is a test UI. */
public final class MapRouteActivity extends AppCompatActivity {
  public static final String EXTRA_POINTS = "map_route_points", EXTRA_NAME = "map_route_name";
  public static final String EXTRA_REFERENCE = "map_reference_id";
  public static final String EXTRA_ENTRY_YAW = "route_entry_yaw", EXTRA_ENTRY_DIRECTION = "route_entry_direction";
  public static final String TEST_MAP = "maps/test_building.indoor.json";
  public static final String MUNHWAGWAN_MAP = "maps/munhwagwan.indoor.json";
  public static final String EXTRA_MAP_INDEX = "map_index";
  private static final String[] MAP_ASSETS = {MUNHWAGWAN_MAP, TEST_MAP};
  private final ExecutorService worker = Executors.newSingleThreadExecutor();
  private TextView summary, output, notice;
  private Spinner maps, start, destination;
  private CheckBox wheelchair;
  private EditText yaw;
  private Button find, apply;
  private MapLoadResult loaded;
  private NavigationRoute selectedRoute;
  private float[] selectedPoints;
  private EntryDirection selectedEntryDirection;
  private int savedStart, savedDestination = 1;
  private boolean savedWheelchair;
  private String savedYaw = "0";
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
    start = findViewById(R.id.start_poi); destination = findViewById(R.id.destination_poi);
    wheelchair = findViewById(R.id.wheelchair_route); yaw = findViewById(R.id.map_yaw);
    find = findViewById(R.id.find_route); apply = findViewById(R.id.apply_ar_route);
    if (saved != null) {
      savedStart = saved.getInt("start"); savedDestination = saved.getInt("destination", 1);
      savedWheelchair = saved.getBoolean("wheelchair"); savedYaw = saved.getString("yaw", "0");
    }
    selectedMap = saved != null ? saved.getInt("map") : getIntent().getIntExtra(EXTRA_MAP_INDEX,
        getPreferences(MODE_PRIVATE).getInt("map", 0));
    selectedMap = Math.max(0, Math.min(selectedMap, MAP_ASSETS.length - 1));
    yaw.setText(savedYaw); wheelchair.setChecked(savedWheelchair);
    find.setEnabled(false); apply.setEnabled(false);
    find.setOnClickListener(v -> findRoute());
    apply.setOnClickListener(v -> {
      findRoute(); // POI, policy or yaw may have changed since the previous calculation.
      if (selectedRoute != null && selectedPoints != null) {
        Poi from = (Poi) start.getSelectedItem(), to = (Poi) destination.getSelectedItem();
        Intent result = new Intent().putExtra(EXTRA_POINTS, selectedPoints)
            .putExtra(EXTRA_REFERENCE, MAP_ASSETS[selectedMap])
            .putExtra(EXTRA_NAME, from.name + " [" + endpointLabel(selectedRoute.nodes.get(0))
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
    final String asset = MAP_ASSETS[selectedMap];
    loaded = null; selectedRoute = null; selectedPoints = null;
    find.setEnabled(false); apply.setEnabled(false); start.setEnabled(false); destination.setEnabled(false);
    summary.setText(R.string.map_loading); notice.setText(""); output.setText("");
    ((TextView)findViewById(R.id.map_yaw_caption)).setText(selectedMap == 0
        ? R.string.map_plan_yaw_label : R.string.map_yaw_label);
    apply.setText(selectedMap == 0 ? R.string.map_apply_preview : R.string.map_apply_ar);
    worker.execute(() -> {
      MapLoadResult result = new IndoorMapRepository().loadFromAsset(getAssets()::open, asset);
      runOnUiThread(() -> {
        if (isFinishing() || isDestroyed() || generation != loadGeneration) return;
        loaded = result;
        if (!result.isSuccess()) {
          summary.setText(R.string.map_load_failed); output.setText(issues(result.validation)); return;
        }
        IndoorMap map = result.map;
        summary.setText(getString(R.string.map_summary_format, map.metadata.name, map.floors.size(),
            map.nodes.size(), map.edges.size(), map.pois.size(), result.validation.warningCount()));
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
        output.setText(getString(R.string.map_select_prompt) + (selectedMap == 0
            ? "\n" + getString(R.string.map_munhwagwan_prompt) : issues(result.validation)));
      });
    });
  }

  private void findRoute() {
    selectedRoute = null; selectedPoints = null; selectedEntryDirection = null; apply.setEnabled(false);
    if (loaded == null || !loaded.isSuccess() || start.getSelectedItem() == null) return;
    Poi from = (Poi) start.getSelectedItem(), to = (Poi) destination.getSelectedItem();
    if (selectedMap == 0 && !from.floorId.equals(to.floorId)) {
      output.setText(R.string.map_munhwagwan_cross_floor); return;
    }
    RouteResult result = new AStarRouteEngine(loaded.graph).findRoute(from, to,
        wheelchair.isChecked() ? RouteOptions.WHEELCHAIR : RouteOptions.DEFAULT);
    if (!result.isSuccess()) { output.setText(result.message); return; }
    selectedRoute = result.route;
    StringBuilder text = new StringBuilder(String.format(Locale.getDefault(),
        "%s → %s\n총 거리: %.2f m · 구간: %d\n", from.name, to.name,
        result.route.totalDistance, result.route.segments.size()));
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
      MapPosition last = ar.get(ar.size()-1);
      text.append(String.format(Locale.getDefault(),
          "\n변환: meter → AR 원점 기준\n목표 AR (%.2f, %.2f, %.2f) m\n", last.x, last.y, last.z));
      if (result.route.isMultiFloor()) text.append(getString(R.string.map_multifloor_notice));
      else if (result.route.segments.isEmpty()) text.append(getString(R.string.map_same_poi));
      else {
        float[] points = new float[ar.size()*3];
        for (int i = 0; i < ar.size(); i++) {
          points[i*3] = (float) ar.get(i).x; points[i*3+1] = (float) ar.get(i).y;
          points[i*3+2] = (float) ar.get(i).z;
        }
        ArRoutePlan.decode("", points);
        selectedPoints = points; apply.setEnabled(true);
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
    }
    output.setText(text.toString());
  }

  private static String endpointLabel(MapNode node) {
    Object label = node.properties.get("label");
    return label instanceof String ? (String)label : node.id;
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
