package com.dillon.starsectormarines.battle.nav.zone;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;

import java.util.ArrayList;
import java.util.Arrays;
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
 * <p>{@link #rebuild()} re-runs both detectors from scratch — O(W×H). On large
 * maps that's a per-wall-breach hitch, so {@link #applyCellsOpened} is the
 * <b>incremental</b> path used in-battle: every dirty trigger only ever makes a
 * cell <em>walkable</em> ({@code damageWall}, {@code flipCellToRubble}), so
 * zones only ever <b>merge/grow, never split</b> — the textbook incremental
 * connected-components-under-insertion problem. An opened cell unions the
 * interior zones it bridges (relabel the <em>smaller</em> into the larger —
 * weighted union), and portals re-detect over a cached doorway list (doorways
 * are static — breaches make non-doorway rubble). Per-breach cost drops from
 * O(W×H) to O(smaller-merged-zone + doorways). {@code rebuild()} stays the
 * initial build, the correctness oracle, and the fallback. (Were splits ever
 * introduced — walls <em>built</em> mid-battle — the merge-only invariant breaks
 * and a bounded re-flood would be needed; nothing does that today.)
 */
public final class ZoneGraph {

    private final NavigationGrid grid;
    private List<NavigationZone> zones = Collections.emptyList();
    private List<Portal> portals = Collections.emptyList();
    private int[] cellToZoneId = new int[0];
    /** Walkable doorway cells — static during a battle, cached at {@link #rebuild()} so the
     *  incremental portal re-detect is O(doorways) rather than an O(W×H) grid scan. */
    private int[] doorwayCells = new int[0];

    public ZoneGraph(NavigationGrid grid) {
        this.grid = grid;
    }

    /** Rebuilds the entire zone + portal graph from the current grid state (O(W×H)). */
    public void rebuild() {
        ZoneDetector.Result zr = ZoneDetector.detect(grid);
        this.zones = zr.zones;                     // ArrayList — mutated in place by applyCellsOpened
        this.cellToZoneId = zr.cellToZoneId;
        this.doorwayCells = PortalDetector.collectDoorwayCells(grid);
        this.portals = PortalDetector.detect(grid, cellToZoneId, doorwayCells);
        indexPortalsOnZones();
    }

    /**
     * Incrementally fold a batch of just-opened cells into the existing graph (the in-battle path —
     * see the class note). Each cell merges the interior zones it bridges; portals then re-detect
     * over the cached doorway list. Produces the same zone <em>partition</em> + portal connectivity
     * as a full {@link #rebuild()} (asserted by {@code ZoneGraphIncrementalTest}); absorbed zones
     * remain as empty tombstones so {@code zoneId == index} stays stable.
     */
    public void applyCellsOpened(int[] openedCells) {
        if (cellToZoneId.length != grid.getWidth() * grid.getHeight()) { rebuild(); return; }
        for (int c : openedCells) openCell(c);
        this.portals = PortalDetector.detect(grid, cellToZoneId, doorwayCells);
        indexPortalsOnZones();
    }

    /** Fold one just-opened cell into the zone partition (no portal re-detect — the caller batches that). */
    private void openCell(int c) {
        if (c < 0 || c >= cellToZoneId.length) return;
        if (cellToZoneId[c] >= 0) return;          // already zoned (duplicate dirty entry)
        if (!grid.isWalkableAt(c)) return;         // defensive: not actually walkable

        if (grid.isDoorwayAt(c)) {
            // Defensive: a newly-walkable doorway becomes its own singleton routing zone and joins
            // the cached doorway list. Breaches make non-doorway rubble, so this is unreachable today.
            newSingletonZone(c);
            doorwayCells = Arrays.copyOf(doorwayCells, doorwayCells.length + 1);
            doorwayCells[doorwayCells.length - 1] = c;
            return;
        }

        int width = grid.getWidth();
        int height = grid.getHeight();
        int x = c % width;
        int y = c / width;
        int[] cand = new int[4];
        int nc = 0;
        nc = addInteriorNeighbor(c + 1,     x + 1 < width,  cand, nc);
        nc = addInteriorNeighbor(c - 1,     x - 1 >= 0,     cand, nc);
        nc = addInteriorNeighbor(c + width, y + 1 < height, cand, nc);
        nc = addInteriorNeighbor(c - width, y - 1 >= 0,     cand, nc);

        if (nc == 0) { newSingletonZone(c); return; }   // isolated opening

        // Weighted union: survivor = the candidate with the most cells, so the relabel touches the
        // smaller zones (the per-merge cost bound).
        int survivor = cand[0];
        for (int i = 1; i < nc; i++) {
            if (zones.get(cand[i]).getCellCount() > zones.get(survivor).getCellCount()) survivor = cand[i];
        }
        NavigationZone surv = zones.get(survivor);
        for (int i = 0; i < nc; i++) {
            int z = cand[i];
            if (z == survivor) continue;
            NavigationZone absorbed = zones.get(z);
            int[] cells = absorbed.getCellIndices();
            for (int cell : cells) cellToZoneId[cell] = survivor;
            surv.absorb(cells);
            absorbed.clearCells();                  // tombstone — id slot kept so zoneId == index holds
        }
        cellToZoneId[c] = survivor;
        surv.addCell(c);
    }

    /** Adds {@code nIdx}'s zone to {@code cand} if it's an in-bounds, walkable, non-doorway, already-zoned interior cell (deduped). */
    private int addInteriorNeighbor(int nIdx, boolean inBounds, int[] cand, int nc) {
        if (!inBounds || !grid.isWalkableAt(nIdx) || grid.isDoorwayAt(nIdx)) return nc;
        int z = cellToZoneId[nIdx];
        if (z < 0) return nc;                        // not yet zoned (e.g. an also-just-opened neighbor)
        for (int i = 0; i < nc; i++) if (cand[i] == z) return nc;   // dedup
        cand[nc] = z;
        return nc + 1;
    }

    /** Appends a fresh 1-cell zone for {@code c} (id == index) and returns its id. */
    private int newSingletonZone(int c) {
        int id = zones.size();
        cellToZoneId[c] = id;
        zones.add(new NavigationZone(id, new int[]{c}));
        return id;
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
