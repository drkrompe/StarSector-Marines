package com.dillon.starsectormarines.battle.command.objective;

import com.dillon.starsectormarines.battle.command.objective.MissionObjectiveSnapshot.Cell;
import com.dillon.starsectormarines.battle.command.objective.MissionObjectiveSnapshot.Item;
import com.dillon.starsectormarines.battle.command.objective.MissionObjectiveSnapshot.Marker;
import com.dillon.starsectormarines.battle.command.objective.MissionObjectiveSnapshot.State;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.PointOfInterest;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MissionObjectiveFootprintTest {
    @Test void beaconAndLocateUseCentralWalkableInteriorWhileOutlineExcludesExteriorRecess() {
        var topology=new CellTopology(12,12);
        var grid=new NavigationGrid(12,12);
        var building=new PointOfInterest(PointOfInterest.Kind.DEPOT,2,2,9,9,1,3,3,3);
        for(int y=3;y<=8;y++)for(int x=3;x<=(y<=5?8:4);x++) {
            topology.setBuildingId(x,y,1);grid.setWalkableFloor(x,y);
        }
        grid.setWalkable(5,5,false); // Furnished floor belongs to the outline but cannot anchor a beacon.
        var marker=new Marker("workshop","Workshop",3,3,"CAPTURE",State.ACTIVE);
        var item=new Item("salvage","Workshop",State.ACTIVE,"WORKSHOP","","","",0,"",List.of(marker),marker.id());
        var decorated=MissionObjectiveProjection.buildingFootprint(item,topology,grid,building);
        var focus=decorated.focus();
        assertEquals(24,focus.footprint().size());
        assertTrue(focus.footprint().contains(new Cell(5,5)));
        assertFalse(focus.footprint().contains(new Cell(8,8)));
        assertFalse(focus.footprint().contains(new Cell(2,3)));
        assertTrue(focus.footprint().contains(new Cell(focus.x(),focus.y())));
        assertTrue(grid.isWalkable(focus.x(),focus.y()));
        assertFalse(focus.x()==marker.x() && focus.y()==marker.y());
        assertEquals(marker.id(),decorated.focusMarkerId());
        assertEquals(0,marker.footprint().size());
        assertThrows(UnsupportedOperationException.class,()->focus.footprint().add(new Cell(8,8)));
    }
}
