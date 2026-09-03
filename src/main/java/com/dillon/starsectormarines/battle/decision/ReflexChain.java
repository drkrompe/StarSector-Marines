package com.dillon.starsectormarines.battle.decision;

import com.dillon.starsectormarines.battle.profile.TickInnerProfile;
import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.squad.Squad;

import java.util.List;

/**
 * Runs a declared {@link Reflex} list in order for one unit on one tick.
 *
 * <p>Deliberately nothing more than that. The chain is a list an arm declares
 * and a test pins, not a registry a reflex signs itself into: a registry would
 * let ordering be decided by class-loading or by whoever registered last, and
 * the order is the law this whole shape exists to state out loud.
 */
public final class ReflexChain {

    private ReflexChain() {}

    /**
     * Asks each reflex in turn until one consumes the tick.
     *
     * @return the reflex that interrupted, or {@code null} when the unit is
     *         free to execute its assigned plan step. Later entries are not
     *         asked once one interrupts.
     */
    public static Reflex run(List<Reflex> chain, long unit, Squad squad,
                             ReflexContext context, BattleControl sim) {
        TickInnerProfile profile = TickInnerProfile.currentIfBound();
        for (int i = 0, n = chain.size(); i < n; i++) {
            Reflex reflex = chain.get(i);
            long t0 = profile != null ? System.nanoTime() : 0L;
            boolean fired = reflex.interrupt(unit, squad, context, sim);
            if (profile != null) {
                profile.record(TickInnerProfile.reflexBucket(reflex.name()),
                        System.nanoTime() - t0);
            }
            if (fired) {
                recordLastReflex(unit, reflex.name(), sim);
                return reflex;
            }
        }
        recordLastReflex(unit, null, sim);
        return null;
    }

    /**
     * Leaves the name behind for the per-unit dumps, so "this marine is evading
     * a grenade" is readable instead of "this marine is not executing its step".
     * Diagnostic only — nothing in the simulation reads it back. Cleared to
     * {@code null} on a tick nothing interrupted, or a stale name outlives the
     * thing it described.
     */
    private static void recordLastReflex(long unit, String name, BattleControl sim) {
        if (sim.world().hasAiState(unit)) sim.world().setLastReflex(unit, name);
    }
}
