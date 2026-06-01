package com.dillon.starsectormarines.battle.flyby;

import com.dillon.starsectormarines.battle.unit.Faction;

/**
 * Debug-only factory for force-spawned fighter wings — drives the
 * {@code DevConfig.DEBUG_AIRCRAFT_PICKER} briefing panel. Lets us put any
 * {@link FighterProfile} in the air on either side, independent of the player's
 * carrier fleet or the mission's enemy-air roll, so fighter-feel calibration (the
 * scraped {@code AirHandling} + atmosphere knobs) doesn't need a curated setup.
 *
 * <p>The schedule is a few prompt sorties so the feel settles quickly; it's a
 * test harness, not a balance source, so the numbers are deliberately simple.
 */
public final class DebugAirRoster {

    /** Sorties per forced wing — a few passes so the feel settles while watching. */
    static final int SORTIES = 3;
    /** First arrival (sim-sec) — soon, so you don't wait out a long stagger. */
    static final float FIRST_ARRIVAL_SEC = 4f;
    /** Interval between sorties (sim-sec). */
    static final float INTERVAL_SEC = 8f;

    private DebugAirRoster() {}

    /** One forced wing of {@code profile} flying for {@code side} on the debug schedule. */
    public static FighterWing wing(FighterProfile profile, Faction side) {
        return new FighterWing(profile, side, SORTIES, FIRST_ARRIVAL_SEC, INTERVAL_SEC);
    }
}
