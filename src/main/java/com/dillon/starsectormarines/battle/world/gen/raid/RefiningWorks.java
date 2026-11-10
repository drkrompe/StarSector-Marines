package com.dillon.starsectormarines.battle.world.gen.raid;

import com.dillon.starsectormarines.battle.world.gen.fit.RoomShape;
import com.dillon.starsectormarines.battle.world.gen.fortress.FortressBuilding;
import com.dillon.starsectormarines.battle.world.gen.fortress.FortressProgram;
import com.dillon.starsectormarines.battle.world.gen.fortress.Ward;
import com.dillon.starsectormarines.battle.world.gen.precinct.RaidSiteTemplate;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;

import java.util.List;
import java.util.Set;

/**
 * Refining's site: a still per machine, the pump house that moves the stock between them, the control house that runs
 * them, and the posts at its gates, in a dense industrial quarter. Awaiting its rebuild to the mine's standard (owner,
 * 2026-11-10).
 */
public final class RefiningWorks implements RaidIndustry {

    /**
     * A refinery's still: a cracking column on its plinth. Square: cut corners let two columns touch diagonally,
     * which the detached packing does not count as a shared wall.
     */
    private static final RoomShape STILL = RoomShape.rectangle(10, 10);
    private static final RoomShape PUMP_HOUSE = RoomShape.rectangle(8, 6);
    private static final RoomShape CONTROL_HOUSE = RoomShape.rectangle(6, 5);
    /** Ground a refinery keeps per still: pipe runs and the fire gaps between columns. UNTUNED. */
    static final int YARD_PER_STILL = 320;

    @Override public String key() { return "refining"; }

    @Override public Set<String> industryIds() { return Set.of("refining", "fuelprod"); }

    @Override public float worksFit() { return RaidSiteTemplate.MINE_FIT; }

    @Override public FortressProgram works(int stills) {
        if (stills < 1) throw new IllegalArgumentException("a refinery has at least one still");
        return new FortressProgram(List.of(
                new FortressBuilding(RoomPurpose.PARTS_CAGE, STILL, Ward.YARD, stills),
                new FortressBuilding(RoomPurpose.ENGINE_ROOM, PUMP_HOUSE, Ward.REAR, 1),
                new FortressBuilding(RoomPurpose.CONTROL_ROOM, CONTROL_HOUSE, Ward.REAR, 1),
                RaidWorksParts.guardPosts(2)),
                0, stills * YARD_PER_STILL, true);
    }
}
