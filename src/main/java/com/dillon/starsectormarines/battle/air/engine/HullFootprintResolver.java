package com.dillon.starsectormarines.battle.air.engine;

import com.dillon.starsectormarines.battle.air.AirScale;
import com.fs.starfarer.api.Global;
import org.apache.log4j.Logger;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

/**
 * Lazy cache of a hull's derived <b>render length</b> (cells along the forward
 * axis), keyed by vanilla hull id. The single authority for "how big does this
 * hull draw" — shared by the hull sprite quad ({@code ShuttleRenderSystem}),
 * the engine-slot scaling ({@link EngineSlotResolver#resolve}), and the turret
 * author panel, so all three agree on one number.
 *
 * <p>A hull's length is its {@code .ship} {@code height} (the sprite's pixel
 * extent along the forward/+X axis, since ships are drawn nose-up) times the one
 * global {@link AirScale#METERS_PER_PX}. Because every Starsector sprite shares
 * one pixel density, this single factor reproduces the whole relative-size
 * ladder — base and modded — for free.
 *
 * <p>First lookup loads {@code data/hulls/<hullId>.ship} via
 * {@link com.fs.starfarer.api.SettingsAPI#loadJSON(String)} (which follows the
 * game's mod load order, so modded hulls drop in automatically). Failures
 * (no hull id, missing {@code .ship}, no {@code height}) log once and cache
 * {@link AirScale#FALLBACK_LENGTH_CELLS} so the render path degrades silently.
 *
 * <p>Mirrors {@link EngineSlotResolver}'s caching shape; the two scrape the same
 * {@code .ship} for different fields and cache independently.
 */
public final class HullFootprintResolver {

    private static final Logger LOG = Global.getLogger(HullFootprintResolver.class);

    /** Key: vanilla hull id. Value: derived render length in cells. */
    private static final Map<String, Float> CACHE_BY_HULL = new HashMap<>();

    private HullFootprintResolver() {}

    /**
     * Returns the derived forward render length, in cells, for {@code hullId}.
     * Lazy-loads on first call; cached thereafter. Never throws — a null/empty
     * id or any resolution failure returns {@link AirScale#FALLBACK_LENGTH_CELLS}.
     */
    public static float visualLengthCells(String hullId) {
        if (hullId == null || hullId.isEmpty()) return AirScale.FALLBACK_LENGTH_CELLS;
        Float cached = CACHE_BY_HULL.get(hullId);
        if (cached != null) return cached;

        float resolved = doResolve(hullId);
        CACHE_BY_HULL.put(hullId, resolved);
        return resolved;
    }

    private static float doResolve(String hullId) {
        String path = "data/hulls/" + hullId + ".ship";
        try {
            JSONObject spec = Global.getSettings().loadJSON(path);
            float heightPx = (float) spec.optDouble("height", 0.0);
            if (heightPx <= 0f) {
                LOG.warn("HullFootprintResolver: " + hullId + " (" + path + ") — "
                        + "missing/invalid 'height'; using fallback length");
                return AirScale.FALLBACK_LENGTH_CELLS;
            }
            return AirScale.cellsForHeightPx(heightPx);
        } catch (Exception e) {
            LOG.warn("HullFootprintResolver: " + hullId + " (" + path + ") — "
                    + e.getClass().getSimpleName() + ": " + e.getMessage());
            return AirScale.FALLBACK_LENGTH_CELLS;
        }
    }
}
