package com.dillon.starsectormarines.battle.drone;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlockFieldTest {

    /** A 21x21 yard with a wall at x = 10, y 4..16, between the members' west side and the anchor's east. */
    private static boolean wallYard(int x, int y) {
        if (x < 0 || y < 0 || x > 20 || y > 20) return false;
        return !(x == 10 && y >= 4 && y <= 16);
    }

    @Test
    void membersEitherSideOfAWallGoRoundItByTheirOwnSide() {
        FlockField field = FlockField.build(15, 10, 12, FlockFieldTest::wallYard);
        float[] north = field.way(7.5f, 14.5f, 15.5f, 10.5f);
        float[] south = field.way(7.5f, 6.5f, 15.5f, 10.5f);
        assertNotNull(north);
        assertNotNull(south);
        assertTrue(north[1] > 0f, "the northern member heads north round the wall: " + north[1]);
        assertTrue(south[1] < 0f, "the southern member heads south round the wall: " + south[1]);
    }

    @Test
    void theRouteGoesRoundTheWallNotThroughIt() {
        FlockField field = FlockField.build(15, 10, 12, FlockFieldTest::wallYard);
        // Straight-line from (5,10) is 10 cells; round the wall's end it is much further.
        assertTrue(field.distance(5, 10) / 10f > 14f, "distance " + field.distance(5, 10));
        assertEquals(FlockField.UNREACHABLE, field.distance(10, 10), "the wall cell has no route");
    }

    @Test
    void aRoofedRoomIsNeverOnARoute() {
        // A roofed room x 4..8, y 4..8 with a door at (6, 9): it is closed to the flock door or no door.
        FlockField.Open open = (x, y) -> x >= 0 && y >= 0 && x <= 20 && y <= 20 && !(x >= 4 && x <= 8 && y >= 4 && y <= 8);
        FlockField field = FlockField.build(15, 15, 12, open);
        for (int y = 4; y <= 8; y++) {
            for (int x = 4; x <= 8; x++) assertEquals(FlockField.UNREACHABLE, field.distance(x, y));
        }
        assertNull(field.way(6.5f, 6.5f, 15.5f, 15.5f), "a point inside has no way on the field");
    }

    @Test
    void noDiagonalStepCutsAWallsCorner() {
        // Two walls touching only at a corner: (5,5) and (6,6) closed; the diagonal (5,6)->(6,5) must not be taken.
        FlockField.Open open = (x, y) -> x >= 0 && y >= 0 && x <= 10 && y <= 10 && !(x == 5 && y == 5) && !(x == 6 && y == 6);
        FlockField field = FlockField.build(6, 5, 5, open);
        assertTrue(field.distance(5, 6) > FlockField.DIAGONAL, "the way round, not the one diagonal through the corner: "
                + field.distance(5, 6));
    }

    @Test
    void anAnchorOverAClosedCellHasNoRoutes() {
        FlockField field = FlockField.build(10, 10, 5, FlockFieldTest::wallYard);
        assertEquals(FlockField.UNREACHABLE, field.distance(12, 10));
    }

    @Test
    void theFieldReachesOnlyItsRadius() {
        FlockField field = FlockField.build(10, 18, 3, (x, y) -> true);
        assertEquals(FlockField.UNREACHABLE, field.distance(10, 10));
        assertEquals(30, field.distance(10, 15));
    }
}
