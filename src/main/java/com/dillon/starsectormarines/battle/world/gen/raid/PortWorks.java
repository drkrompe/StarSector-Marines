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
 * A spaceport's site: the cargo terminal, a bonded shed per machine round the apron the lighters land on, the tower
 * that talks them down, the fuel yard and the posts, in a dense industrial quarter. Awaiting its rebuild to the mine's
 * standard (owner, 2026-11-10).
 */
public final class PortWorks implements RaidIndustry {

    /** A cargo terminal's shed: a bonded warehouse the lighters unload into. */
    private static final RoomShape CARGO_SHED = RoomShape.rectangle(14, 8);
    private static final RoomShape TOWER = RoomShape.rectangle(6, 6);
    private static final RoomShape FUEL_YARD = RoomShape.rectangle(8, 6);
    /** Apron a cargo terminal keeps per shed, for the lighters standing at it. UNTUNED. */
    static final int APRON_PER_SHED = 360;

    @Override public String key() { return "spaceport"; }

    @Override public Set<String> industryIds() { return Set.of("spaceport", "megaport"); }

    @Override public float worksFit() { return RaidSiteTemplate.MINE_FIT; }

    @Override public FortressProgram works(int sheds) {
        if (sheds < 1) throw new IllegalArgumentException("a cargo terminal has at least one shed");
        return new FortressProgram(List.of(
                new FortressBuilding(RoomPurpose.PARTS_CAGE, CARGO_SHED, Ward.YARD, sheds),
                new FortressBuilding(RoomPurpose.CONTROL_ROOM, TOWER, Ward.REAR, 1),
                new FortressBuilding(RoomPurpose.ENGINE_ROOM, FUEL_YARD, Ward.REAR, 1),
                RaidWorksParts.guardPosts(2)),
                0, sheds * APRON_PER_SHED, true);
    }
}
