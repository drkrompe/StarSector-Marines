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
 * the engine-slot scaling ({@link EngineSlotResolver#resolve}), the body radius
 * an aircraft is shot at ({@code Airframe.targetRadiusCells}), and the turret
 * author panel, so all of them agree on one number.
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
 * <p>Silent degradation is right for a frame and wrong for a measurement, which
 * is what {@link #useHullDimensions} and {@link #isMeasured} are for. Outside
 * the game there is no {@code SettingsAPI} at all, so every hull alike fell to
 * the fallback and a Wasp came out the size of a Valkyrie — an aircraft that
 * exists nowhere, measured by every headless snapshot, scene and test that
 * asked about drawn size, target radius or blast catch.
 *
 * <p>Mirrors {@link EngineSlotResolver}'s caching shape; the two scrape the same
 * {@code .ship} for different fields and cache independently.
 */
public final class HullFootprintResolver {

    private static final Logger LOG = Global.getLogger(HullFootprintResolver.class);

    /**
     * Where a hull's forward pixel extent comes from when there is no loaded
     * game to ask for it.
     *
     * <p>Deliberately a seam and not a reader. Mod code may not touch the
     * filesystem, so the half that opens the install lives in test/tool scope
     * and hands its answers in through here. The loaded game still wins
     * wherever there is one, so nothing about a running game changes.
     */
    @FunctionalInterface
    public interface HullDimensions {

        /**
         * Forward sprite extent of {@code hullId} in pixels — the same number
         * vanilla's {@code .ship} calls {@code height} — or 0 when this source
         * cannot say.
         */
        float forwardExtentPx(String hullId);
    }

    /** Key: vanilla hull id. Value: the resolved footprint, real or stood in for. */
    private static final Map<String, Footprint> CACHE_BY_HULL = new HashMap<>();

    private static final Footprint FALLBACK =
            new Footprint(AirScale.FALLBACK_LENGTH_CELLS, false);

    private static volatile HullDimensions hullDimensions;

    private HullFootprintResolver() {}

    /**
     * Returns the derived forward render length, in cells, for {@code hullId}.
     * Lazy-loads on first call; cached thereafter. Never throws — a null/empty
     * id or any resolution failure returns {@link AirScale#FALLBACK_LENGTH_CELLS}.
     */
    public static float visualLengthCells(String hullId) {
        if (hullId == null || hullId.isEmpty()) return AirScale.FALLBACK_LENGTH_CELLS;
        return footprint(hullId).lengthCells();
    }

    /**
     * Whether {@code hullId}'s length was read off a real spec, or is the
     * fallback standing in for one.
     *
     * <p>A caller drawing a frame has no use for this: a hull that cannot be
     * sized should still appear, which is exactly why the failure is quiet. A
     * caller <em>measuring</em> has every use for it, because the same silence
     * turns "how much aircraft is there" into a constant, and a constant is an
     * answer that looks like all the others.
     */
    public static boolean isMeasured(String hullId) {
        if (hullId == null || hullId.isEmpty()) return false;
        return footprint(hullId).measured();
    }

    /**
     * Points the resolver at a stand-in for the loaded game's hull specs, and
     * drops whatever it resolved without one.
     *
     * <p>Pass {@code null} to go back to the game alone. Installing a source
     * changes nothing in a running game — the game is consulted first and only
     * a failure reaches the source — and everything outside one, where the
     * alternative is every hull reporting the same length.
     */
    public static synchronized void useHullDimensions(HullDimensions source) {
        hullDimensions = source;
        CACHE_BY_HULL.clear();
    }

    private static synchronized Footprint footprint(String hullId) {
        Footprint cached = CACHE_BY_HULL.get(hullId);
        if (cached != null) return cached;

        Footprint resolved = doResolve(hullId);
        CACHE_BY_HULL.put(hullId, resolved);
        return resolved;
    }

    private static Footprint doResolve(String hullId) {
        String path = "data/hulls/" + hullId + ".ship";
        String failure;
        try {
            JSONObject spec = Global.getSettings().loadJSON(path);
            float heightPx = (float) spec.optDouble("height", 0.0);
            if (heightPx > 0f) return measured(heightPx);
            failure = "missing/invalid 'height'";
        } catch (Exception e) {
            failure = e.getClass().getSimpleName() + ": " + e.getMessage();
        }

        HullDimensions source = hullDimensions;
        if (source != null) {
            float heightPx = source.forwardExtentPx(hullId);
            if (heightPx > 0f) return measured(heightPx);
        }

        LOG.warn("HullFootprintResolver: " + hullId + " (" + path + ") — "
                + failure + "; using fallback length");
        return FALLBACK;
    }

    private static Footprint measured(float heightPx) {
        return new Footprint(AirScale.cellsForHeightPx(heightPx), true);
    }

    /** A hull's render length, and whether anything actually measured it. */
    private record Footprint(float lengthCells, boolean measured) {}
}
