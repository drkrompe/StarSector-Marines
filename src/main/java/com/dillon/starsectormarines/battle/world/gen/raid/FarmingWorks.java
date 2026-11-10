package com.dillon.starsectormarines.battle.world.gen.raid;

import com.dillon.starsectormarines.battle.world.gen.bsp.GrownTrunkPlan;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomShape;
import com.dillon.starsectormarines.battle.world.gen.fortress.FortressBuilding;
import com.dillon.starsectormarines.battle.world.gen.fortress.FortressProgram;
import com.dillon.starsectormarines.battle.world.gen.fortress.Ward;
import com.dillon.starsectormarines.battle.world.gen.precinct.PrecinctCharacter;
import com.dillon.starsectormarines.battle.world.gen.precinct.RaidSiteTemplate;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;

import java.util.List;
import java.util.Set;

/**
 * Farming's site: a silo per machine, the packing shed the harvest goes through, a stores and one post, among
 * farmsteads. Awaiting its rebuild to the mine's standard (owner, 2026-11-10).
 */
public final class FarmingWorks implements RaidIndustry {

    /** A farm plant's silo: a bin on its pad, square for the same reason as a still. */
    private static final RoomShape SILO = RoomShape.rectangle(7, 7);
    private static final RoomShape PACKING_SHED = RoomShape.rectangle(16, 7);
    /** Yard a farm plant keeps per silo: the threshing floor and the trucks' turning ground. UNTUNED. */
    static final int YARD_PER_SILO = 240;

    @Override public String key() { return "farming"; }

    @Override public Set<String> industryIds() { return Set.of("farming", "aquaculture"); }

    @Override public GrownTrunkPlan.Profile industryGrowth() { return RaidSiteTemplate.SPARSE_GROWTH; }

    @Override public PrecinctCharacter industryCharacter() { return PrecinctCharacter.FARMSTEAD; }

    @Override public FortressProgram works(int silos) {
        if (silos < 1) throw new IllegalArgumentException("a farm plant has at least one silo");
        return new FortressProgram(List.of(
                new FortressBuilding(RoomPurpose.PARTS_CAGE, SILO, Ward.YARD, silos),
                new FortressBuilding(RoomPurpose.PRODUCTION_FLOOR, PACKING_SHED, Ward.REAR, 1),
                new FortressBuilding(RoomPurpose.STOCKROOM, RaidWorksParts.STORES, Ward.REAR, 1),
                RaidWorksParts.guardPosts(1)),
                0, silos * YARD_PER_SILO, true);
    }
}
