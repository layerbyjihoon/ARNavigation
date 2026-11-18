package com.google.ar.core.examples.java.helloar.map;

import com.google.ar.core.examples.java.helloar.routing.IndoorNavGraph;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Enables only the source map's explicitly marked demonstration stair candidates. */
public final class MunhwagwanDemoFloors {
  private MunhwagwanDemoFloors() {}

  public static MapLoadResult add(IndoorMapRepository.AssetSource assets, String path,
      MapLoadResult original) throws IOException {
    if (!original.isSuccess()) return original;
    List<MapEdge> edges = new ArrayList<>(original.map.edges);
    try (InputStream input = assets.open(path);
        InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
      JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
      JsonArray candidates = root.getAsJsonObject("sourceData").getAsJsonArray("excludedEdges");
      int added = 0;
      for (JsonElement item : candidates) {
        JsonObject candidate = item.getAsJsonObject().getAsJsonObject("edge");
        if (!"plan_aligned_stair_candidate".equals(candidate.get("status").getAsString())) continue;
        String from = candidate.get("from").getAsString();
        String to = candidate.get("to").getAsString();
        if (!original.graph.nodes.containsKey(from) || !original.graph.nodes.containsKey(to))
          throw new IllegalArgumentException("Unknown demonstration stair node");
        edges.add(new MapEdge(candidate.get("id").getAsString(), from, to, true,
            EdgeType.STAIR, Collections.emptyList(), new EdgeCost(0.0, null),
            new Accessibility(null)));
        added++;
      }
      if (added != 8) throw new IllegalArgumentException("Expected eight demonstration stair links");
    }
    IndoorMap source = original.map;
    IndoorMapMetadata metadata = source.metadata;
    String description = metadata.description == null ? "" : metadata.description;
    description = description.replace("같은 층 경로만 사용.",
        "층간 계단은 시연용 후보이며 계단 이동 거리는 제외.")
        .replace("층고·AR 정렬 미확정.", "층고 미측정. AR 정렬은 자북 기준 반시계 70°.");
    IndoorMap map = new IndoorMap(source.schema, source.version,
        new IndoorMapMetadata(metadata.id, metadata.name, description, metadata.createdAt,
            metadata.updatedAt, metadata.author, metadata.source), source.coordinateSystem,
        source.floors, source.nodes, edges, source.pois, source.anchors);
    MapValidationResult validation = new IndoorMapValidator().validate(map);
    return new MapLoadResult(map, validation.hasErrors() ? null : new IndoorNavGraph(map), validation);
  }
}
