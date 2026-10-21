package com.dillon.starsectormarines.battle.world.gen.fit;

import com.dillon.starsectormarines.battle.world.gen.Affordance;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import java.util.List;

/** Dry cardio, strength and mat stations beside a compact pool with a complete walking surround. */
public final class ShipGymFitting implements RoomFitting {
    private static final String KIT="ship-gym.";
    @Override public RoomPurpose purpose() { return RoomPurpose.GYMNASIUM; }

    @Override public void fit(RoomFloor floor) {
        int w=floor.canonicalWidth(), h=floor.canonicalHeight();
        if(GrandGymFitting.fits(w,h)) { new GrandGymFitting().fit(floor); return; }
        if(w<14 || h<10 || !available()) { new GymFitting().fit(floor); return; }
        reserve(floor,0,0,w,1);reserve(floor,0,h-1,w,1);
        reserve(floor,0,0,1,h);reserve(floor,w-1,0,1,h);
        reserve(floor,6,0,1,h);reserve(floor,0,4,7,1);
        for(Doorway door:floor.localDoors()) floor.reserveLane(door.x()-1,door.y()-1,3,3);

        // The basin is actual water for the shader and closed, see-through terrain for navigation.
        // Its surround stays walkable; neither the water nor the coping is furniture.
        surface(floor,7,2,6,6,"coping");
        reserve(floor,7,2,6,1);reserve(floor,7,7,6,1);
        reserve(floor,7,2,1,6);reserve(floor,12,2,1,6);
        int[] basin=floor.toLocalRect(8,3,4,4);
        floor.closeOff(basin[0],basin[1],basin[2],basin[3]);
        floor.markGround(basin[0],basin[1],basin[2],basin[3],GroundKind.WATER);
        surface(floor,8,3,4,4,"water");

        // Machines at their real size, a crewman's shoulders being 0.6 of a cell: treadmills and rowers one cell
        // wide and three long, the press a 2.2 m bar over a narrow bench. A sprite's top edge lies at its
        // footprint's highest row, so the console, flywheel and bar are up and the foot is the lowest row; each is
        // worked from the free cell below its foot, facing up its length, and the user is drawn on it
        // (WorkMotion.mountCells).
        for(int x:new int[]{1,2}) station(floor,KIT+"treadmill",x,5,x,4,x,5);
        for(int x:new int[]{4,5}) station(floor,KIT+"rower",x,5,x,4,x,5);
        reserve(floor,1,1,2,3);surface(floor,1,1,2,3,"mat");
        task(floor,1,1,2,1);task(floor,1,3,2,3);
        station(floor,KIT+"bench-press",3,2,4,1,4,2);
        if(floor.placeAssembly(8,8,KIT+"weights",List.of())) {
            task(floor,9,7,9,8);task(floor,11,7,11,8);
        }
    }
    private static boolean available() {
        var tiles=TileRegistry.installed();
        for(String id:List.of("treadmill","rower","bench-press","weights"))
            if(tiles.doodad(KIT+id)==null) return false;
        for(String id:List.of("mat","coping","water")) if(tiles.block(KIT+id)==null) return false;
        return true;
    }
    private void station(RoomFloor floor,String id,int x,int y,int sx,int sy,int fx,int fy) {
        if(floor.placeAssembly(x,y,id,List.of())) task(floor,sx,sy,fx,fy);
    }
    private void task(RoomFloor floor,int sx,int sy,int fx,int fy) {
        int[] s=floor.toLocal(sx,sy),f=floor.toLocal(fx,fy);
        floor.fixtureTask(s[0],s[1],Affordance.EXERCISE,f[0],f[1]);
    }
    private void reserve(RoomFloor floor,int x,int y,int w,int h) {
        int[] r=floor.toLocalRect(x,y,w,h);floor.reserveLane(r[0],r[1],r[2],r[3]);
    }
    private void surface(RoomFloor floor,int x,int y,int w,int h,String id) {
        int[] r=floor.toLocalRect(x,y,w,h);floor.markFloorSurface(r[0],r[1],r[2],r[3],KIT+id);
    }
}
