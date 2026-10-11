package com.dillon.starsectormarines.battle.world.gen.fit;

import com.dillon.starsectormarines.battle.world.gen.Affordance;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import java.util.ArrayList;
import java.util.List;

/** Basins opposite grouped toilet and shower cubicles, joined by a two-person aisle. */
public final class ShipWashroomFitting implements RoomFitting {
    private static final String KIT = "ship-washrooms.";

    @Override public RoomPurpose purpose() { return RoomPurpose.WASHROOM; }

    @Override public void fit(RoomFloor floor) {
        int width = floor.canonicalWidth(), height = floor.canonicalHeight();
        if (width < 4 || height < 5 || !available()) {
            new WashroomFitting().fit(floor);
            return;
        }
        int stalls = height - 2;
        reserve(floor, 0, 1, width, height - 3);
        // Continue each hatch through whichever bank it meets. Adjacent hatch
        // cells retain their full width rather than sharing one narrowed stub.
        for (Doorway door : floor.localDoors()) {
            int[] d = floor.toCanonical(door.x(), door.y());
            int x = Math.max(0, Math.min(width - 1, d[0]));
            if (d[1] < 1) reserve(floor, x, 0, 1, 2);
            else if (d[1] >= stalls) reserve(floor, x, stalls - 1, 1, 3);
        }
        int[] room = floor.toLocalRect(0, 0, width, height);
        floor.markFloorSurface(room[0], room[1], room[2], room[3], KIT + "deck");

        // Divide the surviving run by position, so a hatch cannot erase all
        // showers merely because it happened to land at the shower end.
        List<Integer> columns = new ArrayList<>();
        for (int x = 0; x < width; x++) {
            int[] r = floor.toLocalRect(x, stalls, 1, 2);
            if (floor.isFree(r[0], r[1], r[2], r[3])) columns.add(x);
        }
        int toilets = (columns.size() + 1) / 2;
        for (int i = 0; i < columns.size(); i++) {
            int x = columns.get(i);
            place(floor, i < toilets ? "toilet" : "shower", x, stalls, x, stalls - 1);
        }
        for (int x = 0; x < width; x++) place(floor, "sink", x, 0, x, 1);
    }

    private static boolean available() {
        var tiles = TileRegistry.installed();
        for (String id : List.of("sink", "toilet", "shower"))
            if (tiles.doodad(KIT + id) == null) return false;
        return tiles.block(KIT + "deck") != null;
    }
    private static void place(RoomFloor floor, String id, int x, int y, int sx, int sy) {
        if (!floor.placeAssembly(x, y, KIT + id, List.of())) return;
        int[] stand = floor.toLocal(sx, sy), fixture = floor.toLocal(x, y);
        floor.fixtureTask(stand[0], stand[1], Affordance.WASH, fixture[0], fixture[1]);
    }
    private static void reserve(RoomFloor floor, int x, int y, int w, int h) {
        int[] r = floor.toLocalRect(x, y, w, h);
        floor.reserveLane(r[0], r[1], r[2], r[3]);
    }
}
