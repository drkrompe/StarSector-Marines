package com.dillon.starsectormarines.battle.world.gen.ship.stage;

import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.GenStage;
import com.dillon.starsectormarines.battle.world.gen.ship.DeckProfile;
import com.dillon.starsectormarines.battle.world.gen.ship.ShipKeys;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;

/**
 * Step 3 (ship) — carve the athwartships corridors that cross the spine and run
 * out to the hull on both sides.
 *
 * <p>Without these the deck is a comb: one long corridor with rooms hanging off
 * it and no way to cross from port to starboard except back through the spine.
 * The transverse passages divide the length into bays, give the deck its
 * loops, and are what later compartments open onto.
 *
 * <p>Corridor frames are spaced evenly along the deck rather than drawn at
 * random, because their spacing is what sets bay size and therefore how large a
 * compartment can be. Where they land is structure, not decoration.
 */
public final class TransverseCorridorStage implements GenStage {

    /** Walkable width of an athwartships corridor. Narrower than the spine: these are cross-passages, not the main line. */
    public static final int CORRIDOR_WIDTH = 2;
    /** Target frames per bay. Bay length drives how large a compartment can be. */
    private static final int BAY_TARGET = 16;

    @Override
    public void run(GenContext ctx) {
        DeckProfile profile = ctx.get(ShipKeys.DECK_PROFILE);
        if (profile == null) {
            throw new IllegalStateException("TransverseCorridorStage requires a published deck profile");
        }
        int[] frames = corridorFrames(profile.frames());
        CellTopology topology = ctx.topology;
        for (int frame : frames) {
            for (int x = frame; x < frame + CORRIDOR_WIDTH; x++) {
                for (int y = profile.top(x); y <= profile.bottom(x); y++) {
                    ctx.grid.setWalkableFloor(x, y);
                    topology.setGroundKind(x, y, GroundKind.INDOOR);
                    topology.setRoomPurpose(x, y, RoomPurpose.CORRIDOR);
                }
            }
        }
        ctx.put(ShipKeys.CORRIDOR_FRAMES, frames);
    }

    /**
     * Evenly spaced interior frames, never touching either end of the deck so a
     * corridor cannot degenerate into the bow or stern cap.
     */
    static int[] corridorFrames(int frames) {
        int bays = Math.max(2, frames / BAY_TARGET);
        int count = bays - 1;
        int[] result = new int[count];
        for (int k = 0; k < count; k++) {
            result[k] = (int) ((long) (k + 1) * frames / bays);
        }
        return result;
    }
}
