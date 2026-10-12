package com.dillon.starsectormarines.battle.world.gen.fit;

import com.dillon.starsectormarines.battle.world.gen.Affordance;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import java.util.List;

/** Enclosed CIC: tactical island, four watch banks, equipment pockets and a hatch sentry. */
public final class ShipBridgeFitting implements RoomFitting {
    private static final String KIT = "ship-bridge.";
    private static final int[][] STEPS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    @Override public RoomPurpose purpose() { return RoomPurpose.BRIDGE; }

    @Override public void fit(RoomFloor floor) {
        int w = floor.canonicalWidth(), h = floor.canonicalHeight();
        if (w < 13 || h < 11 || TileRegistry.installed().doodad(KIT + "plot") == null) {
            new BridgeFitting().fit(floor);
            guard(floor);
            return;
        }
        int cx = w / 2, cy = h / 2;
        boolean cic = w >= 17 && h >= 13;
        int bankX = cic ? 4 : 3, bankY = cic ? 4 : 3;
        // The quieter command island reads separately from the circulation deck.
        if (cic) {
            surface(floor, 0, 0, w, h, "ship-floor-trim.passage");
            surface(floor, cx - 4, cy - 4, 9, 9, "ship-floor-trim.lounge-inset");
        }
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            if (floor.contains(x, y) && (!floor.contains(x - 1, y) || !floor.contains(x + 1, y)
                    || !floor.contains(x, y - 1) || !floor.contains(x, y + 1))) reserve(floor, x, y);
        }
        for (int y = cy - 2; y <= cy + 2; y++) for (int x = cx - 2; x <= cx + 2; x++)
            if (x == cx - 2 || x == cx + 2 || y == cy - 2 || y == cy + 2) reserve(floor, x, y);
        for (Doorway door : floor.localDoors()) floor.reserveLane(door.x() - 1, door.y() - 1, 3, 3);
        guard(floor);

        if (put(floor, cx - 1, cy - 1, "plot")) task(floor, cx, cy - 2, cx, cy - 1);
        boolean helm = put(floor, cx - 1, cy + bankY, "helm");
        boolean systems = put(floor, cx - 1, cy - bankY, "systems");
        boolean west = put(floor, cx - bankX, cy - 1, "console-west");
        boolean east = put(floor, cx + bankX, cy - 1, "console-east");
        // A short watch covers plot, navigation and systems before doubling a bank.
        if (helm) task(floor, cx - 1, cy + bankY + 1, cx - 1, cy + bankY);
        if (systems) task(floor, cx + 1, cy - bankY - 1, cx + 1, cy - bankY);
        for (int y : new int[]{cy - 1, cy + 1}) {
            if (west) task(floor, cx - bankX - 1, y, cx - bankX, y);
            if (east) task(floor, cx + bankX + 1, y, cx + bankX, y);
        }
        if (helm) task(floor, cx + 1, cy + bankY + 1, cx + 1, cy + bankY);
        if (systems) task(floor, cx - 1, cy - bankY - 1, cx - 1, cy - bankY);
        if (cic) {
            for (int x : new int[]{2, w - 3}) for (int y : new int[]{2, h - 4})
                put(floor, x, y, "data-cabinet");
        } else {
            put(floor, cx - 5, cy - 1, "data-cabinet");
            put(floor, cx + 5, cy, "data-cabinet");
        }
    }

    /** One real hatch owns the post. Stand to its side, never in the straight approach. */
    private void guard(RoomFloor floor) {
        List<Doorway> doors = floor.localDoors();
        for (Doorway door : doors) for (int[] inward : STEPS) {
            int insideX = door.x() + inward[0], insideY = door.y() + inward[1];
            if (!floor.room().shape().contains(insideX, insideY)) continue;
            for (int side : new int[]{1, -1}) {
                int x = insideX - inward[1] * side, y = insideY + inward[0] * side;
                if (doors.stream().anyMatch(other -> Math.abs(x - other.x()) + Math.abs(y - other.y()) <= 1)) continue;
                if (floor.fixtureTask(x, y, Affordance.GUARD, door.x(), door.y())) return;
            }
        }
    }

    private boolean put(RoomFloor floor, int x, int y, String id) {
        return floor.placeAssembly(x, y, KIT + id, List.of());
    }
    private void task(RoomFloor floor, int x, int y, int fx, int fy) {
        int[] s = floor.toLocal(x, y), f = floor.toLocal(fx, fy);
        floor.fixtureTask(s[0], s[1], Affordance.WATCH, f[0], f[1]);
    }
    private void reserve(RoomFloor floor, int x, int y) {
        int[] cell = floor.toLocal(x, y);
        floor.reserveLane(cell[0], cell[1], 1, 1);
    }
    private void surface(RoomFloor floor, int x, int y, int w, int h, String id) {
        if (TileRegistry.installed().block(id) == null) return;
        int[] rect = floor.toLocalRect(x, y, w, h);
        floor.markFloorSurface(rect[0], rect[1], rect[2], rect[3], id);
    }
}
