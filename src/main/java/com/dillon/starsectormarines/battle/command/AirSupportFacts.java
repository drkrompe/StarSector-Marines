package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.airsupport.AirStrikeCapability;
import com.dillon.starsectormarines.battle.airsupport.AirStrikeDirector;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * What a side's air picture says to a plan, frozen: how many strikes it could place right now and what a pass over a
 * given cell is believed to cost. Read once per pulse off the side's own pool and its fire-support officer's sightings
 * ({@link AirStrikeCapability}), so a plan that counts a strike and the strike that is later asked for cannot drift apart.
 * A side knows its own pool and its own sightings, never a hostile's anti-air it has not seen.
 *
 * @param calls        strikes the side could place back to back: free operators against airframes in stock
 * @param riskByCell   the believed airframe share a pass over a works cell would cost, keyed by {@link #key}; absent when
 *                     the cell was not assessed
 * @param riskCeiling  the share above which the director declines a strike
 * @param flownAt      whether a unit of the side has lately had a hostile armed aircraft in view
 */
public record AirSupportFacts(int calls, Map<Long, Float> riskByCell, float riskCeiling, boolean flownAt) {

    /** Ticks a sighting of hostile armed aircraft keeps the side wanting the sky. UNTUNED. */
    public static final int FLOWN_AT_WINDOW_TICKS = 1800;

    /** A picture with no one flying at the side. */
    public AirSupportFacts(int calls, Map<Long, Float> riskByCell, float riskCeiling) {
        this(calls, riskByCell, riskCeiling, false);
    }

    /** No pool, no operators, nothing to count. */
    public static final AirSupportFacts NONE = new AirSupportFacts(0, Map.of(), AirStrikeDirector.RISK_CEILING, false);

    public AirSupportFacts {
        riskByCell = Map.copyOf(riskByCell);
    }

    /** The key a cell is stored under. */
    public static long key(int x, int y) {
        return ((long) x << 32) | (y & 0xFFFFFFFFL);
    }

    /** Whether a pass over the cell was assessed and is believed to cost no more than the director will risk. */
    public boolean acceptable(int x, int y) {
        Float risk = riskByCell.get(key(x, y));
        return risk != null && risk <= riskCeiling;
    }

    /**
     * Whether the sky is shut over a fort the side knows: a strike is on offer, some works cell was assessed, and every
     * assessed cell is above the ceiling. The side that wants the sky back takes the anti-air that refuses it.
     */
    public boolean blocked() {
        if (calls <= 0 || riskByCell.isEmpty()) return false;
        for (float risk : riskByCell.values()) if (risk <= riskCeiling) return false;
        return true;
    }

    /** Why this side wants the sky opened: shut out of it, flown at, or both. */
    public SkyNeed skyNeed() {
        return new SkyNeed(blocked(), flownAt);
    }

    /** The same picture, knowing the side is (or is not) being flown at. */
    public AirSupportFacts withFlownAt(boolean flownAt) {
        return new AirSupportFacts(calls, riskByCell, riskCeiling, flownAt);
    }

    /**
     * Whether a unit of {@code side} has had a hostile armed aircraft in view within {@link #FLOWN_AT_WINDOW_TICKS}: the
     * side's own eyes, read for any side whether or not it has a pool of its own.
     */
    public static boolean flownAt(BattleSimulation sim, Faction side) {
        AirStrikeDirector director = sim.airPool().director(side);
        director.noteHostileAir(sim);
        return director.ticksSinceHostileAir(sim.getSimTickIndex()) <= FLOWN_AT_WINDOW_TICKS;
    }

    /** Whether a strike is on offer at all. */
    public boolean any() {
        return calls > 0;
    }

    /**
     * The side's picture over the given works cells. A side with no pool gets {@link #NONE}. The fire-support officer first
     * looks at the field, so the risk is built from what the side has sighted by now and not from the last time it was asked.
     */
    public static AirSupportFacts of(BattleSimulation sim, Faction side, List<int[]> cells) {
        boolean flownAt = flownAt(sim, side);
        if (!sim.airPool().hasPool(side)) return NONE.withFlownAt(flownAt);
        sim.airPool().director(side).look(sim);
        AirStrikeCapability capability = AirStrikeCapability.of(sim, side);
        Map<Long, Float> risk = new HashMap<>();
        if (capability.calls() > 0) {
            for (int[] cell : cells) {
                risk.put(key(cell[0], cell[1]), AirStrikeCapability.aaRisk(sim, side, cell[0] + 0.5f, cell[1] + 0.5f));
            }
        }
        return new AirSupportFacts(capability.calls(), risk, AirStrikeDirector.RISK_CEILING, flownAt);
    }
}
