package com.dillon.starsectormarines.battle.ambient;

import com.dillon.starsectormarines.battle.world.gen.Affordance;
import com.dillon.starsectormarines.battle.world.gen.FixtureTask;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class BuildingWorkTest {

    /** A ten-by-ten room of floor, walls at 0 and 9, a doorway at (5, 0). */
    private static final BuildingWork.Floor ROOM = (x, y) -> x > 0 && x < 9 && y > 0 && y < 9;

    @Test
    void aPalletIsWorkedFromFreeFloorBesideIt() {
        List<FixtureTask> work = BuildingWork.recover(
                List.of(new BuildingWork.Fixture(4, 4, 2, 2, "main-stores.pallet")), ROOM);
        assertEquals(1, work.size());
        FixtureTask task = work.get(0);
        assertEquals(Affordance.STOW, task.affordance());
        int dx = Math.max(Math.max(4 - task.cellX(), task.cellX() - 5), 0);
        int dy = Math.max(Math.max(4 - task.cellY(), task.cellY() - 5), 0);
        assertEquals(1, dx + dy, "stands beside the pallet, not on it or off from it");
    }

    @Test
    void twoFixturesSharingAnAisleCellAreTwoPlacesToStand() {
        // Pallets against the north and south of a one-cell aisle at y = 5, both flush on the west wall.
        BuildingWork.Floor aisle = (x, y) -> x == 1 && y == 5;
        List<FixtureTask> work = BuildingWork.recover(List.of(
                new BuildingWork.Fixture(1, 3, 1, 2, "main-stores.pallet"),
                new BuildingWork.Fixture(1, 6, 1, 2, "main-stores.pallet")), aisle);
        assertEquals(1, work.size(), "the second pallet has nowhere left to be worked from");
    }

    @Test
    void scenerySeesNoWorkAndAStandNeverLandsOnAnotherFixture() {
        List<FixtureTask> work = BuildingWork.recover(List.of(
                new BuildingWork.Fixture(2, 2, 1, 1, "doodad.potted-plant"),
                new BuildingWork.Fixture(3, 2, 1, 1, "main-stores.tally"),
                new BuildingWork.Fixture(3, 1, 1, 1, "doodad.potted-plant")), ROOM);
        assertEquals(1, work.size());
        assertEquals(Affordance.READOUT, work.get(0).affordance());
        assertNotEquals(List.of(3, 1), List.of(work.get(0).cellX(), work.get(0).cellY()));
        assertNotEquals(List.of(2, 2), List.of(work.get(0).cellX(), work.get(0).cellY()));
    }

    @Test
    void aDepotIsAWarehouseUnlessAMachineMakesItAFactory() {
        List<BuildingWork.Fixture> stock = List.of(
                new BuildingWork.Fixture(1, 1, 2, 2, "main-stores.pallet"),
                new BuildingWork.Fixture(4, 1, 2, 2, "main-stores.pallet"),
                new BuildingWork.Fixture(7, 1, 1, 2, "colony-furnishings.shelf"));
        List<FixtureTask> work = BuildingWork.recover(stock, ROOM);
        assertEquals(BuildingWork.Kind.WAREHOUSE, BuildingWork.kindOf(stock, work, true, 3));
        assertNull(BuildingWork.kindOf(stock, work, true, 4), "too little stock to staff");

        List<BuildingWork.Fixture> line = new ArrayList<>(stock);
        line.add(new BuildingWork.Fixture(3, 5, 3, 3, "factory-machines.fabricator"));
        assertEquals(BuildingWork.Kind.FACTORY, BuildingWork.kindOf(line, BuildingWork.recover(line, ROOM), true, 4));
    }

    @Test
    void shelvesMakeAShopOnlyWithACounter() {
        List<BuildingWork.Fixture> shelves = new ArrayList<>();
        for (int x = 1; x <= 7; x += 2) shelves.add(new BuildingWork.Fixture(x, 1, 1, 2, "colony-furnishings.shelf"));
        assertNull(BuildingWork.kindOf(shelves, BuildingWork.recover(shelves, ROOM), false, 4), "bookcases");
        shelves.add(new BuildingWork.Fixture(4, 6, 1, 1, "colony-furnishings.desk"));
        assertEquals(BuildingWork.Kind.GROCERY,
                BuildingWork.kindOf(shelves, BuildingWork.recover(shelves, ROOM), false, 4));
    }
}
