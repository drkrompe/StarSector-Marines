package com.dillon.starsectormarines.battle.ruins;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.ruins.PowerGraph.Branch;
import com.dillon.starsectormarines.battle.ruins.PowerGraph.Consumer;
import com.dillon.starsectormarines.battle.setup.RuinsDefence;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    @Test
    void readingTheRecordsMarksTheStoreAndWhatEachJunctionFeeds() {
        try (BattleSimulation sim = simulation()) {
            RuinsWorks works = works();
            sim.installRuinsWorks(works);
            assertFalse(works.junctionKnown(0));
            assertFalse(marked(works, "store-1"), "a sealed store is a secret until the records are read");

            sim.spawn(new EntitySpec("clerk", Faction.MARINE, UnitType.MARINE, 2, 14));
            works.choose(RuinsWorks.RECORDS, "read");
            for (int t = 0; t * BattleSimulation.TICK_DT < RuinsWorks.READ_SECONDS + 0.2f; t++) works.tick(sim);

            assertTrue(works.recordsRead());
            assertTrue(works.junctionKnown(0));
            assertTrue(marked(works, "store-1"));
        }
    }

    @Test
    void aConsoleInABuildingIsWorkedFromAnywhereInItOrAtItsDoor() {
        try (BattleSimulation sim = simulation()) {
            RuinsWorks.Site office = new RuinsWorks.Site(RuinsWorks.RECORDS, "office", 7, 6, new int[]{5, 5, 9, 9});
            long clerk = sim.spawn(new EntitySpec("clerk", Faction.MARINE, UnitType.MARINE, 7, 11));
            assertTrue(RuinsWorks.worked(sim, null, office), "two cells outside its wall, as a squad sent to its door stands");
            sim.world().setCellPos(clerk, 7, 15);
            assertFalse(RuinsWorks.worked(sim, null, office));
        }
    }

    @Test
    void aCollapseIsClearedOnceAnyOfItsCellsIsCut() {
        try (BattleSimulation sim = simulation()) {
            SealDoor rubble = new SealDoor("collapse", "collapse", new int[][]{{5, 6}, {5, 7}}, 6, 7,
                    new int[]{5, 6, 5, 7}, false);
            RuinsWorks works = new RuinsWorks(new PowerGraph(1, List.of(Branch.ONWARD), List.of()),
                    new RuinsWorks.Site(RuinsWorks.HALL, "hall", 2, 2),
                    List.of(new RuinsWorks.Site(RuinsWorks.JUNCTION + 0, "junction", 12, 2)), List.of(), List.of(),
                    null, rubble);
            sim.installRuinsWorks(works);
            works.settle(sim);
            assertFalse(works.collapseCleared());

            sim.mapEditor().openPassage(5, 7, 4, 7);
            works.settle(sim);
            assertTrue(works.collapseCleared());
        }
    }

    @Test
    void anEmptySiteSealsASecondStoreOnItsLastLayer() {
        assertEquals(2, RuinsWorks.storesIn(2, 3, 2, RuinsDefence.NONE));
        assertEquals(1, RuinsWorks.storesIn(1, 3, 2, RuinsDefence.NONE), "earlier layers keep one");
        assertEquals(1, RuinsWorks.storesIn(2, 3, 2, RuinsDefence.CREW), "an occupied site keeps one per layer");
        assertEquals(1, RuinsWorks.storesIn(0, 1, 1, RuinsDefence.NONE), "never more than the layer's rooms");
    }

    @Test
    void thePoweredVaultDoorWaitsOnItsTwoKeys() {
        try (BattleSimulation sim = simulation()) {
            PowerGraph graph = new PowerGraph(1, List.of(Branch.ONWARD),
                    List.of(new Consumer(RuinsWorks.VAULT_SEAL, 0, Branch.ONWARD)));
            SealDoor seal = new SealDoor(RuinsWorks.VAULT_SEAL, "vault", new int[][]{{DOOR_X, DOOR_Y}}, 7, 8,
                    new int[]{5, 5, 9, 9}, false, true);
            RuinsWorks works = new RuinsWorks(graph, new RuinsWorks.Site(RuinsWorks.HALL, "hall", 2, 2),
                    List.of(new RuinsWorks.Site(RuinsWorks.JUNCTION + 0, "junction", 12, 2)), List.of(seal), List.of(),
                    new RuinsWorks.Site(RuinsWorks.RECORDS, "records", 2, 14), null);
            works.keypad = new RuinsWorks.Site(RuinsWorks.KEYPAD, "keypad", DOOR_X, DOOR_Y + 2);
            sim.installRuinsWorks(works);

            works.graph().restart();
            works.settle(sim);
            assertFalse(sim.getGrid().isWalkable(DOOR_X, DOOR_Y), "powered, it still waits on its keys");

            works.keys.tick(true, true, true, VaultKeys.TURN_SECONDS);
            works.settle(sim);
            assertTrue(sim.getGrid().isWalkable(DOOR_X, DOOR_Y), "powered and keyed, it opens");
        }
    }

    @Test
    void aRecordsCodeVaultDoorWaitsOnItsCode() {
        try (BattleSimulation sim = simulation()) {
            RuinsWorks works = vaultWorks();
            works.access = VaultAccess.RECORDS_CODE;
            sim.installRuinsWorks(works);
            works.graph().restart();
            works.settle(sim);
            assertFalse(sim.getGrid().isWalkable(DOOR_X, DOOR_Y), "powered, it waits on its code");
            works.code.tick(true, true, true, VaultCode.ENTER_SECONDS);
            works.settle(sim);
            assertTrue(sim.getGrid().isWalkable(DOOR_X, DOOR_Y), "the code entered, it opens");
        }
    }

    @Test
    void anyOneJunctionOnItsStoreSideFitsTheHall() {
        // A layer with two stores and a court beside one gate needs two more than the whole way on.
        assertEquals(4, RuinsWorks.capacity(new int[]{1, 1}, new int[]{1, 3}));
        assertEquals(3, RuinsWorks.capacity(new int[]{1, 1}, new int[]{1, 1}), "one store beside the way on");
    }

    private static RuinsWorks vaultWorks() {
        PowerGraph graph = new PowerGraph(1, List.of(Branch.ONWARD),
                List.of(new Consumer(RuinsWorks.VAULT_SEAL, 0, Branch.ONWARD)));
        SealDoor seal = new SealDoor(RuinsWorks.VAULT_SEAL, "vault", new int[][]{{DOOR_X, DOOR_Y}}, 7, 8,
                new int[]{5, 5, 9, 9}, false, true);
        RuinsWorks works = new RuinsWorks(graph, new RuinsWorks.Site(RuinsWorks.HALL, "hall", 2, 2),
                List.of(new RuinsWorks.Site(RuinsWorks.JUNCTION + 0, "junction", 12, 2)), List.of(seal), List.of(),
                new RuinsWorks.Site(RuinsWorks.RECORDS, "records", 2, 14), null);
        works.keypad = new RuinsWorks.Site(RuinsWorks.KEYPAD, "keypad", DOOR_X, DOOR_Y + 2);
        return works;
    }

    private static boolean marked(RuinsWorks works, String door) {
        return works.item().markers().stream().anyMatch(m -> m.id().endsWith(door));
    }

    private static RuinsWorks works() {
        PowerGraph graph = new PowerGraph(1, List.of(Branch.ONWARD), List.of(new Consumer("store-1", 0, Branch.STORE)));
        SealDoor door = new SealDoor("store-1", "store", new int[][]{{DOOR_X, DOOR_Y}}, 7, 8,
                new int[]{5, 5, 9, 9}, true);
        return new RuinsWorks(graph, new RuinsWorks.Site(RuinsWorks.HALL, "hall", 2, 2),
                List.of(new RuinsWorks.Site(RuinsWorks.JUNCTION + 0, "junction", 12, 2)), List.of(door), List.of(),
                new RuinsWorks.Site(RuinsWorks.RECORDS, "records", 2, 14), null);
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
