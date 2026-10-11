package com.dillon.starsectormarines.battle.world.gen.fit;

import com.dillon.starsectormarines.battle.world.gen.Affordance;
import com.dillon.starsectormarines.battle.world.gen.fit.layout.LayoutOp;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import java.util.List;

/** Ship-only equipment in the existing bay's two-cell working bands; never consumes a berth. */
final class ShuttleBayEquipment {
    private static final String KIT = "shuttle-bay.";

    static boolean available() {
        TileRegistry registry = TileRegistry.installed();
        for (String id : List.of(KIT+"flight-desk", KIT+"service-cart", KIT+"ground-power",
                KIT+"fluid-skid", KIT+"wheels", KIT+"tie-down", "main-stores.rack",
                "main-stores.frame", "main-stores.pallet", "machine-shop.electrical", "main-stores.cartons"))
            if (registry.doodad(id) == null) return false;
        return registry.block(KIT+"landing-deck") != null;
    }

    void fit(RoomFloor floor) {
        int w = floor.canonicalWidth(), h = floor.canonicalHeight();
        // Office and mobile gear face the walking ring, storage faces it from the opposite side.
        station(floor, "flight-desk", 0, 3, 2, 4, 1, 4, Affordance.READOUT);
        for (int y=11; y+3<=h-2; y+=8)
            station(floor, "service-cart", 0, y, 2, y+1, 1, y+1, Affordance.REPAIR);
        for (int y=3; y+6<=h-2; y+=8) {
            if (floor.placeAssembly(w-2, y, "main-stores.rack", List.of(
                    new LayoutOp.Layer(0,0,KIT+"wheels"),
                    new LayoutOp.Layer(0,2,"machine-shop.electrical"),
                    new LayoutOp.Layer(0,4,"main-stores.cartons"),
                    new LayoutOp.Layer(0,0,"main-stores.frame"))))
                for(int slot=0;slot<3;slot++) task(floor,w-3,y+slot*2,w-2,y+slot*2,Affordance.STOW);
        }
        // Small gig bays still get ready stores without pretending they can hold a full picking rack.
        if (h<11 && floor.placeAssembly(w-2, 3, "main-stores.pallet", List.of(
                new LayoutOp.Layer(0,0,"main-stores.cartons"))))
            task(floor,w-3,3,w-2,3,Affordance.STOW);
        station(floor,"ground-power",3,h-2,4,h-3,4,h-2,Affordance.TEND);
        station(floor,"fluid-skid",w-7,h-2,w-6,h-3,w-6,h-2,Affordance.TEND);
    }
    private void station(RoomFloor floor,String id,int x,int y,int sx,int sy,int fx,int fy,Affordance work) {
        if (floor.placeAssembly(x,y,KIT+id,List.of())) task(floor,sx,sy,fx,fy,work);
    }
    private void task(RoomFloor floor,int x,int y,int fx,int fy,Affordance work) {
        int[] stand=floor.toLocal(x,y), target=floor.toLocal(fx,fy);
        floor.fixtureTask(stand[0],stand[1],work,target[0],target[1]);
    }
}
