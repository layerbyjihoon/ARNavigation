package com.example.arnavigation.ui.ar;

import com.example.arnavigation.model.ManeuverType;
import java.util.Locale;

/** Formats already-computed relative angles into concise Korean guidance. */
public final class NavigationInstructionFormatter {
    private final double forwardToleranceDegrees;

    public NavigationInstructionFormatter(double forwardToleranceDegrees) {
        if (!Double.isFinite(forwardToleranceDegrees)
                || forwardToleranceDegrees < 0.0d || forwardToleranceDegrees > 180.0d) {
            throw new IllegalArgumentException("forwardToleranceDegrees must be in [0, 180]");
        }
        this.forwardToleranceDegrees = forwardToleranceDegrees;
    }

    public String format(ManeuverType maneuver, double relativeAngleDegrees) {
        if (!Double.isFinite(relativeAngleDegrees)) {
            throw new IllegalArgumentException("relativeAngleDegrees must be finite");
        }
        if (maneuver == ManeuverType.ARRIVE) return "목적지에 도착합니다";
        if (Math.abs(relativeAngleDegrees) <= forwardToleranceDegrees) return "직진 방향";
        int degrees = (int) Math.round(Math.abs(relativeAngleDegrees));
        return relativeAngleDegrees > 0.0d
                ? String.format(Locale.getDefault(), "오른쪽으로 %d°", degrees)
                : String.format(Locale.getDefault(), "왼쪽으로 %d°", degrees);
    }

    public String contentDescription(double relativeAngleDegrees) {
        return format(ManeuverType.UNKNOWN, relativeAngleDegrees);
    }
}
