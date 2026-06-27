package com.dillon.starsectormarines.battle.nav.zone;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;

import java.util.ArrayList;
import java.util.List;

/**
 * Scans for {@link Portal}s emanating from each doorway cell.
 *
 * <p>Ported (slim) from MoonLight Engine's {@code engine.navigation.zone.PortalDetector}.
 * Dropped from the original: stair-link portals (no levels), gate vs
 * tower-entrance type distinction (single {@link com.dillon.starsectormarines.battle.nav.NavigationGrid#isDoorway doorway}
 * bit), the separate {@code PortalSide} record. On a single-level grid the
 * doorway cell + the connected zone fully describes a portal side.
 *
 * <p>Detection rule: each doorway cell is its own 1-cell zone (see
 * {@link ZoneDetector}). For each cardinal neighbor of a doorway cell whose
 * zone differs from the doorway's, emit one portal connecting the doorway
 * zone to the neighbor zone. Multiple neighbors in the same zone collapse to
 * a single portal — a doorway flanked twice by the same street only produces
 * one street connection.
 *
 * <p>Doorway cells are static during a battle (wall-breaches make non-doorway
 * rubble), so {@link ZoneGraph} caches the doorway-cell list once via
 * {@link #collectDoorwayCells} and re-detects portals over just that list on
 * each incremental rebuild — O(doorways) rather than the O(W×H) full-grid scan.
 */
public final class PortalDetector {

    private PortalDetector() {}

    /** Full-grid scan: collect every walkable doorway cell, then {@link #detect(NavigationGrid, int[], int[])}. */
    public static List<Portal> detect(NavigationGrid grid, int[] cellToZoneId) {
        return detect(grid, cellToZoneId, collectDoorwayCells(grid));
    }

    /**
     * Detect portals over a pre-collected list of doorway cell indices. The caller owns the list
     * (typically cached by {@link ZoneGraph}); cells that are no longer walkable doorways are
     * skipped defensively.
     */
    public static List<Portal> detect(NavigationGrid grid, int[] cellToZoneId, int[] doorwayCells) {
        int width = grid.getWidth();
        int height = grid.getHeight();
        List<Portal> portals = new ArrayList<>();
        // Per-doorway scratch: up to 4 distinct neighbor zones (one per cardinal).
        int[] seenZones = new int[4];

        for (int idx : doorwayCells) {
            if (!grid.isWalkableAt(idx) || !grid.isDoorwayAt(idx)) continue;
            int doorwayZone = cellToZoneId[idx];
            if (doorwayZone < 0) continue;
            int x = idx % width;
            int y = idx / width;

            int seenCount = 0;
            if (x + 1 < width)  seenCount = emitIfNew(grid, idx + 1,     cellToZoneId, doorwayZone, idx, portals, seenZones, seenCount);
            if (x - 1 >= 0)     seenCount = emitIfNew(grid, idx - 1,     cellToZoneId, doorwayZone, idx, portals, seenZones, seenCount);
            if (y + 1 < height) seenCount = emitIfNew(grid, idx + width, cellToZoneId, doorwayZone, idx, portals, seenZones, seenCount);
            if (y - 1 >= 0)     seenCount = emitIfNew(grid, idx - width, cellToZoneId, doorwayZone, idx, portals, seenZones, seenCount);
        }

        return portals;
    }

    /** Flat indices of every walkable doorway cell — the routing nodes the portal graph hangs off. */
    public static int[] collectDoorwayCells(NavigationGrid grid) {
        int size = grid.getWidth() * grid.getHeight();
        int[] buf = new int[size];
        int n = 0;
        for (int idx = 0; idx < size; idx++) {
            if (grid.isWalkableAt(idx) && grid.isDoorwayAt(idx)) buf[n++] = idx;
        }
        int[] out = new int[n];
        System.arraycopy(buf, 0, out, 0, n);
        return out;
    }

    /**
     * If {@code neighborIdx} lives in a different, not-yet-seen zone from
     * {@code doorwayZone}, appends one portal to {@code out} and returns the
     * incremented seen-count. Otherwise returns {@code seenCount} unchanged.
     */
    private static int emitIfNew(NavigationGrid grid, int neighborIdx, int[] cellToZoneId,
                                 int doorwayZone, int doorwayCellIdx, List<Portal> out,
                                 int[] seenZones, int seenCount) {
        if (!grid.isWalkableAt(neighborIdx)) return seenCount;
        int neighborZone = cellToZoneId[neighborIdx];
        if (neighborZone < 0 || neighborZone == doorwayZone) return seenCount;
        for (int i = 0; i < seenCount; i++) {
            if (seenZones[i] == neighborZone) return seenCount;
        }
        seenZones[seenCount] = neighborZone;
        int portalId = out.size();
        out.add(new Portal(portalId, doorwayZone, neighborZone, doorwayCellIdx));
        return seenCount + 1;
    }
}
