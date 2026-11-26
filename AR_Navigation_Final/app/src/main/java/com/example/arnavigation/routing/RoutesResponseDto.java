package com.example.arnavigation.routing;

import java.util.List;

/** Package-private Gson DTOs matching only the requested Routes API field mask. */
final class RoutesResponseDto {
    List<RouteDto> routes;

    static final class RouteDto {
        Integer distanceMeters;
        String duration;
        PolylineDto polyline;
        List<LegDto> legs;
    }

    static final class PolylineDto {
        String encodedPolyline;
    }

    static final class LegDto {
        List<StepDto> steps;
    }

    static final class StepDto {
        LocationDto startLocation;
        LocationDto endLocation;
        NavigationInstructionDto navigationInstruction;
    }

    static final class LocationDto {
        LatLngDto latLng;
    }

    static final class LatLngDto {
        Double latitude;
        Double longitude;
    }

    static final class NavigationInstructionDto {
        String maneuver;
        String instructions;
    }
}
