package com.dillon.starsectormarines.battle.world.gen.bsp.stage;

import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.GenStage;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.gen.precinct.Precinct;
import com.dillon.starsectormarines.battle.world.gen.precinct.PrecinctPlan;
import com.dillon.starsectormarines.battle.world.gen.raid.RaidIndustries;
import com.dillon.starsectormarines.battle.world.gen.raid.RaidIndustry;
import com.dillon.starsectormarines.battle.world.gen.raid.WorksDefences;

/**
 * A Raid site's works dressed by its industry ({@link RaidIndustry#dressWorks}), then given its defences by security
 * ({@link WorksDefences}): after the buildings, roads and
 * doorways are final and before the landscape pass takes the open country. Inert on every map that is not a Raid
 * site.
 */
public final class RaidWorksDressingStage implements GenStage {

    @Override public void run(GenContext c) {
        PrecinctPlan plan = c.get(BspKeys.PRECINCTS);
        if (plan == null) return;
        RaidIndustry industry = RaidIndustries.byKey(plan.raidIndustry());
        Precinct works = plan.objective();
        if (industry == null || works == null) return;
        industry.dressWorks(c, plan, works);
        WorksDefences.lay(c, plan, works, industry);
    }
}
