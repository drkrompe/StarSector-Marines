package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.appearance.LayeredArmorFamily;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SwarmStrainsTest {

    @Test
    void everyStrainIsSwarmAndDrawsAsItsOwnChitinFamily() {
        assertEquals(LayeredArmorFamily.XENO, LayeredArmorFamily.spawnDefault(UnitType.SWARM_RUNNER));
        assertEquals(LayeredArmorFamily.XENO_BRUTE, LayeredArmorFamily.spawnDefault(UnitType.SWARM_BRUTE));
        assertEquals(LayeredArmorFamily.XENO_SPITTER, LayeredArmorFamily.spawnDefault(UnitType.SWARM_SPITTER));
        for (UnitType type : new UnitType[]{UnitType.SWARM_RUNNER, UnitType.SWARM_BRUTE,
                UnitType.SWARM_SPITTER}) {
            assertTrue(type.isSwarm());
            assertTrue(type.drawnAsLayers());
            assertFalse(type.drawsLayeredWeapon(), type + " carries no firearm layer");
            assertFalse(LayeredArmorFamily.spawnDefault(type).hasDownedArt(),
                    type + " falls through its own corpse sheet");
        }
        assertFalse(UnitType.MARINE.isSwarm());
    }

    @Test
    void onlySwarmTypesAreStrains() {
        assertThrows(IllegalArgumentException.class,
                () -> SwarmStrains.spec("x", UnitType.MARINE, 0, 0));
    }

    @Test
    void aBruteBreaksOnlyLightWallsOfTheBuildingItIsAfter() {
        NavigationGrid grid = new NavigationGrid(6, 3);
        CellTopology topology = new CellTopology(6, 3);
        for (int x = 0; x < 6; x++) {
            for (int y = 0; y < 3; y++) grid.setWalkableFloor(x, y);
        }
        // Building 1 interior at x=1, its wall at x=2; building 2 interior at x=4, wall at x=3.
        topology.setBuildingId(1, 1, 1);
        topology.setBuildingId(4, 1, 2);
        wall(grid, topology, 2, 1, 100);
        wall(grid, topology, 3, 1, 240);

        assertTrue(SwarmBruteBehavior.breakableWall(2, 1, 1, grid, topology),
                "a colony wall touching the target building");
        assertFalse(SwarmBruteBehavior.breakableWall(2, 1, 2, grid, topology),
                "the same wall does not belong to another building");
        assertFalse(SwarmBruteBehavior.breakableWall(3, 1, 2, grid, topology),
                "a fortified wall is not light");
        assertFalse(SwarmBruteBehavior.breakableWall(0, 1, 1, grid, topology),
                "open floor is not a wall");
    }

    @Test
    void acidBitesOnlySkinItsPenetrationReaches() {
        assertFalse(SwarmSpitterBehavior.acidBites(30f, 6f), "an airframe's intact skin turns acid");
        assertTrue(SwarmSpitterBehavior.acidBites(0f, 6f), "stripped skin leaves bare structure");
        assertTrue(SwarmSpitterBehavior.acidBites(10f, 4f), "a thin enough skin is bitten");
    }

    private static void wall(NavigationGrid grid, CellTopology topology, int x, int y, int hp) {
        grid.setWalkable(x, y, false);
        grid.setWallHp(x, y, hp);
        topology.setWall(x, y, true);
    }
}
