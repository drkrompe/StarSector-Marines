package com.dillon.starsectormarines.battle.door;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.vehicle.WallCrush;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LockedDoorTest {

    /** A room walled from (5,5) to (9,9), its one door at (7,9). */
    private static final int DOOR_X = 7, DOOR_Y = 9;

    @Test
    void aHardenedWallTakesNoDamageAndNoHeavyPushesIt() {
        try (BattleSimulation sim = simulation()) {
            HardenedWalls.harden(sim.getGrid(), sim.getTopology(), 5, 5, 9, 7);
            assertFalse(sim.damageCell(5, 6, 1_000_000), "hardened");
            assertFalse(WallCrush.canPush(sim.getGrid(), sim.getTopology(), null, 5, 6, Integer.MAX_VALUE));
            assertTrue(sim.damageCell(5, 8, 1_000_000), "outside the hardened box it is an ordinary wall");
        }
    }

    @Test
    void aHardenedDoorOpensOnlyByItsLockAndLocksAgainOnceClear() {
        try (BattleSimulation sim = simulation()) {
            LockedDoor door = door(true);
            door.install(sim);
            assertFalse(sim.getGrid().isWalkable(DOOR_X, DOOR_Y));
            assertFalse(sim.damageCell(DOOR_X, DOOR_Y, 1_000_000), "a charge does not open it");
            door.settle(sim, false);
            assertFalse(door.isOpen());

            door.settle(sim, true);
            assertTrue(sim.getGrid().isWalkable(DOOR_X, DOOR_Y), "unlocked, it opens");

            long inside = sim.spawn(new EntitySpec("inside", Faction.MARINE, UnitType.MARINE, 7, 7));
            door.settle(sim, false);
            assertTrue(door.isOpen(), "it never shuts on anybody in its room");
            sim.world().setCellPos(inside, 15, 15);
            door.settle(sim, false);
            assertFalse(sim.getGrid().isWalkable(DOOR_X, DOOR_Y));
            assertTrue(HardenedWalls.hardened(sim.getGrid(), DOOR_X, DOOR_Y), "locked again, hardened again");
        }
    }

    @Test
    void aForcibleDoorIsForcedOpenForGoodByABreach() {
        try (BattleSimulation sim = simulation()) {
            LockedDoor door = door(false);
            door.install(sim);
            assertTrue(sim.damageCell(DOOR_X, DOOR_Y, 1_000_000));
            door.settle(sim, false);
            assertTrue(door.isForced());
            assertTrue(sim.getGrid().isWalkable(DOOR_X, DOOR_Y));
        }
    }

    private static LockedDoor door(boolean hardened) {
        return new LockedDoor("door", new int[][]{{DOOR_X, DOOR_Y}}, 7, 8, new int[]{5, 5, 9, 9}, hardened);
    }

    private static BattleSimulation simulation() {
        NavigationGrid grid = new NavigationGrid(20, 20);
        CellTopology topology = new CellTopology(20, 20);
        for (int y = 0; y < 20; y++) {
            for (int x = 0; x < 20; x++) {
                boolean wall = (x == 5 || x == 9 || y == 5 || y == 9) && x >= 5 && x <= 9 && y >= 5 && y <= 9
                        && !(x == DOOR_X && y == DOOR_Y);
                if (wall) {
                    topology.setWall(x, y, true);
                    grid.setWallHp(x, y, 100);
                } else {
                    grid.setWalkableFloor(x, y);
                }
            }
        }
        return new BattleSimulation(grid, topology);
    }
}
