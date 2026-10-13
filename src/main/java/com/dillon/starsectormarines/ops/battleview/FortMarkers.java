package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.battery.FortressBattery;
import com.dillon.starsectormarines.battle.fort.Fort;
import com.dillon.starsectormarines.battle.fort.FortAspect;
import com.dillon.starsectormarines.battle.fort.FortService;
import com.dillon.starsectormarines.battle.unit.Faction;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * What the player is shown of a fort: a ring and a label at each aspect saying whose it is and what is happening to
 * it, and one line over the guns saying what the aspects are currently doing to them (rationed, unpowered, silent).
 * Consistent with {@link FortressBatteryMarkers}: the same tint for hostile and own, and nothing is shown for a part
 * of a fort that has not been stamped yet.
 */
public final class FortMarkers {

    /** What a marker marks: an aspect place, a wrecked aspect, or the status line over the guns. */
    public enum Kind { ASPECT, WRECKED, STATUS }

    /**
     * @param hostile  whether the holder is hostile to the player's side
     * @param progress for an aspect being taken, how far the challenger has got, 0 to 1; otherwise 0
     * @param radius   the ring, in cells; zero for a label with no ring
     */
    public record Marker(String label, float x, float y, float radius, Kind kind, boolean hostile, float progress) { }

    /** Radius of the ring drawn round an aspect core: a little more than the three-by-three structure. */
    static final float ASPECT_RING_CELLS = 2.4f;

    private FortMarkers() { }

    public static List<Marker> forForts(FortService service, Faction player) {
        List<Marker> out = new ArrayList<>();
        if (service == null) return out;
        for (Fort fort : service.forts()) {
            FortressBattery battery = fort.battery();
            if (battery == null) continue;
            for (Fort.Place place : fort.places()) {
                if (!place.resolved()) continue;
                out.add(aspectMarker(fort, battery, place, player));
            }
            if (!battery.destroyed()) {
                String status = status(fort, battery);
                if (!status.isEmpty()) {
                    out.add(new Marker(status, battery.x(), battery.y() - 2.4f, 0f, Kind.STATUS,
                            battery.owner().hostileTo(player), 0f));
                }
            }
        }
        return out;
    }

    private static Marker aspectMarker(Fort fort, FortressBattery battery, Fort.Place place, Faction player) {
        FortAspect aspect = place.aspect();
        boolean fireControl = aspect == FortAspect.FIRE_CONTROL;
        Faction holder = fireControl ? battery.owner() : place.control().holder();
        boolean hostile = holder.hostileTo(player);
        if (place.wrecked()) {
            return new Marker(aspect.label() + " WRECKED", place.x(), place.y(), ASPECT_RING_CELLS, Kind.WRECKED, hostile, 0f);
        }
        boolean contested = fireControl ? battery.fireControl().contested() : place.control().contested();
        float progress = fireControl ? battery.fireControl().captureProgress() : place.control().captureProgress();
        String label = aspect.label();
        if (contested) {
            label += String.format(Locale.ROOT, " CONTESTED %d%%", Math.round(progress * 100f));
        } else if (!holder.friendlyTo(battery.originalOwner())) {
            label += " TAKEN";
        }
        return new Marker(label, place.x(), place.y(), ASPECT_RING_CELLS, Kind.ASPECT, hostile, progress);
    }

    /** What the aspects are doing to the guns right now, or an empty string when nothing is. */
    static String status(Fort fort, FortressBattery battery) {
        List<String> parts = new ArrayList<>();
        Faction owner = battery.owner();
        if (!fort.fireControlIntact()) {
            parts.add("NO FIRE CONTROL");
        } else if (!fort.magazineSupplies(owner)) {
            parts.add(fort.readyRounds() >= 1f
                    ? String.format(Locale.ROOT, "RATIONED %d", (int) Math.floor(fort.readyRounds()))
                    : "OUT OF ROUNDS");
        }
        if (!fort.powered(owner)) parts.add("UNPOWERED");
        return String.join(" / ", parts);
    }
}
