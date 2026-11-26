package com.example.arnavigation.model;

/** Normalized walking maneuver independent of the route provider's DTO values. */
public enum ManeuverType {
    START,
    STRAIGHT,
    TURN_SLIGHT_LEFT,
    TURN_SLIGHT_RIGHT,
    TURN_LEFT,
    TURN_RIGHT,
    TURN_SHARP_LEFT,
    TURN_SHARP_RIGHT,
    U_TURN_LEFT,
    U_TURN_RIGHT,
    RAMP_LEFT,
    RAMP_RIGHT,
    FORK_LEFT,
    FORK_RIGHT,
    ROUNDABOUT_LEFT,
    ROUNDABOUT_RIGHT,
    FERRY,
    ARRIVE,
    UNKNOWN
}
