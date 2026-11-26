package com.example.arnavigation.entrance;

import com.example.arnavigation.model.Destination;
import com.example.arnavigation.model.DestinationSource;
import com.example.arnavigation.model.GeoPoint;

/** Front door selected and confirmed by the user on the phone map on 2026-11-25.
 * Use the same outdoor endpoint for the room journey and the indoor-entry proximity gate.
 */
public final class CultureHallEntrance {
    public static final GeoPoint FRONT_POINT = new GeoPoint(37.49637, 126.95442);
    private CultureHallEntrance() {}

    public static Destination destination() {
        return new Destination("campus:munhwagwan:front", "문화관", FRONT_POINT,
                DestinationSource.MAP_TAP, null);
    }
}
