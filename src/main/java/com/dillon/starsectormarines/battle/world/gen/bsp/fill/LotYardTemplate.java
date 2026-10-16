package com.dillon.starsectormarines.battle.world.gen.bsp.fill;

import com.dillon.starsectormarines.battle.world.gen.BlockLeaf;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.PlacementGuards;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomPose;
import com.dillon.starsectormarines.battle.world.model.BuildingKind;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.PointOfInterest;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import java.util.List;

/** A medium building plus its own garden, forecourt or service yard inside one claimed lot. */
final class LotYardTemplate {
    private LotYardTemplate() {}
    static boolean tryPlace(BlockLeaf lot,GenContext c,BuildingShellCore.BuildingConfig config,BuildingPlacement.Side front) {
        if (!canPlace(lot,c,front)) return false;
        if (c.rng.nextInt(3)!=0) return false;
        place(lot,c,config,front);return true;
    }
    static boolean canPlace(BlockLeaf lot,GenContext c,BuildingPlacement.Side front) {
        boolean vertical=front==BuildingPlacement.Side.TOP||front==BuildingPlacement.Side.BOTTOM;
        int width=vertical?lot.width():lot.height(),height=vertical?lot.height():lot.width();
        if(width<15||height<17||lot.left<1||lot.top<1||lot.right>=c.width-1||lot.bottom>=c.height-1) return false;
        boolean[][] roads=c.get(BspKeys.ROAD_CELLS),reserved=c.get(BspKeys.ROAD_RESERVATION);
        for(int y=lot.top;y<=lot.bottom;y++) for(int x=lot.left;x<=lot.right;x++) {
            if(!c.grid.isWalkable(x,y)||c.grid.isDoorway(x,y)||c.topology.hasBuildingKindHint(x,y)||c.topology.isFixture(x,y)
                    ||c.topology.getGroundKind(x,y)==GroundKind.STREET||c.topology.getGroundKind(x,y)==GroundKind.STRIPED
                    ||(roads!=null&&roads[x][y])||(reserved!=null&&reserved[x][y])
                    ||PlacementGuards.touchesEdgeBarrier(c.grid,x,y)) return false;
        }
        for(var pad:c.landingPads) if(lot.left<=pad.right()+2&&lot.right>=pad.left()-2
                &&lot.top<=pad.top()+2&&lot.bottom>=pad.bottom()-2) return false;
        for(var d:c.doodads) if(lot.left<d.cellX+d.footprintCellsX&&lot.right>=d.cellX
                &&lot.top<d.cellY+d.footprintCellsY&&lot.bottom>=d.cellY) return false;
        return true;
    }
    static void place(BlockLeaf lot,GenContext c,BuildingShellCore.BuildingConfig config,BuildingPlacement.Side front) {
        if(!canPlace(lot,c,front)) throw new IllegalArgumentException("Occupied or undersized yard lot");
        int turn=switch(front){case TOP->0;case RIGHT->1;case BOTTOM->2;case LEFT->3;};
        RoomPose pose=new RoomPose(turn,false);
        int width=(turn&1)==0?lot.width():lot.height(),height=(turn&1)==0?lot.height():lot.width();
        int houseW=Math.min(11,width-6),houseH=Math.min(10,height-9);
        int hx=(width-houseW)/2,hy=6;
        int[] a=pose.map(hx,hy,width,height),b=pose.map(hx+houseW-1,hy+houseH-1,width,height);
        var house=new BlockLeaf(lot.left+Math.min(a[0],b[0]),lot.top+Math.min(a[1],b[1]),
                lot.left+Math.max(a[0],b[0]),lot.top+Math.max(a[1],b[1]),false);
        boolean home=config.buildingKind==BuildingKind.RESIDENTIAL;
        for(int y=lot.top;y<=lot.bottom;y++) for(int x=lot.left;x<=lot.right;x++) {
            c.topology.setGroundKind(x,y,home?GroundKind.GRASS:GroundKind.COURTYARD);
            if(!home)c.topology.setSurface(x,y,c.topology.surfaceIndex("colony.concrete"));
            c.markMadeGround(x,y);
        }
        var poi=BuildingShellCore.carve(house,c.grid,c.topology,c.doodads,c.rng,config,new BuildingPlacement(front,true));
        if(poi!=null) {
            // The lot's public interaction anchor belongs to its actual front approach.
            outer: for(int y=house.top;y<=house.bottom;y++)for(int x=house.left;x<=house.right;x++) {
                boolean facade=switch(front){case TOP->y==house.top;case BOTTOM->y==house.bottom;
                    case LEFT->x==house.left;case RIGHT->x==house.right;};
                if(!facade||!c.grid.isDoorway(x,y))continue;
                int ax=x+(front==BuildingPlacement.Side.LEFT?-1:front==BuildingPlacement.Side.RIGHT?1:0);
                int ay=y+(front==BuildingPlacement.Side.TOP?-1:front==BuildingPlacement.Side.BOTTOM?1:0);
                poi=new PointOfInterest(poi.kind,poi.left,poi.top,poi.right,poi.bottom,ax,ay,poi.interiorAnchorX,poi.interiorAnchorY);
                break outer;
            }
            c.pois.add(poi);
        }
        boolean[][] paths=new boolean[c.width][c.height];
        // Every real door gets a three-cell-wide approach all the way to its lot edge.
        for(int y=house.top;y<=house.bottom;y++) for(int x=house.left;x<=house.right;x++) {
            if(!c.grid.isDoorway(x,y))continue;
            if(x==house.left||x==house.right) {
                int lo=x==house.left?lot.left:house.right+1,hi=x==house.left?house.left-1:lot.right;
                for(int px=lo;px<=hi;px++)for(int py=Math.max(lot.top,y-1);py<=Math.min(lot.bottom,y+1);py++)paths[px][py]=true;
            }
            if(y==house.top||y==house.bottom) {
                int lo=y==house.top?lot.top:house.bottom+1,hi=y==house.top?house.top-1:lot.bottom;
                for(int py=lo;py<=hi;py++)for(int px=Math.max(lot.left,x-1);px<=Math.min(lot.right,x+1);px++)paths[px][py]=true;
            }
        }
        for(int y=lot.top;y<=lot.bottom;y++)for(int x=lot.left;x<=lot.right;x++)if(paths[x][y]) {
            c.topology.setGroundKind(x,y,GroundKind.COURTYARD);c.topology.setSurface(x,y,c.topology.surfaceIndex(home?"colony.terracotta":"colony.tread"));
        }
        // Furniture is confined to the front court; reserved access cells protect the approach.
        int[] f1=pose.map(2,2,width,height),f2=pose.map(width-3,5,width,height);
        int fl=lot.left+Math.min(f1[0],f2[0]),fb=lot.top+Math.min(f1[1],f2[1]);
        int fr=lot.left+Math.max(f1[0],f2[0]),ft=lot.top+Math.max(f1[1],f2[1]);
        if(home)for(int y=fb;y<=ft;y++)for(int x=fl;x<=fr;x++) {
            c.topology.setGroundKind(x,y,GroundKind.COURTYARD);
            c.topology.setSurface(x,y,c.topology.surfaceIndex("colony.terracotta"));
        }
        var groups=new BuildingWorkGroups(c.grid,c.topology,c.doodads);
        // Protect complete approaches, including cells farther from the threshold.
        for(int y=lot.top;y<=lot.bottom;y++)for(int x=lot.left;x<=lot.right;x++)if(paths[x][y])groups.reserveAccess(x,y);
        var furniture=home||config.buildingKind==BuildingKind.CIVIC?NeighborhoodFurnishing.dining(true)
                :config.buildingKind==BuildingKind.COMMERCIAL?CompactBuildingFurnishing.pair("shelf","shelf")
                :BuildingWorkGroups.pallets(1,"cases");
        groups.fit(fl-1,fb-1,fr+1,ft+1,null,furniture,home?1:2);
        if(home)groups.fit(fl-1,fb-1,fr+1,ft+1,null,new BuildingWorkGroups.Group(2,2,List.of(
                new BuildingWorkGroups.Part("crew-lounge.planter",0,0,true))),2);
        for(int y=lot.top+1;y<lot.bottom;y++)for(int x=lot.left+1;x<lot.right;x++) {
            if(x!=lot.left+1&&x!=lot.right-1&&y!=lot.top+1&&y!=lot.bottom-1)continue;
            // Secondary side gates also preserve cross-lot pedestrian access.
            if(paths[x][y]||Math.abs(y-lot.centerY())<=1||PlacementGuards.touchesEdgeBarrier(c.grid,x,y)
                    ||PlacementGuards.touchesDoorway(c.grid,x,y)||PlacementGuards.wouldStrandGround(c.grid,x,y))continue;
            String piece=x==lot.left+1&&y==lot.top+1?"corner-sw":x==lot.right-1&&y==lot.top+1?"corner-se"
                    :x==lot.left+1&&y==lot.bottom-1?"corner-nw":x==lot.right-1&&y==lot.bottom-1?"corner-ne"
                    :y==lot.top+1||y==lot.bottom-1?"straight-h":"straight-v";
            c.grid.setWalkable(x,y,false);c.grid.setSeeThrough(x,y,true);c.topology.setFixture(x,y,true);
            c.doodads.add(new Doodad(x,y,TileRegistry.installed().doodad("doodad.industrial-fence-"+piece)));
        }
    }
}
