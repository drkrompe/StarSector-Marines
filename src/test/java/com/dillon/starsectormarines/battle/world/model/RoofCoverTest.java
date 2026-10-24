package com.dillon.starsectormarines.battle.world.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How much of a cell a roof covers: inside a building all of it, over a wall that faces the outside only the eave on
 * the building's side, so the wall's outer face stays in view.
 */
class RoofCoverTest {

    private static final float D = RoofCover.EAVE_DEPTH;

    /**
     * A house on a 9x7 map: floor x 2..6, y 2..4 (building 1) inside a wall ring x 1..7, y 1..5, a partition wall
     * down x = 4 and a doorway in the south wall at (3, 1).
     */
    private static CellTopology house() {
        CellTopology t = new CellTopology(9, 7);
        for (int y = 1; y <= 5; y++) for (int x = 1; x <= 7; x++) {
            boolean ring = x == 1 || x == 7 || y == 1 || y == 5;
            if (ring || x == 4) t.setWall(x, y, true);
            else t.setBuildingId(x, y, 1);
        }
        t.setWall(3, 1, false);
        t.setBuildingId(3, 1, 1);
        return t;
    }

    @Test
    void aRoomsFloorIsCoveredWhole() {
        CellTopology t = house();
        assertCovers(t, 3, 3, RoofCover.Piece.FULL, 1f);
    }

    @Test
    void aPartitionBetweenRoomsIsCoveredWholeSoAnInteriorReadsAsOneRoof() {
        CellTopology t = house();
        assertCovers(t, 4, 3, RoofCover.Piece.FULL, 1f);
        assertEquals(1, RoofCover.owner(t, 4, 3));
    }

    @Test
    void anExteriorWallIsCoveredOnlyByTheEaveOnTheBuildingsSide() {
        CellTopology t = house();
        // The north wall: the outside is north of it, so its north part stays bare.
        int cover = assertCovers(t, 3, 5, RoofCover.Piece.EAVE, D);
        assertEquals(0, RoofCover.facing(cover));
        assertTrue(covered(cover, 0.5f, 0.1f), "the eave reaches over the wall from the room below it");
        assertFalse(covered(cover, 0.5f, 0.9f), "the wall's outer face shows");
        // The east wall faces east.
        int east = assertCovers(t, 7, 3, RoofCover.Piece.EAVE, D);
        assertTrue(covered(east, 0.1f, 0.5f));
        assertFalse(covered(east, 0.9f, 0.5f));
    }

    @Test
    void anOutsideCornerIsCoveredOnlyInTheBuildingsCorner() {
        CellTopology t = house();
        // The north-west corner: outside to the north and the west, the room to the south-east.
        int cover = assertCovers(t, 1, 5, RoofCover.Piece.CORNER, D * D);
        assertTrue(covered(cover, 0.9f, 0.1f));
        assertFalse(covered(cover, 0.1f, 0.9f));
        assertFalse(covered(cover, 0.9f, 0.9f));
        assertFalse(covered(cover, 0.1f, 0.1f));
    }

    @Test
    void aWindowStampedAsTheBuildingsFloorTakesTheWallsEave() {
        CellTopology t = house();
        int cover = assertCovers(t, 3, 1, RoofCover.Piece.EAVE, D);
        assertEquals(2, RoofCover.facing(cover), "it faces south, like the wall it is in");
        assertCovers(t, 3, 2, RoofCover.Piece.FULL, 1f);
        assertCovers(t, 2, 1, RoofCover.Piece.EAVE, D);
    }

