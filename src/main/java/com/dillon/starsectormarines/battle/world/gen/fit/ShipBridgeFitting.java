package com.dillon.starsectormarines.battle.world.gen.fit;

import com.dillon.starsectormarines.battle.world.gen.Affordance;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import java.util.List;

/** A central plot, four inward-facing watch banks and an open outer approach. */
public final class ShipBridgeFitting implements RoomFitting {
    private static final String KIT = "ship-bridge.";
    @Override public RoomPurpose purpose() { return RoomPurpose.BRIDGE; }

    @Override public void fit(RoomFloor floor) {
        int w=floor.canonicalWidth(), h=floor.canonicalHeight();
        if (w<13 || h<11 || TileRegistry.installed().doodad(KIT+"plot")==null) {
            new BridgeFitting().fit(floor);
            return;
        }
        int cx=w/2, cy=h/2;
        // Follow the actual outline, including chamfers, rather than its bounding box.
        for(int y=0;y<h;y++) for(int x=0;x<w;x++) {
            if(floor.contains(x,y) && (!floor.contains(x-1,y) || !floor.contains(x+1,y)
                    || !floor.contains(x,y-1) || !floor.contains(x,y+1))) reserve(floor,x,y);
        }
        for(int y=cy-2;y<=cy+2;y++) for(int x=cx-2;x<=cx+2;x++)
            if(x==cx-2 || x==cx+2 || y==cy-2 || y==cy+2) reserve(floor,x,y);
        for(Doorway door:floor.localDoors()) floor.reserveLane(door.x()-1,door.y()-1,3,3);

        if(put(floor,cx-1,cy-1,"plot")) task(floor,cx,cy-2,cx,cy-1);
        // Operators stand outboard of the banks and look over them toward the plot.
        if(put(floor,cx-1,cy+3,"helm"))
            for(int x:new int[]{cx-1,cx+1}) task(floor,x,cy+4,x,cy+3);
        if(put(floor,cx-1,cy-3,"systems"))
            for(int x:new int[]{cx-1,cx+1}) task(floor,x,cy-4,x,cy-3);
        if(put(floor,cx-3,cy-1,"console-west"))
            for(int y:new int[]{cy-1,cy+1}) task(floor,cx-4,y,cx-3,y);
        if(put(floor,cx+3,cy-1,"console-east"))
            for(int y:new int[]{cy-1,cy+1}) task(floor,cx+4,y,cx+3,y);
        put(floor,cx-5,cy-1,"data-cabinet");
        put(floor,cx+5,cy,"data-cabinet");
    }

    private boolean put(RoomFloor floor,int x,int y,String id) {
        return floor.placeAssembly(x,y,KIT+id,List.of());
    }
    private void task(RoomFloor floor,int x,int y,int fx,int fy) {
        int[] s=floor.toLocal(x,y), f=floor.toLocal(fx,fy);
        floor.fixtureTask(s[0],s[1],Affordance.WATCH,f[0],f[1]);
    }
    private void reserve(RoomFloor floor,int x,int y) {
        int[] cell=floor.toLocal(x,y);floor.reserveLane(cell[0],cell[1],1,1);
    }
}
