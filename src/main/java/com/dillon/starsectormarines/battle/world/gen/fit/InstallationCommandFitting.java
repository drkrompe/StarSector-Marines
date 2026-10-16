package com.dillon.starsectormarines.battle.world.gen.fit;

import com.dillon.starsectormarines.battle.world.gen.Affordance;
import com.dillon.starsectormarines.battle.world.gen.fit.ConsoleBankLayout.Cell;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** A ground command post: central plot, working console pockets and a continuous circulation loop. */
public final class InstallationCommandFitting implements RoomFitting {
    @Override public RoomPurpose purpose() { return RoomPurpose.KEEP_THRONE; }

    @Override public void fit(RoomFloor floor) {
        int w = floor.canonicalWidth(), h = floor.canonicalHeight();
        if (w < 3 || h < 3) return;
        int size = w >= 7 && h >= 5 ? 3 : 1;
        int px = (w - size) / 2, py = (h - size) / 2;
        // The plot is approached on all four sides; the entry side has a wider cross-aisle when it fits.
        reserve(floor, px - 1, py - 1, size + 2, 1);
        reserve(floor, px - 1, py + size, size + 2, 1);
        reserve(floor, px - 1, py, 1, size);
        reserve(floor, px + size, py, 1, size);
        reserve(floor, 0, h - 1, w, 1);
        if (h - 2 >= py + size) reserve(floor, 0, h - 2, w, 1);
        for (Doorway door : floor.localDoors()) {
            int[] d = floor.toCanonical(door.x(), door.y());
            int x = clamp(d[0], 0, w - 1), y = clamp(d[1], 0, h - 1);
            // Route to the nearest side of the loop rather than drawing a lane through the plot.
            if (d[0] < 0 || d[0] >= w) {
                int edge = d[0] < 0 ? px - 1 : px + size;
                reserve(floor, Math.min(x, edge), y, Math.abs(x - edge) + 1, 1);
                int join = clamp(y, py - 1, py + size);
                reserve(floor, edge, Math.min(y, join), 1, Math.abs(y - join) + 1);
            } else {
                int edge = d[1] < 0 ? py - 1 : py + size;
                reserve(floor, x, Math.min(y, edge), 1, Math.abs(y - edge) + 1);
                int join = clamp(x, px - 1, px + size);
                reserve(floor, Math.min(x, join), edge, Math.abs(x - join) + 1, 1);
            }
        }
        String table = size == 3 ? "ship-bridge.plot" : "doodad.military-tactical-table";
        if (floor.placeAssembly(px, py, table, List.of()))
            task(floor, px + size / 2, py + size, px + size / 2, py + size - 1);
        bank(floor, 0, px - 2, h, false);
        bank(floor, px + size + 1, w - 1, h, true);
    }

    /** Complete L/U assemblies are tried first; a tight or door-cut pocket takes a complete straight bank. */
    private static void bank(RoomFloor floor, int left, int right, int h, boolean uShape) {
        if (right < left) return;
        if (right - left >= 2 && h >= 5) {
            int top = h - 4;
            List<Cell> path = uShape ? stroke(left, top, left, 0, right, 0, right, top)
                    : stroke(left, top, left, 0, right, 0);
            if (placeBank(floor, path, false)) return;
            List<Cell> reflected = path.stream().map(c -> new Cell(left + right - c.x(), c.y())).toList();
            if (placeBank(floor, reflected, true)) return;
        }
        // Interior-facing straights never put a watchkeeper against the outside wall.
        for (int length : new int[]{3, 2}) for (int y = 0; y + length <= h; y++)
            if (placeBank(floor, stroke(uShape ? right : left, y,
                    uShape ? right : left, y + length - 1), !uShape)) return;
    }

    private static boolean placeBank(RoomFloor floor, List<Cell> cells, boolean operatorRight) {
        if (!floor.placeConsoleBank(cells, false, operatorRight)) return false;
        Set<Cell> stands = new HashSet<>();
        int jobs = 0;
        for (int i = 1; i < cells.size() - 1 && jobs < 2; i++) {
            Cell before = cells.get(i - 1), at = cells.get(i), after = cells.get(i + 1);
            int dx = at.x() - before.x(), dy = at.y() - before.y();
            if (after.x() - at.x() != dx || after.y() - at.y() != dy) continue;
            int side = operatorRight ? -1 : 1;
            Cell stand = new Cell(at.x() - side * dy, at.y() + side * dx);
            if (stands.add(stand) && task(floor, stand.x(), stand.y(), at.x(), at.y())) jobs++;
        }
        // A two-cell straight still affords one watch position.
        if (cells.size() == 2) {
            Cell at = cells.get(0), next = cells.get(1);
            int side = operatorRight ? -1 : 1;
            task(floor, at.x() - side * (next.y() - at.y()),
                    at.y() + side * (next.x() - at.x()), at.x(), at.y());
        }
        return true;
    }

    private static List<Cell> stroke(int... bends) {
        List<Cell> cells = new ArrayList<>();
        int x = bends[0], y = bends[1]; cells.add(new Cell(x, y));
        for (int i = 2; i < bends.length; i += 2) {
            int dx = Integer.signum(bends[i] - x), dy = Integer.signum(bends[i + 1] - y);
            while (x != bends[i] || y != bends[i + 1]) {
                x += dx; y += dy; cells.add(new Cell(x, y));
            }
        }
        return cells;
    }

    private static boolean task(RoomFloor floor, int sx, int sy, int fx, int fy) {
        int[] stand = floor.toLocal(sx, sy), fixture = floor.toLocal(fx, fy);
        return floor.fixtureTask(stand[0], stand[1], Affordance.WATCH, fixture[0], fixture[1]);
    }
    private static void reserve(RoomFloor floor, int x, int y, int w, int h) {
        int[] r = floor.toLocalRect(x, y, w, h); floor.reserveLane(r[0], r[1], r[2], r[3]);
    }
    private static int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }
}
