package com.dillon.starsectormarines.battle.world.gen.ship.stage;

import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.GenStage;
import com.dillon.starsectormarines.battle.world.gen.ship.BayAperture;
import com.dillon.starsectormarines.battle.world.gen.ship.DeckGraph;
import com.dillon.starsectormarines.battle.world.gen.ship.ShipKeys;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;

/**
 * Opens the ship's published pressure apertures after structural finalization.
 * Vacuum is sight-clear but never infantry floor. Running last keeps the shared
 * wall seeding pass from turning this opening back into a structural wall.
 */
public final class BayApertureStage implements GenStage {
    @Override
    public void run(GenContext ctx) {
        DeckGraph deck = ctx.get(ShipKeys.DECK_GRAPH);
        if (deck == null) throw new IllegalStateException("Bay apertures require a deck graph");
        for (BayAperture door : deck.apertures()) open(ctx, door);
    }

    private static void open(GenContext ctx, BayAperture door) {
        boolean vertical = door.outDx() != 0;
        float across = vertical ? door.centerY() : door.centerX();
        int start = Math.round(across - door.widthCells() * 0.5f);
        int fixed = (int) Math.floor(vertical ? door.centerX() : door.centerY());
        for (int i = 0; i < door.widthCells(); i++) {
            int x = vertical ? fixed : start + i;
            int y = vertical ? start + i : fixed;
            for (; ctx.grid.inBounds(x, y); x += door.outDx(), y += door.outDy()) {
                ctx.grid.setWalkable(x, y, false);
                ctx.grid.setSeeThrough(x, y, true);
                ctx.grid.setWallHp(x, y, 0);
                ctx.topology.setWall(x, y, false);
                ctx.topology.setWallDirMask(x, y, 0);
                ctx.topology.setGroundKind(x, y, GroundKind.VOID);
                ctx.topology.setSurface(x, y, 0);
            }
        }
        // Cover on the inner threshold and beside both jambs must see the cut.
        for (int i = -1; i <= door.widthCells(); i++) {
            int x = vertical ? fixed : start + i;
            int y = vertical ? start + i : fixed;
            for (int step = -1; ctx.grid.inBounds(x + door.outDx() * step,
                    y + door.outDy() * step); step++) {
                ctx.grid.recomputeCoverAt(x + door.outDx() * step, y + door.outDy() * step);
            }
        }
    }
}
