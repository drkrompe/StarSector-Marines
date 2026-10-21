package com.dillon.starsectormarines.battle.world.gen.fit;

import com.dillon.starsectormarines.battle.world.gen.Affordance;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;

/**
 * A brig: two holding cells against one bulkhead, barred across their fronts,
 * and the guard post outside them.
 *
 * <p>A bulkhead divides the cells and a run of bars closes each, so a cell is
 * seen into and never walked into; each holds a bunk and a head. The rest of
 * the compartment is the guard's: a passage along the bars, a desk console and
 * a locker for what prisoners came aboard with. The one place published is the
 * {@link Affordance#GUARD} post at the desk, facing the cells, which is the
 * security rating's duty rather than anybody's recreation. The cells take the
 * side of the room with fewer hatches, so the way in never opens onto bars.
 *
 * <p>Holding nobody: the bunks publish no rest, because a prisoner is not crew.
 * Drawn for a 6-by-6 compartment; a smaller one is left bare.
 */
public final class BrigFitting implements RoomFitting {
    private static final String BUNK = "barracks-bunks.";
    private static final String WALL = "ship.neutral-bulkhead";

    @Override public RoomPurpose purpose() { return RoomPurpose.BRIG; }

    @Override public void fit(RoomFloor floor) {
        int w = floor.canonicalWidth(), h = floor.canonicalHeight();
        if (w < 6 || h < 6 || !available()) return;
        // Authored with the cells forward (low y); turned over when that side has the hatches.
        int forward = 0, aft = 0;
        for (int[] door : EndFloor.canonicalDoors(floor)) {
            if (door[1] < h / 2) forward++; else aft++;
        }
        EndFloor room = new EndFloor(floor, false, forward > aft);

        // The guard's passage along the bars, and the floor behind it.
        room.reserve(0, 4, w, 1);
        room.reserve(1, 5, w - 2, 1);
        room.clearHatches();
        room.surface(0, 4, w, 2, "ship-floor-trim.service-aisle");

        // The cells: a bulkhead between them, bars across each front.
        room.partition(2, 0, 1, 4, WALL);
        String bars = barsAlong(floor);
        for (int x = 0; x < w; x++) if (x != 2) room.place(x, 3, bars);
        cell(room, 0, 1);
        cell(room, w - 1, w - 3);

        // The post: a desk at whichever end of the passage is clear of the
        // hatches, its rating facing the bars; the property locker at the other.
        for (int end : new int[]{0, w - 1}) {
            int stand = end == 0 ? 1 : w - 2;
            if (room.put(end, h - 1, "ship-utility.console")
                    && room.task(stand, 4, Affordance.GUARD, stand, 3)) {
                room.put(w - 1 - end, h - 1, "ship-utility.supply-locker");
                return;
            }
        }
        // Both ends taken by hatches: the post is a place on the passage alone.
        for (int x = 1; x < w - 1; x++)
            if (x != 2 && !room.besideHatch(x, 4) && room.task(x, 4, Affordance.GUARD, x, 3)) return;
        for (int x = 1; x < w - 1; x++)
            if (x != 2 && room.task(x, 4, Affordance.GUARD, x, 3)) return;
    }

    /** One cell's bunk against the outboard bulkhead and the head beside it. */
    private static void cell(EndFloor room, int bunk, int head) {
        String bedding = BUNK + (room.flippedAcross() ? "bedding-high" : "bedding-low");
        room.put(bunk, 0, BUNK + "bed", bedding, BUNK + "frame");
        room.put(head, 0, "ship-washrooms.toilet");
    }

    /** The straight bar run that lies along the room's length as the room is posed. */
    private static String barsAlong(RoomFloor floor) {
        int[] along = floor.pose().mapDirection(1, 0);
        return along[0] != 0 ? "doodad.industrial-fence-straight-h" : "doodad.industrial-fence-straight-v";
    }

    private static boolean available() {
        var tiles = TileRegistry.installed();
        for (String id : new String[]{BUNK + "bed", "ship-washrooms.toilet", "ship-utility.console",
                "ship-utility.supply-locker", "doodad.industrial-fence-straight-h", "doodad.industrial-fence-straight-v"})
            if (tiles.doodad(id) == null) return false;
        return tiles.block(WALL) != null;
    }
}
