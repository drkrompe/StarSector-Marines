package com.dillon.starsectormarines.battle.world;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.NavigationService;
import com.dillon.starsectormarines.battle.world.gen.nature.FormationBrush;
import com.dillon.starsectormarines.battle.world.gen.nature.MineralFormation;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.DoodadService;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class MineralDamageTest {
    @Test void oneBlastDamagesEachFormationOnceAndDestructionReopensItsWholeRotatedFootprint() {
        for(var shape:MineralFormation.values())for(int turn=0;turn<4;turn++) {
            var grid=new NavigationGrid(12,12);var topology=new CellTopology(12,12);
            for(int y=0;y<12;y++)for(int x=0;x<12;x++) {
                grid.setWalkableFloor(x,y);topology.setGroundKind(x,y,CellTopology.GroundKind.SNOW);
            }
            var props=new ArrayList<Doodad>();var p=new FormationBrush.Placement(3,3,shape,turn);
            FormationBrush.stamp(List.of(p),grid,topology,props,null);
            var service=new DoodadService(grid);props.forEach(service::addDoodad);
            try(var navigation=new NavigationService(grid,topology,false)) {
                var editor=new MapEditor(navigation);editor.setDoodads(service);
                editor.damageWallsInRadius(5.5f,5.5f,8,shape.hitPoints()/2,null);
                assertEquals(shape.id()+"-damaged",service.getDoodads().get(0).definitionId());
                for(int y=3;y<3+p.height();y++)for(int x=3;x<3+p.width();x++)if(p.blocks(x,y))assertFalse(grid.isWalkable(x,y));
                editor.damageWallsInRadius(5.5f,5.5f,8,shape.hitPoints()/2,null);
                var debris=service.getDoodads().get(0);
                assertEquals(shape.id()+"-destroyed",debris.definitionId());assertEquals(turn,debris.quarterTurns);
                for(int y=0;y<12;y++)for(int x=0;x<12;x++) {
                    assertTrue(grid.isWalkable(x,y));assertFalse(grid.blocksLineOfSightAt(grid.index(x,y)));
                    assertEquals(CellTopology.GroundKind.SNOW,topology.getGroundKind(x,y));
                    assertFalse(debris.hasFogSurfaceAt(x,y));
                }
                assertTrue(navigation.isNavigationTopologyDirty());
            }
        }
    }
}
