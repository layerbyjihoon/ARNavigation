package com.example.arnavigation.ui.ar;

/** Places a right-pointing double chevron on the usable screen edge. Angles are clockwise from up. */
final class EdgeDirectionIndicator {
    final float x;
    final float y;
    final float rotation;

    private EdgeDirectionIndicator(float x, float y, float rotation) {
        this.x = x;
        this.y = y;
        this.rotation = rotation;
    }

    static EdgeDirectionIndicator place(double angle, float left, float top, float right,
            float bottom, boolean visibleRouteArrow) {
        if (!Double.isFinite(angle) || right <= left || bottom <= top) return null;
        double radians = Math.toRadians(angle);
        double dx = Math.sin(radians);
        double dy = -Math.cos(radians);
        // A future segment in view must not suppress the cue when the next waypoint is behind us.
        if (visibleRouteArrow && Math.cos(radians) > 0) return null;
        double halfWidth = (right-left)/2.0;
        double halfHeight = (bottom-top)/2.0;
        double tx = Math.abs(dx) < 1e-9 ? Double.POSITIVE_INFINITY : halfWidth/Math.abs(dx);
        double ty = Math.abs(dy) < 1e-9 ? Double.POSITIVE_INFINITY : halfHeight/Math.abs(dy);
        double distance = Math.min(tx,ty);
        return new EdgeDirectionIndicator((float)((left+right)/2.0+dx*distance),
                (float)((top+bottom)/2.0+dy*distance), (float)(angle-90));
    }
}
