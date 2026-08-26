package com.dillon.starsectormarines.battle.world.gen.ship.stage;

import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.GenStage;
import com.dillon.starsectormarines.battle.world.gen.ship.DeckProfile;
import com.dillon.starsectormarines.battle.world.gen.ship.ShipKeys;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;

/**
 * Step 2 (ship) — carve the fore-aft spine, the deck's primary circulation
 * corridor and its main line of advance.
 *
 * <p>The spine is a fixed-width walkable channel running the full length of the
 * deck. It is not whichever corridor turns out longest; it is authored first and
 * everything else hangs off it. The rows immediately outside it stay solid, so
 * the corridor is enclosed by bulkhead and later compartments must open onto it
 * through carved doors rather than merging with it.
 */
public final class SpineStage implements GenStage {

    @Override
    public void run(GenContext ctx) {
        DeckProfile profile = ctx.get(ShipKeys.DECK_PROFILE);
        if (profile == null) {
            throw new IllegalStateException("SpineStage requires a published deck profile");
        }
        CellTopology topology = ctx.topology;
        for (int x = 0; x < profile.frames(); x++) {
            for (int y = profile.spineTop(); y <= profile.spineBottom(); y++) {
                ctx.grid.setWalkableFloor(x, y);
                topology.setGroundKind(x, y, GroundKind.INDOOR);
                topology.setRoomPurpose(x, y, RoomPurpose.CORRIDOR);
            }
        }
    }
}
