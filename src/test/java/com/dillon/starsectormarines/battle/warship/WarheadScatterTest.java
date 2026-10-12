package com.dillon.starsectormarines.battle.warship;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** How wide a sheaf of warheads is. */
class WarheadScatterTest {

    @Test
    void theSpreadGrowsWithTheNumberAndSizeOfTheWarheads() {
        assertTrue(WarheadScatter.spreadRadius(7, 3.4f) > WarheadScatter.spreadRadius(3, 3.4f));
        assertTrue(WarheadScatter.spreadRadius(7, 4.5f) > WarheadScatter.spreadRadius(7, 3.4f));
    }
}
