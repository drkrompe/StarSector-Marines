package com.dillon.starsectormarines.battle.world.gen.fortress;

import com.dillon.starsectormarines.battle.world.gen.fit.RoomPacker;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomShape;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;

import java.util.List;

/**
 * The works programs of the Raid sites after heavy industry's ({@code raid-mission.md}, Site templates): what each
 * industry's compound holds. Heavy industry's is {@link FortressProgram#works}. Each is {@link FortressProgram#detached()
 * detached}, so every machine stands as a building of its own, and its machines are its
 * {@link RoomPurpose#PARTS_CAGE parts cages}, which is how the raid finds them.
 */
public final class RaidWorksPrograms {

    /** A mine's winding house: the headframe and its hoist over a shaft, the floor one charge wrecks. */
    private static final RoomShape WINDING_HOUSE = RoomShape.rectangle(9, 9);
    /** The ore mill the shafts feed: one long crushing and sorting hall. */
    private static final RoomShape ORE_MILL = RoomShape.rectangle(16, 8);
    private static final RoomShape STORES = RoomShape.rectangle(12, 5);
    private static final RoomShape GUARD_POST = RoomShape.rectangle(4, 4);

    /**
     * Ore yard a mine keeps per shaft, on top of its packing slack: the stockpiles and haul ground each headframe
     * stands apart on, more than a works' yard because ore is heaped where it comes up. UNTUNED.
     */
    static final int MINE_YARD_PER_SHAFT = 260;

    private RaidWorksPrograms() { }

    /**
     * A mine's head works: one winding house per shaft, which are the machines a raid wrecks; the ore mill they
     * feed; a stores; and the posts its gates are watched from. No keep and no airfield: the garrison beside it
     * holds the market.
     *
     * @param shafts winding houses in the head works, one per machine
     */
    public static FortressProgram mine(int shafts) {
        if (shafts < 1) throw new IllegalArgumentException("a mine has at least one shaft");
        return new FortressProgram(List.of(
                new FortressBuilding(RoomPurpose.PARTS_CAGE, WINDING_HOUSE, Ward.YARD, shafts),
                new FortressBuilding(RoomPurpose.ENGINE_ROOM, ORE_MILL, Ward.REAR, 1),
                new FortressBuilding(RoomPurpose.STOCKROOM, STORES, Ward.REAR, 1),
                new FortressBuilding(RoomPurpose.CONTROL_ROOM, GUARD_POST, Ward.FRONTAGE,
                        RoomPacker.EdgeContact.ANY, 2)),
                0, shafts * MINE_YARD_PER_SHAFT, true);
    }
}
