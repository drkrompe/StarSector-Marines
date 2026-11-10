package com.dillon.starsectormarines.battle.world.gen.elevation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NaturalChasmTest {

    static final int W = 120, H = 70;

    private static int pieces(boolean[] cut) {
        int[] label = OpenPit.label(W, H, i -> !cut[i]);
        int n = 0;
        for (int v : label) n = Math.max(n, v);
        return n;
    }

    @Test
    void aDropRunsAcrossOpenCountryAndStrandsNothingAndItsCrossingsAreDecked() {
        NaturalChasm.Cut planned = NaturalChasm.plan(W, H, true, .5f, i -> true, i -> true, 5);
        assertNotNull(planned);
        boolean[] cut = planned.drop();
        boolean west = false, east = false;
        // Within a crossing's width of either edge: a crossing may fall at the edge itself.
        for (int y = 0; y < H; y++) for (int x = 0; x <= NaturalChasm.CROSS_WIDTH + 1; x++) {
            west |= cut[y * W + x];
            east |= cut[y * W + W - 1 - x];
        }
        assertTrue(west && east, "edge to edge");
        assertEquals(1, pieces(cut), "the crossings keep the country one piece");
        int decks = 0;
        for (int i = 0; i < cut.length; i++) {
            if (!planned.decks()[i]) continue;
            assertFalse(cut[i]);
            decks++;
        }
        assertTrue(decks >= 3 * NaturalChasm.CROSS_WIDTH, "a deck over each crossing");
    }

    @Test
    void aRoadAcrossItsLineIsLeftStandingAsABridge() {
        // A road the reservations keep, running north-south across the whole map.
        NaturalChasm.Cut planned = NaturalChasm.plan(W, H, true, .5f, i -> Math.abs(i % W - 60) > 2, i -> true, 5);
        assertNotNull(planned);
        boolean[] cut = planned.drop();
        for (int y = 0; y < H; y++) for (int x = 58; x <= 62; x++) {
            assertFalse(cut[y * W + x]);
            assertFalse(planned.decks()[y * W + x], "a road is its own bridge, never decked over");
        }
        assertEquals(1, pieces(cut));
    }

    @Test
    void aRouteAcrossItsLineIsDeckedAtItsLineAndTheDropTakesTheRestOfItsGround() {
        // A route's line north-south at x = 60; its five reserved cells are not kept out of the drop.
        NaturalChasm.Cut planned = NaturalChasm.plan(W, H, true, .5f, i -> true, i -> true, i -> i % W == 60, 5);
        assertNotNull(planned);
        boolean decked = false, cutBeside = false;
        for (int y = 0; y < H; y++) {
            assertFalse(planned.drop()[y * W + 60], "the route's line is never cut");
            decked |= planned.decks()[y * W + 60];
            cutBeside |= planned.drop()[y * W + 57] || planned.drop()[y * W + 63];
        }
        assertTrue(decked, "a deck carries the route over");
        assertTrue(cutBeside, "the drop runs on either side of the deck, through the route's ground");
        assertEquals(1, pieces(planned.drop()));
    }

    @Test
    void aCutThatStrandsMoreThanAPocketIsRefusedAndAPocketIsTakenIn() {
        // A cut walling off the west third of the map strands too much.
        boolean[] wall = new boolean[W * H];
        for (int y = 0; y < H; y++) wall[y * W + 40] = true;
        int[] before = OpenPit.label(W, H, i -> true);
        assertNull(NaturalChasm.proved(W, H, wall, before, i -> true, i -> true));
        // A ring round a 3x3 pocket: the pocket is taken into the drop.
        boolean[] ring = new boolean[W * H];
        for (int y = 9; y <= 15; y++) for (int x = 9; x <= 15; x++) {
            if (x == 9 || x == 15 || y == 9 || y == 15) ring[y * W + x] = true;
        }
        boolean[] proved = NaturalChasm.proved(W, H, ring, before, i -> true, i -> true);
        assertNotNull(proved);
        assertTrue(proved[12 * W + 12]);
        // The same pocket on ground the drop may not take (a building's yard) refuses the cut.
        assertNull(NaturalChasm.proved(W, H, ring, before, i -> i != 12 * W + 12, i -> true));
    }
}
