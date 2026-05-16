package com.dillon.starsectormarines.battle.nav.zone;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Hierarchical navigation layer built on top of {@link NavigationGrid}.
 * Holds {@link NavigationZone}s, the cell→zone map, all {@link Portal}s, and
 * a per-zone portal-id index for O(1) "what portals touch this zone" lookups.
 *
 * <p>Ported (slim) from MoonLight Engine's {@code PortalGraphService}. Dropped
 * from the original: multi-level support, {@code IntraZoneCostComputer}'s
 * Theta* distance matrices (cell A* on the small grid is fast enough),
 * connected-component caching, fastutil. Kept the orchestrator shape and the
 * "rebuild is a single entry point" rule so the AI side never sees a
 * partially-built graph.
 *
 * <p>{@link #rebuild()} re-runs both detectors from scratch — cheap on a
 * ~100×50 grid even on the worst-case full scan. A local incremental rebuild
 * is a future optimization; the AI win is in the queryable shape, not the
 * detection speed.
 */
public final class ZoneGraph {

    private final NavigationGrid grid;
    private List<NavigationZone> zones = Collections.emptyList();
    private List<Portal> portals = Collections.emptyList();
    private int[] cellToZoneId = new int[0];

    public ZoneGraph(NavigationGrid grid) {
        this.grid = grid;
    }

    /** Rebuilds the entire zone + portal graph from the current grid state. */
    public void rebuild() {
        ZoneDetector.Result zr = ZoneDetector.detect(grid);
        this.zones = zr.zones;
        this.cellToZoneId = zr.cellToZoneId;
        this.portals = PortalDetector.detect(grid, cellToZoneId);
        indexPortalsOnZones();
    }

    /**
     * Walks {@link #portals} and registers each one on both of its zones'
     * portal-id lists. Cleared first so a rebuild after a previous detect
     * doesn't double up.
     */
    private void indexPortalsOnZones() {
        for (NavigationZone z : zones) z.getPortalIds().clear();
        for (Portal p : portals) {
            NavigationZone za = zoneById(p.getZoneIdA());
            NavigationZone zb = zoneById(p.getZoneIdB());
            if (za != null) za.addPortalId(p.getPortalId());
            if (zb != null) zb.addPortalId(p.getPortalId());
        }
    }

    // ---- Queries -----------------------------------------------------------

    public List<NavigationZone> getZones()   { return zones; }
    public List<Portal>         getPortals() { return portals; }

    /** Zone id containing the given cell, or -1 if the cell isn't part of any zone (e.g. a wall). */
    public int zoneIdAt(int x, int y) {
        if (!grid.inBounds(x, y)) return -1;
        return cellToZoneId[grid.index(x, y)];
    }

    public NavigationZone zoneAt(int x, int y) {
        int id = zoneIdAt(x, y);
        return id < 0 ? null : zoneById(id);
    }

    public NavigationZone zoneById(int id) {
        if (id < 0 || id >= zones.size()) return null;
        return zones.get(id);
    }

    public Portal portalById(int id) {
        if (id < 0 || id >= portals.size()) return null;
        return portals.get(id);
    }

    /**
     * Returns the zone ids reachable from {@code zoneId} in one portal hop,
     * de-duplicated. Useful for "what rooms can I reach from here" AI queries
     * without having to walk the full portal list.
     */
    public List<Integer> adjacentZones(int zoneId) {
        NavigationZone z = zoneById(zoneId);
        if (z == null) return Collections.emptyList();
        List<Integer> out = new ArrayList<>();
        for (int portalId : z.getPortalIds()) {
            Portal p = portalById(portalId);
            if (p == null) continue;
            int other = p.otherZone(zoneId);
            if (other < 0 || out.contains(other)) continue;
            out.add(other);
        }
        return out;
    }

    /**
     * BFS over the portal graph — returns true if {@code goalZone} is
     * reachable from {@code startZone} ignoring portal cost. Used by AI to
     * skip impossible objectives (e.g., a charge site inside a fully
     * isolated room) without paying the cost of cell-level pathfinding.
     */
    public boolean areConnected(int startZone, int goalZone) {
        if (startZone < 0 || goalZone < 0) return false;
        if (startZone == goalZone) return true;
        boolean[] visited = new boolean[zones.size()];
        int[] queue = new int[zones.size()];
        int head = 0, tail = 0;
        queue[tail++] = startZone;
        visited[startZone] = true;
        while (head < tail) {
            int cur = queue[head++];
            NavigationZone z = zoneById(cur);
            if (z == null) continue;
            for (int portalId : z.getPortalIds()) {
                Portal p = portalById(portalId);
                if (p == null) continue;
                int other = p.otherZone(cur);
                if (other < 0 || visited[other]) continue;
                if (other == goalZone) return true;
                visited[other] = true;
                queue[tail++] = other;
            }
        }
        return false;
    }
}
