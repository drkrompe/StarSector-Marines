package com.dillon.starsectormarines.battle.evacuation;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SwarmDefenseRosterTest {

    @Test
    void aRunnerMovedOutOfAnObjectivesYardTakesTheNextCellOutsideTheRing() {
        int width = 100;
        // In the roster's order: inside the ring round (50, 50), outside it, inside, outside.
        List<Integer> cells = List.of(50 * width + 52, 50 * width + 90, 55 * width + 50, 10 * width + 10);
        assertEquals(1, SwarmDefenseRoster.outside(cells, width, 50, 50, 28, 0));
        assertEquals(3, SwarmDefenseRoster.outside(cells, width, 50, 50, 28, 2), "a taken cell is not handed out again");
        assertEquals(-1, SwarmDefenseRoster.outside(cells, width, 50, 50, 28, 4), "none left");
    }
}
