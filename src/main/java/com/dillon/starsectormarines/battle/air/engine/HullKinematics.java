package com.dillon.starsectormarines.battle.air.engine;

import com.dillon.starsectormarines.battle.air.AirHandling;
import com.dillon.starsectormarines.battle.air.AirScale;

/**
 * Converts a vanilla hull's maneuver stats (from {@code ship_data.csv}, exposed
 * at runtime via {@code ShipHullSpecAPI.getEngineSpec()}) into this mod's
 * ground-scale {@link AirHandling}. Pure — no {@code Global} — so the scrape →
 * scale math is unit-testable offline; the {@link HullKinematicsResolver} does
 * the runtime lookup + caching.
 *
 * <h2>Why scrape</h2>
 * <p>Every Starsector hull (vanilla <em>and</em> any mod using the stock ship
 * file format) carries its own {@code max speed / acceleration / deceleration /
 * max turn rate}. Deriving {@link AirHandling} from those means a craft's feel —
 * twitchy interceptor vs sluggish bomber — is the <em>data's</em>, not a
 * hand-authored tier, and modded fighters fly correctly with no new code.
 *
 * <h2>The conversion</h2>
 * <ul>
 *   <li><b>Linear</b> ({@code maxSpeed/accel/decel}, in Starsector units/sec):
 *       {@code su} are sprite-pixel-scaled, so the geometry density
 *       ({@link AirScale#METERS_PER_PX}) seeds the {@code su → cells/sec} factor,
 *       times {@link #SPEED_ATMO_MULT} (fighters should read faster over the
 *       battlefield than their campaign crawl).</li>
 *   <li><b>Angular</b> ({@code maxTurnRate}, deg/sec): passes through times
 *       {@link #TURN_ATMO_MULT} — angular rate is scale-invariant.</li>
 *   <li><b>Lateral / station damping</b>: NOT in the ship spec (vanilla
 *       space-flight has no atmospheric drag). These are the atmosphere knobs
 *       that give the boat-feel; constants here, the calibration surface.</li>
 * </ul>
 */
public final class HullKinematics {

    /** Atmosphere speed multiplier on top of the geometry density — the dial for "how fast air craft read." Calibration surface. */
    static final float SPEED_ATMO_MULT = 1.3f;
    /** Atmosphere turn multiplier; 1.0 = the hull's raw deg/sec. */
    static final float TURN_ATMO_MULT = 1.0f;
    /** Atmospheric lateral drift damping (1/sec) — not in the ship spec; the boat-feel knob. */
    static final float LATERAL_DRIFT_DAMPING = 2.5f;
    /** Atmospheric station-keeping damping (1/sec); higher so a hovering craft settles. */
    static final float STATION_DAMPING = 4.0f;

    /** Starsector-units/sec → cells/sec. {@code su} ≈ sprite pixels, so the geometry density seeds it, scaled by the atmosphere mult. */
    static final float CELLS_PER_SU = AirScale.METERS_PER_PX * SPEED_ATMO_MULT;

    private HullKinematics() {}

    /**
     * Builds an {@link AirHandling} from a hull's raw maneuver stats. Negative
     * inputs are clamped to 0 (a degenerate spec yields a frozen-but-safe craft
     * rather than NaN steering).
     *
     * @param maxSpeedSu      {@code ship_data.csv} "max speed" (su/sec)
     * @param accelSu         "acceleration" (su/sec²)
     * @param decelSu         "deceleration" (su/sec²)
     * @param maxTurnRateDeg  "max turn rate" (deg/sec)
     */
    public static AirHandling fromSpec(float maxSpeedSu, float accelSu, float decelSu, float maxTurnRateDeg) {
        return new Scraped(
                Math.max(0f, maxSpeedSu) * CELLS_PER_SU,
                Math.max(0f, accelSu) * CELLS_PER_SU,
                Math.max(0f, decelSu) * CELLS_PER_SU,
                Math.max(0f, maxTurnRateDeg) * TURN_ATMO_MULT,
                LATERAL_DRIFT_DAMPING,
                STATION_DAMPING);
    }

    /** Immutable {@link AirHandling} backed by scraped + scaled values. Record component names match the interface accessors. */
    record Scraped(float maxSpeed, float accel, float brakingAccel,
                   float maxTurnRateDegPerSec, float lateralDriftDamping,
                   float stationDamping) implements AirHandling {}
}
