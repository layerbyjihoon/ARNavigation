package com.example.arnavigation.routing;

import com.example.arnavigation.model.Destination;
import com.example.arnavigation.model.GeoPoint;
import com.example.arnavigation.model.ManeuverType;
import com.example.arnavigation.model.OutdoorRoute;
import com.example.arnavigation.model.Waypoint;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Converts TMAP WGS84 GeoJSON into the shared navigation route without straightening bends. */
final class TmapRouteMapper {
    OutdoorRoute map(String json, Destination destination) throws RouteMappingException {
        try {
            return mapObject(JsonParser.parseString(json).getAsJsonObject(), destination);
        } catch (RuntimeException error) {
            throw new RouteMappingException(RouteError.MALFORMED_RESPONSE,
                    "Invalid TMAP pedestrian response", error);
        }
    }

    private OutdoorRoute mapObject(JsonObject root, Destination destination)
            throws RouteMappingException {
        if (!"FeatureCollection".equals(root.get("type").getAsString())) {
            throw malformed();
        }
        JsonArray features = root.getAsJsonArray("features");
        if (features == null) throw malformed();
        if (features.isEmpty()) {
            throw new RouteMappingException(RouteError.NO_ROUTE, "TMAP returned no route");
        }
        List<JsonObject> ordered = new ArrayList<>();
        for (JsonElement feature : features) ordered.add(feature.getAsJsonObject());
        // sort=index is requested; also normalize numeric strings and shuffled proxy responses.
        ordered.sort(Comparator.comparingDouble(f -> f.getAsJsonObject("properties")
                .get("index").getAsDouble()));
        List<GeoPoint> polyline = new ArrayList<>();
        List<Waypoint> waypoints = new ArrayList<>();
        Double totalDistance = null;
        Long totalTime = null;
        double lineDistance = 0;
        long lineTime = 0;
        boolean hasLines = false;
        for (JsonObject feature : ordered) {
            JsonObject properties = feature.getAsJsonObject("properties");
            JsonObject geometry = feature.getAsJsonObject("geometry");
            String type = geometry.get("type").getAsString();
            JsonArray coordinates = geometry.getAsJsonArray("coordinates");
            if (properties.has("totalDistance")) {
                totalDistance = nonNegative(properties.get("totalDistance").getAsDouble());
            }
            if (properties.has("totalTime")) {
                totalTime = seconds(properties.get("totalTime"));
            }
            if ("LineString".equals(type)) {
                if (coordinates.size() < 2) throw malformed();
                hasLines = true;
                lineDistance += nonNegative(properties.get("distance").getAsDouble());
                lineTime = Math.addExact(lineTime, seconds(properties.get("time")));
                for (JsonElement coordinate : coordinates) {
                    GeoPoint point = point(coordinate.getAsJsonArray());
                    if (polyline.isEmpty() || !point.equals(polyline.get(polyline.size() - 1))) {
                        polyline.add(point);
                    }
                    addWaypoint(waypoints, point, null, ManeuverType.UNKNOWN);
                }
            } else if ("Point".equals(type)) {
                int turn = properties.has("turnType") ? properties.get("turnType").getAsInt() : 0;
                String instruction = properties.has("description")
                        ? properties.get("description").getAsString().trim() : "";
                addWaypoint(waypoints, point(coordinates),
                        instruction.isEmpty() ? null : instruction, maneuver(turn));
            } else {
                throw malformed();
            }
        }
        if (!hasLines || polyline.size() < 2) throw malformed();
        // The selected entrance remains the final arrival target, matching journey handoff.
        addWaypoint(waypoints, destination.getPoint(), null, ManeuverType.ARRIVE);
        return new OutdoorRoute(polyline, waypoints,
                totalDistance == null ? nonNegative(lineDistance) : totalDistance,
                totalTime == null ? lineTime : totalTime, Collections.emptyList());
    }

    private static void addWaypoint(List<Waypoint> result, GeoPoint point,
            String instruction, ManeuverType maneuver) {
        int index = result.size();
        if (index > 0 && result.get(index - 1).getPoint().equals(point)) {
            // A subsequent geometry vertex must not erase the maneuver at the same position.
            if (maneuver == ManeuverType.UNKNOWN) return;
            index--;
            result.remove(index);
        }
        result.add(new Waypoint("tmap-" + index, point, instruction, maneuver, index));
    }

    private static GeoPoint point(JsonArray coordinate) {
        if (coordinate.size() < 2) throw new IllegalArgumentException("Missing coordinate");
        return new GeoPoint(coordinate.get(1).getAsDouble(), coordinate.get(0).getAsDouble());
    }

    private static double nonNegative(double value) {
        if (!Double.isFinite(value) || value < 0) throw new IllegalArgumentException("Invalid metric");
        return value;
    }

    private static long seconds(JsonElement element) {
        double value = nonNegative(element.getAsDouble());
        if (value >= Long.MAX_VALUE || value != Math.floor(value)) {
            throw new IllegalArgumentException("Invalid duration");
        }
        return (long) value;
    }

    private static RouteMappingException malformed() {
        return new RouteMappingException(RouteError.MALFORMED_RESPONSE, "Invalid TMAP route geometry");
    }

    static ManeuverType maneuver(int turn) {
        switch (turn) {
            case 200: return ManeuverType.START;
            case 201: return ManeuverType.ARRIVE;
            case 11: case 211: case 233: return ManeuverType.STRAIGHT;
            case 12: case 212: return ManeuverType.TURN_LEFT;
            case 13: case 213: return ManeuverType.TURN_RIGHT;
            case 14: return ManeuverType.U_TURN_LEFT;
            case 16: case 214: return ManeuverType.TURN_SHARP_LEFT;
            case 17: case 215: return ManeuverType.TURN_SLIGHT_LEFT;
            case 18: case 216: return ManeuverType.TURN_SLIGHT_RIGHT;
            case 19: case 217: return ManeuverType.TURN_SHARP_RIGHT;
            default: return ManeuverType.UNKNOWN;
        }
    }
}
