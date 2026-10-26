package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.nav.Direction;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FortifiedWorksTest {

    @Test
    void aWorksWallTakesMoreBruteBlowsThanAHouseWallOfTheSameHitPoints() {
        NavigationGrid grid = new NavigationGrid(8, 3);
        grid.setWallHp(1, 1, SwarmBruteBehavior.LIGHT_WALL_HP);
        grid.setWallHp(6, 1, SwarmBruteBehavior.LIGHT_WALL_HP);
        FortifiedWorks.mark(grid, 5, 0, 7, 2);

        int house = blowsToBreak(grid, 1, 1);
        int works = blowsToBreak(grid, 6, 1);

        assertTrue(works > house, "works took " + works + " blows, a house wall " + house);
    }

    @Test
    void aWorksWallKeepsItsHitPointsSoTheBruteStillChoosesIt() {
        NavigationGrid grid = new NavigationGrid(3, 3);
        grid.setWallHp(1, 1, SwarmBruteBehavior.LIGHT_WALL_HP);
        FortifiedWorks.mark(grid, 0, 0, 2, 2);
        assertEquals(SwarmBruteBehavior.LIGHT_WALL_HP, grid.getWallHp(1, 1));
    }

    @Test
    void anEdgeOntoWorksGroundIsWorks() {
        NavigationGrid grid = new NavigationGrid(4, 1);
        FortifiedWorks.mark(grid, 2, 0, 3, 0);
        assertTrue(FortifiedWorks.isWorks(grid, 1, 0, Direction.E));
        assertFalse(FortifiedWorks.isWorks(grid, 1, 0, Direction.W));
        assertFalse(FortifiedWorks.isWorks(grid, 1, 0, null));
    }

    private static int blowsToBreak(NavigationGrid grid, int x, int y) {
        int blows = 0;
        while (!grid.isWalkable(x, y)) {
            grid.damageCell(x, y, FortifiedWorks.swarmDamage(grid, x, y, null, SwarmBruteBehavior.WALL_BLOW));
            blows++;
        }
        return blows;
    }
}
