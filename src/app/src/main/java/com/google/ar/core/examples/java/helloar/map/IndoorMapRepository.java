package com.google.ar.core.examples.java.helloar.map;

import com.google.ar.core.examples.java.helloar.routing.IndoorNavGraph;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Owns loading, parsing, validation and the one-time adjacency build. No Activity or ARCore APIs. */
public final class IndoorMapRepository implements IndoorMapImporter {
  @FunctionalInterface public interface AssetSource { InputStream open(String path) throws IOException; }
  private final Gson gson = new Gson();
  @Override public String format() { return "IndoorNavGraph"; }
  @Override public MapLoadResult importMap(InputStream input) { return loadFromInputStream(input); }

  public MapLoadResult loadFromAsset(AssetSource assets, String path) {
    try (InputStream input = assets.open(path)) { return loadFromInputStream(input); }
    catch (IOException e) { return failure(path, "지도 파일을 열 수 없습니다: " + e.getMessage()); }
  }

  /** Caller owns the stream. Unknown optional keys are ignored; custom node properties are retained. */
  public MapLoadResult loadFromInputStream(InputStream input) {
    if (input == null) return failure("$", "InputStream is null");
    try {
      JsonReader reader = new JsonReader(new InputStreamReader(input, StandardCharsets.UTF_8));
      reader.setStrictness(Strictness.STRICT);
      JsonElement element = JsonParser.parseReader(reader);
      if (reader.peek() != JsonToken.END_DOCUMENT) throw bad("$", "Trailing JSON content");
      IndoorMap map = parse(object(element, "$"));
      MapValidationResult validation = validate(map);
      return new MapLoadResult(map, validation.hasErrors() ? null : new IndoorNavGraph(map), validation);
    } catch (JsonParseException | IllegalStateException | IllegalArgumentException | IOException e) {
      return failure("$", e.getMessage() == null ? "Invalid JSON map" : e.getMessage());
    }
  }

  public MapValidationResult validate(IndoorMap map) { return new IndoorMapValidator().validate(map); }

  private IndoorMap parse(JsonObject root) {
    String schema = string(root, "schema", "$");
    String version = string(root, "version", "$");
    JsonObject m = child(root, "metadata", "$");
    IndoorMapMetadata metadata = new IndoorMapMetadata(string(m, "id", "$.metadata"),
        string(m, "name", "$.metadata"), optionalString(m, "description"),
        optionalString(m, "createdAt"), optionalString(m, "updatedAt"),
        optionalString(m, "author"), optionalString(m, "source"));
    JsonObject c = child(root, "coordinateSystem", "$");
    JsonObject a = child(c, "axis", "$.coordinateSystem");
    IndoorCoordinateSystem.Georeference geo = null;
    if (c.has("georeference")) {
      JsonObject g = child(c, "georeference", "$.coordinateSystem");
      geo = new IndoorCoordinateSystem.Georeference(
          number(g, "latitude", "$.coordinateSystem.georeference"),
          number(g, "longitude", "$.coordinateSystem.georeference"),
          optionalNumber(g, "altitude", "$.coordinateSystem.georeference"),
          optionalNumber(g, "heading", "$.coordinateSystem.georeference"));
    }
    IndoorCoordinateSystem coordinates = new IndoorCoordinateSystem(
        string(c, "type", "$.coordinateSystem"), string(c, "unit", "$.coordinateSystem"),
        string(a, "x", "$.coordinateSystem.axis"), string(a, "y", "$.coordinateSystem.axis"),
        string(a, "z", "$.coordinateSystem.axis"), geo);

    List<Floor> floors = new ArrayList<>();
    for (JsonElement e : array(root, "floors", "$", true)) {
      JsonObject f = object(e, "floors[]");
      number(f, "level", "floors[]");
      int level;
      try { level = f.get("level").getAsBigDecimal().intValueExact(); }
      catch (ArithmeticException ex) { throw bad("floors[].level", "Expected a 32-bit integer"); }
      floors.add(new Floor(string(f, "id", "floors[]"), optionalString(f, "name"), level,
          optionalNumber(f, "elevation", "floors[]")));
    }
    List<MapNode> nodes = new ArrayList<>();
    for (JsonElement e : array(root, "nodes", "$", true)) {
      JsonObject n = object(e, "nodes[]");
      Map<String, Object> properties = new LinkedHashMap<>();
      if (n.has("properties")) {
        JsonObject values = child(n, "properties", "nodes[]");
        for (Map.Entry<String, JsonElement> entry : values.entrySet())
          properties.put(entry.getKey(), gson.fromJson(entry.getValue(), Object.class));
      }
      nodes.add(new MapNode(string(n, "id", "nodes[]"), string(n, "floorId", "nodes[]"),
          enumeration(NodeType.class, string(n, "type", "nodes[]"), NodeType.UNKNOWN),
          position(child(n, "position", "nodes[]"), "nodes[].position"), properties));
    }
    List<MapEdge> edges = new ArrayList<>();
    for (JsonElement e : array(root, "edges", "$", true)) {
      JsonObject edge = object(e, "edges[]");
      List<MapPosition> geometry = new ArrayList<>();
      if (edge.has("geometry")) {
        JsonArray points = array(edge, "geometry", "edges[]", true);
        if (points.size() < 2) throw bad("edges[].geometry", "Polyline needs at least two points");
        for (JsonElement point : points) geometry.add(position(object(point, "geometry[]"), "geometry[]"));
      }
      EdgeCost cost = new EdgeCost(null, null);
      if (edge.has("cost")) {
        JsonObject k = child(edge, "cost", "edges[]");
        cost = new EdgeCost(optionalNumber(k, "distance", "edges[].cost"),
            optionalNumber(k, "time", "edges[].cost"));
      }
      Boolean wheelchair = null;
      if (edge.has("accessibility")) {
        JsonObject access = child(edge, "accessibility", "edges[]");
        if (access.has("wheelchair")) wheelchair = bool(access, "wheelchair", "edges[].accessibility");
      }
      edges.add(new MapEdge(string(edge, "id", "edges[]"), string(edge, "from", "edges[]"),
          string(edge, "to", "edges[]"),
          !edge.has("bidirectional") || bool(edge, "bidirectional", "edges[]"),
          enumeration(EdgeType.class, string(edge, "type", "edges[]"), EdgeType.UNKNOWN),
          geometry, cost, new Accessibility(wheelchair)));
    }
    List<Poi> pois = new ArrayList<>();
    for (JsonElement e : array(root, "pois", "$", false)) {
      JsonObject poi = object(e, "pois[]");
      String primary = string(poi, "nodeId", "pois[]");
      List<String> entrances = new ArrayList<>();
      if (poi.has("nodeIds")) {
        for (JsonElement entrance : array(poi, "nodeIds", "pois[]", true)) {
          if (!entrance.isJsonPrimitive() || !entrance.getAsJsonPrimitive().isString()
              || entrance.getAsString().trim().isEmpty())
            throw bad("pois[].nodeIds", "Expected nonempty node ID strings");
          entrances.add(entrance.getAsString());
        }
      } else entrances.add(primary);
      pois.add(new Poi(string(poi, "id", "pois[]"), string(poi, "name", "pois[]"),
          enumeration(PoiCategory.class, string(poi, "category", "pois[]"), PoiCategory.UNKNOWN),
          primary, string(poi, "floorId", "pois[]"), entrances));
    }
    List<MapAnchor> anchors = new ArrayList<>();
    for (JsonElement e : array(root, "anchors", "$", false)) {
      JsonObject anchor = object(e, "anchors[]");
      anchors.add(new MapAnchor(string(anchor, "id", "anchors[]"),
          enumeration(AnchorType.class, string(anchor, "type", "anchors[]"), AnchorType.UNKNOWN),
          optionalString(anchor, "value"), string(anchor, "floorId", "anchors[]"),
          position(child(anchor, "position", "anchors[]"), "anchors[].position"),
          optionalNumber(anchor, "heading", "anchors[]")));
    }
    return new IndoorMap(schema, version, metadata, coordinates, floors, nodes, edges, pois, anchors);
  }

