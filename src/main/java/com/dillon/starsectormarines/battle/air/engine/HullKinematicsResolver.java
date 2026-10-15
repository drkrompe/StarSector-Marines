package com.dillon.starsectormarines.battle.air.engine;

import com.dillon.starsectormarines.battle.air.AirHandling;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import org.apache.log4j.Logger;

import java.util.concurrent.ConcurrentHashMap;
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

    /** Keyed on hull id, filled lazily during a battle, and concurrent because two battles can run in one JVM. The value is a pure function of the id, so a race would only re-scrape the spec. */
    private static final Map<String, AirHandling> CACHE_BY_HULL =
            new ConcurrentHashMap<>();

    /**
     * Where a hull's raw maneuver numbers come from when there is no loaded game to ask: {@code {maxSpeed, acceleration,
     * deceleration, maxTurnRate}} in the units of {@code ship_data.csv}, or {@code null} for a hull it does not know.
     * Headless code installs one so that a Dagger and a Talon do not both fly as the generic fallback fighter; the game
     * never does.
     */
    public interface EngineSource {
        float[] engine(String hullId);
    }

    private static volatile EngineSource engineSource;

    /** Installs (or, with {@code null}, removes) the headless source of maneuver numbers, forgetting what was resolved without it. */
    public static void useEngineSource(EngineSource source) {
        engineSource = source;
        CACHE_BY_HULL.clear();
    }

    private HullKinematicsResolver() {}

    /**
     * The flight profile for {@code hullId}, scraped + scaled. Lazy-loaded;
     * cached thereafter. Never null — a null/empty id or any failure yields
     * {@link #FALLBACK}.
     */
    public static AirHandling resolve(String hullId) {
        if (hullId == null || hullId.isEmpty()) return FALLBACK;
        return CACHE_BY_HULL.computeIfAbsent(
                hullId, HullKinematicsResolver::doResolve);
    }

    private static AirHandling doResolve(String hullId) {
        EngineSource headless = engineSource;
        if (headless != null) {
            float[] e = headless.engine(hullId);
            if (e != null && e.length >= 4 && e[0] > 0f && e[3] > 0f) return HullKinematics.fromSpec(e[0], e[1], e[2], e[3]);
        }
        try {
            ShipHullSpecAPI spec = Global.getSettings().getHullSpec(hullId);
            ShipHullSpecAPI.EngineSpecAPI eng = spec.getEngineSpec();
            float maxSpeedSu = eng.getMaxSpeed();
            float maxTurnSu = eng.getMaxTurnRate();
            // A craft that can't move or can't turn is a degenerate spec — fall
            // back rather than ship a fighter that flies a dead-straight line.
            if (maxSpeedSu <= 0f || maxTurnSu <= 0f) {
                LOG.warn("HullKinematicsResolver: " + hullId + " degenerate maneuver spec (maxSpeed="
                        + maxSpeedSu + ", maxTurnRate=" + maxTurnSu + ") — using FALLBACK");
                return FALLBACK;
            }
            return HullKinematics.fromSpec(
                    maxSpeedSu, eng.getAcceleration(), eng.getDeceleration(), maxTurnSu);
        } catch (Exception e) {
            LOG.warn("HullKinematicsResolver: " + hullId + " — "
                    + e.getClass().getSimpleName() + ": " + e.getMessage());
            return FALLBACK;
        }
    }
}
