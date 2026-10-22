package com.dillon.starsectormarines.battle.fire;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FireEscapeTest {

    private static final int W = 20, H = 9;

    /** Open floor; grass in columns 4..8 only, all of it alight. */
    private static FireField burningBand(NavigationGrid grid) {
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) grid.setWalkableFloor(x, y);
        FireField field = new FireField(W, H, 3L, idx -> idx % W >= 4 && idx % W <= 8 ? FireFuel.GRASS.bit : 0);
        for (int y = 0; y < H; y++) for (int x = 4; x <= 8; x++) field.addHeat(x, y, 5000);
        for (int t = 0; t < FireField.STEP_TICKS * 2; t++) field.tick(t);
        for (int y = 0; y < H; y++) for (int x = 4; x <= 8; x++) assertTrue(field.isBurning(x, y), "band alight at " + x + "," + y);
        return field;
    }

    @Test
    void aUnitInTheFireIsSentToTheNearestGroundClearOfIt() {
        NavigationGrid grid = new NavigationGrid(W, H);
        FireField field = burningBand(grid);

        int refuge = FireEscape.refuge(grid, field, 5, 4);
        int x = refuge % W, y = refuge / W;
        assertFalse(FireEffects.refusedAsRefuge(field, x, y), "refuge " + x + "," + y + " is in or beside the fire");
        assertEquals(2, x, "the band's west side is nearer from x=5: bare, and two clear of the burning edge at 4");
    }

    @Test
    void walledOffGroundIsNotARefuge() {
        NavigationGrid grid = new NavigationGrid(W, H);
        FireField field = burningBand(grid);
        for (int y = 0; y < H; y++) grid.setWalkable(3, y, false);

        int refuge = FireEscape.refuge(grid, field, 5, 4);
        assertEquals(10, refuge % W, "with the west closed the refuge is east, two clear of the edge at 8");
    }

    @Test
    void groundAheadOfTheFrontIsNotSafeButBareGroundBeyondItIs() {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) grid.setWalkableFloor(x, y);
        // Grass from 0 to 12, alight only at its west end; bare beyond.
        FireField field = new FireField(W, H, 3L, idx -> idx % W <= 12 ? FireFuel.GRASS.bit : 0);
        for (int y = 0; y < H; y++) field.addHeat(0, y, 5000);
        int t = 0;
        while (!field.isBurning(4, 4) || field.isBurning(6, 4)) {
            field.tick(t++);
            assertTrue(t < 30 * 60, "the front never reached x=4 alone");
        }
        int front = 0;
        while (field.isBurning(front + 1, 4)) front++;

        assertTrue(FireEscape.needsToMove(field, front + 1, 4), "on grass beside the front, the fire will reach him");
        int refuge = FireEscape.refuge(grid, field, front + 1, 4);
        int x = refuge % W;
        assertTrue(x > 12 || field.isBurnt(x, refuge / W), "refuge at " + x + " is unburnt grass the front will overtake");
    }

    @Test
    void aUnitOnBareGroundOutOfTheFireStays() {
        NavigationGrid grid = new NavigationGrid(W, H);
        FireField field = burningBand(grid);
        assertEquals(-1, FireEscape.refuge(grid, field, 9, 4), "bare ground beside the fire is the route costing's, not this");
        assertEquals(-1, FireEscape.refuge(grid, null, 5, 4));
    }
}
