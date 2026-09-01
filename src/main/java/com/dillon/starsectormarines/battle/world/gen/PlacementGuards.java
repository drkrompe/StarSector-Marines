package com.dillon.starsectormarines.battle.world.gen;

import com.dillon.starsectormarines.battle.nav.Direction;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

/**
 * Shared placement validators for stampers that turn rectangular footprints
 * non-walkable (defense posts, parked vehicles, building fixtures, future
 * obstacles). They exist because the obvious "footprint doesn't contain a
 * doorway / wall" gate misses several failure modes:
 *
 * <ul>
 *   <li>{@link #touchesDoorway} — the stamp doesn't land ON a doorway, but it
 *       lands on the doorway's perpendicular <em>through-cell</em>, which is
 *       walkable and unflagged but is the building's only egress. Sealing it
 *       traps the interior.</li>
 *   <li>{@link #wouldPartitionWalkable} — the stamp sits one cell off an
 *       existing non-walkable mass (BSP outdoor wall, fortress wall, another
 *       building), sealing a thin strip of walkable cells between itself and
 *       the wall.</li>
 *   <li>{@link #wouldStrandGround} — the same question asked locally and with
 *       the pathfinder's own step rule, for the placements that happen hundreds
 *       of times per map.</li>
 *   <li>{@link #touchesEdgeBarrier} — the stamp would consume one of the two
 *       standable cells that an authored shared-edge feature divides.</li>
 * </ul>
 *
 * <p>All are pre-stamp checks — they answer about the grid as it stands and
 * leave it exactly as they found it, so a caller skips a doomed placement
 * rather than repairing the map after the fact.
 */
public final class PlacementGuards {

    /**
     * How far around a footprint {@link #wouldStrandGround} will follow a
     * detour. Wide enough to run around any room a building filler furnishes,
     * small enough that the flood stays a few hundred cells.
     */
    private static final int STRAND_WINDOW_RADIUS = 8;

    private PlacementGuards() {}

    /**
     * True if {@code (x, y)} is a doorway cell, or if any of its 4 cardinal
     * neighbors is. Cardinal-only because only the perpendicular neighbors of
     * a doorway carry traffic in/out — the parallel neighbors are the wall
     * continuing past the gap (already non-walkable), and the diagonal
     * neighbors don't block the threshold.
     */
    public static boolean touchesDoorway(NavigationGrid grid, int x, int y) {
        if (grid.isDoorway(x, y)) return true;
        if (grid.inBounds(x + 1, y) && grid.isDoorway(x + 1, y)) return true;
        if (grid.inBounds(x - 1, y) && grid.isDoorway(x - 1, y)) return true;
        if (grid.inBounds(x, y + 1) && grid.isDoorway(x, y + 1)) return true;
        if (grid.inBounds(x, y - 1) && grid.isDoorway(x, y - 1)) return true;
        return false;
    }

    /**
     * True when {@code (x, y)} is one endpoint of an authored shared-edge
     * barrier. Both endpoint cells are part of that feature's usable geometry;
     * a later hard fixture must choose another cell rather than invalidating
     * an otherwise valid window, gate, or future narrow-wall profile.
     */
    public static boolean touchesEdgeBarrier(NavigationGrid grid, int x, int y) {
        if (!grid.inBounds(x, y)) return false;
        for (Direction direction : Direction.CARDINALS) {
            if (grid.getEdgeBarrier(x, y, direction) != null) return true;
        }
        return false;
    }

    /**
     * Rectangular-footprint connectivity check — convenience overload for
     * stampers whose footprint fills its whole bounding box (e.g. parked
     * vehicles). Treats every cell of
     * {@code [minX..minX+width-1] × [minY..minY+height-1]} as non-walkable and
     * delegates to {@link #wouldPartitionWalkable(NavigationGrid, int[][])}.
     *
     * <p><b>Do not use for sparse footprints</b> (a defense-post vent ring
     * leaves its bbox corners walkable): treating the whole rect as blocked
     * excludes those open cells from the check, so a corner the real stamp
     * leaves walkable — and that some pre-existing wall/water boxes in — is
     * silently missed. Such callers must pass the actual blocked cells.
     */
    public static boolean wouldPartitionWalkable(NavigationGrid grid,
                                                 int minX, int minY,
                                                 int width, int height) {
        int[][] cells = new int[width * height][2];
        int i = 0;
        for (int dy = 0; dy < height; dy++) {
            for (int dx = 0; dx < width; dx++) {
                cells[i][0] = minX + dx;
                cells[i][1] = minY + dy;
                i++;
            }
        }
        return wouldPartitionWalkable(grid, cells);
    }

