package com.dillon.starsectormarines.battle.air.engine;

import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.fs.starfarer.api.Global;
import org.apache.log4j.Logger;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

/**
 * Lazy cache of engine slot data, keyed by vanilla hull id. First lookup
 * loads {@code data/hulls/<hullId>.ship} via
 * {@link com.fs.starfarer.api.SettingsAPI#loadJSON(String)} — which follows
 * the game's mod load order, so modded hulls drop in automatically. Failed
 * resolutions (no hullId, missing .ship, malformed JSON) log once and cache
 * an empty array so the render pass degrades silently.
 *
 * <p>One cache shared across air entities, keyed by hull id. The cached slot
 * positions are scaled to the {@code visualLengthCells} of the <em>first</em>
 * caller for a given hull. That's safe today because shuttle hull ids and flyby
 * fighter hull ids are disjoint, and shuttles all resolve their length the same
 * way (the global density via {@link HullFootprintResolver}); if a hull is ever
 * shared across callers wanting different lengths, key this cache on
 * {@code (hullId, length)}. Same parser as the preview test
 * ({@link ShipSpecEngineParser}), so the in-game coordinates and the
 * {@code build/engine-previews/*.png} images track the same source of truth.
 */
public final class EngineSlotResolver {

    private static final Logger LOG = Global.getLogger(EngineSlotResolver.class);
    private static final EngineSlotData[] EMPTY = new EngineSlotData[0];

    /**
     * Key: vanilla hull id (lowercase). HashMap because we now key on
     * arbitrary hull strings (shuttles + fighters + future air entities);
     * EnumMap is no longer sufficient.
     */
    private static final Map<String, EngineSlotData[]> CACHE_BY_HULL = new HashMap<>();

    private EngineSlotResolver() {}

    /**
     * Returns the engine slots for {@code hullId}, scaled to render at
     * {@code visualLengthCells} cells along the forward axis. Lazy-loads on
     * first call; cached thereafter. Never returns null.
     *
     * @param hullId            vanilla hull id (matches the file at
     *                          {@code data/hulls/<hullId>.ship}); null or
     *                          empty falls back to {@code EMPTY}
     * @param visualLengthCells the air entity's rendered length in cells —
     *                          pairs with the spec's {@code height} pixel
     *                          value to fix the pixel-to-cell ratio
     */
    public static EngineSlotData[] resolve(String hullId, float visualLengthCells) {
        if (hullId == null || hullId.isEmpty()) return EMPTY;
        EngineSlotData[] cached = CACHE_BY_HULL.get(hullId);
        if (cached != null) return cached;

        EngineSlotData[] resolved = doResolve(hullId, visualLengthCells);
        CACHE_BY_HULL.put(hullId, resolved);
        return resolved;
    }

    /**
     * Shuttle-specific convenience that picks the first matching vanilla hull
     * id off the type and threads through to {@link #resolve(String, float)}
     * with the hull's globally-derived render length
     * ({@link HullFootprintResolver#visualLengthCells(String)}) — so the engine
     * slots scale to the same footprint the hull sprite renders at. Types with
     * no matching hull id (e.g. {@code AEROSHUTTLE}) return an empty array.
     */
    public static EngineSlotData[] resolve(ShuttleType type) {
        if (type.matchingHullIds.isEmpty()) return EMPTY;
        String hullId = type.matchingHullIds.get(0);
        return resolve(hullId, HullFootprintResolver.visualLengthCells(hullId));
    }

    private static EngineSlotData[] doResolve(String hullId, float visualLengthCells) {
        String path = "data/hulls/" + hullId + ".ship";
        try {
            JSONObject spec = Global.getSettings().loadJSON(path);
            return ShipSpecEngineParser.parse(spec, visualLengthCells);
        } catch (Exception e) {
            LOG.warn("EngineSlotResolver: " + hullId + " (" + path + ") — "
                    + e.getClass().getSimpleName() + ": " + e.getMessage());
            return EMPTY;
        }
    }
}
