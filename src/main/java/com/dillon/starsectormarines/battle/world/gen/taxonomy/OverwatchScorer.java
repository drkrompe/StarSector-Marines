package com.dillon.starsectormarines.battle.world.gen.taxonomy;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.TraversalAxis;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The <em>positional</em> layer of the structural taxonomy: scores walkable
 * cells as defender overwatch corners — cover at the back, a long field of fire
 * <em>out</em> over low-cover ground — and returns the strongest,
 * non-max-suppressed set. Generator-agnostic: reads only a
 * {@link NavigationGrid} (walkability), a {@link TacticalRegionMap} (forward
 * cover density), and an optional {@link TraversalAxis} (which way the defender
 * faces). Pure analysis, draws no randomness.
 *
 * <p>This complements {@link TacticalRegion}'s region-level enclosure
 * (membership: "where to garrison / fall back"). A turret wants the opposite of
 * an enclosed pocket — its walls would box the arc in — so this read is
 * directional and per-cell. See
 * {@code roadmap/mapgen/stories/structural-taxonomy.md} § "Membership vs.
 * positional — the corner-tower correction" for the why; this class is the
 * generalization of {@code FortressWallStamper}'s hand-placed wall towers.
 */
public final class OverwatchScorer {

    /** {E, W, N, S} as {dx, dy}; the open-run grid index matches this order. */
    private static final int[][] DIRS = { {1, 0}, {-1, 0}, {0, 1}, {0, -1} };

    /** Minimum outward open-run (cells) before a position counts as a real field of fire. */
    public static final int MIN_REACH = 6;
    /**
     * Reach past this contributes nothing extra to the score — beyond ~14 cells
     * it is all "long field of fire", and corner quality should win over raw
     * sightline length (which otherwise lets map-spanning edge cells dominate).
     */
    public static final int REACH_CAP = 14;
    /** Forward region must be at most this cover-dense — a killing ground, not another building. */
    public static final float FWD_COVER_MAX = 0.45f;
    /** Non-max-suppression radius: keep only the strongest pick within this Manhattan range. */
    public static final int SEPARATION = 8;
    /** Score multiplier for a corner (perpendicular wall present) over a flat-wall mount. */
    private static final float CORNER_BONUS = 1.25f;

    private OverwatchScorer() {}

