package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.battery.FortressBattery;
import com.dillon.starsectormarines.battle.battery.FortressBatteryService;
import com.dillon.starsectormarines.battle.fort.FortAspect;
import com.dillon.starsectormarines.battle.unit.Faction;

import java.util.ArrayList;
import java.util.List;

/**
 * The ring a fortress battery shows the ships overhead: its firing envelope, drawn for as long as the guns can fire,
 * and, while its fire control is being fought over, the point being taken. A pilot sees where the barrier is; a
 * silenced or destroyed battery shows nothing, which is what tells the player the sky is open.
 */
public final class FortressBatteryMarkers {

    /** What a ring marks: the envelope, or the fire-control point under contest. */
    public enum Kind { ENVELOPE, CONTEST }

    /**
     * @param hostile  whether the battery is hostile to the player's side
     * @param progress for a contest, how far the challenger has got, 0 to 1; otherwise 0
     */
    public record Marker(String label, float x, float y, float radius, Kind kind, boolean hostile, float progress) { }

    private FortressBatteryMarkers() { }

    public static List<Marker> forBatteries(FortressBatteryService service, Faction player) {
        List<Marker> out = new ArrayList<>();
        if (service == null) return out;
        for (FortressBattery b : service.batteries()) {
            if (b.destroyed()) continue;
            boolean hostile = b.owner().hostileTo(player);
            if (b.operational()) {
                out.add(new Marker(hostile ? "BATTERY ENVELOPE" : "FRIENDLY BATTERY", b.x(), b.y(), b.envelopeCells(),
                        Kind.ENVELOPE, hostile, 0f));
            } else if (b.fireControl().contested() && !hasBunker(b)) {
                // A fort's contest is shown at its bunker by FortMarkers, not as a ring round the guns.
                out.add(new Marker("FIRE CONTROL", b.x(), b.y(), b.structure().fireControlRadius, Kind.CONTEST,
                        hostile, b.fireControl().captureProgress()));
            }
        }
        return out;
    }

    private static boolean hasBunker(FortressBattery battery) {
        return battery.fort() != null && battery.fort().place(FortAspect.FIRE_CONTROL) != null;
    }
}
