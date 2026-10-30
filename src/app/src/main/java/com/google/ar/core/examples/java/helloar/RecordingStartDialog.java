package com.google.ar.core.examples.java.helloar;

import android.app.Activity;
import android.content.SharedPreferences;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AlertDialog;
import com.google.ar.core.examples.java.helloar.map.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** Map/floor/node picker and explicit physical facing instruction before recording. */
final class RecordingStartDialog {
  private static final String[] ASSETS = {MapRouteActivity.MUNHWAGWAN_MAP, MapRouteActivity.TEST_MAP};
  private final Activity activity;
  private final Consumer<RecordingStartPoint> start;
  private final SharedPreferences prefs;
  private final ExecutorService loader = Executors.newSingleThreadExecutor();
  private final Spinner maps, floors, nodes;
  private final TextView info;
  private final AlertDialog dialog;
  private IndoorMap loaded;
  private List<Floor> floorItems = Collections.emptyList();
  private List<MapNode> nodeItems = Collections.emptyList();
  private int generation;
  private boolean closed;

  static AlertDialog show(Activity activity, String preferredAsset, Consumer<RecordingStartPoint> start) {
    return new RecordingStartDialog(activity, preferredAsset, start).dialog;
  }

  private RecordingStartDialog(Activity activity, String preferredAsset, Consumer<RecordingStartPoint> start) {
    this.activity = activity; this.start = start;
    prefs = activity.getSharedPreferences("recording_start", Activity.MODE_PRIVATE);
    LinearLayout content = new LinearLayout(activity); content.setOrientation(LinearLayout.VERTICAL);
    int pad = (int) (20 * activity.getResources().getDisplayMetrics().density);
    content.setPadding(pad, pad / 2, pad, pad / 2);
    TextView guidance = new TextView(activity);
    guidance.setText("선택한 실제 위치에 서세요. 문화관은 폰 방위 센서와 반시계 85.5°로 정렬합니다. 출발 위치를 자동으로 인식하지는 않습니다.");
    content.addView(guidance);
    maps = addSpinner(content, "지도", R.id.record_start_map);
    floors = addSpinner(content, "층", R.id.record_start_floor);
    nodes = addSpinner(content, "출발 노드", R.id.record_start_node);
    info = new TextView(activity); info.setId(R.id.record_start_info); content.addView(info);
    ScrollView scroll = new ScrollView(activity); scroll.addView(content);
    dialog = new AlertDialog.Builder(activity).setTitle("AR 녹화 출발 지점")
        .setView(scroll).setPositiveButton("이 지점에서 녹화 시작", null)
        .setNeutralButton("지정 없이 녹화", (d, which) -> start.accept(null))
        .setNegativeButton("취소", null).create();
    dialog.setOnDismissListener(d -> { closed = true; generation++; loader.shutdownNow(); });
    dialog.setOnShowListener(d -> {
      dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
      dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
        RecordingStartPoint point = selected();
        if (point == null) return;
        prefs.edit().putString("map", point.mapAsset).putString("node:" + point.mapAsset, point.nodeId).apply();
        dialog.dismiss(); start.accept(point);
      });
    });
    dialog.show();
    setAdapter(maps, Arrays.asList(activity.getResources().getStringArray(R.array.map_datasets)));
    String initial = Arrays.asList(ASSETS).contains(preferredAsset)
        ? preferredAsset : prefs.getString("map", ASSETS[0]);
    maps.setSelection(Math.max(0, Arrays.asList(ASSETS).indexOf(initial)));
    maps.setOnItemSelectedListener(selection(() -> loadMap()));
    floors.setOnItemSelectedListener(selection(() -> loadNodes()));
    nodes.setOnItemSelectedListener(selection(() -> updateInfo()));
  }

  private Spinner addSpinner(LinearLayout parent, String label, int id) {
    TextView caption = new TextView(activity); caption.setText(label); parent.addView(caption);
    Spinner spinner = new Spinner(activity); spinner.setId(id);
    spinner.setMinimumHeight((int) (48 * activity.getResources().getDisplayMetrics().density));
    parent.addView(spinner, new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
    return spinner;
  }

  private void loadMap() {
    if (closed) return;
    int mapIndex = maps.getSelectedItemPosition();
    if (mapIndex < 0) return;
    int request = ++generation;
    loaded = null; nodeItems = Collections.emptyList(); floorItems = Collections.emptyList();
    floors.setEnabled(false); nodes.setEnabled(false);
    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
    info.setText("지도를 읽는 중…");
    String asset = ASSETS[mapIndex];
    loader.execute(() -> {
      MapLoadResult result = new IndoorMapRepository().loadFromAsset(activity.getAssets()::open, asset);
      activity.runOnUiThread(() -> {
        if (closed || activity.isFinishing() || request != generation) return;
        if (!result.isSuccess()) { info.setText("지도를 읽지 못했습니다. 다른 지도를 선택하거나 지정 없이 녹화하세요."); return; }
        loaded = result.map; floorItems = loaded.floors;
        List<String> names = new ArrayList<>();
        for (Floor floor : floorItems) names.add(floor.name);
        setAdapter(floors, names);
        String savedNode = prefs.getString("node:" + asset, "");
        String savedFloor = loaded.nodes.stream().filter(n -> n.id.equals(savedNode))
            .map(n -> n.floorId).findFirst().orElse("");
        for (int i = 0; i < floorItems.size(); i++) if (floorItems.get(i).id.equals(savedFloor)) floors.setSelection(i);
        floors.setEnabled(!floorItems.isEmpty());
        loadNodes();
      });
    });
  }

  private void loadNodes() {
    if (closed) return;
    int floorIndex = floors.getSelectedItemPosition();
    if (loaded == null || floorIndex < 0 || floorIndex >= floorItems.size()) return;
    String floor = floorItems.get(floorIndex).id;
    nodeItems = new ArrayList<>();
    for (MapNode node : loaded.nodes) if (node.floorId.equals(floor)) nodeItems.add(node);
    List<String> names = new ArrayList<>();
    for (MapNode node : nodeItems) {
      Object label = node.properties.get("label");
      names.add((label instanceof String ? label : node.id) + " [" + node.id + "]");
    }
    setAdapter(nodes, names);
    String saved = prefs.getString("node:" + ASSETS[maps.getSelectedItemPosition()], "");
    for (int i = 0; i < nodeItems.size(); i++) if (nodeItems.get(i).id.equals(saved)) nodes.setSelection(i);
    nodes.setEnabled(!nodeItems.isEmpty());
    updateInfo();
  }

  private RecordingStartPoint selected() {
    int index = nodes.getSelectedItemPosition();
    if (loaded == null || index < 0 || index >= nodeItems.size()) return null;
    int floorIndex = floors.getSelectedItemPosition();
    if (floorIndex < 0 || floorIndex >= floorItems.size()
        || !nodeItems.get(index).floorId.equals(floorItems.get(floorIndex).id)) return null;
    return RecordingStartPoint.fromNode(ASSETS[maps.getSelectedItemPosition()], loaded, nodeItems.get(index).id);
  }
  private void updateInfo() {
    if (closed) return;
    RecordingStartPoint point = selected();
    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(point != null);
    info.setText(point == null ? "이 층에는 출발 노드가 없습니다."
        : point.description() + "\n" + point.coordinates() + "\n기준: " + point.directionDescription()
            + (MapOrientation.usesSensor(point.mapAsset)
                ? "\n폰이 바라보는 방향과 관계없이 센서로 정렬합니다. 방위 정확도가 안정된 뒤 시작하세요."
                : point.direction == null ? "\n이 지점은 기존 방향 보정을 사용합니다."
                : "\n이 방향을 바라보고 시작하세요. 같은 지도의 AR 방향에 자동 적용됩니다.")
            + "\n도면 좌표이며 실제 층고는 확정되지 않았습니다.");
  }
  private void setAdapter(Spinner spinner, List<String> names) {
    ArrayAdapter<String> adapter = new ArrayAdapter<>(activity, R.layout.map_spinner_item, names);
    adapter.setDropDownViewResource(R.layout.map_spinner_item); spinner.setAdapter(adapter);
  }
  private static AdapterView.OnItemSelectedListener selection(Runnable action) {
    return new AdapterView.OnItemSelectedListener() {
      public void onItemSelected(AdapterView<?> parent, View view, int position, long id) { action.run(); }
      public void onNothingSelected(AdapterView<?> parent) { }
    };
  }
}
