package com.example.arnavigation.routing;

import com.example.arnavigation.model.Destination;
import com.example.arnavigation.model.GeoPoint;
import com.example.arnavigation.model.ManeuverType;
import com.example.arnavigation.model.OutdoorRoute;
import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Maps the limited Routes API JSON contract into SDK-independent domain models. */
public final class GoogleRouteMapper {
    private static final Pattern DURATION_PATTERN = Pattern.compile("^(?:0|[1-9]\\d*)(?:\\.\\d+)?s$");
    private static final Pattern HTML_TAG = Pattern.compile("<[^>]*>");

    private final Gson gson;
    private final PolylineDecoder polylineDecoder;
    private final WaypointBuilder waypointBuilder;

    public GoogleRouteMapper(Gson gson, PolylineDecoder polylineDecoder, WaypointBuilder waypointBuilder) {
        this.gson = java.util.Objects.requireNonNull(gson, "gson must not be null");
        this.polylineDecoder = java.util.Objects.requireNonNull(
                polylineDecoder, "polylineDecoder must not be null");
        this.waypointBuilder = java.util.Objects.requireNonNull(
                waypointBuilder, "waypointBuilder must not be null");
    }

    public OutdoorRoute map(String json, Destination destination) throws RouteMappingException {
        if (json == null || json.trim().isEmpty()) {
            throw malformed("response body is empty");
        }
        RoutesResponseDto response;
        try {
            response = gson.fromJson(json, RoutesResponseDto.class);
        } catch (JsonParseException error) {
            throw new RouteMappingException(
                    RouteError.MALFORMED_RESPONSE, "response is not valid JSON", error);
        }
        if (response == null || response.routes == null || response.routes.isEmpty()) {
            throw new RouteMappingException(RouteError.NO_ROUTE, "response contains no route");
        }
        RoutesResponseDto.RouteDto route = response.routes.get(0);
        if (route == null || route.distanceMeters == null || route.distanceMeters < 0) {
            throw malformed("route distance is missing or negative");
        }
        long durationSeconds = parseDurationSeconds(route.duration);
        if (route.polyline == null || route.polyline.encodedPolyline == null) {
            throw malformed("route polyline is missing");
        }
        List<GeoPoint> polyline;
        try {
            polyline = polylineDecoder.decode(route.polyline.encodedPolyline);
        } catch (IllegalArgumentException error) {
            throw new RouteMappingException(
                    RouteError.MALFORMED_RESPONSE, "route polyline is malformed", error);
        }

        ArrayList<WaypointBuilder.StepWaypoint> stepWaypoints = new ArrayList<>();
        if (route.legs != null) {
            for (RoutesResponseDto.LegDto leg : route.legs) {
                if (leg == null) throw malformed("route leg is null");
                if (leg.steps == null) continue;
                for (RoutesResponseDto.StepDto step : leg.steps) {
                    if (step == null) throw malformed("route step is null");
                    GeoPoint endPoint = mapLocation(step.endLocation);
                    String instruction = step.navigationInstruction == null
                            ? null : sanitizeInstruction(step.navigationInstruction.instructions);
                    ManeuverType maneuver = step.navigationInstruction == null
                            ? ManeuverType.UNKNOWN
                            : mapManeuver(step.navigationInstruction.maneuver);
                    stepWaypoints.add(new WaypointBuilder.StepWaypoint(
                            endPoint, instruction, maneuver));
                }
            }
        }

        return new OutdoorRoute(
                polyline,
                waypointBuilder.build(stepWaypoints, destination.getPoint()),
                route.distanceMeters,
                durationSeconds,
                Collections.emptyList());
    }

    private static GeoPoint mapLocation(RoutesResponseDto.LocationDto location)
            throws RouteMappingException {
        if (location == null || location.latLng == null
                || location.latLng.latitude == null || location.latLng.longitude == null) {
            throw malformed("step end coordinate is missing");
        }
        try {
            return new GeoPoint(location.latLng.latitude, location.latLng.longitude);
        } catch (IllegalArgumentException error) {
            throw new RouteMappingException(
                    RouteError.MALFORMED_RESPONSE, "step end coordinate is invalid", error);
        }
    }

    private static long parseDurationSeconds(String duration) throws RouteMappingException {
        if (duration == null || !DURATION_PATTERN.matcher(duration).matches()) {
            throw malformed("route duration is invalid");
        }
        try {
            BigDecimal seconds = new BigDecimal(duration.substring(0, duration.length() - 1));
            return seconds.setScale(0, RoundingMode.CEILING).longValueExact();
        } catch (ArithmeticException | NumberFormatException error) {
            throw new RouteMappingException(
                    RouteError.MALFORMED_RESPONSE, "route duration is out of range", error);
        }
    }

    private static ManeuverType mapManeuver(String rawManeuver) {
        if (rawManeuver == null || rawManeuver.trim().isEmpty()) return ManeuverType.UNKNOWN;
        switch (rawManeuver.trim().toUpperCase(Locale.ROOT)) {
            case "DEPART": return ManeuverType.START;
            case "STRAIGHT":
            case "NAME_CHANGE":
            case "MERGE": return ManeuverType.STRAIGHT;
            case "TURN_SLIGHT_LEFT": return ManeuverType.TURN_SLIGHT_LEFT;
            case "TURN_SLIGHT_RIGHT": return ManeuverType.TURN_SLIGHT_RIGHT;
            case "TURN_LEFT": return ManeuverType.TURN_LEFT;
            case "TURN_RIGHT": return ManeuverType.TURN_RIGHT;
            case "TURN_SHARP_LEFT": return ManeuverType.TURN_SHARP_LEFT;
            case "TURN_SHARP_RIGHT": return ManeuverType.TURN_SHARP_RIGHT;
            case "UTURN_LEFT": return ManeuverType.U_TURN_LEFT;
            case "UTURN_RIGHT": return ManeuverType.U_TURN_RIGHT;
            case "RAMP_LEFT": return ManeuverType.RAMP_LEFT;
            case "RAMP_RIGHT": return ManeuverType.RAMP_RIGHT;
            case "FORK_LEFT": return ManeuverType.FORK_LEFT;
            case "FORK_RIGHT": return ManeuverType.FORK_RIGHT;
            case "ROUNDABOUT_LEFT": return ManeuverType.ROUNDABOUT_LEFT;
            case "ROUNDABOUT_RIGHT": return ManeuverType.ROUNDABOUT_RIGHT;
            case "FERRY":
            case "FERRY_TRAIN": return ManeuverType.FERRY;
            default: return ManeuverType.UNKNOWN;
        }
    }

    private static String sanitizeInstruction(String instruction) {
        if (instruction == null) return null;
        String text = HTML_TAG.matcher(instruction).replaceAll("")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .trim();
        return text.isEmpty() ? null : text;
    }

    private static RouteMappingException malformed(String message) {
        return new RouteMappingException(RouteError.MALFORMED_RESPONSE, message);
    }
}
