package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.unit.UnitType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MechMeleeStandoffTest {

    @Test
    void aMechStepsStraightBackFromTheChargeToOpenGroundOrNotAtAll() {
        NavigationGrid grid = new NavigationGrid(20, 20);
        for (int y = 0; y < 20; y++) for (int x = 0; x < 20; x++) grid.setWalkable(x, y, true);
        assertArrayEquals(new int[]{10, 6}, MechReflexes.backStep(grid, 10, 10, 0f, -1f), "four cells straight back");
        for (int y = 0; y < 20; y++) for (int x = 0; x < 20; x++) if (y < 9) grid.setWalkable(x, y, false);
        assertNull(MechReflexes.backStep(grid, 10, 10, 0f, -1f), "a wall behind: no step, the plan step goes on");
    }

    @Test
    void onlyTheRavThatFightInClawsReachAreKeptAtArmsLength() {
        assertTrue(MechReflexes.meleeRav(UnitType.SWARM_BRUTE));
        assertTrue(MechReflexes.meleeRav(UnitType.SWARM_MATRIARCH));
        assertFalse(MechReflexes.meleeRav(UnitType.SWARM_SPITTER));
        assertFalse(MechReflexes.meleeRav(UnitType.SWARM_RUNNER));
    }
}
