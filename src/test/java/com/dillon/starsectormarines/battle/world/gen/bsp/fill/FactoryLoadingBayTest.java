package com.dillon.starsectormarines.battle.world.gen.bsp.fill;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FactoryLoadingBayTest {
    @Test void narrowReceivingUsesTwoCellsInsteadOfOmittingTheBay() {
        NavigationGrid grid = new NavigationGrid(10,10);
        CellTopology topology = new CellTopology(10,10);
        for (int y=0;y<10;y++) for (int x=0;x<10;x++) grid.setWalkableFloor(x,y);
        for (int y=3;y<=4;y++) for (int x=4;x<=5;x++) topology.setRoomPurpose(x,y,RoomPurpose.LOADING_BAY);
        FactoryLoadingBay.carve(grid,topology,2,2,7,7,BuildingPlacement.Side.TOP,CellTopology.GroundKind.INDOOR);
        assertTrue(grid.isDoorway(4,2));
        assertTrue(grid.isDoorway(5,2));
        assertFalse(grid.isDoorway(3,2));
        assertFalse(grid.isDoorway(6,2));
    }

    @Test void cargoOpeningFacesTheSelectedFrontageAndLeavesBothApproachesOpen() {
        for (BuildingPlacement.Side side : BuildingPlacement.Side.values()) {
            NavigationGrid grid = new NavigationGrid(16,16);
            CellTopology topology = new CellTopology(16,16);
            for (int y=0;y<16;y++) for (int x=0;x<16;x++) {
                grid.setWalkableFloor(x,y);
                if (x>2 && x<13 && y>2 && y<13) topology.setRoomPurpose(x,y,RoomPurpose.LOADING_BAY);
                if (((x==2 || x==13) && y>=2 && y<=13) || ((y==2 || y==13) && x>=2 && x<=13)) grid.setWalkable(x,y,false);
            }
            FactoryLoadingBay.carve(grid,topology,2,2,13,13,side,CellTopology.GroundKind.INDOOR);
            int count=0;
            for (int y=0;y<16;y++) for (int x=0;x<16;x++) if(grid.isDoorway(x,y)) {
                count++;
                assertTrue(grid.isWalkable(x,y));
                assertEquals(0,topology.getWallDirMask(x,y));
                switch(side) {
                    case TOP -> assertEquals(2,y);
                    case BOTTOM -> assertEquals(13,y);
                    case LEFT -> assertEquals(2,x);
                    case RIGHT -> assertEquals(13,x);
                }
            }
            assertEquals(3,count);
        }
    }

    @Test void secondUnloadingRowCannotBeConsumedByFurniture() {
        NavigationGrid grid = new NavigationGrid(16,16);
        CellTopology topology = new CellTopology(16,16);
        for(int y=0;y<16;y++) for(int x=0;x<16;x++) {
            grid.setWalkableFloor(x,y); topology.setRoomPurpose(x,y,RoomPurpose.LOADING_BAY);
        }
        grid.setDoorway(5,2,true); grid.setDoorway(6,2,true);
        BuildingWorkGroups groups = new BuildingWorkGroups(grid,topology,new ArrayList<>());
        groups.reserveCargoApproaches(2,2,13,13);
        assertFalse(groups.place(BuildingWorkGroups.cargoDesk(true),4,4,0,RoomPurpose.LOADING_BAY));
        assertTrue(groups.place(BuildingWorkGroups.cargoDesk(true),4,5,0,RoomPurpose.LOADING_BAY));
    }
}
