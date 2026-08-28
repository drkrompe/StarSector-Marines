package com.dillon.starsectormarines.battle.world.gen.fit;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The loop chooser, asked on hand-drawn networks rather than on generated ships.
 *
 * <p>Every question here is about the same mechanism: given circulation that
 * already exists and deck that could be cut, where is a short cut worth making?
 * A hairpin — two long corridors joined only at one end — is the smallest shape
 * that has an answer, and it is enough to pin every rule the pass has.
 */
class CirculationLoopsTest {

    private static final int WIDTH = 14;
    private static final int HEIGHT = 9;
    /** Far enough to matter, cheap enough not to fight the fixture's arithmetic. */
    private static final CirculationLoops.Policy POLICY =
            new CirculationLoops.Policy(20, 4, 8);

    /**
     * A hairpin: corridors along the top and bottom, joined only at the left.
     * Walking from one far end to the other is the whole length twice over.
     */
    private static boolean[][] hairpin() {
        boolean[][] circulation = new boolean[WIDTH][HEIGHT];
        for (int x = 1; x <= 12; x++) {
            circulation[x][1] = true;
            circulation[x][7] = true;
        }
        for (int y = 1; y <= 7; y++) circulation[1][y] = true;
        return circulation;
    }

    /** Open deck everywhere inside the border that circulation does not already hold. */
    private static int[][] openDeck(boolean[][] circulation) {
        int[][] routable = new int[WIDTH][HEIGHT];
        for (int x = 0; x < WIDTH; x++) {
            for (int y = 0; y < HEIGHT; y++) {
                boolean border = x == 0 || y == 0 || x == WIDTH - 1 || y == HEIGHT - 1;
                routable[x][y] = border || circulation[x][y] ? -1 : 1;
            }
        }
        return routable;
    }

    /** A passage crossing structure may emerge into open deck or into circulation. */
    private static boolean[][] throughable(boolean[][] circulation, int[][] routable) {
        boolean[][] throughable = new boolean[WIDTH][HEIGHT];
        for (int x = 0; x < WIDTH; x++) {
            for (int y = 0; y < HEIGHT; y++) {
                throughable[x][y] = circulation[x][y] || routable[x][y] == 1;
            }
        }
        return throughable;
    }

    @Test
    void cutsTheHairpinWhereTheWalkIsLongest() {
        boolean[][] circulation = hairpin();
        int[][] routable = openDeck(circulation);
        CirculationLoops.Link link = CirculationLoops.best(
                routable, circulation, throughable(circulation, routable), POLICY);

        assertNotNull(link, "a hairpin is exactly what a loop is for");
        // The far end is where the detour costs most, so that is where the cut
        // belongs. Anywhere nearer the join saves less for the same five cells.
        for (int[] cell : link.route()) {
            assertEquals(12, cell[0], "the cut should stand at the open end");
        }
        assertEquals(5, link.route().size(), "straight across the gap, no wandering");
        // Twenty-eight cells round the hairpin against six through the cut.
        assertEquals(22, link.saving());
    }

    @Test
    void refusesALoopThatSavesNothing() {
        // The same two corridors, but only three cells long, so going round is
        // barely further than cutting across.
        boolean[][] circulation = new boolean[WIDTH][HEIGHT];
        for (int x = 1; x <= 3; x++) {
            circulation[x][1] = true;
            circulation[x][7] = true;
        }
        for (int y = 1; y <= 7; y++) circulation[1][y] = true;
        int[][] routable = openDeck(circulation);

        assertNull(CirculationLoops.best(routable, circulation,
                throughable(circulation, routable), POLICY),
                "a loop nobody would walk is a hole in a bulkhead for nothing");
    }

    @Test
    void willNotRunAlongStructureItMayOnlyCross() {
        boolean[][] circulation = hairpin();
        int[][] routable = openDeck(circulation);
        // Fill the gap with structure. Crossing it is allowed; what is not is
        // entering a cell whose far side is more of the same, which is a wall
        // being followed rather than a door being cut.
        for (int x = 1; x <= 12; x++) {
            for (int y = 2; y <= 6; y++) routable[x][y] = 8;
        }

        assertNull(CirculationLoops.best(routable, circulation,
                throughable(circulation, routable), POLICY),
                "five cells of solid structure is a bulkhead, not a doorway");
    }

    @Test
    void crossesASingleBulkheadBetweenTwoPassages() {
        // Two passages one cell apart, joined only round the left-hand end, with
        // nothing but the bulkhead between them anywhere else.
        boolean[][] circulation = new boolean[WIDTH][HEIGHT];
        for (int x = 1; x <= 12; x++) {
            circulation[x][3] = true;
            circulation[x][5] = true;
        }
        for (int y = 3; y <= 5; y++) circulation[1][y] = true;
        int[][] routable = new int[WIDTH][HEIGHT];
        for (int[] column : routable) Arrays.fill(column, -1);
        for (int x = 2; x <= 12; x++) routable[x][4] = 8;

        CirculationLoops.Link link = CirculationLoops.best(
                routable, circulation, throughable(circulation, routable), POLICY);
        assertNotNull(link, "a hatch through one bulkhead is a door");
        assertEquals(1, link.route().size(), "a door is a door-sized hole");
        assertEquals(12, link.route().get(0)[0], "at the far end, where the walk is worst");
        assertEquals(4, link.route().get(0)[1], "on the bulkhead itself");
        assertTrue(link.cost() >= 8, "structure is paid for, not walked through");
    }
}
