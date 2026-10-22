package com.dillon.starsectormarines.battle.evacuation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ColonyRoadblocksTest {

    /**
     * A street running north-south: asphalt on x 10..14, a sidewalk each side
     * (x 9 and x 15), buildings beyond. A side street joins from the east on
     * y 28..30. North of y 50 the east side is an open yard.
     */
    private static final RedoubtWorks.Roadway ROAD = (x, y) ->
            x >= 10 && x <= 14 && y >= 0 && y < 60 || x >= 15 && x <= 30 && y >= 28 && y <= 30;

    private static boolean building(int x, int y) {
        if (ROAD.at(x, y) || x == 9 || x == 15 && !(y >= 27 && y <= 31)) return false;
        if (x >= 16 && (y >= 27 && y <= 31)) return false;
        if (x >= 16 && y > 50) return false;
        return x <= 8 || x >= 16;
    }

    private static ColonyRoadblocks.Ground ground(int holeX, int holeY) {
        return new ColonyRoadblocks.Ground() {
            @Override public boolean inBounds(int x, int y) { return x >= 0 && y >= 0 && x < 60 && y < 60; }

            @Override public boolean walkable(int x, int y) { return !building(x, y); }

            @Override public boolean frontage(int x, int y) { return building(x, y); }

            @Override public boolean open(int x, int y) { return !building(x, y) && !(x == holeX && y == holeY); }
        };
    }

    @Test
    void theLineRunsFromFrontageToFrontageAndRefusesOpenGround() {
        assertArrayEquals(new int[]{-2, 2}, ColonyRoadblocks.asphaltSpan(ROAD, 12, 10, 1, 0));
        // Sidewalks included: x 9..15.
        assertArrayEquals(new int[]{-3, 3}, ColonyRoadblocks.frontageSpan(ground(-1, -1), 12, 10, 1, 0));
        // Beside an open yard nothing closes the street's side.
        assertNull(ColonyRoadblocks.frontageSpan(ground(-1, -1), 12, 55, 1, 0));
    }

    @Test
    void aJunctionWithinReachIsNotAStraightStreet() {
        assertTrue(ColonyRoadblocks.straight(ROAD, 12, 15, 0, 1, -5, 6));
        // The side street opens two cells past the reach of a line at y 22.
        assertFalse(ColonyRoadblocks.straight(ROAD, 12, 22, 0, 1, -5, 6));
    }

    @Test
    void obstaclesFaceTheThreatWithBagsAndGunBehindAndTheWalkLeftOpen() {
        RedoubtWorks.Keep walk = (x, y) -> x != 12;
        ColonyRoadblocks.Plan plan = ColonyRoadblocks.layout(ground(-1, -1), walk, 12, 10, 0, 1,
                new int[]{-3, 3}, new int[]{-2, 2}, null, 4, 0L);
        assertNotNull(plan);
        assertEquals(1, plan.gap());
        assertEquals(6, plan.obstacles().size());
        for (RedoubtWorks.Piece p : plan.obstacles()) {
            assertEquals(10, p.y());
            assertTrue(p.x() != 12);
        }
        // The gun takes the course's east end; the lane takes x 12.
        assertEquals(5, plan.bags().size());
        // Inward is north (+y), so the bags face south toward the threat.
        for (RedoubtWorks.Bag b : plan.bags()) {
            assertEquals(11, b.y());
            assertEquals("doodad.fort-sandbag-s", b.doodad());
        }
        // On the course's line, so it fires out past the riflemen; its light a row behind.
        assertEquals(15, plan.gunX());
        assertEquals(11, plan.gunY());
        assertEquals(15, plan.lightX());
        assertEquals(12, plan.lightY());
    }

    @Test
    void aHoleInTheLineRefusesTheSite() {
        assertNull(ColonyRoadblocks.layout(ground(9, 10), (x, y) -> true, 12, 10, 0, 1,
                new int[]{-3, 3}, new int[]{-2, 2}, null, 4, 0L));
    }

    @Test
    void aTruckLiesAcrossOneLaneWithItsBedTowardTheThreat() {
        ColonyRoadblocks.Plan plan = ColonyRoadblocks.layout(ground(-1, -1), (x, y) -> true, 12, 10, 0, 1,
                new int[]{-3, 3}, new int[]{-2, 2}, "doodad.parked-box-van", 4, 0L);
        assertNotNull(plan);
        RedoubtWorks.Piece truck = plan.obstacles().get(0);
        assertEquals("doodad.parked-box-van", truck.doodad());
        assertEquals(10, truck.x());
        assertEquals(9, truck.y());
        assertEquals(3, truck.width());
        assertEquals(2, truck.height());
        assertEquals(5, plan.obstacles().size());
    }
}
