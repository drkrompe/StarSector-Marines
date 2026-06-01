package com.dillon.starsectormarines.battle.air.engine;

import com.dillon.starsectormarines.battle.air.AirHandling;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import org.apache.log4j.Logger;

import java.util.HashMap;
import java.util.Map;

/**
 * Lazy cache of {@link AirHandling} scraped from a hull's runtime maneuver spec,
 * keyed by hull id. First lookup reads
 * {@code Global.getSettings().getHullSpec(hullId).getEngineSpec()} — the
 * <em>merged</em> spec across vanilla + every loaded mod — and converts it via
 * {@link HullKinematics}. So any modded hull using the stock ship file format
 * gets a correct flight profile with no per-hull code. Sandbox-safe: SettingsAPI
 * only, no file I/O ({@code [[starsector_script_sandbox]]}).
 *
 * <p>Mirrors {@link EngineSlotResolver} / {@link HullFootprintResolver}: failed
 * resolutions (no hull id, missing spec, zero/garbage stats) log once and cache
 * the {@link #FALLBACK} profile so steering degrades to a flyable mid-tier craft
 * rather than NPEing or freezing.
 */
public final class HullKinematicsResolver {

    private static final Logger LOG = Global.getLogger(HullKinematicsResolver.class);

    /** Mid-tier profile for hulls with a missing / zero maneuver spec — keeps the craft flyable. Values are a generic fighter (su): 200 speed, 300/250 accel/decel, 120 deg/sec turn. */
    static final AirHandling FALLBACK = HullKinematics.fromSpec(200f, 300f, 250f, 120f);

    private static final Map<String, AirHandling> CACHE_BY_HULL = new HashMap<>();

    private HullKinematicsResolver() {}

    /**
     * The flight profile for {@code hullId}, scraped + scaled. Lazy-loaded;
     * cached thereafter. Never null — a null/empty id or any failure yields
     * {@link #FALLBACK}.
     */
    public static AirHandling resolve(String hullId) {
        if (hullId == null || hullId.isEmpty()) return FALLBACK;
        AirHandling cached = CACHE_BY_HULL.get(hullId);
        if (cached != null) return cached;
        AirHandling resolved = doResolve(hullId);
        CACHE_BY_HULL.put(hullId, resolved);
        return resolved;
    }

    private static AirHandling doResolve(String hullId) {
        try {
            ShipHullSpecAPI spec = Global.getSettings().getHullSpec(hullId);
            ShipHullSpecAPI.EngineSpecAPI eng = spec.getEngineSpec();
            float maxSpeedSu = eng.getMaxSpeed();
            if (maxSpeedSu <= 0f) {
                LOG.warn("HullKinematicsResolver: " + hullId + " has zero maxSpeed — using FALLBACK");
                return FALLBACK;
            }
            return HullKinematics.fromSpec(
                    maxSpeedSu, eng.getAcceleration(), eng.getDeceleration(), eng.getMaxTurnRate());
        } catch (Exception e) {
            LOG.warn("HullKinematicsResolver: " + hullId + " — "
                    + e.getClass().getSimpleName() + ": " + e.getMessage());
            return FALLBACK;
        }
    }
}
