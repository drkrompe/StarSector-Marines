package com.dillon.starsectormarines.battle.world.gen.ship.stage;

import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.ship.DeckGraph;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;

/** A pressure closure retains the doorway's location while denying passage and sight. */
final class SealedShipHatches {
    static final String ART = "ship-sealed-hatches.pressure-hatch";

    private SealedShipHatches() {}

    static void seal(GenContext ctx, DeckGraph.Compartment room) {
        var hatch = TileRegistry.installed().doodad(ART);
        if (hatch == null) throw new IllegalStateException("Missing sealed ship hatch art");
        for (var door : room.doors()) {
            int x = door.x(), y = door.y();
            // Derive the axis from the actual entrance, including mirrored/rotated rooms.
            boolean horizontal = room.contains(x, y - 1) || room.contains(x, y + 1);
            ctx.grid.setDoorway(x, y, false);
            ctx.grid.setWalkable(x, y, false);
            ctx.grid.setSeeThrough(x, y, false);
            ctx.topology.setWall(x, y, true);
            ctx.topology.setSurface(x, y, 0);
            ctx.doodads.add(new Doodad(x, y, hatch, horizontal ? 0 : 1, false));
        }
    }
}
