package com.dillon.starsectormarines.battle.world.gen.ship.stage;

import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.ship.DeckGraph;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;

/**
 * A pressure closure retains the doorway's location while denying passage and sight.
 *
 * <p>Nothing is placed on the doorway: its compartment door is drawn shut and
 * locked wherever the grid refuses passage through a doorway
 * ({@code RoomDoorRenderSystem}).
 */
final class SealedShipHatches {
    private SealedShipHatches() {}

    static void seal(GenContext ctx, DeckGraph.Compartment room) {
        for (var door : room.doors()) {
            int x = door.x(), y = door.y();
            ctx.grid.setDoorway(x, y, true);
            ctx.grid.setWalkable(x, y, false);
            ctx.grid.setSeeThrough(x, y, false);
            ctx.topology.setWall(x, y, true);
            ctx.topology.setSurface(x, y, 0);
        }
    }

    /** Whether this doorway is one {@link #seal} closed. */
    static boolean sealed(GenContext ctx, int x, int y) {
        return ctx.grid.isDoorway(x, y) && !ctx.grid.isWalkable(x, y);
    }

    /**
     * Finalize seeds structural HP/cover on these blocked cells. Their picture is
     * still a threshold, not a full-cell wall: keep deck beneath the locked door.
     * Run before ship material/trim so adjacent bulkheads receive ordinary jambs.
     * Navigation, opacity and structural HP remain untouched.
     */
    static void revealThresholds(GenContext ctx) {
        for (int y = 0; y < ctx.grid.getHeight(); y++) {
            for (int x = 0; x < ctx.grid.getWidth(); x++) {
                if (!sealed(ctx, x, y)) continue;
                ctx.topology.setWall(x, y, false);
                ctx.topology.setWallDirMask(x, y, 0);
                ctx.topology.setGroundKind(x, y, GroundKind.INDOOR);
                ctx.topology.setSurface(x, y, ctx.topology.surfaceIndex("ship-floor-trim.passage"));
            }
        }
    }
}