    @Test
    void anOutsideDoorTakesAShallowEaveUpToItsLeafWhileADoorBetweenRoomsIsRoofedOver() {
        CellTopology t = house();
        // An exterior door in the east wall, and a door through the partition, neither a building cell.
        t.setWall(7, 3, false);
        t.setWall(4, 3, false);
        int door = assertCovers(t, 7, 3, RoofCover.Piece.EAVE, RoofCover.DOOR_EAVE_DEPTH);
        assertEquals(RoofCover.Edge.DOOR, RoofCover.edge(door));
        assertEquals(1, RoofCover.facing(door), "it faces east, out of the doorway");
        assertTrue(covered(door, 0.1f, 0.5f));
        assertFalse(covered(door, 0.5f, 0.5f), "the leaf across the doorway's middle shows");
        assertCovers(t, 4, 3, RoofCover.Piece.FULL, 1f);
        // The floor inside is covered whole, not cut back to an eave, and the walls either side keep theirs.
        assertCovers(t, 6, 3, RoofCover.Piece.FULL, 1f);
        assertCovers(t, 5, 3, RoofCover.Piece.FULL, 1f);
        assertCovers(t, 7, 2, RoofCover.Piece.EAVE, D);
        assertEquals(1, RoofCover.owner(t, 7, 3));
    }

    @Test
    void aSlitWindowTakesAnEaveUpToItsPaneAndTheWallsBesideItKeepTheirs() {
        CellTopology t = house();
        t.setWall(5, 5, false);
        t.setWindow(5, 5, true);
        int window = assertCovers(t, 5, 5, RoofCover.Piece.EAVE, RoofCover.PANE_EAVE_DEPTH);
        assertEquals(RoofCover.Edge.PANE, RoofCover.edge(window));
        assertCovers(t, 5, 4, RoofCover.Piece.FULL, 1f);
        assertCovers(t, 6, 5, RoofCover.Piece.EAVE, D);
    }

    @Test
    void anInsideCornerLeavesOnlyTheSquareTheOutsideTouches() {
        // Floor west and south of a wall cell at (2, 2), walls running north and east, open ground across its
        // north-east corner: the inside corner of an L-shaped building.
        CellTopology t = new CellTopology(5, 5);
        t.setBuildingId(1, 1, 1);
        t.setBuildingId(1, 2, 1);
        t.setBuildingId(2, 1, 1);
        t.setWall(2, 2, true);
        t.setWall(2, 3, true);
        t.setWall(3, 2, true);
        t.setWall(1, 3, true);
        t.setWall(3, 1, true);
        t.setWall(1, 0, true);
        t.setWall(0, 1, true);
        t.setWall(2, 0, true);
        t.setWall(0, 2, true);
        t.setWall(0, 0, true);
        int cover = assertCovers(t, 2, 2, RoofCover.Piece.INNER, 1f - (1f - D) * (1f - D));
        assertFalse(covered(cover, 0.9f, 0.9f));
        assertTrue(covered(cover, 0.1f, 0.9f));
        assertTrue(covered(cover, 0.9f, 0.1f));
    }

    @Test
    void openGroundAndAWallNoBuildingTouchesHaveNoRoof() {
        CellTopology t = house();
        t.setWall(0, 6, true);
        assertCovers(t, 8, 6, RoofCover.Piece.NONE, 0f);
        assertCovers(t, 0, 6, RoofCover.Piece.NONE, 0f);
        assertFalse(RoofCover.isRoofed(t, 0, 6));
    }

    private static int assertCovers(CellTopology t, int x, int y, RoofCover.Piece piece, float area) {
        int cover = RoofCover.of(t, x, y);
        assertEquals(piece, RoofCover.piece(cover), "piece at " + x + "," + y);
        float sum = 0f;
        for (float[] r : RoofCover.rects(cover)) sum += (r[2] - r[0]) * (r[3] - r[1]);
        assertEquals(area, sum, 1e-5f, "area covered at " + x + "," + y);
        return cover;
    }

    private static boolean covered(int cover, float px, float py) {
        for (float[] r : RoofCover.rects(cover)) {
            if (px >= r[0] && px <= r[2] && py >= r[1] && py <= r[3]) return true;
        }
        return false;
    }
}
