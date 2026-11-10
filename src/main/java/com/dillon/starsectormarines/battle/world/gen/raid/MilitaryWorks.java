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
 * A military base's site: the motor pool, a motor shed per machine, and the magazine, among depots; the barracks are
 * the garrison beside it.
 */
public final class MilitaryWorks implements RaidIndustry {

    /** A base's motor shed: where its vehicles stand and are worked on. */
    private static final RoomShape MOTOR_SHED = RoomShape.rectangle(14, 9);
    private static final RoomShape MAGAZINE = RoomShape.rectangle(9, 7);
    /** Vehicle park a motor pool keeps per shed. UNTUNED. */
    static final int POOL_PER_SHED = 170;

    @Override public String key() { return "military"; }

    @Override public Set<String> industryIds() { return Set.of("militarybase", "patrolhq", "highcommand"); }

    @Override public float worksFit() { return RaidSiteTemplate.MINE_FIT; }

    @Override public GrownTrunkPlan.Profile industryGrowth() { return RaidSiteTemplate.SPARSE_GROWTH; }

    @Override public PrecinctCharacter industryCharacter() { return PrecinctCharacter.DEPOT; }

    @Override public FortressProgram works(int sheds) {
        if (sheds < 1) throw new IllegalArgumentException("a motor pool has at least one shed");
        return new FortressProgram(List.of(
                new FortressBuilding(RoomPurpose.PARTS_CAGE, MOTOR_SHED, Ward.YARD, sheds),
                new FortressBuilding(RoomPurpose.ARMORY, MAGAZINE, Ward.REAR, 1),
                RaidWorksParts.guardPosts(2)),
                0, sheds * POOL_PER_SHED, true);
    }
}
