package com.dillon.starsectormarines.battle.nav.zone;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Flood-fills the {@link NavigationGrid} into connected zones.
 *
 * <p>Ported (slim) from MoonLight Engine's {@code engine.navigation.zone.ZoneDetector}.
 * Dropped from the original: multi-level support, gate / tower-entrance flags
 * (the only barrier on this grid is the doorway bit), the {@code fastutil}
 * {@code IntArrayList} BFS queue. Kept the head-pointer BFS pattern for cache
 * locality and the "barrier cells become their own 1-cell zones" rule that
 * makes doorway cells routable distinct nodes.
 *
 * <p>Flood-fill stops at:
 * <ul>
 *   <li>Non-walkable cells (walls)</li>
 *   <li>Doorway-flagged cells (zone boundaries — get their own zone in pass 2)</li>
 * </ul>
 */
public final class ZoneDetector {

    private ZoneDetector() {}

    public static final class Result {
        /** Detected zones in id order (id == index). */
        public final List<NavigationZone> zones;
        /** Flat array mapping cell index → zone id, or -1 if the cell isn't part of any zone (i.e. a wall). */
        public final int[] cellToZoneId;

        public Result(List<NavigationZone> zones, int[] cellToZoneId) {
            this.zones = zones;
            this.cellToZoneId = cellToZoneId;
        }
    }

    /**
     * Two-pass detection:
     * <ol>
     *   <li>BFS-flood every walkable, non-doorway cell into a connected zone.</li>
     *   <li>Each doorway cell becomes its own 1-cell zone — these are the
     *       routing nodes the portal graph hangs off.</li>
     * </ol>
     */
    public static Result detect(NavigationGrid grid) {
        int width = grid.getWidth();
        int height = grid.getHeight();
        int size = width * height;

        int[] cellToZoneId = new int[size];
        Arrays.fill(cellToZoneId, -1);

        List<NavigationZone> zones = new ArrayList<>();
        // Re-used per zone — sized for the worst case (a single zone covering
        // the entire grid). Avoids allocations during BFS.
        int[] bfsQueue = new int[size];
        int currentZoneId = 0;

        // ----- Pass 1 — flood interior (non-doorway) cells -----
        for (int startIdx = 0; startIdx < size; startIdx++) {
            if (cellToZoneId[startIdx] != -1) continue;
            if (!grid.isWalkableAt(startIdx)) continue;
            if (grid.isDoorwayAt(startIdx)) continue;

            int head = 0;
            int tail = 0;
            bfsQueue[tail++] = startIdx;
            cellToZoneId[startIdx] = currentZoneId;

            while (head < tail) {
                int idx = bfsQueue[head++];
                int x = idx % width;
                int y = idx / width;
                if (x + 1 < width)  tail = visitCardinal(grid, idx + 1,     cellToZoneId, bfsQueue, tail, currentZoneId);
                if (x - 1 >= 0)     tail = visitCardinal(grid, idx - 1,     cellToZoneId, bfsQueue, tail, currentZoneId);
                if (y + 1 < height) tail = visitCardinal(grid, idx + width, cellToZoneId, bfsQueue, tail, currentZoneId);
                if (y - 1 >= 0)     tail = visitCardinal(grid, idx - width, cellToZoneId, bfsQueue, tail, currentZoneId);
            }

            int[] cells = Arrays.copyOf(bfsQueue, tail);
            zones.add(new NavigationZone(currentZoneId, cells));
            currentZoneId++;
        }

        // ----- Pass 2 — doorway cells get their own 1-cell zone -----
        for (int idx = 0; idx < size; idx++) {
            if (cellToZoneId[idx] != -1) continue;
            if (!grid.isWalkableAt(idx)) continue;
            if (!grid.isDoorwayAt(idx)) continue;
            cellToZoneId[idx] = currentZoneId;
            zones.add(new NavigationZone(currentZoneId, new int[]{idx}));
            currentZoneId++;
        }

        return new Result(zones, cellToZoneId);
    }

    /**
     * If {@code nIdx} is unassigned, walkable, and not a doorway, tags it for
     * {@code zoneId} and appends to the BFS queue. Returns the new tail.
     */
    private static int visitCardinal(NavigationGrid grid, int nIdx, int[] cellToZoneId, int[] bfsQueue, int tail, int zoneId) {
        if (cellToZoneId[nIdx] != -1) return tail;
        if (!grid.isWalkableAt(nIdx)) return tail;
        if (grid.isDoorwayAt(nIdx)) return tail;
        cellToZoneId[nIdx] = zoneId;
        bfsQueue[tail] = nIdx;
        return tail + 1;
    }
}
