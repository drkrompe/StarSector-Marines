package com.dillon.starsectormarines.battle.creep;

import com.dillon.starsectormarines.battle.evacuation.ColonyEruptionPlan;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;

/**
 * Creep on a Colony Breach battle while nothing stands up on an eruption site:
 * each opened site becomes a creep source, the mother at maw growth and the
 * first wave at bore growth, and nothing ever removes them.
 *
 * <p>This is the breach sequence's default eruption handler. A handler that
 * builds bores replaces it and declares creep from each living bore instead
 * (`hive-creep.md`), so a collapsed bore's creep can recede.
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