  private static MapLoadResult failure(String path, String message) {
    return new MapLoadResult(null, null, new MapValidationResult(Collections.singletonList(
        new MapValidationIssue(MapValidationIssue.Severity.ERROR, path, message))));
  }
  private static IllegalArgumentException bad(String path, String message) {
    return new IllegalArgumentException(path + ": " + message);
  }
  private static JsonObject object(JsonElement e, String path) {
    if (e == null || !e.isJsonObject()) throw bad(path, "Expected object");
    return e.getAsJsonObject();
  }
  private static JsonObject child(JsonObject parent, String key, String path) {
    return object(parent.get(key), path + "." + key);
  }
  private static JsonArray array(JsonObject parent, String key, String path, boolean required) {
    JsonElement e = parent.get(key);
    if (e == null && !required) return new JsonArray();
    if (e == null || !e.isJsonArray()) throw bad(path + "." + key, "Expected array");
    return e.getAsJsonArray();
  }
  private static String string(JsonObject o, String key, String path) {
    JsonElement e = o.get(key);
    if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isString()
        || e.getAsString().trim().isEmpty()) throw bad(path + "." + key, "Expected nonempty string");
    return e.getAsString();
  }
  private static String optionalString(JsonObject o, String key) {
    if (!o.has(key)) return null;
    JsonElement e = o.get(key);
    if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isString())
      throw bad(key, "Expected string");
    return e.getAsString();
  }
  private static double number(JsonObject o, String key, String path) {
    JsonElement e = o.get(key);
    if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber())
      throw bad(path + "." + key, "Expected number");
    return e.getAsDouble();
  }
  private static Double optionalNumber(JsonObject o, String key, String path) {
    return o.has(key) ? number(o, key, path) : null;
  }
  private static boolean bool(JsonObject o, String key, String path) {
    JsonElement e = o.get(key);
    if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isBoolean())
      throw bad(path + "." + key, "Expected boolean");
    return e.getAsBoolean();
  }
  private static MapPosition position(JsonObject o, String path) {
    return new MapPosition(number(o, "x", path), number(o, "y", path), number(o, "z", path));
  }
  private static <T extends Enum<T>> T enumeration(Class<T> type, String value, T fallback) {
    try { return Enum.valueOf(type, value); } catch (IllegalArgumentException e) { return fallback; }
  }
}
