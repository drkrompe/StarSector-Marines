package com.dillon.starsectormarines.battle.world.gen;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.tiles.DoodadCover;
import com.dillon.starsectormarines.battle.world.tiles.DoodadDef;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@link PreparedGround#prop}: what a prop of any footprint closes, by its cover, on an open 8x8 field. */
class PreparedGroundPropTest {

    private static GenContext field() {
        NavigationGrid grid = new NavigationGrid(8, 8);
        for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) grid.setWalkableFloor(x, y);
        return new GenContext(grid, new CellTopology(8, 8), new Random(1L), 8, 8, 1L);
    }

    private static DoodadDef def(DoodadCover cover, float halfHeight, int w, int h) {
        return new DoodadDef("test.prop", "sheet", 0, 0, cover, halfHeight, w, h);
    }

    @Test
    void aTallHeavyPropClosesItsTurnedFootprintToMovementAndSight() {
        GenContext ctx = field();
        assertTrue(PreparedGround.prop(ctx, 2, 2, def(DoodadCover.HEAVY, 1.1f, 3, 1), 1));
        for (int y = 2; y <= 4; y++) {
            assertFalse(ctx.grid.isWalkable(2, y));
            assertFalse(ctx.grid.isSeeThrough(2, y));
            assertTrue(ctx.topology.isFixture(2, y));
        }
        assertTrue(ctx.grid.isWalkable(3, 2), "a quarter turn stands the 3x1 up as 1x3");
        Doodad placed = ctx.doodads.get(0);
        assertEquals(1, placed.footprintCellsX);
        assertEquals(3, placed.footprintCellsY);
    }

    @Test
    void aLowPropIsSeenOverAndLightCoverIsSteppedOver() {
        GenContext ctx = field();
        assertTrue(PreparedGround.prop(ctx, 0, 0, def(DoodadCover.MED, 0.4f, 2, 1), 0));
        assertFalse(ctx.grid.isWalkable(0, 0));
        assertTrue(ctx.grid.isSeeThrough(0, 0));
        assertTrue(PreparedGround.prop(ctx, 0, 3, def(DoodadCover.LIGHT, 0.3f, 2, 1), 0));
        assertTrue(ctx.grid.isWalkable(1, 3));
    }

    @Test
    void aCoveringPropGoesDownWholeOrNotAtAll() {
        GenContext ctx = field();
        ctx.grid.setWalkable(4, 1, false);
        assertFalse(PreparedGround.prop(ctx, 2, 1, def(DoodadCover.HEAVY, 1.1f, 3, 1), 0));
        assertTrue(ctx.grid.isWalkable(2, 1), "nothing of a refused prop is left behind");
        assertTrue(ctx.doodads.isEmpty());
    }

    @Test
    void aCoverlessSpanLiesOverWhatIsBeneathIt() {
        GenContext ctx = field();
        assertTrue(PreparedGround.prop(ctx, 0, 5, def(DoodadCover.HEAVY, 1.1f, 1, 3), 0));
        assertTrue(PreparedGround.prop(ctx, 0, 5, def(DoodadCover.NONE, 0f, 7, 3), 0), "a crane span over its own leg");
        assertTrue(ctx.grid.isWalkable(3, 6), "and the ground under it stays open");
        assertFalse(PreparedGround.prop(ctx, 4, 6, def(DoodadCover.NONE, 0f, 7, 3), 0), "but not off the map");
    }
}
