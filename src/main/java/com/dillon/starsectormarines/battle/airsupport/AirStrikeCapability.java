package com.dillon.starsectormarines.battle.airsupport;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;

/**
 * What a side can ask of its air right now, as a plan or a commander reads it: whether it can direct a strike at all,
 * how many airframes of each kind are in stock, how many have been lost, and how many operators are free to call.
 *
 * <p>Read off the same pool and the same operators that tasking uses, so a plan's idea of "this company can call a
 * strike" and the company's ability to do it cannot drift apart. A side knows its own capability, never an enemy's;
 * the anti-air figure is the side's own estimate from what it has sighted ({@link AirStrikeDirector#risk}), so it can
 * be wrong in the way an estimate is.
 *
 * @param operators      free forward observers on the side (alive, unsuppressed, off cooldown)
 * @param bombersReady   bombers in stock
 * @param fightersReady  fighters in stock
 * @param bombersLost    bombers the side has lost to anti-air this battle
 * @param fightersLost   fighters the side has lost to anti-air this battle
 */
public record AirStrikeCapability(int operators, int bombersReady, int fightersReady, int bombersLost,
                                  int fightersLost) {

    /** Nothing: no pool, or nobody to call. */
    public static final AirStrikeCapability NONE = new AirStrikeCapability(0, 0, 0, 0, 0);

    /** The side's capability right now. */
    public static AirStrikeCapability of(BattleSimulation sim, Faction side) {
        AirPoolService pool = sim.airPool();
        if (!pool.hasPool(side)) return NONE;
        return new AirStrikeCapability(CommsOperators.available(sim, side).size(),
                pool.available(side, AirPoolKind.BOMBER), pool.available(side, AirPoolKind.FIGHTER),
                pool.lost(side, AirPoolKind.BOMBER), pool.lost(side, AirPoolKind.FIGHTER));
    }

    /** Whether a strike can be called at all: an operator is free and some airframe is in stock. */
    public boolean canStrike() {
        return operators > 0 && (bombersReady > 0 || fightersReady > 0);
    }

    /**
     * Strikes the side could place back to back before the pool or the operators run out: the smaller of free
     * operators and airframes in stock. The plan's "N calls".
     */
    public int calls() {
        return Math.min(operators, bombersReady + fightersReady);
    }

    /** The believed airframe share a pass over {@code (x, y)} would cost {@code side}, from what it has sighted. */
    public static float aaRisk(BattleSimulation sim, Faction side, float x, float y) {
        return sim.airPool().director(side).risk(sim, x, y);
    }
}
