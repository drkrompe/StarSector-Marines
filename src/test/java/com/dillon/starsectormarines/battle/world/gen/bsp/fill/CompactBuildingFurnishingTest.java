package com.dillon.starsectormarines.battle.world.gen.bsp.fill;

import com.dillon.starsectormarines.battle.world.gen.BlockLeaf;
import com.dillon.starsectormarines.battle.world.gen.GenMappingRegistry;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.PropDamage;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import org.junit.jupiter.api.Test;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class CompactBuildingFurnishingTest {
    @Test void themedShellsKeepEveryFloorAndEntranceReachable() {
        for (int theme=0;theme<4;theme++) for (int size:new int[]{5,11})
            for (BuildingPlacement.Side side:BuildingPlacement.Side.values()) {
                var grid=CompactBuildingSnapshotSuite.open(20,20);var topology=new CellTopology(20,20);
                List<Doodad> props=new ArrayList<>();
                var leaf=new BlockLeaf(3,3,2+size,2+size,false);
                var poi=BuildingShellCore.carve(leaf,grid,topology,props,new Random(42),
                        CompactBuildingSnapshotSuite.config(theme),new BuildingPlacement(side,true));
                assertNotNull(poi);
                assertFalse(props.isEmpty(),"occupied building should have a purpose: "+theme+" / "+size+" / "+side);
                boolean[][] seen=new boolean[20][20];var queue=new ArrayDeque<int[]>();
                seen[0][0]=true;queue.add(new int[]{0,0});
                while(!queue.isEmpty()) {
                    int[] at=queue.removeFirst();
                    for(int[] step:new int[][]{{1,0},{-1,0},{0,1},{0,-1}}) {
                        int x=at[0]+step[0],y=at[1]+step[1];
                        if(grid.inBounds(x,y)&&!seen[x][y]&&grid.canTraverseCellStep(at[0],at[1],x,y)) {
                            seen[x][y]=true;queue.add(new int[]{x,y});
                        }
                    }
                }
                for(int y=0;y<20;y++) for(int x=0;x<20;x++) if(grid.isWalkable(x,y)) assertTrue(seen[x][y],"stranded floor");
                for(Doodad prop:props) {
                    assertTrue(prop.cellX>leaf.left&&prop.cellY>leaf.top);
                    assertTrue(prop.cellX+prop.footprintCellsX<=leaf.right&&prop.cellY+prop.footprintCellsY<=leaf.bottom);
                    assertFalse(grid.isDoorway(prop.cellX,prop.cellY));
                    assertFalse(prop.definitionId().contains("door"));
                    assertFalse(prop.definitionId().endsWith("-damaged"));
                }
                assertTrue(seen[poi.interiorAnchorX][poi.interiorAnchorY]);
            }
    }
    @Test void ordinaryPoolsContainIntactCompactFurnitureWithoutLooseDoors() {
        for(String pool:List.of("MIXED","RESIDENTIAL","COMMERCIAL","WAREHOUSE","SKY_PORT")) {
            var defs=GenMappingRegistry.installed().doodadPool(pool);assertFalse(defs.isEmpty());
            for(var def:defs) {
                assertTrue(def.id.startsWith("colony-furnishings."));
                assertEquals(1,def.footprintCellsX);assertEquals(1,def.footprintCellsY);
                assertFalse(def.id.endsWith("-damaged")||def.id.endsWith("-destroyed"));
            }
        }
    }
    @Test void compactFurnitureDamageReleasesItsFootprintInEveryRotation() {
        var registry=TileRegistry.installed();
        for(String name:List.of("case","locker","desk","utility","galley","shelf")) for(int turn=0;turn<4;turn++) {
            String id="colony-furnishings."+name;
            var grid=CompactBuildingSnapshotSuite.open(6,6);grid.setWalkable(2,2,false);
            List<Doodad> props=new ArrayList<>(List.of(new Doodad(2,2,registry.doodad(id),turn)));
            var damage=new PropDamage(grid,props);assertTrue(damage.register(0,props.get(0),registry));
            damage.damage(2,2,60,1,(x,y)->grid.setWalkable(x,y,true));
            assertEquals(id+"-damaged",props.get(0).definitionId());assertFalse(grid.isWalkable(2,2));
            damage.damage(2,2,1000,2,(x,y)->grid.setWalkable(x,y,true));
            assertEquals(id+"-destroyed",props.get(0).definitionId());assertTrue(grid.isWalkable(2,2));
            assertEquals(0,props.get(0).cover);assertEquals(turn,props.get(0).quarterTurns);
        }
    }
}