    /**
     * Simulated-stamp connectivity check. Treats {@code stampedCells} (the
     * exact cells a stamp will turn non-walkable) as blocked, picks the first
     * walkable cell outside that set as a BFS seed, walks the walkable graph,
     * and returns true if any walkable cell would remain unreached.
     *
     * <p>Catches two cases the cheaper footprint gate misses: the stamp sealing
     * a thin strip against an existing non-walkable mass, AND the stamp boxing
     * in one of its <em>own</em> footprint cells that it leaves walkable (a vent
     * ring's open corner trapped between the ring arms and pre-existing
     * water/wall). The latter is why this takes the real blocked-cell set rather
     * than a bounding rect — open footprint cells stay in the walkable graph and
     * are checked for reachability like any other cell.
     *
     * <p>Runs in {@code O(w*h)} on the grid but only fires for anchors that pass
     * the cheaper footprint/doorway gates, so typical use is a handful of calls
     * per gen, not hundreds.
     *
     * <p><b>It answers a different question than its name suggests, and the
     * difference matters.</b> "Would this stamp partition the graph" and "is any
     * walkable cell unreachable" are the same only on a map that is whole to
     * begin with. Where an earlier stage has left an orphan pocket — an interior
     * not yet given its doorway, a cell a fill sealed — this returns true for
     * <em>every</em> candidate anchor, so the caller places nothing at all and
     * says nothing about it. That is not hypothetical: a precinct map carrying
     * three orphan pockets of 21 cells between them refused all 5605 defence-post
     * anchors on it. Prefer {@link #wouldStrandGround}, which asks locally and is
     * therefore immune, unless the caller has a reason to want the global
     * property.
     */
    public static boolean wouldPartitionWalkable(NavigationGrid grid, int[][] stampedCells) {
        int gridW = grid.getWidth();
        int gridH = grid.getHeight();
        Set<Integer> stamped = new HashSet<>();
        for (int[] c : stampedCells) {
            if (c[0] >= 0 && c[0] < gridW && c[1] >= 0 && c[1] < gridH) {
                stamped.add(c[1] * gridW + c[0]);
            }
        }

        int seedIdx = -1;
        int target = 0;
        for (int y = 0; y < gridH; y++) {
            for (int x = 0; x < gridW; x++) {
                if (!grid.isWalkable(x, y)) continue;
                int idx = y * gridW + x;
                if (stamped.contains(idx)) continue;
                if (seedIdx < 0) seedIdx = idx;
                target++;
            }
        }
        if (seedIdx < 0) return false;

        boolean[] visited = new boolean[gridW * gridH];
        Deque<Integer> stack = new ArrayDeque<>();
        stack.push(seedIdx);
        visited[seedIdx] = true;
        int reached = 1;
        while (!stack.isEmpty()) {
            int idx = stack.pop();
            int x = idx % gridW;
            int y = idx / gridW;
            for (int dir = 0; dir < 4; dir++) {
                int nx = x + (dir == 0 ? 1 : dir == 1 ? -1 : 0);
                int ny = y + (dir == 2 ? 1 : dir == 3 ? -1 : 0);
                if (nx < 0 || nx >= gridW || ny < 0 || ny >= gridH) continue;
                if (!grid.isWalkable(nx, ny)) continue;
                int nidx = ny * gridW + nx;
                if (stamped.contains(nidx)) continue;
                if (visited[nidx]) continue;
                visited[nidx] = true;
                reached++;
                stack.push(nidx);
            }
        }
        return reached != target;
    }

