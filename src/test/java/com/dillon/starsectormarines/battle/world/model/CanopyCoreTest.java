package com.dillon.starsectormarines.battle.world.model;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A tree stops a round at its trunk and dense core; a round grazing its outer crown flies on. */
class CanopyCoreTest {
    /** A 5x5 crown at the origin: centre (2.5, 2.5). */
    private static final float C = 2.5f, R = CanopyCore.radius(5);

    private static boolean anyCellStops(float x0, float y0, float x1, float y1) {
        for (int y = 0; y < 5; y++) for (int x = 0; x < 5; x++)
            if (CanopyCore.stops(x, y, x0, y0, x1, y1, C, C, R, R)) return true;
        return false;
    }

    @Test
    void aRoundGrazingTheCrownPassesAndOneThroughTheTrunkStops() {
        assertFalse(anyCellStops(-3f, 0.4f, 8f, 0.4f), "along the crown's edge row");
        assertFalse(anyCellStops(-1f, 2f, 2f, -1f), "clipping the crown's corner");
        assertTrue(anyCellStops(-3f, 2.5f, 8f, 2.5f), "straight through the trunk");
        assertTrue(anyCellStops(-3f, -2.9f, 8f, 8.1f), "diagonally through the trunk");
    }

    @Test
    void theRoundStopsInTheCellWhereItReachesTheCore() {
        // Entering from the west on the centre line, the core begins at x = 2.5 - R, inside cell 1.
        assertFalse(CanopyCore.stops(0, 2, -3f, 2.5f, 8f, 2.5f, C, C, R, R));
        assertTrue(CanopyCore.stops(1, 2, -3f, 2.5f, 8f, 2.5f, C, C, R, R));
        assertFalse(CanopyCore.stops(1, 0, -3f, 2.5f, 8f, 2.5f, C, C, R, R), "a cell the round never crosses");
    }

    @Test
    void theGridAsksTheCanopyOnlyOfNaturalSolids() {
        NavigationGrid grid = new NavigationGrid(12, 9);
        for (int y = 0; y < 9; y++) for (int x = 0; x < 12; x++) grid.setWalkableFloor(x, y);
        NavigationGrid crown = new NavigationGrid(12, 9);
        for (int y = 0; y < 9; y++) for (int x = 0; x < 12; x++)
            if (x < 4 || x > 8 || y < 2 || y > 6) crown.setWalkableFloor(x, y);
        // The crown stands on cells 4..8 x 2..6, centre (6.5, 4.5); grid and crown differ only in that solid block.
        for (int y = 2; y <= 6; y++) for (int x = 4; x <= 8; x++)
            crown.setTag(x, y, NavigationGrid.CellTag.NATURAL_SOLID, true);
        assertTrue(grid.hasLineOfFire(0.5f, 2.4f, 11.5f, 2.4f));
        assertFalse(crown.hasLineOfFire(0.5f, 2.4f, 11.5f, 2.4f), "with no canopy rule the whole cell stops it");

        crown.setCanopyFire((x, y, x0, y0, x1, y1) ->
                CanopyCore.stops(x, y, x0, y0, x1, y1, 6.5f, 4.5f, R, R));
        assertTrue(crown.hasLineOfFire(0.5f, 2.4f, 11.5f, 2.4f), "a graze passes");
        assertFalse(crown.hasLineOfFire(0.5f, 4.5f, 11.5f, 4.5f), "the trunk stops it");
        long wall = crown.firstWallOnLine(0.5f, 4.5f, 11.5f, 4.5f);
        assertEquals(5, (int) wall, "the round stops where it meets the core");
        assertEquals(4, (int) (wall >>> 32));

        // A solid cell that is not natural ignores the canopy rule.
        crown.setTag(4, 2, NavigationGrid.CellTag.NATURAL_SOLID, false);
        assertFalse(crown.hasLineOfFire(0.5f, 2.4f, 11.5f, 2.4f));
    }
}
