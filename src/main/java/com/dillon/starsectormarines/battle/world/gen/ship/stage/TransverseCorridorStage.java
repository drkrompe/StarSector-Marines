package com.dillon.starsectormarines.battle.world.gen.ship.stage;

import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.GenStage;
import com.dillon.starsectormarines.battle.world.gen.ship.DeckProfile;
import com.dillon.starsectormarines.battle.world.gen.ship.ShipKeys;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;

import java.util.Random;

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
    /** How far a corridor may drift from its evenly divided position, in frames. */
    private static final int SPACING_NUDGE = 4;
    /** Shortest bay the nudge may leave between two corridors. */
    private static final int MIN_BAY = 7;

    @Override
    public void run(GenContext ctx) {
        DeckProfile profile = ctx.get(ShipKeys.DECK_PROFILE);
        if (profile == null) {
            throw new IllegalStateException("TransverseCorridorStage requires a published deck profile");
        }
        int[] frames = corridorFrames(profile.frames(), ctx.rng);
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
     * Interior frames spaced around an even division, then nudged so bays differ
     * in length. Evenly divided bays produce identically sized rooms the whole
     * way down the ship, which reads as an office block; the nudge is bounded so
     * spacing stays structural rather than arbitrary.
     */
    static int[] corridorFrames(int frames, Random rng) {
        int bays = Math.max(2, frames / BAY_TARGET);
        int count = bays - 1;
        int[] result = new int[count];
        int previous = 0;
        for (int k = 0; k < count; k++) {
            int even = (int) ((long) (k + 1) * frames / bays);
            int nudged = even + rng.nextInt(SPACING_NUDGE * 2 + 1) - SPACING_NUDGE;
            int earliest = previous + CORRIDOR_WIDTH + MIN_BAY;
            int latest = frames - 1 - CORRIDOR_WIDTH - (count - k - 1) * (CORRIDOR_WIDTH + MIN_BAY);
            result[k] = Math.max(earliest, Math.min(latest, nudged));
            previous = result[k];
        }
        return result;
    }
}
