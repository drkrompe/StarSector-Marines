package com.dillon.starsectormarines.battle.world.gen.fit;

import com.dillon.starsectormarines.battle.world.gen.fit.layout.LayoutOp;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import java.util.List;

/** Eight inward-facing seats around one briefing table, with an open presentation aisle. */
public final class ShipBriefingRoomFitting implements RoomFitting {
    private static final String KIT = "briefing-room.";
    @Override public RoomPurpose purpose() { return RoomPurpose.CONFERENCE_ROOM; }

    @Override public void fit(RoomFloor floor) {
        int w = floor.canonicalWidth(), h = floor.canonicalHeight();
        if (w < 14 || h < 10 || !available()) {
            RoomFittings.forPurpose(purpose()).fit(floor);
            return;
        }
        int x = (w-6)/2, y = (h-2)/2;
        reserve(floor,0,0,w,1); reserve(floor,0,h-1,w,1);
        reserve(floor,0,0,1,h); reserve(floor,w-1,0,1,h);
        reserve(floor,x-2,y-2,10,1); reserve(floor,x-2,y+3,10,1);
        reserve(floor,x-2,y-2,1,6); reserve(floor,x+7,y-2,1,6);
        for (Doorway door : floor.localDoors()) floor.reserveLane(door.x()-1,door.y()-1,3,3);

        // The map is visual content on the table, not a second physical fixture.
        if (floor.placeAssembly(x,y,KIT+"table",List.of(new LayoutOp.Layer(0,0,KIT+"tactical-map")))) {
            for (int seat : new int[]{x,x+2,x+4}) {
                put(floor,seat,y-1,"chair-back-bottom");
                put(floor,seat,y+2,"chair-back-top");
            }
            put(floor,x-1,y,"chair-back-left");
            put(floor,x+6,y,"chair-back-right");
        }
        put(floor,x+1,h-2,"display");
        put(floor,x-2,h-2,"lectern");
        // A briefing is an event. Furniture deliberately publishes no permanent jobs.
    }

    private static boolean available() {
        for (String id : List.of("table","tactical-map","display","lectern","chair-back-top",
                "chair-back-bottom","chair-back-left","chair-back-right"))
            if (TileRegistry.installed().doodad(KIT+id) == null) return false;
        return true;
    }
    private void put(RoomFloor floor,int x,int y,String id) {
        floor.placeAssembly(x,y,KIT+id,List.of());
    }
    private void reserve(RoomFloor floor,int x,int y,int w,int h) {
        int[] r=floor.toLocalRect(x,y,w,h); floor.reserveLane(r[0],r[1],r[2],r[3]);
    }
}
