package com.dillon.starsectormarines.battle.air.engine;

import com.fs.starfarer.api.Global;
import org.apache.log4j.Logger;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Filters {@link WeaponSlotParser}'s scrape down to mountable weapon-slot
 * positions — each already in the shuttle-local frame at the one global pixel
 * density and center-compensated. The result is each hardpoint's real on-hull
 * position in cells, so shuttle turrets mount where the artist painted the
 * turret slots instead of at hand-authored generic offsets.
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

    /** Keyed on hull id, filled lazily during a battle, and concurrent because two battles can run in one JVM. The value is a pure function of the id, so a race would only re-parse the spec. */
    private static final Map<String, float[][]> CACHE_BY_HULL =
            new ConcurrentHashMap<>();

    /**
     * Where a hull's {@code .ship} specification comes from when no game is loaded.
     *
     * <p>Consulted only after the game has failed to answer, exactly as
     * {@link HullFootprintResolver.HullDimensions} is: outside a game every hull would otherwise
     * resolve to no slots, so an armed craft would fly unarmed and say nothing.
     */
    public interface HullSpecSource {
        /** The parsed {@code .ship} for {@code hullId}; throws when there is none. */
        JSONObject spec(String hullId) throws Exception;
    }

    private static volatile HullSpecSource fallbackSource;

    private TurretSlotResolver() {}

    /** Points the resolver at a stand-in for the loaded game's hull specs, and drops what it resolved without one. Pass {@code null} to go back to the game alone. */
    public static synchronized void useHullSpecs(HullSpecSource source) {
        fallbackSource = source;
        CACHE_BY_HULL.clear();
    }

    /**
     * Returns the mountable weapon-slot positions for {@code hullId}, each a
     * {@code {localX, localY}} pair in the shuttle-local frame (cells, {@code +Y}
     * nose / {@code +X} starboard), in spec order. Lazy-loaded; cached. Never
     * null — null/empty id or any failure yields an empty array.
     */
    public static float[][] resolve(String hullId) {
        if (hullId == null || hullId.isEmpty()) return EMPTY;
        return CACHE_BY_HULL.computeIfAbsent(
                hullId, TurretSlotResolver::doResolve);
    }

    private static float[][] doResolve(String hullId) {
        String path = "data/hulls/" + hullId + ".ship";
        try {
            JSONObject spec = loadSpec(hullId, path);
            List<float[]> out = new ArrayList<>();
            for (WeaponSlot slot : WeaponSlotParser.parse(spec)) {
                if (MOUNTABLE.contains(slot.type())) {
                    out.add(new float[]{ slot.localX(), slot.localY() });
                }
            }
            return out.toArray(new float[0][]);
        } catch (Exception e) {
            LOG.warn("TurretSlotResolver: " + hullId + " (" + path + ") — "
                    + e.getClass().getSimpleName() + ": " + e.getMessage());
            return EMPTY;
        }
    }

    private static JSONObject loadSpec(String hullId, String path) throws Exception {
        Exception failure = null;
        try {
            JSONObject spec = Global.getSettings().loadJSON(path);
            // A settings stand-in may answer nothing rather than throw; that is no answer either.
            if (spec != null) return spec;
        } catch (Exception game) {
            failure = game;
        }
        HullSpecSource source = fallbackSource;
        if (source != null) return source.spec(hullId);
        throw failure != null ? failure : new IllegalStateException("no hull spec for " + hullId);
    }
}
