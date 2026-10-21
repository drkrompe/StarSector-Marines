package com.dillon.starsectormarines.battle.world.gen.fit;

import com.dillon.starsectormarines.battle.world.gen.Affordance;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;

/**
 * A frigate's rec lounge with a chapel at its quiet end.
 *
 * <p>One aisle runs the length of the compartment. On the lounge side a sofa
 * faces a low table and a card table has its stools either side; at the other
 * end two pews face a small altar, the near pew across the aisle from it. The
 * chapel takes whichever end has fewer hatches, so nobody walks through a
 * service to reach the card table. Every seat, the pews' included, is an
 * {@link Affordance#UNWIND} place: the chapel is somewhere a crew chooses to
 * be off watch, not a posting.
 *
 * <p>Drawn for Second Wind's 8-by-6 reserve, from the crew lounge's own
 * furniture. Another size falls back to the ordinary lounge.
 */
public final class ChapelLoungeFitting implements RoomFitting {
    private static final String KIT = "crew-lounge.";

    @Override public RoomPurpose purpose() { return RoomPurpose.CREW_LOUNGE; }

    @Override public void fit(RoomFloor floor) {
        int w = floor.canonicalWidth(), h = floor.canonicalHeight();
        if (w < 8 || h < 6 || !available()) { new LoungeFitting().fit(floor); return; }
        int far = EndFloor.doorsAtFarEnd(floor), near = EndFloor.canonicalDoors(floor).size() - far;
        EndFloor room = new EndFloor(floor, far > near, false);
        int chapel = w - 3;

        room.reserve(0, 3, w, 1);
        room.reserve(chapel - 2, 0, 2, 3);
        room.clearHatches();
        room.surface(0, 0, chapel, h, "ship-floor-trim.lounge");
        room.surface(chapel, 0, 3, h, "ship-floor-trim.lounge-inset");

        // A sofa facing its low table.
        if (room.put(0, 0, KIT + "sofa-low")) {
            for (int x = 0; x < 3; x++) room.task(x, 1, Affordance.UNWIND, Math.min(x, 1), 2);
            room.put(0, 2, KIT + "coffee", KIT + "magazines");
        }
        // Cards across the aisle, a stool at each corner of the table.
        if (room.put(1, 4, KIT + "card-table", KIT + "game")) {
            stool(room, 0, 4, 1, 4); stool(room, 0, 5, 1, 5);
            stool(room, 3, 4, 2, 4); stool(room, 3, 5, 2, 5);
        }
        room.put(chapel - 1, h - 1, KIT + "planter");

        // The chapel: an altar against the end of the near side, the pews facing it.
        room.put(chapel, 0, KIT + "coffee");
        room.put(chapel + 2, 0, KIT + "planter");
        for (int pew : new int[]{2, h - 1}) {
            if (!room.put(chapel, pew, KIT + "sofa-high")) continue;
            for (int x = chapel; x < chapel + 3; x++) room.task(x, pew - 1, Affordance.UNWIND, chapel + 1, 0);
        }
    }

    private static void stool(EndFloor room, int x, int y, int fx, int fy) {
        if (room.task(x, y, Affordance.UNWIND, fx, fy)) room.pave(x, y, KIT + "stool");
    }

    private static boolean available() {
        var tiles = TileRegistry.installed();
        for (String id : new String[]{"sofa-low", "sofa-high", "coffee", "card-table", "planter", "stool"})
            if (tiles.doodad(KIT + id) == null) return false;
        return true;
    }
}
