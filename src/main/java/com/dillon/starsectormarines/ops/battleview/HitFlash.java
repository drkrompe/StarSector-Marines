package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.CombatService;

/**
 * When a body flashes: briefly white the moment a hit lands on it, whatever then absorbed the round.
 *
 * <p>Read off the tick the simulation already stamps when a hit lands ({@link CombatService#ticksSinceDamaged}),
 * before cover, screens and armour have their say, so a round a plate stops still flashes. A pure function of the
 * simulation's state: nothing is added to it and nothing in it reads this, so the flash cannot move a battle, and a
 * paused battle holds its flash.
 */
final class HitFlash {
    /** How long a flash takes to fade, in seconds of battle. */
    static final float SECONDS = 0.16f;
    /** The share of white at a flash's peak. */
    static final float PEAK = 0.75f;

    private HitFlash() { }

    /** Body {@code id}'s flash at {@code tick}, the share of white to draw it with; zero when it was not just hit. */
    static float amount(CombatService combat, long id, int tick) {
        int since = combat.ticksSinceDamaged(id, tick);
        return since == Integer.MAX_VALUE ? 0f : fade(since * BattleSimulation.TICK_DT);
    }

    /** The share of white {@code age} seconds after a hit: {@link #PEAK} at once, gone after {@link #SECONDS}. */
    static float fade(float age) {
        if (age < 0f || age >= SECONDS) return 0f;
        float left = 1f - age / SECONDS;
        return PEAK * left * left;
    }
}
