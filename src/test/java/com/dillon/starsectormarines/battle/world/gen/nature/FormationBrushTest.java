package com.dillon.starsectormarines.battle.world.gen.nature;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class FormationBrushTest {
    @Test void everyRotationUsesItsActualSilhouetteAndPreservesGround() {
        for(var shape:MineralFormation.values())for(int turn=0;turn<4;turn++) {
            var grid=new NavigationGrid(12,12);var topology=ground(grid);
            var props=new ArrayList<Doodad>();var p=new FormationBrush.Placement(3,3,shape,turn);
            FormationBrush.stamp(List.of(p),grid,topology,props,null);
            assertEquals(1,props.size());
            for(int y=0;y<12;y++)for(int x=0;x<12;x++) {
                assertEquals(!p.blocks(x,y),grid.isWalkable(x,y));
                assertEquals(p.blocks(x,y),grid.blocksLineOfSightAt(grid.index(x,y)));
                assertEquals(CellTopology.GroundKind.SNOW,topology.getGroundKind(x,y));
                assertEquals(p.blocks(x,y),props.get(0).hasFogSurfaceAt(x-3,y-3));
            }
            var def=TileRegistry.installed().doodad(shape.id());
            assertEquals(shape.width(),def.footprintCellsX);assertEquals(shape.height(),def.footprintCellsY);
        }
    }
    @Test void reservedOrAuthoredFloorRefusesTheEntirePlacementBeforeMutation() {
        for(boolean floor:new boolean[]{false,true}) {
            var grid=new NavigationGrid(12,12);var topology=ground(grid);var props=new ArrayList<Doodad>();
            boolean[] reserved=new boolean[144];
            if(floor)topology.setSurface(4,4,topology.surfaceIndex("floors.stone"));else reserved[4*12+4]=true;
            assertThrows(IllegalArgumentException.class,()->FormationBrush.stamp(
                    List.of(new FormationBrush.Placement(3,3,MineralFormation.BOULDERS,0)),grid,topology,props,reserved));
            assertTrue(props.isEmpty());
            for(int y=0;y<12;y++)for(int x=0;x<12;x++)assertTrue(grid.isWalkable(x,y));
        }
    }
    private static CellTopology ground(NavigationGrid grid) {
        var topology=new CellTopology(12,12);
        for(int y=0;y<12;y++)for(int x=0;x<12;x++) {
            grid.setWalkableFloor(x,y);topology.setGroundKind(x,y,CellTopology.GroundKind.SNOW);
        }
        return topology;
    }
}
