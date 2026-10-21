package com.dillon.starsectormarines.battle.creep;

import com.dillon.starsectormarines.battle.evacuation.ColonyEruptionPlan;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;

/**
 * Creep studies without bores: each eruption site as a creep source that never
 * dies, the mother at maw growth and the first wave at bore growth. For look
 * and cost evidence that advances the creep field alone (the {@code hive-creep}
 * snapshot suite, {@code renderEvidence}'s colony); a battle's creep comes from
 * its bores ({@link BoreCreep}).
 */
public final class ColonyCreep {

    /** Source ids for eruption sites: this plus the site's number (the mother is 0). */
    public static final long SITE_SOURCE_BASE = 0x5173_0000L;

    private ColonyCreep() { }

    /** Declares the site as a creep source. */
    public static void siteErupted(BattleSimulation sim, ColonyEruptionPlan.Site site, int tick) {
        sim.creepField().putSource(SITE_SOURCE_BASE + site.number(), site.x(), site.y(),
                site.mother() ? CreepGrowth.MAW : CreepGrowth.BORE);
    }
}
