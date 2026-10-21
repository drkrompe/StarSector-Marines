package com.dillon.starsectormarines.battle.world.gen.fit;

import com.dillon.starsectormarines.battle.world.gen.Affordance;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;

import java.util.List;

/**
 * A frigate's gymnasium: the equipment in the middle of the compartment and a
 * running loop round its bulkheads.
 *
 * <p>The loop is the room's circulation as well as its track. It is the whole
 * one-cell ring inside the bulkheads, so every hatch opens onto it wherever the
 * deck cut one, and every piece of equipment is reached off the cross-aisle
 * that joins it. Two exercise places stand on the loop itself, away from the
 * hatches; the rest are at the machines, the weights and the mat. All of them
 * are recreation, not anybody's watch.
 *
 * <p>Drawn for Second Wind's 12-by-8 reserve. A smaller room falls back to the
 * general gymnasium.
 */
public final class TrackGymFitting implements RoomFitting {
    private static final String KIT = "ship-gym.";
    static final String TRACK = "ship-floor-trim.service-pad";

    @Override public RoomPurpose purpose() { return RoomPurpose.GYMNASIUM; }

    @Override public void fit(RoomFloor floor) {
        int w = floor.canonicalWidth(), h = floor.canonicalHeight();
        if (w < 12 || h < 8 || !available()) { new GymFitting().fit(floor); return; }
        EndFloor room = new EndFloor(floor, false, false);
        // The loop, then the cross-aisle every station is worked from.
        room.reserve(0, 0, w, 1); room.reserve(0, h - 1, w, 1);
        room.reserve(0, 0, 1, h); room.reserve(w - 1, 0, 1, h);
        room.reserve(1, 4, w - 2, 1);
        room.reserve(5, 1, 2, h - 2);
        room.clearHatches();
        room.surface(0, 0, w, 1, TRACK); room.surface(0, h - 1, w, 1, TRACK);
        room.surface(0, 1, 1, h - 2, TRACK); room.surface(w - 1, 1, 1, h - 2, TRACK);

        // Cardio and the press along the forward side, each worked from the aisle.
        station(room, "treadmill", 1);
        station(room, "rower", 3);
        station(room, "bench-press", 7);
        station(room, "treadmill", 9);
        // A mat for floor work, and the weights rack facing it across the aisle.
        room.reserve(1, 5, 4, 2);
        room.surface(1, 5, 4, 2, KIT + "mat");
        room.task(1, 5, Affordance.EXERCISE, 1, 6);
        room.task(3, 6, Affordance.EXERCISE, 3, 5);
        if (room.put(7, 6, KIT + "weights")) {
            room.task(7, 5, Affordance.EXERCISE, 7, 6);
            room.task(9, 5, Affordance.EXERCISE, 9, 6);
        }
        // Somebody out on the loop, at the two middles furthest from any hatch.
        int laps = 0;
        for (int[] lap : new int[][]{{w / 2 - 1, 0, w / 2, 0}, {w / 2, h - 1, w / 2 - 1, h - 1},
                {0, h / 2, 0, h / 2 - 1}, {w - 1, h / 2 - 1, w - 1, h / 2}}) {
            if (laps < 2 && !room.besideHatch(lap[0], lap[1])
                    && room.task(lap[0], lap[1], Affordance.EXERCISE, lap[2], lap[3])) laps++;
        }
    }

    private static void station(EndFloor room, String id, int x) {
        if (room.put(x, 1, KIT + id)) room.task(x, 4, Affordance.EXERCISE, x, 3);
    }

    private static boolean available() {
        var tiles = TileRegistry.installed();
        for (String id : List.of("treadmill", "rower", "bench-press", "weights"))
            if (tiles.doodad(KIT + id) == null) return false;
        return tiles.block(KIT + "mat") != null;
    }
}