    /**
     * True when blocking the {@code width} x {@code height} footprint anchored
     * at {@code (x, y)} would leave walkable ground that nothing can reach any
     * more. This is the no-island proof a fixture owes: each crate in a parts
     * cage is legal standing alone, and it is the second one that seals the
     * corner, so nothing about the cell being free can tell them apart — only a
     * reachability question can.
     *
     * <p>Two things separate it from {@link #wouldPartitionWalkable}, and both
     * are why fixture stamping needs its own form rather than reusing that one.
     *
     * <p>It asks the question the way the pathfinder answers it: reachability
     * runs through {@link NavigationGrid#canTraverseCellStep}, so diagonal
     * corner-cutting and shared-edge barriers decide it rather than bare cell
     * adjacency. And it asks locally — only the cells that can step onto the
     * footprint today must still reach each other once it is gone, searched
     * within a window around it. A route the window finds is a real route, so an
     * accepted placement never partitions anything; a placement whose only
     * detour runs wider than the window is refused and the room comes out one
     * fixture sparser, which is the cheap direction to be wrong in. Being local
     * also means a partition elsewhere on the map — an interior not yet given
     * its doorway, a pocket a later stage will fill in solid — cannot make every
     * fixture on the map unplaceable, and it costs a few hundred cells per call
     * instead of the whole grid.
     */
    public static boolean wouldStrandGround(NavigationGrid grid, int x, int y,
                                            int width, int height) {
        int footprintCells = Math.max(0, width) * Math.max(0, height);
        if (footprintCells == 0) return false;

        int[] blockedX = new int[footprintCells];
        int[] blockedY = new int[footprintCells];
        int blocked = 0;
        for (int dy = 0; dy < height; dy++) {
            for (int dx = 0; dx < width; dx++) {
                int cellX = x + dx;
                int cellY = y + dy;
                if (!grid.inBounds(cellX, cellY) || !grid.isWalkable(cellX, cellY)) continue;
                blockedX[blocked] = cellX;
                blockedY[blocked] = cellY;
                blocked++;
            }
        }
        // Blocking what is already blocked takes nothing out of the graph.
        if (blocked == 0) return false;

        int[] approachX = new int[blocked * Direction.ALL.length];
        int[] approachY = new int[blocked * Direction.ALL.length];
        int approaches = collectApproaches(grid, blockedX, blockedY, blocked,
                approachX, approachY);
        // One way in is one way out: with no second approach to separate it
        // from, the footprint carried no route between two places.
        if (approaches <= 1) return false;

        for (int i = 0; i < blocked; i++) {
            grid.setWalkable(blockedX[i], blockedY[i], false);
        }
        boolean connected = approachesStayConnected(grid, approachX, approachY, approaches,
                Math.max(0, x - STRAND_WINDOW_RADIUS),
                Math.max(0, y - STRAND_WINDOW_RADIUS),
                Math.min(grid.getWidth() - 1, x + width - 1 + STRAND_WINDOW_RADIUS),
                Math.min(grid.getHeight() - 1, y + height - 1 + STRAND_WINDOW_RADIUS));
        for (int i = 0; i < blocked; i++) {
            grid.setWalkable(blockedX[i], blockedY[i], true);
        }
        return !connected;
    }

    /** Single-cell convenience for the common 1x1 fixture. */
    public static boolean wouldStrandGround(NavigationGrid grid, int x, int y) {
        return wouldStrandGround(grid, x, y, 1, 1);
    }

    /**
     * The same question for a stamp whose footprint is not a filled rectangle.
     *
     * <p>A defence post's embankment is a ring, a cross or a notched trapezoid:
     * cells inside its bounding box that the stamp leaves walkable. Those open
     * cells are exactly where the interesting failure lives — a ring arm can box
     * in its own corner against a pre-existing wall — so they are treated as
     * approaches that must stay connected rather than being excluded the way a
     * rectangle's interior is.
     *
     * <p>Prefer this over {@link #wouldPartitionWalkable} for anything stamped
     * more than a handful of times per map. That check asks whether <em>any</em>
     * walkable cell anywhere is unreachable, so a single orphan cell left
     * somewhere else by an earlier stage makes it answer true for every
     * candidate on the map — which is silent, total, and looks exactly like a
     * stamper that was never called.
     *
     * @param blockedCells the exact cells the stamp will turn non-walkable
     */
    public static boolean wouldStrandGround(NavigationGrid grid, int[][] blockedCells) {
        if (blockedCells == null || blockedCells.length == 0) return false;

        int[] blockedX = new int[blockedCells.length];
        int[] blockedY = new int[blockedCells.length];
        int blocked = 0;
        for (int[] cell : blockedCells) {
            if (!grid.inBounds(cell[0], cell[1])) continue;
            if (!grid.isWalkable(cell[0], cell[1])) continue;
            blockedX[blocked] = cell[0];
            blockedY[blocked] = cell[1];
            blocked++;
        }
        // Blocking what is already blocked takes nothing out of the graph.
        if (blocked == 0) return false;

        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
        for (int i = 0; i < blocked; i++) {
            minX = Math.min(minX, blockedX[i]);
            maxX = Math.max(maxX, blockedX[i]);
            minY = Math.min(minY, blockedY[i]);
            maxY = Math.max(maxY, blockedY[i]);
        }

        int[] approachX = new int[blocked * Direction.ALL.length];
        int[] approachY = new int[blocked * Direction.ALL.length];
        int approaches = collectApproaches(grid, blockedX, blockedY, blocked,
                approachX, approachY);
        // One way in is one way out: with no second approach to separate it
        // from, the footprint carried no route between two places.
        if (approaches <= 1) return false;

        for (int i = 0; i < blocked; i++) {
            grid.setWalkable(blockedX[i], blockedY[i], false);
        }
        boolean connected = approachesStayConnected(grid, approachX, approachY, approaches,
                Math.max(0, minX - STRAND_WINDOW_RADIUS),
                Math.max(0, minY - STRAND_WINDOW_RADIUS),
                Math.min(grid.getWidth() - 1, maxX + STRAND_WINDOW_RADIUS),
                Math.min(grid.getHeight() - 1, maxY + STRAND_WINDOW_RADIUS));
        for (int i = 0; i < blocked; i++) {
            grid.setWalkable(blockedX[i], blockedY[i], true);
        }
        return !connected;
    }

