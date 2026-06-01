package com.dillon.starsectormarines.battle.air.engine;

import com.dillon.starsectormarines.battle.air.AirScale;
import com.fs.starfarer.api.Global;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Scrapes mountable weapon-slot positions out of a vanilla {@code .ship} hull
 * spec and converts each into the shuttle-local frame at the one global pixel
 * density ({@link AirScale#METERS_PER_PX}) — the same transform
 * {@link ShipSpecEngineParser} applies to engine slots. The result is each
 * hardpoint's real on-hull position in cells, so shuttle turrets mount where the
 * artist painted the turret slots instead of at hand-authored generic offsets.
 *
 * <p>No per-ship scaling: every hull's slots come out of the same density, so
 * relative placement falls out of vanilla's own art consistency (base and
 * modded) for free.
 *
 * <p>Cached by hull id; mirrors {@link EngineSlotResolver} /
 * {@link HullFootprintResolver}. Degrades to an empty array (logged once) on any
 * failure, so turret setup silently mounts fewer/none rather than crashing.
 */
public final class TurretSlotResolver {

    private static final Logger LOG = Global.getLogger(TurretSlotResolver.class);
    private static final float[][] EMPTY = new float[0][];

    /**
     * Weapon-slot types a turret can hang on. Skips non-weapon mounts
     * ({@code SYSTEM}, {@code DECORATIVE}, {@code BUILT_IN}, {@code STATION_MODULE}).
     */
    private static final Set<String> MOUNTABLE = Set.of(
            "BALLISTIC", "ENERGY", "MISSILE", "COMPOSITE", "HYBRID", "UNIVERSAL", "SYNERGY");

    private static final Map<String, float[][]> CACHE_BY_HULL = new HashMap<>();

    private TurretSlotResolver() {}

    /**
     * Returns the mountable weapon-slot positions for {@code hullId}, each a
     * {@code {localX, localY}} pair in the shuttle-local frame (cells, {@code +Y}
     * nose / {@code +X} starboard), in spec order. Lazy-loaded; cached. Never
     * null — null/empty id or any failure yields an empty array.
     */
    public static float[][] resolve(String hullId) {
        if (hullId == null || hullId.isEmpty()) return EMPTY;
        float[][] cached = CACHE_BY_HULL.get(hullId);
        if (cached != null) return cached;
        float[][] resolved = doResolve(hullId);
        CACHE_BY_HULL.put(hullId, resolved);
        return resolved;
    }

    private static float[][] doResolve(String hullId) {
        String path = "data/hulls/" + hullId + ".ship";
        try {
            JSONObject spec = Global.getSettings().loadJSON(path);
            JSONArray slots = spec.optJSONArray("weaponSlots");
            if (slots == null || slots.length() == 0) return EMPTY;
            // px → cell at the global density (METERS_PER_CELL == 1).
            float k = AirScale.METERS_PER_PX / AirScale.METERS_PER_CELL;
            List<float[]> out = new ArrayList<>(slots.length());
            for (int i = 0; i < slots.length(); i++) {
                JSONObject slot = slots.getJSONObject(i);
                if (!MOUNTABLE.contains(slot.optString("type", ""))) continue;
                JSONArray loc = slot.optJSONArray("locations");
                if (loc == null || loc.length() < 2) continue;
                float vx = (float) loc.getDouble(0);
                float vy = (float) loc.getDouble(1);
                // Same frame swap as ShipSpecEngineParser: vanilla port (+Y) →
                // our starboard-negative (-X); vanilla forward (+X) → our forward (+Y).
                out.add(new float[]{ -vy * k, vx * k });
            }
            return out.toArray(new float[0][]);
        } catch (Exception e) {
            LOG.warn("TurretSlotResolver: " + hullId + " (" + path + ") — "
                    + e.getClass().getSimpleName() + ": " + e.getMessage());
            return EMPTY;
        }
    }
}
