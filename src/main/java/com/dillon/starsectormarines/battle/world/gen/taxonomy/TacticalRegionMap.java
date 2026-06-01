package com.dillon.starsectormarines.battle.world.gen.taxonomy;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.TraversalAxis;
import com.dillon.starsectormarines.battle.world.model.CellTopology;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

/**
 * Segmentation of a generated map's walkable space into {@link TacticalRegion}s
 * — the city's structural-taxonomy artifact. Generator-agnostic: it reads only
 * a {@link NavigationGrid} (walkability) + {@link CellTopology} (ground kind) +
 * an optional {@link TraversalAxis}, so the same segmenter serves the BSP city,
 * a future station recipe, or any other map type.
 *
 * <p><b>Why texture, not topology.</b> A porous open-with-obstacles city has no
 * real movement chokepoints (a unit walks around a building island through the
 * adjacent park), so a connectivity graph yields confident-looking roles that
 * correspond to nothing a unit experiences. This artifact instead reads the
 * <em>texture</em> of the walkable blob: it cuts the blob into regions at every
 * ground-kind change and every obstacle, then tags each region with cover /
 * exposure / enclosure / assault-depth attributes that a placement pass can
 * query directly. See {@code roadmap/mapgen/stories/structural-taxonomy.md}.
 *
 * <p>Pure analysis — draws no randomness and mutates nothing, so inserting the
 * stage that builds it leaves generated maps byte-identical.
 */
public final class TacticalRegionMap {

    /** Region id per cell; {@code -1} for non-walkable cells (walls / water / building shells). */
    private final int[][] regionIdAt;
    private final List<TacticalRegion> regions;
    private final int width;
    private final int height;

    private TacticalRegionMap(int[][] regionIdAt, List<TacticalRegion> regions, int width, int height) {
        this.regionIdAt = regionIdAt;
        this.regions = Collections.unmodifiableList(regions);
        this.width = width;
        this.height = height;
    }

    public List<TacticalRegion> regions() { return regions; }
    public int size() { return regions.size(); }

    /** Region id at {@code (x,y)}, or {@code -1} if non-walkable / out of bounds. */
    public int regionIdAt(int x, int y) {
        if (x < 0 || x >= width || y < 0 || y >= height) return -1;
        return regionIdAt[x][y];
    }

    /** The region at {@code (x,y)}, or {@code null} if non-walkable / out of bounds. */
    public TacticalRegion regionAt(int x, int y) {
        int id = regionIdAt(x, y);
        return id < 0 ? null : regions.get(id);
    }

    // --- builder ---

