package com.dillon.starsectormarines.battle.world.gen.bsp.fill;

import com.dillon.starsectormarines.battle.world.gen.BlockLeaf;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import org.junit.jupiter.api.Test;
import java.util.ArrayDeque;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class NeighborhoodBuildingsTest {
    static BuildingShellCore.BuildingConfig config(int kind) {
        return switch(kind){case 0->BuildingResidentialFiller.HOME_CONFIG;case 1->BuildingCommercialFiller.CONFIG;
            case 2->BuildingIndustrialFiller.CONFIG;default->BuildingCivicFiller.CONFIG;};
    }
    static GenContext open(int width,int height) {
        var grid=CompactBuildingSnapshotSuite.open(width,height);var t=new CellTopology(width,height);
        for(int y=0;y<height;y++)for(int x=0;x<width;x++)t.setGroundKind(x,y,CellTopology.GroundKind.STONE);
        return new GenContext(grid,t,new Random(42),width,height,42);
    }
    @Test void mediumAndLargeHomesHaveBedroomsKitchenLivingAndDining() {
        for(int[] size:new int[][]{{11,10},{17,15}}) for(var side:BuildingPlacement.Side.values()) {
            var c=open(30,30);var leaf=new BlockLeaf(4,4,3+size[0],3+size[1],false);
            var poi=BuildingShellCore.carve(leaf,c.grid,c.topology,c.doodads,c.rng,config(0),new BuildingPlacement(side,true));
            c.pois.add(poi);
            for(RoomPurpose p:new RoomPurpose[]{RoomPurpose.BEDROOM,RoomPurpose.HOME_KITCHEN,RoomPurpose.APARTMENT_LIVING}) {
                boolean found=false;
                for(int y=leaf.top+1;y<leaf.bottom;y++)for(int x=leaf.left+1;x<leaf.right;x++)found|=c.topology.getRoomPurpose(x,y)==p;
                assertTrue(found,"missing "+p+" / "+side);
            }
            assertTrue(c.doodads.stream().anyMatch(d->d.definitionId().startsWith("doodad.residential-bed")),"bed / "+side);
            assertTrue(c.doodads.stream().anyMatch(d->d.definitionId().contains("galley")),"kitchen / "+side);
            assertTrue(c.doodads.stream().anyMatch(d->d.definitionId().contains("sofa")),"sofa / "+side);
            assertTrue(c.doodads.stream().anyMatch(d->d.definitionId().equals("mess-galley.table")||d.definitionId().equals("crew-lounge.coffee")),"table / "+side);
            assertTrue(c.doodads.stream().anyMatch(d->d.definitionId().equals("mess-galley.stool")),"seating / "+side);
            assertConnected(c);
        }
    }
    @Test void longShallowHomesArePartitionedAndReachable() {
        for(int[] size:new int[][]{{13,7},{7,13},{16,6},{6,16}}) {
            var c=open(26,26);var leaf=new BlockLeaf(3,3,2+size[0],2+size[1],false);
            var poi=BuildingShellCore.carve(leaf,c.grid,c.topology,c.doodads,c.rng,config(0));c.pois.add(poi);
            assertTrue(c.doodads.stream().anyMatch(d->d.definitionId().startsWith("doodad.residential-bed")));
            assertTrue(c.doodads.stream().anyMatch(d->d.definitionId().equals("colony-furnishings.galley")));
            assertConnected(c);
        }
    }
    @Test void everyLotThemeAndFrontageKeepsTheHouseSmallerAndRoutesOpen() {
        for(int kind=0;kind<4;kind++)for(var side:BuildingPlacement.Side.values()) {
            var c=open(32,32);var lot=new BlockLeaf(4,4,24,24,false);
            LotYardTemplate.place(lot,c,config(kind),side);
            assertEquals(1,c.pois.size());var house=c.pois.get(0);
            assertTrue((house.right-house.left+1)*(house.bottom-house.top+1)<lot.area()/2);
            assertTrue(c.doodads.stream().anyMatch(d->d.definitionId().startsWith("doodad.industrial-fence-")));
            for(var d:c.doodads)assertTrue(d.cellX>=lot.left&&d.cellY>=lot.top
                    &&d.cellX+d.footprintCellsX<=lot.right+1&&d.cellY+d.footprintCellsY<=lot.bottom+1);
            for(int y=0;y<c.height;y++)for(int x=0;x<c.width;x++)if(x<lot.left||x>lot.right||y<lot.top||y>lot.bottom) {
                assertTrue(c.grid.isWalkable(x,y));assertEquals(CellTopology.GroundKind.STONE,c.topology.getGroundKind(x,y));
            }
            assertConnected(c);
        }
    }
    @Test void roadReservationsRefuseTheWholeYardBeforeMutation() {
        var c=open(32,32);var reserved=new boolean[32][32];reserved[8][9]=true;c.put(BspKeys.ROAD_RESERVATION,reserved);
        var lot=new BlockLeaf(4,4,24,24,false);
        assertFalse(LotYardTemplate.tryPlace(lot,c,config(0),BuildingPlacement.Side.TOP));
        assertTrue(c.pois.isEmpty());assertTrue(c.doodads.isEmpty());assertEquals(new Random(42).nextInt(),c.rng.nextInt());
        for(int y=4;y<=24;y++)for(int x=4;x<=24;x++)assertEquals(CellTopology.GroundKind.STONE,c.topology.getGroundKind(x,y));
    }
    @Test void roadTerrainWithoutMasksAlsoRefusesTheYard() {
        for(var road:new CellTopology.GroundKind[]{CellTopology.GroundKind.STREET,CellTopology.GroundKind.STRIPED}) {
            var c=open(32,32);c.topology.setGroundKind(8,9,road);
            assertFalse(LotYardTemplate.tryPlace(new BlockLeaf(4,4,24,24,false),c,config(0),BuildingPlacement.Side.TOP));
            assertTrue(c.pois.isEmpty());assertTrue(c.doodads.isEmpty());
            assertEquals(new Random(42).nextInt(),c.rng.nextInt());
            assertEquals(road,c.topology.getGroundKind(8,9));
        }
    }
    static void assertConnected(GenContext c) {
        boolean[][] seen=new boolean[c.width][c.height];var queue=new ArrayDeque<int[]>();seen[0][0]=true;queue.add(new int[]{0,0});
        while(!queue.isEmpty()) {
            var at=queue.removeFirst();for(int[] d:new int[][]{{1,0},{-1,0},{0,1},{0,-1}}) {
                int x=at[0]+d[0],y=at[1]+d[1];
                if(c.grid.inBounds(x,y)&&!seen[x][y]&&c.grid.canTraverseCellStep(at[0],at[1],x,y)) {
                    seen[x][y]=true;queue.add(new int[]{x,y});
                }
            }
        }
        for(int y=0;y<c.height;y++)for(int x=0;x<c.width;x++)if(c.grid.isWalkable(x,y))assertTrue(seen[x][y],"stranded "+x+","+y);
        for(var p:c.pois) { assertTrue(seen[p.interiorAnchorX][p.interiorAnchorY],"interior anchor");assertTrue(seen[p.anchorCellX][p.anchorCellY],"exterior anchor"); }
    }
}
