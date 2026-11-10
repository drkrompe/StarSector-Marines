package com.dillon.starsectormarines.battle.world.gen.raid;

import com.dillon.starsectormarines.battle.world.gen.fit.RoomPacker;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomShape;
import com.dillon.starsectormarines.battle.world.gen.fortress.FortressBuilding;
import com.dillon.starsectormarines.battle.world.gen.fortress.Ward;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;

/** Buildings every industry's works may hold alike: its stores and the posts its gates are watched from. */
public final class RaidWorksParts {

    /** A works' stores shed. */
    public static final RoomShape STORES = RoomShape.rectangle(12, 5);
    /** A guard post on a works' gate. */
    public static final RoomShape GUARD_POST = RoomShape.rectangle(4, 4);

    private RaidWorksParts() { }

    /** {@code count} guard posts against the wall the attacker faces, cornered into it as posts are. */
    public static FortressBuilding guardPosts(int count) {
        return new FortressBuilding(RoomPurpose.CONTROL_ROOM, GUARD_POST, Ward.FRONTAGE,
                RoomPacker.EdgeContact.ANY, count);
    }
}
