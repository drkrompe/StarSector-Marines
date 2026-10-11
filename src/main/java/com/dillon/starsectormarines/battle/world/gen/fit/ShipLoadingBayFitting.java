package com.dillon.starsectormarines.battle.world.gen.fit;

import com.dillon.starsectormarines.battle.world.gen.Affordance;
import com.dillon.starsectormarines.battle.world.gen.fit.layout.LayoutOp;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import java.util.List;

/** Receiving apron and transfer equipment, with staged provisions and a single picking rack. */
public final class ShipLoadingBayFitting implements RoomFitting {
    private static final String KIT = "loading-bay.";
    @Override public RoomPurpose purpose() { return RoomPurpose.LOADING_BAY; }
    @Override public boolean handed() { return true; }
    @Override public List<Hookup> hookups(RoomShape canonical) {
        return new LoadingBayFitting().hookups(canonical);
    }

    @Override public void fit(RoomFloor floor) {
        int w=floor.canonicalWidth(), h=floor.canonicalHeight();
        if (w<18 || h<12 || !available()) { new LoadingBayFitting().fit(floor); return; }
        // A four-cell receiving apron meets a three-cell transverse handling aisle.
        reserve(floor,0,0,4,h);
        reserve(floor,0,5,w-3,3);
        reserve(floor,0,0,w,1); reserve(floor,0,h-1,w,1);
        reserve(floor,w-1,0,1,h); reserve(floor,w-4,0,1,h);
        for (Doorway door : floor.localDoors()) floor.reserveLane(door.x()-1,door.y()-1,3,3);

        if (floor.placeAssembly(4,1,KIT+"manifest-desk",List.of()))
            task(floor,5,3,5,2,Affordance.READOUT);
        if (floor.placeAssembly(4,h-4,KIT+"pallet-tug",List.of()))
            task(floor,3,h-3,4,h-3,Affordance.TEND);
        if (floor.placeAssembly(7,h-3,KIT+"conveyor",List.of())) {
            task(floor,8,h-4,8,h-3,Affordance.STOW);
            task(floor,11,h-4,11,h-3,Affordance.REPAIR);
        }
        // Whole loads sit on independent pallets; the staging patches own no collision.
        pallet(floor,9,2,"insulated-tote",9,4,9,3);
        pallet(floor,12,2,"provision-cartons",12,4,12,3);
        pallet(floor,w-3,h-3,"provision-cartons",w-4,h-3,w-3,h-3);
        if (floor.placeAssembly(w-3,2,"main-stores.rack",List.of(
                new LayoutOp.Layer(0,0,KIT+"insulated-tote"),
                new LayoutOp.Layer(0,2,KIT+"insulated-tote"),
                new LayoutOp.Layer(0,4,KIT+"provision-cartons"),
                new LayoutOp.Layer(0,0,"main-stores.frame"))))
            for(int y=2;y<8;y+=2) task(floor,w-4,y,w-3,y,Affordance.STOW);
    }

    private static boolean available() {
        for(String id:List.of(KIT+"manifest-desk",KIT+"pallet-tug",KIT+"conveyor",
                KIT+"insulated-tote",KIT+"provision-cartons","main-stores.rack",
                "main-stores.frame","main-stores.pallet"))
            if(TileRegistry.installed().doodad(id)==null) return false;
        return TileRegistry.installed().block(KIT+"staging-deck")!=null;
    }
    private void pallet(RoomFloor floor,int x,int y,String load,int sx,int sy,int fx,int fy) {
        if(floor.placeAssembly(x,y,"main-stores.pallet",List.of(new LayoutOp.Layer(0,0,KIT+load)))) {
            int[] r=floor.toLocalRect(x,y,2,3);
            floor.markFloorSurface(r[0],r[1],r[2],r[3],KIT+"staging-deck");
            task(floor,sx,sy,fx,fy,Affordance.STOW);
        }
    }
    private void reserve(RoomFloor floor,int x,int y,int w,int h) {
        int[] r=floor.toLocalRect(x,y,w,h);floor.reserveLane(r[0],r[1],r[2],r[3]);
    }
    private void task(RoomFloor floor,int sx,int sy,int fx,int fy,Affordance work) {
        int[] s=floor.toLocal(sx,sy),f=floor.toLocal(fx,fy);
        floor.fixtureTask(s[0],s[1],work,f[0],f[1]);
    }
}
