package com.dillon.starsectormarines.battle.nav.zone;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

/**
 * The correctness oracle for {@link ZoneGraph#applyCellsOpened} (the incremental, merge-only
 * zone-graph update used in-battle): after any sequence of cell-opens, the incremental graph must
 * produce the <b>same zone partition</b> and the <b>same portal reachability</b> as a from-scratch
 * {@link ZoneGraph#rebuild()} on the identical grid. Raw zone ids differ (incremental leaves
 * tombstones; full rebuild is compact), so equivalence is checked on the canonicalized partitions,
 * not the ids.
 */
class ZoneGraphIncrementalTest {

    // ---------------------------------------------------------------- scenarios

    @Test
    void breachMergesTwoRooms() {
        NavigationGrid g = new NavigationGrid(9, 5);
        room(g, 1, 1, 3, 3);               // left room, cols 1-3
        room(g, 5, 1, 7, 3);               // right room, cols 5-7 (col 4 is solid wall)
        ZoneGraph inc = built(g);
        // Two separate zones to start; breaching col 4 should merge them.
        breach(g, inc, 4, 2);
        assertEquivalent(inc, g);
    }

    @Test
    void breachNextToDoorwayCollapsesItsPortals() {
        NavigationGrid g = new NavigationGrid(9, 5);
        room(g, 1, 1, 3, 3);
        room(g, 5, 1, 7, 3);
        doorway(g, 4, 2);                  // rooms initially linked only through this doorway
        ZoneGraph inc = built(g);
        // Breach the wall above the doorway: now the rooms are one zone, and the doorway's two
        // portals (to left + right) must collapse to one (both sides are the same zone now).
        breach(g, inc, 4, 1);
        assertEquivalent(inc, g);
    }

    @Test
    void batchOfBreachesInOneApply() {
        NavigationGrid g = new NavigationGrid(9, 5);
        room(g, 1, 1, 3, 3);
        room(g, 5, 1, 7, 3);
        ZoneGraph inc = built(g);
        breachBatch(g, inc, new int[][]{{4, 1}, {4, 3}});   // two openings folded in one call
        assertEquivalent(inc, g);
    }

    @Test
    void isolatedOpeningThenBridge() {
        NavigationGrid g = new NavigationGrid(9, 5);
        room(g, 1, 1, 3, 3);
        room(g, 5, 1, 7, 3);
        ZoneGraph inc = built(g);
        breach(g, inc, 4, 0);              // a wall cell with no walkable orthogonal neighbor → singleton
        assertEquivalent(inc, g);
        breach(g, inc, 4, 1);              // now bridges left/right; the singleton folds in too
        assertEquivalent(inc, g);
    }

    @Test
    void randomizedFuzzMatchesFullRebuild() {
        Random rng = new Random(20260627L);
        for (int trial = 0; trial < 40; trial++) {
            int w = 8 + rng.nextInt(12), h = 6 + rng.nextInt(10);
            NavigationGrid g = new NavigationGrid(w, h);
            // Random interior walkability (border stays solid) → a percolation of rooms + walls.
            for (int y = 1; y < h - 1; y++) {
                for (int x = 1; x < w - 1; x++) {
                    if (rng.nextInt(100) < 62) g.setWalkable(x, y, true);
                    if (g.isWalkable(x, y) && rng.nextInt(100) < 6) g.setDoorway(x, y, true);
                }
            }
            ZoneGraph inc = built(g);
            assertEquivalent(inc, g);      // initial state already agrees with a full build
            for (int step = 0; step < 30; step++) {
                List<int[]> walls = currentWalls(g);
                if (walls.isEmpty()) break;
                int batch = 1 + rng.nextInt(3);
                int[][] cells = new int[Math.min(batch, walls.size())][];
                for (int i = 0; i < cells.length && !walls.isEmpty(); i++) {
                    cells[i] = walls.remove(rng.nextInt(walls.size()));
                }
                breachBatch(g, inc, cells);
                assertEquivalent(inc, g);
            }
        }
    }

    // ---------------------------------------------------------------- grid setup helpers