    /**
     * Segment {@code grid}'s walkable space into regions. {@code axis} drives the
     * geometric assault-depth attribute; pass {@code null} for legacy maps with
     * no attacker edge (depth comes back {@link DepthBand#UNSET}).
     */
    public static TacticalRegionMap build(NavigationGrid grid, CellTopology topology, TraversalAxis axis) {
        int w = grid.getWidth();
        int h = grid.getHeight();

        int[][] regionId = new int[w][h];
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) regionId[x][y] = -1;
        }

        // Pass A — flood-fill connected, same-kind walkable runs into regions,
        // accumulating area / centroid sums / bbox per region as we go.
        List<int[]> accum = new ArrayList<>(); // per region: {kindOrdinal, area, sumX, sumY, left, top, right, bottom}
        Deque<int[]> stack = new ArrayDeque<>();
        for (int sy = 0; sy < h; sy++) {
            for (int sx = 0; sx < w; sx++) {
                if (regionId[sx][sy] != -1 || !grid.isWalkable(sx, sy)) continue;
                RegionKind kind = RegionKind.fromGround(topology.getGroundKind(sx, sy));
                int id = accum.size();
                int[] a = {kind.ordinal(), 0, 0, 0, sx, sy, sx, sy};
                accum.add(a);

                stack.push(new int[]{sx, sy});
                regionId[sx][sy] = id;
                while (!stack.isEmpty()) {
                    int[] c = stack.pop();
                    int cx = c[0], cy = c[1];
                    a[1]++;            // area
                    a[2] += cx;        // sumX
                    a[3] += cy;        // sumY
                    if (cx < a[4]) a[4] = cx;
                    if (cy < a[5]) a[5] = cy;
                    if (cx > a[6]) a[6] = cx;
                    if (cy > a[7]) a[7] = cy;
                    pushSameKind(grid, topology, regionId, stack, cx + 1, cy, kind, id, w, h);
                    pushSameKind(grid, topology, regionId, stack, cx - 1, cy, kind, id, w, h);
                    pushSameKind(grid, topology, regionId, stack, cx, cy + 1, kind, id, w, h);
                    pushSameKind(grid, topology, regionId, stack, cx, cy - 1, kind, id, w, h);
                }
            }
        }
        int n = accum.size();

        // Cross-exposure grid: consecutive walkable cells along each cardinal
        // (excluding self), summed — the "how far can I see/shoot before a
        // wall" reach. Four linear sweeps, O(cells).
        int[][] exposure = computeCrossExposure(grid, w, h);

        // Pass B — per-region edge tallies + exposure sum, one grid sweep.
        long[] exposureSum = new long[n];
        int[] cellsWithClosed = new int[n];
        long[] closedEdges = new long[n];
        long[] mouthEdges = new long[n];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rid = regionId[x][y];
                if (rid < 0) continue;
                exposureSum[rid] += exposure[x][y];
                int closed = 0, mouths = 0;
                closed += isClosed(grid, x + 1, y, w, h) ? 1 : 0;
                closed += isClosed(grid, x - 1, y, w, h) ? 1 : 0;
                closed += isClosed(grid, x, y + 1, w, h) ? 1 : 0;
                closed += isClosed(grid, x, y - 1, w, h) ? 1 : 0;
                mouths += isMouth(regionId, x + 1, y, rid, w, h) ? 1 : 0;
                mouths += isMouth(regionId, x - 1, y, rid, w, h) ? 1 : 0;
                mouths += isMouth(regionId, x, y + 1, rid, w, h) ? 1 : 0;
                mouths += isMouth(regionId, x, y - 1, rid, w, h) ? 1 : 0;
                if (closed > 0) cellsWithClosed[rid]++;
                closedEdges[rid] += closed;
                mouthEdges[rid] += mouths;
            }
        }

        // Pass C — opening count: connected components of mouth-bearing boundary
        // cells, per region. A global visited grid keeps it O(cells).
        int[] openingCount = new int[n];
        boolean[][] visited = new boolean[w][h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rid = regionId[x][y];
                if (rid < 0 || visited[x][y] || !isMouthCell(regionId, x, y, w, h)) continue;
                openingCount[rid]++;
                stack.push(new int[]{x, y});
                visited[x][y] = true;
                while (!stack.isEmpty()) {
                    int[] c = stack.pop();
                    int cx = c[0], cy = c[1];
                    pushMouth(regionId, visited, stack, cx + 1, cy, rid, w, h);
                    pushMouth(regionId, visited, stack, cx - 1, cy, rid, w, h);
                    pushMouth(regionId, visited, stack, cx, cy + 1, rid, w, h);
                    pushMouth(regionId, visited, stack, cx, cy - 1, rid, w, h);
                }
            }
        }

        // Assemble immutable regions.
        List<TacticalRegion> out = new ArrayList<>(n);
        for (int id = 0; id < n; id++) {
            int[] a = accum.get(id);
            RegionKind kind = RegionKind.values()[a[0]];
            int area = a[1];
            int cx = a[2] / area;
            int cy = a[3] / area;
            float cover = (float) cellsWithClosed[id] / area;
            float meanExp = (float) exposureSum[id] / area;
            long boundary = closedEdges[id] + mouthEdges[id];
            float enclosure = boundary == 0 ? 1f : (float) closedEdges[id] / boundary;
            float depth01 = depth01(cx, cy, axis, w, h);
            out.add(new TacticalRegion(id, kind, area,
                    a[4], a[5], a[6], a[7], cx, cy,
                    cover, meanExp, enclosure, openingCount[id],
                    depth01, DepthBand.fromDepth01(depth01)));
        }
        return new TacticalRegionMap(regionId, out, w, h);
    }

    private static void pushSameKind(NavigationGrid grid, CellTopology topology, int[][] regionId,
                                     Deque<int[]> stack, int x, int y, RegionKind kind, int id,
                                     int w, int h) {
        if (x < 0 || x >= w || y < 0 || y >= h) return;
        if (regionId[x][y] != -1 || !grid.isWalkable(x, y)) return;
        if (RegionKind.fromGround(topology.getGroundKind(x, y)) != kind) return;
        regionId[x][y] = id;
        stack.push(new int[]{x, y});
    }

    /** A cardinal neighbor is "closed" when it's off-map or non-walkable (wall / building / water). */
    private static boolean isClosed(NavigationGrid grid, int x, int y, int w, int h) {
        if (x < 0 || x >= w || y < 0 || y >= h) return true;
        return !grid.isWalkable(x, y);
    }

    /** A cardinal neighbor is a "mouth" when it's walkable but belongs to a different region. */
    private static boolean isMouth(int[][] regionId, int x, int y, int rid, int w, int h) {
        if (x < 0 || x >= w || y < 0 || y >= h) return false;
        int nid = regionId[x][y];
        return nid >= 0 && nid != rid;
    }

    private static boolean isMouthCell(int[][] regionId, int x, int y, int w, int h) {
        int rid = regionId[x][y];
        return isMouth(regionId, x + 1, y, rid, w, h)
                || isMouth(regionId, x - 1, y, rid, w, h)
                || isMouth(regionId, x, y + 1, rid, w, h)
                || isMouth(regionId, x, y - 1, rid, w, h);
    }

    private static void pushMouth(int[][] regionId, boolean[][] visited, Deque<int[]> stack,
                                  int x, int y, int rid, int w, int h) {
        if (x < 0 || x >= w || y < 0 || y >= h) return;
        if (visited[x][y] || regionId[x][y] != rid) return;
        if (!isMouthCell(regionId, x, y, w, h)) return;
        visited[x][y] = true;
        stack.push(new int[]{x, y});
    }

    private static int[][] computeCrossExposure(NavigationGrid grid, int w, int h) {
        int[][] left = new int[w][h];
        int[][] right = new int[w][h];
        int[][] up = new int[w][h];
        int[][] down = new int[w][h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (!grid.isWalkable(x, y)) continue;
                left[x][y] = (x > 0 && grid.isWalkable(x - 1, y)) ? left[x - 1][y] + 1 : 0;
                down[x][y] = (y > 0 && grid.isWalkable(x, y - 1)) ? down[x][y - 1] + 1 : 0;
            }
        }
        for (int y = h - 1; y >= 0; y--) {
            for (int x = w - 1; x >= 0; x--) {
                if (!grid.isWalkable(x, y)) continue;
                right[x][y] = (x < w - 1 && grid.isWalkable(x + 1, y)) ? right[x + 1][y] + 1 : 0;
                up[x][y] = (y < h - 1 && grid.isWalkable(x, y + 1)) ? up[x][y + 1] + 1 : 0;
            }
        }
        int[][] cross = new int[w][h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                cross[x][y] = left[x][y] + right[x][y] + up[x][y] + down[x][y];
            }
        }
        return cross;
    }

    /**
     * Normalized {@code [0,1]} distance of a centroid from the attacker-facing
     * edge along {@code axis}; {@code -1f} when no axis (legacy). SOUTH_TO_NORTH
     * attacks from {@code y=0}; WEST_TO_EAST from {@code x=0}.
     */
    private static float depth01(int cx, int cy, TraversalAxis axis, int w, int h) {
        if (axis == null) return -1f;
        switch (axis) {
            case SOUTH_TO_NORTH: return h <= 1 ? 0f : (float) cy / (h - 1);
            case WEST_TO_EAST:   return w <= 1 ? 0f : (float) cx / (w - 1);
            default:             return -1f;
        }
    }
}
