package com.dillon.starsectormarines.battle.world.gen.fit;

import com.dillon.starsectormarines.battle.world.gen.Affordance;
import com.dillon.starsectormarines.battle.world.gen.fit.layout.LayoutOp;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import java.util.List;

/** Four recreation groups around a clear cross-passage; all furniture follows the room pose. */
public final class CrewLoungeFitting implements RoomFitting {
    private static final String KIT = "crew-lounge.";
    @Override public RoomPurpose purpose() { return RoomPurpose.CREW_LOUNGE; }

    @Override public void fit(RoomFloor floor) {
        int w = floor.canonicalWidth(), h = floor.canonicalHeight();
        if (w < 16 || h < 12 || TileRegistry.installed().doodad(KIT + "sofa-low") == null) {
            new LoungeFitting().fit(floor);
            return;
        }
        reserve(floor,0,0,w,1); reserve(floor,0,h-1,w,1);
        reserve(floor,0,0,1,h); reserve(floor,w-1,0,1,h);
        reserve(floor,w/2-1,0,2,h); reserve(floor,0,h/2-1,w,2);
        for (Doorway door : floor.localDoors())
            floor.reserveLane(door.x()-1,door.y()-1,3,3);

        // Screen-watching group: sofa faces past a low table toward its own console.
        if (free(floor,2,1,3,4)) {
            put(floor,2,1,"sofa-low");
            table(floor,2,3,"coffee","magazines");
            put(floor,2,4,"media");
            for (int x=2;x<5;x++) task(floor,x,2,x,4);
        }
        // Conversation nook: opposing sofas, with the low table at the open end.
        int back = h-5;
        if (free(floor,2,back,3,4)) {
            put(floor,2,back,"sofa-low"); put(floor,2,back+3,"sofa-high");
            table(floor,5,back+1,"coffee","magazines");
            for (int x=2;x<5;x++) {
                task(floor,x,back+1,x,back+3);
                task(floor,x,back+2,x,back);
            }
        }
        // Seated table games and standing pool have different spatial arrangements.
        int games = w-6;
        if (free(floor,games,back,2,4)) {
            table(floor,games,back+1,"card-table","game");
            for (int x=games;x<games+2;x++) {
                stool(floor,x,back,x,back+1);
                stool(floor,x,back+3,x,back+2);
            }
        }
        if (free(floor,games-1,1,5,4) && put(floor,games,2,"pool")) {
            task(floor,games-1,2,games,2);
            task(floor,games+3,3,games+2,3);
        }
        if (put(floor,w-2,1,"refreshments")) task(floor,w-3,1,w-2,1);
        put(floor,w-2,h-4,"books");
        put(floor,6,1,"planter"); put(floor,6,h-2,"planter");
    }

    private boolean put(RoomFloor floor,int x,int y,String id) {
        return floor.placeAssembly(x,y,KIT+id,List.of());
    }
    private void table(RoomFloor floor,int x,int y,String base,String overlay) {
        floor.placeAssembly(x,y,KIT+base,List.of(new LayoutOp.Layer(0,0,KIT+overlay)));
    }
    private void stool(RoomFloor floor,int x,int y,int fx,int fy) {
        if (task(floor,x,y,fx,fy)) {
            int[] cell=floor.toLocal(x,y);
            // A round seat needs no heading and stays traversable for its occupant.
            floor.pave(cell[0],cell[1],KIT+"stool");
        }
    }
    private boolean task(RoomFloor floor,int x,int y,int fx,int fy) {
        int[] s=floor.toLocal(x,y), f=floor.toLocal(fx,fy);
        return floor.fixtureTask(s[0],s[1],Affordance.UNWIND,f[0],f[1]);
    }
    private boolean free(RoomFloor floor,int x,int y,int w,int h) {
        int[] r=floor.toLocalRect(x,y,w,h);return floor.isFree(r[0],r[1],r[2],r[3]);
    }
    private void reserve(RoomFloor floor,int x,int y,int w,int h) {
        int[] r=floor.toLocalRect(x,y,w,h);floor.reserveLane(r[0],r[1],r[2],r[3]);
    }
}
