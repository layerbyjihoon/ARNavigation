package com.google.ar.core.examples.java.helloar;

import com.google.ar.core.examples.java.helloar.map.*;
import java.util.Locale;

/** Snapshot of a manually declared map location; independent of AR session coordinates. */
final class RecordingStartPoint {
  final String mapAsset, mapId, mapName, floorId, floorName, nodeId, label, nodeType;
  final double x, y, z;
  final EntryDirection direction;

  private RecordingStartPoint(String asset, IndoorMap map, Floor floor, MapNode node) {
    mapAsset = asset; mapId = map.metadata.id; mapName = map.metadata.name;
    floorId = floor.id; floorName = floor.name; nodeId = node.id; nodeType = node.type.name();
    Object name = node.properties.get("label");
    label = name instanceof String ? (String) name : node.id;
    x = node.position.x; y = node.position.y; z = node.position.z;
    direction = EntryDirection.resolve(map, node);
  }

  static RecordingStartPoint fromNode(String asset, IndoorMap map, String nodeId) {
    if (asset == null || asset.trim().isEmpty() || map == null || nodeId == null)
      throw new IllegalArgumentException("출발 지점을 선택하세요.");
    MapNode node = map.nodes.stream().filter(n -> n.id.equals(nodeId)).findFirst()
        .orElseThrow(() -> new IllegalArgumentException("지도에 없는 출발 노드입니다."));
    Floor floor = map.floors.stream().filter(f -> f.id.equals(node.floorId)).findFirst()
        .orElseThrow(() -> new IllegalArgumentException("출발 지점의 층을 확인할 수 없습니다."));
    if (!node.position.isFinite()) throw new IllegalArgumentException("출발 좌표가 올바르지 않습니다.");
    return new RecordingStartPoint(asset, map, floor, node);
  }

  String description() {
    return mapName + " · " + floorName + " · " + label + " [" + nodeId + "]";
  }
  String coordinates() {
    return String.format(Locale.getDefault(), "지도 좌표 X %.3f / Y %.3f / Z %.3f m", x, y, z);
  }
  static String describe(RecordingStartPoint point) {
    return point == null ? "미지정" : point.description();
  }
  String directionDescription() {
    if (MapOrientation.usesSensor(mapAsset)) return "폰 방위 센서 · 자북 기준 반시계 85.5°"
        + (direction == null ? "" : String.format(Locale.getDefault(), " · 복도 쪽 방위 %.1f°",
            MapOrientation.entryBearing(direction.canonicalYawDegrees)));
    return direction == null ? "자동 출발 방향 없음 · 기존 방향 보정 사용" : direction.description();
  }
}