    private static ZoneGraph built(NavigationGrid g) {
        ZoneGraph z = new ZoneGraph(g);
        z.rebuild();
        return z;
    }

    private static void room(NavigationGrid g, int x0, int y0, int x1, int y1) {
        for (int y = y0; y <= y1; y++) for (int x = x0; x <= x1; x++) g.setWalkable(x, y, true);
    }

    private static void doorway(NavigationGrid g, int x, int y) {
        g.setWalkable(x, y, true);
        g.setDoorway(x, y, true);
    }

    private static void breach(NavigationGrid g, ZoneGraph inc, int x, int y) {
        breachBatch(g, inc, new int[][]{{x, y}});
    }

    private static void breachBatch(NavigationGrid g, ZoneGraph inc, int[][] cells) {
        int[] opened = new int[cells.length];
        for (int i = 0; i < cells.length; i++) {
            g.setWalkable(cells[i][0], cells[i][1], true);
            opened[i] = g.index(cells[i][0], cells[i][1]);
        }
        inc.applyCellsOpened(opened);
    }

    private static List<int[]> currentWalls(NavigationGrid g) {
        List<int[]> out = new ArrayList<>();
        for (int y = 0; y < g.getHeight(); y++) {
            for (int x = 0; x < g.getWidth(); x++) {
                if (!g.isWalkable(x, y)) out.add(new int[]{x, y});
            }
        }
        return out;
    }

    // ---------------------------------------------------------------- equivalence oracle

    private static void assertEquivalent(ZoneGraph inc, NavigationGrid g) {
        ZoneGraph full = new ZoneGraph(g);
        full.rebuild();
        assertArrayEquals(canonical(zoneRaw(full, g)), canonical(zoneRaw(inc, g)),
                "zone partition diverged from a full rebuild");
        assertArrayEquals(canonical(reachRaw(full, g)), canonical(reachRaw(inc, g)),
                "portal reachability diverged from a full rebuild");
    }

    /** Per-cell raw zone id (-1 for walls). */
    private static int[] zoneRaw(ZoneGraph z, NavigationGrid g) {
        int[] raw = new int[g.getWidth() * g.getHeight()];
        for (int y = 0; y < g.getHeight(); y++)
            for (int x = 0; x < g.getWidth(); x++)
                raw[g.index(x, y)] = z.zoneIdAt(x, y);
        return raw;
    }

    /** Per-cell portal-connected-component id (-1 for walls), via a union-find over the portals. */
    private static int[] reachRaw(ZoneGraph z, NavigationGrid g) {
        int zc = z.getZones().size();
        int[] parent = new int[zc];
        for (int i = 0; i < zc; i++) parent[i] = i;
        for (Portal p : z.getPortals()) union(parent, p.getZoneIdA(), p.getZoneIdB());
        int[] raw = new int[g.getWidth() * g.getHeight()];
        for (int y = 0; y < g.getHeight(); y++) {
            for (int x = 0; x < g.getWidth(); x++) {
                int id = z.zoneIdAt(x, y);
                raw[g.index(x, y)] = id < 0 ? -1 : find(parent, id);
            }
        }
        return raw;
    }

    /** Relabel groups to 0,1,2,… in first-appearance order so two equal partitions with different
     *  raw ids compare equal; walls (-1) stay -1. */
    private static int[] canonical(int[] rawGroupPerCell) {
        int[] out = new int[rawGroupPerCell.length];
        Map<Integer, Integer> remap = new HashMap<>();
        int next = 0;
        for (int i = 0; i < rawGroupPerCell.length; i++) {
            int raw = rawGroupPerCell[i];
            if (raw < 0) { out[i] = -1; continue; }
            Integer m = remap.get(raw);
            if (m == null) { m = next++; remap.put(raw, m); }
            out[i] = m;
        }
        return out;
    }

    private static int find(int[] p, int a) { while (p[a] != a) { p[a] = p[p[a]]; a = p[a]; } return a; }
    private static void union(int[] p, int a, int b) { int ra = find(p, a), rb = find(p, b); if (ra != rb) p[ra] = rb; }
}
