package com.dillon.starsectormarines.battle.world.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * A wall nobody stamped still gets a face.
 *
 * <p>The defect this closes was invisible in the most literal way. A wall whose
 * mask is zero resolves to its block's centre cell, which is transparent, so it
 * draws nothing — right for a building, whose interior walls sit under a roof,
 * and wrong for a ship, whose bulkheads are one cell thick between two open
 * spaces and are looked straight down at. Every wall on every generated deck
 * rendered as empty air, and a room asking for its own bulkhead changed the art
 * of something that was not being drawn.
 */
class UnclaimedWallFacesTest {

    /** Wall down the middle, open deck either side — a bulkhead. */
    private static CellTopology bulkhead() {
        CellTopology topology = new CellTopology(5, 5);
        for (int y = 1; y <= 3; y++) topology.setWall(2, y, true);
        return topology;
    }

    /**
     * A bulkhead shows a face to both of the open sides it separates.
     *
     * <p>Which is what makes it draw as a wall rather than as the block's
     * hollow middle. A vertical run takes its block's east and west edges.
     */
    @Test
    void aBulkheadFacesBothWaysBecauseBothSidesAreOpen() {
        CellTopology topology = bulkhead();
        WallMasks.stampUnclaimed(topology);

        int mask = topology.getWallDirMask(2, 2);
        assertNotEquals(0, mask, "the middle of the run still draws as nothing");
        assertEquals(CellTopology.WALL_DIR_E, mask & CellTopology.WALL_DIR_E,
                "no face toward the deck on the east");
        assertEquals(CellTopology.WALL_DIR_W, mask & CellTopology.WALL_DIR_W,
                "no face toward the deck on the west");
        assertEquals(0, mask & CellTopology.WALL_DIR_N,
                "a face was shown to the wall above it");
        assertEquals(0, mask & CellTopology.WALL_DIR_S,
                "a face was shown to the wall below it");
    }

    /**
     * The end of a run caps off.
     *
     * <p>Three sides open, so three faces — which is the corner and end-cap
     * behaviour the 3x3 block exists to provide.
     */
    @Test
    void theEndOfARunShowsAFaceToTheOpenBeyondIt() {
        CellTopology topology = bulkhead();
        WallMasks.stampUnclaimed(topology);

        int mask = topology.getWallDirMask(2, 3);
        assertEquals(CellTopology.WALL_DIR_N, mask & CellTopology.WALL_DIR_N,
                "the end of the run did not cap toward the open deck past it");
        assertEquals(0, mask & CellTopology.WALL_DIR_S,
                "the end capped toward the rest of its own run");
    }

    /**
     * A mask somebody already set is left exactly alone.
     *
     * <p>A building stamper has said which faces of its walls are exterior, and
     * that is a fact about the building rather than about whatever happens to
     * abut it. Re-deriving it from neighbours would replace a considered answer
     * with a guess, and would change the look of every city that ships.
     */
    @Test
    void aWallSomebodyAlreadyStampedIsNotSecondGuessed() {
        CellTopology topology = bulkhead();
        topology.orWallDirMask(2, 2, CellTopology.WALL_DIR_N);

        WallMasks.stampUnclaimed(topology);

        assertEquals(CellTopology.WALL_DIR_N, topology.getWallDirMask(2, 2),
                "an already-stamped wall was re-derived from its neighbours");
    }

    /**
     * The edge of the grid is not something to face.
     *
     * <p>Off the map is not open deck, so a bulkhead running along the boundary
     * does not grow a cap pointing at nothing.
     */
    @Test
    void theEdgeOfTheMapIsNotAnOpenSide() {
        CellTopology topology = new CellTopology(3, 3);
        for (int y = 0; y < 3; y++) topology.setWall(0, y, true);

        WallMasks.stampUnclaimed(topology);

        assertEquals(0, topology.getWallDirMask(0, 1) & CellTopology.WALL_DIR_W,
                "a wall on the grid boundary capped toward off-map");
        assertEquals(CellTopology.WALL_DIR_E,
                topology.getWallDirMask(0, 1) & CellTopology.WALL_DIR_E,
                "it did not face the deck beside it");
    }
}
