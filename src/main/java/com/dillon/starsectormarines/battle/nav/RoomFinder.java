package com.dillon.starsectormarines.battle.nav;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Doorway-bounded flood-fill utility — the "what cells are in the same
 * room as this seed" primitive. Treats {@link NavigationGrid#isDoorway
 * doorway-flagged} cells as room boundaries: the flood does not enter a
 * non-seed doorway cell, and a doorway encountered as a neighbor of a
 * non-doorway cell terminates that branch of the search. The seed cell
 * is exempt from the boundary rule so that a seed sitting <em>on</em> a
 * doorway (a fortress gate's tactical anchor, a sewer-entrance
 * INFILTRATION_POINT) can still escape into both adjacent rooms — the
 * consumer asked about cells near a doorway, not within a single room.
 *
 * <h2>Use cases</h2>
 * <ul>
 *   <li>{@link com.dillon.starsectormarines.battle.mapgen.bsp.KeepEntryChamberStamper}
 *       — given a COMMAND_POST anchor, find cells in the throne-room flood
 *       component so the antechamber can be detected as "bbox \ throne-room".</li>
 *   <li>{@link com.dillon.starsectormarines.battle.BattleSetup#pickCellsNear}
 *       — given a tactical-node anchor, restrict garrison spawn cells to
 *       the seed's room so a multi-room building's COMMAND_POST squad
 *       can't spill into the antechamber's spawn pool.</li>
 * </ul>
 *
 * <h2>Result contract</h2>
 * The returned list contains <em>walkable</em> cells reachable from the
 * seed via 4-neighbor steps, in BFS-discovery order (closer cells first,
 * ties broken by enqueue order). Each entry is a freshly-allocated
 * {@code int[3]} of {@code {x, y, manhattanDistanceFromSeed}}. Non-walkable
 * cells (walls, turret mounts) never appear in the result, but they do
 * not terminate the search — the flood walks <em>past</em> the seed itself
 * even when the seed is unwalkable (turret-mount anchors). Doorway cells
 * also never appear in the result <em>except</em> when the seed itself
 * is a doorway, in which case the seed cell is included.
 *
 * <h2>Bounds</h2>
 * Both {@code maxRadius} and {@code bbox} are optional. {@code maxRadius}
 * is the Manhattan-distance cap (use {@link Integer#MAX_VALUE} for
 * unbounded). {@code bbox} (when non-null) clamps the flood to an
 * inclusive {@code [left, top, right, bottom]} rectangle — useful when
 * floods need to stay inside a building leaf's bbox without leaking out a
 * perimeter doorway.
 */
public final class RoomFinder {

    private static final int[][] NEIGHBORS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    private RoomFinder() {}

    /**
     * Flood from {@code (seedX, seedY)} returning walkable, in-room cells.
     * See class javadoc for the doorway-boundary + seed-exemption contract.
     *
     * @param grid       walkability + doorway source
     * @param seedX      flood origin X
     * @param seedY      flood origin Y
     * @param maxRadius  Manhattan-distance cap from the seed (use {@code Integer.MAX_VALUE} for unbounded)
     * @param bbox       optional inclusive {@code [left, top, right, bottom]} clamp; {@code null} for unbounded
     * @return BFS-ordered list of {@code {x, y, dist}} tuples; empty when no walkable cells are reachable
     */
    public static List<int[]> flood(NavigationGrid grid, int seedX, int seedY,
                                    int maxRadius, int[] bbox) {
        List<int[]> out = new ArrayList<>();
        if (grid == null) return out;
        Set<Long> seen = new HashSet<>();
        Deque<int[]> q = new ArrayDeque<>();
        q.add(new int[]{seedX, seedY, 0});
        seen.add(key(seedX, seedY));

        while (!q.isEmpty()) {
            int[] p = q.poll();
            int x = p[0], y = p[1], d = p[2];
            if (d > maxRadius) continue;
            if (bbox != null && (x < bbox[0] || x > bbox[2] || y < bbox[1] || y > bbox[3])) continue;
            if (!grid.inBounds(x, y)) continue;

            boolean isSeed = (x == seedX && y == seedY);
            boolean walkable = grid.isWalkable(x, y);
            boolean isDoorway = grid.isDoorway(x, y);

            // Include in result if walkable. Non-seed doorway cells are
            // excluded — they're boundaries, not floor. The seed itself is
            // exempt: a doorway-anchored tactical node (a gate) still wants
            // the doorway in its spawn pool.
            if (walkable && (!isDoorway || isSeed)) {
                out.add(new int[]{x, y, d});
            }

            // Expand from this cell? Yes if:
            //   - this is the seed (always — even unwalkable / doorway seeds
            //     should kick off the search), or
            //   - this is a walkable non-doorway cell (the normal in-room
            //     case — keep flooding).
            // Don't expand from non-seed doorways (boundary) or non-seed
            // unwalkable cells (walls; flood already paused at the wall on
            // the prior hop).
            boolean expand = isSeed || (walkable && !isDoorway);
            if (!expand) continue;

            for (int[] dir : NEIGHBORS) {
                int nx = x + dir[0];
                int ny = y + dir[1];
                if (!grid.inBounds(nx, ny)) continue;
                if (!seen.add(key(nx, ny))) continue;
                q.add(new int[]{nx, ny, d + 1});
            }
        }
        return out;
    }

    /** Convenience: pack a flood result into a {@code Set<Long>} for O(1) membership lookup. Useful when iterating a bbox to find cells <em>not</em> in the flood (the antechamber-detection pattern). */
    public static Set<Long> toMembership(List<int[]> cells) {
        Set<Long> set = new HashSet<>(cells.size() * 2);
        for (int[] c : cells) set.add(key(c[0], c[1]));
        return set;
    }

    /** Packed {@code (x, y)} key used by both the BFS visited-set and {@link #toMembership}. Stable across calls so consumers can mix-and-match. */
    public static long key(int x, int y) {
        return ((long) x << 32) | (y & 0xFFFFFFFFL);
    }
}
