package com.dillon.starsectormarines.battle.world.gen.bsp.fill;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomPose;
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
            grid.setWalkableFloor(x, y); grid.setSeeThrough(x, y, true);
            topology.setRoomPurpose(x, y, RoomPurpose.PATIENT_WARD);
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

    @Test void machineFootprintsAreOpaqueAndKeepOperatingAndCrossAislesAtEveryPose() {
        for (String id : List.of("factory-machines.fabricator", "factory-machines.assembly", "factory-machines.coolant"))
            for (int turn = 0; turn < 4; turn++) {
                BuildingWorkGroupsTest fixture = new BuildingWorkGroupsTest();
                var group = BuildingWorkGroups.machine(id);
                assertTrue(fixture.groups.place(group, 3, 3, turn, RoomPurpose.PATIENT_WARD));
                Doodad machine = fixture.doodads.get(0);
                RoomPose pose = new RoomPose(turn, false);
                for (int y = 0; y < pose.posedHeight(group.width(), group.height()); y++)
                    for (int x = 0; x < pose.posedWidth(group.width(), group.height()); x++) {
                        boolean occupied = machine.occupiesCell(x + 3, y + 3);
                        assertEquals(!occupied, fixture.grid.isWalkable(x + 3, y + 3));
                        assertEquals(!occupied, fixture.grid.isSeeThrough(x + 3, y + 3));
                    }
                assertFalse(fixture.groups.place(group, 3, 3, (turn + 2) % 4, RoomPurpose.PATIENT_WARD));
            }
    }

    @Test void wholeProductionLineTurnsWithItsStockFeedsAndClearWorkingRows() {
        for (int turn=0;turn<4;turn++) {
            BuildingWorkGroupsTest fixture = new BuildingWorkGroupsTest();
            var group = BuildingWorkGroups.productionLine(true);
            assertTrue(fixture.groups.place(group,3,3,turn,RoomPurpose.PATIENT_WARD));
            assertEquals(7,fixture.doodads.size());
            assertEquals(2,fixture.doodads.stream().filter(d -> d.definitionId().equals("factory-machines.conveyor")).count());
            RoomPose pose = new RoomPose(turn,false);
            for (int y : new int[]{0,1,5,6}) for(int x=0;x<group.width();x++) {
                int[] at=pose.map(x,y,group.width(),group.height());
                assertTrue(fixture.grid.isWalkable(3+at[0],3+at[1]));
            }
            Doodad input=fixture.doodads.get(0), stock=fixture.doodads.get(1);
            assertEquals(input.cellX,stock.cellX); assertEquals(input.cellY,stock.cellY);
            for (Doodad d : fixture.doodads) assertEquals(turn,d.quarterTurns);
        }
        grid.setDoorway(7,6,true);
        assertFalse(groups.place(BuildingWorkGroups.productionLine(true),3,3,0,RoomPurpose.PATIENT_WARD));
        assertTrue(doodads.isEmpty(),"rejected lines cannot leave orphan stock or a conveyor behind");
    }
}
