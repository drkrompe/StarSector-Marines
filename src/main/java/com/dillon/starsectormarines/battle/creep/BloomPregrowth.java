package com.dillon.starsectormarines.battle.creep;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;

/**
 * A bloom that has held its ground for weeks before the battle starts
 * (`bloom-mission-arc.md`, the heart; `colony-breach-nouns.md`, Creep).
 *
 * <p>Grows the creep of every bore already open, and lets the rules that read
 * the creep act on it, over a span of ticks <em>before</em> tick zero: the
 * creep reaches as far as its sources reach, walls crust over, the trees it
 * climbs die to bare skeletons and the roofs it stands on cave in. Nothing is
 * painted on: it is the same field and the same rules a battle runs, run early,
 * so a creep-covered country at tick zero is exactly what those bores would
 * have made of it, and a bore collapsed in the battle withers its creep back as
 * any bore does (the sources are the bores' own, declared through
 * {@link BattleSimulation#syncBoreCreep}).
 *
 * <p>The early ticks are negative, so the battle's clock still starts at zero
 * and a tree that died or a roof that fell before it is recorded as having done
 * so before the battle rather than in its first second.
 */
public final class BloomPregrowth {

    private static final int TICKS_PER_SECOND = 30;

    private BloomPregrowth() { }

    /**
     * Grows the open bores' creep for {@code seconds} before tick zero. Call at
     * setup, once, after the bores are open and before the battle's first tick.
     */
    public static void age(BattleSimulation sim, float seconds) {
        if (sim.getSimTickIndex() != 0) throw new IllegalStateException("a bloom is grown before the battle, not during it");
        sim.syncBoreCreep();
        CreepField field = sim.creepFieldIfPresent();
        if (field == null) return;
        int ticks = Math.round(seconds * TICKS_PER_SECOND);
        CreepRoofCollapse roofs = sim.creepRoofs();
        CreepVegetation trees = sim.creepVegetation();
        for (int tick = -ticks; tick < 0; tick++) {
            field.tick(tick);
            if (Math.floorMod(tick, CreepRoofCollapse.CHECK_TICKS) == 0) roofs.look(sim, tick);
            if (Math.floorMod(tick, CreepVegetation.CHECK_TICKS) == 0) trees.look(sim, tick);
        }
    }
}
