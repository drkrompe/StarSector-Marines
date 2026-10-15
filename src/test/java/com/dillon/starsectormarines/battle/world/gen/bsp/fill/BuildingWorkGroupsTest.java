package com.dillon.starsectormarines.battle.world.gen.bsp.fill;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class BuildingWorkGroupsTest {
    private final NavigationGrid grid = new NavigationGrid(24, 24);
    private final CellTopology topology = new CellTopology(24, 24);
    private final List<Doodad> doodads = new ArrayList<>();
    private final BuildingWorkGroups groups = new BuildingWorkGroups(grid, topology, doodads);
    BuildingWorkGroupsTest() {
        for (int y = 0; y < 24; y++) for (int x = 0; x < 24; x++) {
            grid.setWalkableFloor(x, y); topology.setRoomPurpose(x, y, RoomPurpose.PATIENT_WARD);
        }
    }

    @Test void bedBayProtectsSideAndStretcherAccessFromLaterFurniture() {
        assertTrue(groups.place(BuildingWorkGroups.bedBay(), 3, 3, 0, RoomPurpose.PATIENT_WARD));
        assertFalse(groups.place(BuildingWorkGroups.pallets(1, "cases"), 4, 3, 0, RoomPurpose.PATIENT_WARD));
        assertEquals(3, doodads.size());
        for (int y = 3; y <= 7; y++) for (int x = 4; x <= 5; x++) assertTrue(grid.isWalkable(x,y));
        assertTrue(grid.isWalkable(3,6)); assertTrue(grid.isWalkable(3,7));
        assertFalse(grid.isWalkable(3,3)); assertFalse(grid.isWalkable(3,4)); assertFalse(grid.isWalkable(3,5));
    }

    @Test void blockedGroupDoesNotLeaveAMonitorOrLinenBehind() {
        grid.setDoorway(3, 4, true);
        assertFalse(groups.place(BuildingWorkGroups.bedBay(), 3, 3, 0, RoomPurpose.PATIENT_WARD));
        assertTrue(doodads.isEmpty()); assertTrue(grid.isWalkable(3,3));
        assertFalse(topology.isFixture(3,3));
    }

    @Test void stockLayersAndRackFootprintRotateAsOneAssembly() {
        for (int turn = 0; turn < 4; turn++) {
            BuildingWorkGroupsTest fixture = new BuildingWorkGroupsTest();
            assertTrue(fixture.groups.place(BuildingWorkGroups.stockedRack("cases"), 3, 3, turn, RoomPurpose.PATIENT_WARD));
            Doodad rack = fixture.doodads.get(0), frame = fixture.doodads.get(4);
            assertEquals(rack.cellX, frame.cellX); assertEquals(rack.cellY, frame.cellY);
            assertEquals(rack.footprintCellsX, frame.footprintCellsX);
            assertEquals(rack.footprintCellsY, frame.footprintCellsY);
            for (Doodad stock : fixture.doodads.subList(1, 4)) {
                assertEquals(0, stock.cover);
                for (int y = stock.cellY; y < stock.cellY + stock.footprintCellsY; y++)
                    for (int x = stock.cellX; x < stock.cellX + stock.footprintCellsX; x++) {
                        assertTrue(rack.occupiesCell(x,y)); assertFalse(fixture.grid.isWalkable(x,y));
                    }
            }
        }
    }

    @Test void largeWardReceivesRepeatedBedsWithOneAlignedAxis() {
        int beds = groups.fit(0,0,14,10, RoomPurpose.PATIENT_WARD, BuildingWorkGroups.bedBay(), 8, 0,2);
        assertTrue(beds >= 4, "a large ward should have a real bed rank");
        assertEquals(beds, doodads.stream().filter(d -> d.definitionId().equals("sickbay-clinic.bed")).count());
        assertTrue(doodads.stream().allMatch(d -> d.quarterTurns % 2 == 0));
    }

    @Test void cargoDeskKeepsTheSeatedWorkRowAndServiceRowOpenAtEveryPose() {
        for (int turn = 0; turn < 4; turn++) {
            BuildingWorkGroupsTest fixture = new BuildingWorkGroupsTest();
            assertTrue(fixture.groups.place(BuildingWorkGroups.cargoDesk(false), 3, 3, turn, RoomPurpose.PATIENT_WARD));
            Doodad desk = fixture.doodads.get(0), stool = fixture.doodads.get(1);
            assertEquals(0, stool.cover);
            assertTrue(fixture.grid.isWalkable(stool.cellX, stool.cellY));
            for (int y = 3; y < 6; y++) for (int x = 3; x < 6; x++)
                assertEquals(!desk.occupiesCell(x,y), fixture.grid.isWalkable(x,y));
            assertFalse(fixture.groups.place(BuildingWorkGroups.cargoDesk(true), 3, 3, (turn + 2) % 4, RoomPurpose.PATIENT_WARD),
                    "an opposite bench cannot consume the tally desk's service row");
        }
    }
}
