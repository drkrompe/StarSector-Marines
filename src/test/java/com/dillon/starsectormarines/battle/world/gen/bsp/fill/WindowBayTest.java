package com.dillon.starsectormarines.battle.world.gen.bsp.fill;

import com.dillon.starsectormarines.battle.nav.Direction;
import com.dillon.starsectormarines.battle.nav.NavigationService;
import com.dillon.starsectormarines.battle.nav.SharedEdgeBarrier;
import com.dillon.starsectormarines.battle.world.MapEditor;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import com.dillon.starsectormarines.battle.world.gen.BlockLeaf;
import com.dillon.starsectormarines.battle.world.gen.bsp.BuildingFloodFill;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class WindowBayTest {
    private static final BlockLeaf SHELL = new BlockLeaf(4,4,21,18,false);

    @Test void baysKeepRoomApproachesOpenAndCornersSolidInEveryFrontage() {
        for(int kind=0;kind<4;kind++) for(var side:BuildingPlacement.Side.values()) {
            var c=NeighborhoodBuildingsTest.open(28,26);
            c.pois.add(BuildingShellCore.carve(SHELL,c.grid,c.topology,c.doodads,c.rng,
                    NeighborhoodBuildingsTest.config(kind),new BuildingPlacement(side,true)));
            var windows=BuildingWindowTestSupport.windowsOwnedBy(c.grid,SHELL);
            assertNotNull(adjacentPair(windows),"multi-pane bay / kind="+kind+" / "+side);
            for(var window:windows) {
                int x=window.structureCellX(), y=window.structureCellY();
                Direction out=BuildingWindowTestSupport.outwardFromOwner(window);
                assertTrue(c.grid.isWalkable(x-out.dx,y-out.dy),"window approach stays clear kind="+kind+" / "+side+" / "+x+","+y);
                assertFalse(c.grid.isSharedEdgePassable(x,y,out),"intact pane blocks movement");
                for(Direction d:Direction.CARDINALS)
                    assertFalse(c.grid.isDoorway(x+d.dx,y+d.dy),"solid door jamb");
            }
            for(int x:new int[]{SHELL.left,SHELL.right}) for(int y:new int[]{SHELL.top,SHELL.bottom})
                assertFalse(c.grid.isWalkable(x,y),"corner pier stays solid");
            NeighborhoodBuildingsTest.assertConnected(c);
        }
    }

    @Test void shallowHomesRetainTheirLivingSeatingAlongsideWindows() {
        for(int[] size:new int[][]{{13,7},{7,13}}) {
            var c=NeighborhoodBuildingsTest.open(28,26);
            var shell=new BlockLeaf(4,4,3+size[0],3+size[1],false);
            BuildingShellCore.carve(shell,c.grid,c.topology,c.doodads,c.rng,NeighborhoodBuildingsTest.config(0));
            assertTrue(c.doodads.stream().anyMatch(d -> d.definitionId().contains("sofa")), "living seat");
            assertFalse(BuildingWindowTestSupport.windowsOwnedBy(c.grid,shell).isEmpty());
        }
    }

    @Test void compactWindowedOfficesKeepTheirTerminals() {
        for(int[] size:new int[][]{{8,11},{11,8},{11,10}}) for(var side:BuildingPlacement.Side.values()) {
            var c=NeighborhoodBuildingsTest.open(28,26);
            var shell=new BlockLeaf(4,4,3+size[0],3+size[1],false);
            BuildingShellCore.carve(shell,c.grid,c.topology,c.doodads,c.rng,
                    NeighborhoodBuildingsTest.config(3),new BuildingPlacement(side,true));
            for(var purpose:new RoomPurpose[]{
                    RoomPurpose.CIVIC_OFFICE,
                    RoomPurpose.CIVIC_RECEPTION}) {
                assertTrue(c.doodads.stream().anyMatch(d -> d.definitionId().equals("colony-furnishings.desk")
                        &&c.topology.getRoomPurpose(d.cellX,d.cellY)==purpose),"missing terminal "+purpose+" / "+size[0]+"x"+size[1]+" / "+side);
            }
        }
    }

    @Test void breakingOnePaneLeavesItsNeighborAndBuildingOwnershipIntact() {
        var c=NeighborhoodBuildingsTest.open(28,26);
        BuildingShellCore.carve(SHELL,c.grid,c.topology,c.doodads,c.rng,
                NeighborhoodBuildingsTest.config(1),new BuildingPlacement(BuildingPlacement.Side.TOP,true));
        c.topology.tagDefaultWalls(c.grid);BuildingFloodFill.populate(c.topology,42);
        var pair=adjacentPair(BuildingWindowTestSupport.windowsOwnedBy(c.grid,SHELL));
        assertNotNull(pair);
        var pane=pair[0];var neighbor=pair[1];
        int x=pane.structureCellX(),y=pane.structureCellY();
        Direction out=BuildingWindowTestSupport.outwardFromOwner(pane);
        int owner=c.topology.getBuildingId(x,y);
        var navigation=new NavigationService(c.grid,c.topology);
        var editor=new MapEditor(navigation);
        assertTrue(editor.damageEdgeBarrier(x,y,out,SharedEdgeBarrier.Kind.WINDOW.structure()));
        assertTrue(c.grid.isSharedEdgePassable(x,y,out));
        assertFalse(c.grid.isSharedEdgePassable(neighbor.structureCellX(),neighbor.structureCellY(),out));
        assertEquals(owner,c.topology.getBuildingId(x,y));
        assertTrue(owner>0);assertTrue(navigation.isNavigationTopologyDirty());
    }

    private static SharedEdgeBarrier[] adjacentPair(List<SharedEdgeBarrier> windows) {
        for(var a:windows) for(var b:windows)
            if(BuildingWindowTestSupport.outwardFromOwner(a)==BuildingWindowTestSupport.outwardFromOwner(b)
                    &&Math.abs(a.structureCellX()-b.structureCellX())+Math.abs(a.structureCellY()-b.structureCellY())==1)
                return new SharedEdgeBarrier[]{a,b};
        return null;
    }
}
