package com.example.arnavigation.ui.ar;

import com.example.arnavigation.model.*;
import com.example.arnavigation.navigation.DirectionCalculator;
import com.example.arnavigation.navigation.NavigationUiState;
import com.example.arnavigation.location.LocationStatus;
import java.util.List;
import java.util.Locale;

/** Upcoming API maneuver, with distance along the ordered TMAP path vertices. */
final class RouteGuidanceFormatter {
    static final class Guidance {
        final String text;
        final Double distanceMeters;
        Guidance(String text, Double distanceMeters) { this.text=text; this.distanceMeters=distanceMeters; }
    }
    private final DirectionCalculator calculator = new DirectionCalculator();

    Guidance format(NavigationUiState state) {
        List<Waypoint> points=state.getRoute().getWaypoints();
        int start=Math.max(0,state.getActiveWaypointIndex());
        int selected=-1;
        String action=null;
        for(int i=start;i<points.size();i++) {
            String candidate=action(points.get(i));
            if(candidate!=null) { selected=i; action=candidate; break; }
        }
        if(selected<0) return new Guidance("경로를 따라 이동하세요",null);
        Double distance=state.getLocationStatus()==LocationStatus.AVAILABLE
                ? state.getDistanceToWaypointMeters() : null;
        if(distance!=null && Double.isFinite(distance) && distance>=0) {
            // TmapRouteMapper keeps all LineString vertices in this ordered waypoint list.
            // Summing them preserves bends instead of using a straight line to a distant turn.
            for(int i=start;i<selected;i++) distance+=calculator.distanceMeters(points.get(i).getPoint(),points.get(i+1).getPoint());
        } else distance=null;
        Waypoint point=points.get(selected);
        if(distance==null) return new Guidance(action,null);
        // An unrecognized API notice may already contain distances or several clauses.
        // Preserve its wording instead of inventing an "ahead" relationship for that text.
        if(point.getManeuver()==ManeuverType.UNKNOWN && action.equals(point.getInstruction()))
            return new Guidance(action,distance);
        if(point.getManeuver()==ManeuverType.ARRIVE) {
            return new Guidance(distance<10 ? "목적지가 가까이에 있습니다"
                : meters(distance)+" 앞에 목적지가 있습니다",distance);
        }
        return new Guidance(distance<10 ? "잠시 후 "+action : meters(distance)+" 앞에서 "+action,distance);
    }

    private static String meters(double distance) {
        double step=distance<100 ? 5 : 10;
        return String.format(Locale.getDefault(),"%.0fm",Math.max(step,Math.round(distance/step)*step));
    }
    private static String action(Waypoint point) {
        String instruction=point.getInstruction();
        if(point.getManeuver()==ManeuverType.START) return null;
        if(instruction!=null) {
            if(instruction.contains("횡단보도")) return "횡단보도를 건너세요";
            if(instruction.contains("육교")) return "육교로 진입하세요";
            if(instruction.contains("지하보도")) return "지하보도로 진입하세요";
            if(instruction.contains("엘리베이터")) return "엘리베이터를 이용하세요";
            if(instruction.contains("계단")) return "계단으로 진입하세요";
        }
        switch(point.getManeuver()) {
            case TURN_LEFT: case TURN_SLIGHT_LEFT: case TURN_SHARP_LEFT:
            case FORK_LEFT: case RAMP_LEFT: case ROUNDABOUT_LEFT: return "좌회전하세요";
            case TURN_RIGHT: case TURN_SLIGHT_RIGHT: case TURN_SHARP_RIGHT:
            case FORK_RIGHT: case RAMP_RIGHT: case ROUNDABOUT_RIGHT: return "우회전하세요";
            case U_TURN_LEFT: case U_TURN_RIGHT: return "유턴하세요";
            case ARRIVE: return "목적지에 도착합니다";
            case STRAIGHT: return null;
            default: return instruction;
        }
    }
}