    /**
     * Score every walkable cell as a candidate corner-tower and return the
     * strongest, non-max-suppressed set, sorted by descending score. A cell
     * scores in direction {@code d} when the cell <em>behind</em> it (opposite
     * {@code d}) is an in-bounds non-walkable (cover at back — the map edge does
     * NOT count), the open-run ahead in {@code d} is at least {@link #MIN_REACH}
     * (a real field of fire), and the region immediately ahead is low-cover (a
     * killing ground, not another building). Corner positions — a perpendicular
     * neighbor also walled — score {@link #CORNER_BONUS}× higher.
     *
     * <p>In conquest mode the firing direction is restricted to
     * <em>attacker-ward</em> (decreasing depth: south for
     * {@link TraversalAxis#SOUTH_TO_NORTH}, west for
     * {@link TraversalAxis#WEST_TO_EAST}) — both orients the sites correctly and
     * distributes them across the attacker-facing edge of each open field rather
     * than clustering on the largest one. Legacy maps ({@code axis == null})
     * have no attacker edge, so all four directions stay in play.
     *
     * <p>No display cap is applied — callers take as many sites as they need
     * (placement) or {@code subList} the top-N (preview overlays).
     */
    public static List<OverwatchSite> findSites(NavigationGrid grid, TacticalRegionMap regions,
                                                TraversalAxis axis) {
        int w = grid.getWidth();
        int h = grid.getHeight();

        int adx = 0, ady = 0;
        if (axis == TraversalAxis.SOUTH_TO_NORTH) ady = -1;
        else if (axis == TraversalAxis.WEST_TO_EAST) adx = -1;
        boolean restrictDir = adx != 0 || ady != 0;

        int[][][] reach = computeOpenRuns(grid, w, h); // [dir][x][y], dir order == DIRS
        List<OverwatchSite> picks = new ArrayList<>();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (!grid.isWalkable(x, y)) continue;
                float best = 0f;
                int bestDx = 0, bestDy = 0;
                boolean bestCorner = false;
                for (int d = 0; d < DIRS.length; d++) {
                    int dx = DIRS[d][0], dy = DIRS[d][1];
                    if (restrictDir && (dx != adx || dy != ady)) continue;
                    // Cover at back: the cell opposite the firing direction is an
                    // in-bounds non-walkable wall/building. The map edge (OOB)
                    // does NOT count — a tower against the boundary firing inward
                    // isn't a tactical backstop, and its map-spanning sightline
                    // would dominate the ranking.
                    int bx = x - dx, by = y - dy;
                    boolean backCover = bx >= 0 && bx < w && by >= 0 && by < h && !grid.isWalkable(bx, by);
                    if (!backCover) continue;
                    int run = reach[d][x][y];
                    if (run < MIN_REACH) continue;
                    // Forward ground must be a low-cover killing field, not a building.
                    TacticalRegion fwd = regions.regionAt(x + dx, y + dy);
                    if (fwd != null && fwd.coverDensity > FWD_COVER_MAX) continue;
                    boolean corner = perpWalled(grid, x, y, dx, dy);
                    float score = Math.min(run, REACH_CAP) * (corner ? CORNER_BONUS : 1f);
                    if (score > best) { best = score; bestDx = dx; bestDy = dy; bestCorner = corner; }
                }
                if (best > 0f) picks.add(new OverwatchSite(x, y, bestDx, bestDy, best, bestCorner));
            }
        }

        // Greedy non-max suppression: strongest first, drop anything within SEPARATION.
        picks.sort(Comparator.comparingDouble((OverwatchSite o) -> o.score()).reversed());
        List<OverwatchSite> kept = new ArrayList<>();
        for (OverwatchSite o : picks) {
            boolean near = false;
            for (OverwatchSite k : kept) {
                if (Math.abs(k.x() - o.x()) + Math.abs(k.y() - o.y()) < SEPARATION) { near = true; break; }
            }
            if (!near) kept.add(o);
        }
        return kept;
    }

    /** True when a cell perpendicular to the firing axis is also non-walkable — an L-corner, not a flat wall. */
    private static boolean perpWalled(NavigationGrid grid, int x, int y, int dx, int dy) {
        // Perpendicular axis: swap components.
        return nonWalkable(grid, x + dy, y + dx) || nonWalkable(grid, x - dy, y - dx);
    }

    private static boolean nonWalkable(NavigationGrid grid, int x, int y) {
        return x < 0 || x >= grid.getWidth() || y < 0 || y >= grid.getHeight() || !grid.isWalkable(x, y);
    }

    /** Open-run grids: {@code [dir][x][y]} = walkable cells ahead in {@code DIRS[dir]} before a wall (excluding self). */
    private static int[][][] computeOpenRuns(NavigationGrid grid, int w, int h) {
        int[][] east = new int[w][h], west = new int[w][h], north = new int[w][h], south = new int[w][h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (!grid.isWalkable(x, y)) continue;
                west[x][y]  = (x > 0 && grid.isWalkable(x - 1, y)) ? west[x - 1][y] + 1 : 0;
                south[x][y] = (y > 0 && grid.isWalkable(x, y - 1)) ? south[x][y - 1] + 1 : 0;
            }
        }
        for (int y = h - 1; y >= 0; y--) {
            for (int x = w - 1; x >= 0; x--) {
                if (!grid.isWalkable(x, y)) continue;
                east[x][y]  = (x < w - 1 && grid.isWalkable(x + 1, y)) ? east[x + 1][y] + 1 : 0;
                north[x][y] = (y < h - 1 && grid.isWalkable(x, y + 1)) ? north[x][y + 1] + 1 : 0;
            }
        }
        return new int[][][] { east, west, north, south }; // order matches DIRS {E, W, N, S}
    }
}
