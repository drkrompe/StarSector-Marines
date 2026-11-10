package com.dillon.starsectormarines.battle.world.gen.raid;

import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.bsp.GrownTrunkPlan;
import com.dillon.starsectormarines.battle.world.gen.fortress.FortressProgram;
import com.dillon.starsectormarines.battle.world.gen.layout.Side;
import com.dillon.starsectormarines.battle.world.gen.precinct.Precinct;
import com.dillon.starsectormarines.battle.world.gen.precinct.PrecinctCharacter;
import com.dillon.starsectormarines.battle.world.gen.precinct.PrecinctPlan;
import com.dillon.starsectormarines.battle.world.gen.precinct.RaidSiteTemplate;

import java.util.Set;

/**
 * Farming's site: the planet's central grain elevator and processing hub ({@link GrainHub}), among farmsteads, with
 * its fields on the open country and its farm roads in off the map's edges. A fish-farming world plays the same hub;
 * its ponds stand among the fields.
 */
public final class FarmingWorks implements RaidIndustry {

    @Override public String key() { return "farming"; }

    @Override public Set<String> industryIds() { return Set.of("farming", "aquaculture"); }

    /** The hub stands apart on a planet's hauler yards, as a mine's head works does. */
    @Override public float worksFit() { return RaidSiteTemplate.MINE_FIT; }

    @Override public GrownTrunkPlan.Profile industryGrowth() { return RaidSiteTemplate.SPARSE_GROWTH; }

    @Override public PrecinctCharacter industryCharacter() { return PrecinctCharacter.FARMSTEAD; }

    @Override public FortressProgram works(int silos) { return GrainHub.program(silos); }

    /**
     * The hub dressed as its process: the farm roads come in on the works' outer flank (the map edge the works
     * stands nearer) and its deep end (the edge away from the landing).
     */
    @Override public void dressWorks(GenContext c, PrecinctPlan plan, Precinct works) {
        boolean fromWest = plan.attackerFrom() == null || plan.attackerFrom().x0() + plan.attackerFrom().x1() < 1f;
        Side deep = fromWest ? Side.EAST : Side.WEST;
        Side outer = works.seedY() >= c.height / 2 ? Side.NORTH : Side.SOUTH;
        GrainHub.dress(c, plan, works, outer, deep);
    }
}
