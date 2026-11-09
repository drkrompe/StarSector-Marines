package com.dillon.starsectormarines.battle.ruins;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.ruins.PowerGraph.Branch;
import com.dillon.starsectormarines.battle.ruins.PowerGraph.Consumer;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuinsWorksTest {

    /** A store room walled from (5,5) to (9,9), its one door at (7,9). */
    private static final int DOOR_X = 7, DOOR_Y = 9;

    @Test
    void aSealedStoreOpensWhilePoweredAndShutsOnceNobodyIsInside() {
        try (BattleSimulation sim = simulation()) {
            RuinsWorks works = works();
            sim.installRuinsWorks(works);
            assertFalse(sim.getGrid().isWalkable(DOOR_X, DOOR_Y), "sealed as the wall beside it");

            works.graph().restart();
            works.graph().throwJunction(0, Branch.STORE);
            works.settle(sim);
            assertTrue(sim.getGrid().isWalkable(DOOR_X, DOOR_Y), "powered, it opens");

            long inside = sim.spawn(new EntitySpec("looter", Faction.MARINE, UnitType.MARINE, 7, 7));
            works.graph().throwJunction(0, Branch.ONWARD);
            works.settle(sim);
            assertTrue(sim.getGrid().isWalkable(DOOR_X, DOOR_Y), "it never shuts on anybody in the room");

            sim.world().setCellPos(inside, 15, 15);
            works.settle(sim);
            assertFalse(sim.getGrid().isWalkable(DOOR_X, DOOR_Y), "clear, it shuts again");
        }
    }

    @Test
    void aBreachForcesASealDoorOpenForGood() {
        try (BattleSimulation sim = simulation()) {
            RuinsWorks works = works();
            sim.installRuinsWorks(works);

            sim.mapEditor().openPassage(DOOR_X, DOOR_Y, 7, 8);
            works.settle(sim);

            assertTrue(works.isForced(0));
            assertTrue(sim.getGrid().isWalkable(DOOR_X, DOOR_Y), "unpowered, a forced door stays open");
        }
    }

    @Test
    void infantryHoldingTheConsoleRestartsTheHall() {
        try (BattleSimulation sim = simulation()) {
            RuinsWorks works = works();
            sim.installRuinsWorks(works);
            sim.spawn(new EntitySpec("engineer", Faction.MARINE, UnitType.MARINE, 2, 2));

            works.choose(RuinsWorks.HALL, "restart");
            for (int t = 0; t * BattleSimulation.TICK_DT < RuinsWorks.RESTART_SECONDS + 0.2f; t++) works.tick(sim);

            assertTrue(works.graph().running());
        }
    }

    private static RuinsWorks works() {
        PowerGraph graph = new PowerGraph(1, List.of(Branch.ONWARD), List.of(new Consumer("store-1", 0, Branch.STORE)));
        SealDoor door = new SealDoor("store-1", "store", new int[][]{{DOOR_X, DOOR_Y}}, 7, 8,
                new int[]{5, 5, 9, 9}, true);
        return new RuinsWorks(graph, new RuinsWorks.Site(RuinsWorks.HALL, "hall", 2, 2),
                List.of(new RuinsWorks.Site(RuinsWorks.JUNCTION + 0, "junction", 12, 2)), List.of(door), List.of());
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
                } else {
                    grid.setWalkableFloor(x, y);
                }
            }
        }
        return new BattleSimulation(grid, topology);
    }
}
