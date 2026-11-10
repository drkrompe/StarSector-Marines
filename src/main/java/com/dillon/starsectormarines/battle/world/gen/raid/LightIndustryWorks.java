package com.dillon.starsectormarines.battle.world.gen.raid;

import com.dillon.starsectormarines.battle.world.gen.fit.RoomShape;
import com.dillon.starsectormarines.battle.world.gen.fortress.FortressBuilding;
import com.dillon.starsectormarines.battle.world.gen.fortress.FortressProgram;
import com.dillon.starsectormarines.battle.world.gen.fortress.Ward;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;

import java.util.List;
import java.util.Set;

/**
 * Light industry's site: a shop floor per machine, the loading shed they ship from, a stores and the posts, in a dense
 * industrial quarter. Awaiting its rebuild to the mine's standard (owner, 2026-11-10).
 */
public final class LightIndustryWorks implements RaidIndustry {

    /** A light-industry shop: a long, low production floor. */
    private static final RoomShape SHOP = RoomShape.rectangle(11, 7);
    private static final RoomShape LOADING_SHED = RoomShape.rectangle(14, 6);
    /** Yard a light-industry works keeps per shop: lanes and loading, closer set than a heavy works. UNTUNED. */
    static final int YARD_PER_SHOP = 150;

    @Override public String key() { return "light-industry"; }

    @Override public Set<String> industryIds() { return Set.of("lightindustry"); }

    @Override public FortressProgram works(int shops) {
        if (shops < 1) throw new IllegalArgumentException("light industry has at least one shop");
        return new FortressProgram(List.of(
                new FortressBuilding(RoomPurpose.PARTS_CAGE, SHOP, Ward.YARD, shops),
                new FortressBuilding(RoomPurpose.LOADING_BAY, LOADING_SHED, Ward.REAR, 1),
                new FortressBuilding(RoomPurpose.STOCKROOM, RaidWorksParts.STORES, Ward.REAR, 1),
                RaidWorksParts.guardPosts(2)),
                0, shops * YARD_PER_SHOP, true);
    }
}
