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
 */
public final class PortalDetector {

    private PortalDetector() {}

    public static List<Portal> detect(NavigationGrid grid, int[] cellToZoneId) {
        int width = grid.getWidth();
        int height = grid.getHeight();
        List<Portal> portals = new ArrayList<>();
        // Per-doorway scratch: up to 4 distinct neighbor zones (one per cardinal).
        int[] seenZones = new int[4];

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int idx = y * width + x;
                if (!grid.isWalkableAt(idx) || !grid.isDoorwayAt(idx)) continue;
                int doorwayZone = cellToZoneId[idx];
                if (doorwayZone < 0) continue;

                int seenCount = 0;
                if (x + 1 < width)  seenCount = emitIfNew(grid, idx + 1,     cellToZoneId, doorwayZone, idx, portals, seenZones, seenCount);
                if (x - 1 >= 0)     seenCount = emitIfNew(grid, idx - 1,     cellToZoneId, doorwayZone, idx, portals, seenZones, seenCount);
                if (y + 1 < height) seenCount = emitIfNew(grid, idx + width, cellToZoneId, doorwayZone, idx, portals, seenZones, seenCount);
                if (y - 1 >= 0)     seenCount = emitIfNew(grid, idx - width, cellToZoneId, doorwayZone, idx, portals, seenZones, seenCount);
            }
        }

        return portals;
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
