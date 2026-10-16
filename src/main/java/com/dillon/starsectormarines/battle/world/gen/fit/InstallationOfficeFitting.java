package com.dillon.starsectormarines.battle.world.gen.fit;

import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import com.dillon.starsectormarines.battle.world.gen.fit.AisleFitting.FixtureGroup;
import com.dillon.starsectormarines.battle.world.gen.fit.AisleFitting.FixtureGroup.Satellite;
import java.util.List;

/** Dispatch desks, a meeting nook and records storage around a clear cross aisle. */
public final class InstallationOfficeFitting implements RoomFitting {
    @Override public RoomPurpose purpose() { return RoomPurpose.CIVIC_OFFICE; }

    @Override public void fit(RoomFloor floor) {
        int w = floor.canonicalWidth(), h = floor.canonicalHeight();
        if (w < 8 || h < 8) {
            new AisleFitting(purpose(), FixtureGroup.of(
                    "colony-furnishings.desk", 3, 2,
                    new Satellite("mess-galley.stool", 0, 1))).fit(floor);
            return;
        }
        int aisle = (h - 2) / 2;
        reserve(floor, 0, 0, w, 1);
        reserve(floor, 0, h - 1, w, 1);
        reserve(floor, 0, 0, 1, h);
        reserve(floor, w - 1, 0, 1, h);
        reserve(floor, 0, aisle, w, 2);
        for (Doorway door : floor.localDoors()) {
            int[] d = floor.toCanonical(door.x(), door.y());
            int x = Math.max(0, Math.min(w - 1, d[0]));
            int from = Math.min(Math.max(0, d[1]), aisle);
            int to = Math.max(Math.min(h - 1, d[1]), aisle + 1);
            reserve(floor, x, from, 1, to - from + 1);
        }

        // Larger dispatch rooms keep one end for briefings and records rather
        // than repeating desks until the entire floor is an office grid.
        boolean meeting = w >= 11;
        int desksEnd = meeting ? w - 5 : w - 2;
        for (int x = 1; x <= desksEnd; x += 3) {
            desk(floor, x, 1, 2);
            desk(floor, x, h - 2, h - 3);
        }
        if (meeting) {
            int x = w - 4;
            if (free(floor, x, 1, 2, 2)) {
                put(floor, x, 1, "crew-lounge.coffee");
                put(floor, x, 2, "mess-galley.stool");
                put(floor, x + 1, 2, "mess-galley.stool");
            }
            put(floor, x, h - 2, "colony-furnishings.shelf");
            put(floor, x + 1, h - 2, "colony-furnishings.locker");
        }
    }

    private static void desk(RoomFloor floor, int x, int deskY, int seatY) {
        if (!free(floor, x, Math.min(deskY, seatY), 1, 2)) return;
        put(floor, x, deskY, "colony-furnishings.desk");
        put(floor, x, seatY, "mess-galley.stool");
    }

    private static boolean free(RoomFloor floor, int x, int y, int w, int h) {
        int[] r = floor.toLocalRect(x, y, w, h);
        return floor.isFree(r[0], r[1], r[2], r[3]);
    }

    private static void put(RoomFloor floor, int x, int y, String id) {
        floor.placeAssembly(x, y, id, List.of());
    }

    private static void reserve(RoomFloor floor, int x, int y, int w, int h) {
        int[] r = floor.toLocalRect(x, y, w, h);
        floor.reserveLane(r[0], r[1], r[2], r[3]);
    }
}