    /**
     * The distinct cells that can step onto the footprint as the grid stands now.
     *
     * <p>A cell is excluded when the stamp is going to block it too, which for a
     * filled rectangle is the whole rect and for a sparse footprint is only the
     * cells actually stamped — an open cell a ring leaves inside itself stays
     * walkable, so it is somewhere that must still be reachable afterwards.
     */
    private static int collectApproaches(NavigationGrid grid,
                                         int[] blockedX, int[] blockedY, int blocked,
                                         int[] approachX, int[] approachY) {
        int approaches = 0;
        for (int i = 0; i < blocked; i++) {
            for (Direction direction : Direction.ALL) {
                int nx = blockedX[i] + direction.dx;
                int ny = blockedY[i] + direction.dy;
                if (!grid.inBounds(nx, ny)) continue;
                boolean alsoBlocked = false;
                for (int k = 0; k < blocked && !alsoBlocked; k++) {
                    alsoBlocked = blockedX[k] == nx && blockedY[k] == ny;
                }
                if (alsoBlocked) continue;
                if (!grid.canTraverseCellStep(blockedX[i], blockedY[i], nx, ny)) continue;
                boolean known = false;
                for (int k = 0; k < approaches && !known; k++) {
                    known = approachX[k] == nx && approachY[k] == ny;
                }
                if (known) continue;
                approachX[approaches] = nx;
                approachY[approaches] = ny;
                approaches++;
            }
        }
        return approaches;
    }

    /** Flood the window from the first approach cell; every other one must fall into it. */
    private static boolean approachesStayConnected(NavigationGrid grid,
                                                   int[] approachX, int[] approachY,
                                                   int approaches,
                                                   int minX, int minY, int maxX, int maxY) {
        int windowWidth = maxX - minX + 1;
        int windowHeight = maxY - minY + 1;
        boolean[] reached = new boolean[windowWidth * windowHeight];
        int[] stack = new int[windowWidth * windowHeight];
        int stackSize = 0;

        int seed = (approachY[0] - minY) * windowWidth + (approachX[0] - minX);
        reached[seed] = true;
        stack[stackSize++] = seed;
        while (stackSize > 0) {
            int local = stack[--stackSize];
            int cellX = minX + local % windowWidth;
            int cellY = minY + local / windowWidth;
            for (Direction direction : Direction.ALL) {
                int nx = cellX + direction.dx;
                int ny = cellY + direction.dy;
                if (nx < minX || ny < minY || nx > maxX || ny > maxY) continue;
                int neighbor = (ny - minY) * windowWidth + (nx - minX);
                if (reached[neighbor]) continue;
                if (!grid.canTraverseCellStep(cellX, cellY, nx, ny)) continue;
                reached[neighbor] = true;
                stack[stackSize++] = neighbor;
            }
        }

        for (int i = 1; i < approaches; i++) {
            if (!reached[(approachY[i] - minY) * windowWidth + (approachX[i] - minX)]) {
                return false;
            }
        }
        return true;
    }
}
