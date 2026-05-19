package com.dillon.starsectormarines.battle.air.engine;

import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.fs.starfarer.api.Global;
import org.apache.log4j.Logger;
import org.json.JSONObject;

import java.util.EnumMap;

/**
 * Lazy per-{@link ShuttleType} cache of engine slot data. First lookup loads
 * {@code data/hulls/<hullId>.ship} via {@link com.fs.starfarer.api.SettingsAPI#loadJSON(String)}
 * — which follows the game's mod load order, so a modded hull whose id is
 * in {@link ShuttleType#matchingHullIds} picks up that mod's slot definitions
 * automatically. Subsequent lookups return the cached array.
 *
 * <p>Failure modes (no matching hullId, missing .ship file, malformed JSON)
 * all fall back to an empty array, logged once. The renderer treats empty as
 * "no engine FX for this type" — degrades gracefully instead of crashing
 * the battle screen on a malformed mod.
 *
 * <p>Same parser as the preview test (see {@link ShipSpecEngineParser}), so
 * the in-game coordinates and the {@code build/engine-previews/*.png}
 * verification track the same source of truth.
 */
public final class EngineSlotResolver {

    private static final Logger LOG = Global.getLogger(EngineSlotResolver.class);
    private static final EngineSlotData[] EMPTY = new EngineSlotData[0];

    private static final EnumMap<ShuttleType, EngineSlotData[]> cache =
            new EnumMap<>(ShuttleType.class);

    private EngineSlotResolver() {}

    /**
     * Returns the engine slots for the given shuttle type. Lazy-loads on
     * first call; cached thereafter. Never returns null.
     */
    public static EngineSlotData[] resolve(ShuttleType type) {
        EngineSlotData[] cached = cache.get(type);
        if (cached != null) return cached;

        EngineSlotData[] resolved = doResolve(type);
        cache.put(type, resolved);
        return resolved;
    }

    private static EngineSlotData[] doResolve(ShuttleType type) {
        if (type.matchingHullIds.isEmpty()) {
            // AEROSHUTTLE has no matching id — it's the employer-default sprite
            // that doesn't surface as a player hull. No slots to resolve.
            return EMPTY;
        }
        String hullId = type.matchingHullIds.get(0);
        String path = "data/hulls/" + hullId + ".ship";
        try {
            JSONObject spec = Global.getSettings().loadJSON(path);
            return ShipSpecEngineParser.parse(spec, type.visualLengthCells);
        } catch (Exception e) {
            LOG.warn("EngineSlotResolver: " + type + " (" + path + ") — "
                    + e.getClass().getSimpleName() + ": " + e.getMessage());
            return EMPTY;
        }
    }
}
