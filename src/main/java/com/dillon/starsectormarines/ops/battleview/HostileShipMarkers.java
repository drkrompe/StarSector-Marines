package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.power.HostileShipSupport;
import java.util.List;

/** The ground the defender's ship is about to work, from the warning until it settles. */
public final class HostileShipMarkers {
    public record Marker(String label, float x, float y, float radius) { }
    private HostileShipMarkers() { }

    public static List<Marker> forSupport(HostileShipSupport support) {
        if (support == null || !support.markerActive()) return List.of();
        return List.of(new Marker("HOSTILE CAPITAL INBOUND", support.stationX(), support.stationY(),
                HostileShipSupport.MARKER_RADIUS_CELLS));
    }
}
