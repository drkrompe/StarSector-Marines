package com.dillon.starsectormarines.battle.mapgen.bsp;

import com.dillon.starsectormarines.battle.Faction;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.tactical.TacticalNode;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Slice-6 keep multi-chamber pass. Detects when a {@link
 * TacticalNode.Kind#COMMAND_POST} sub-building has multiple interior
 * rooms (BuildingShellCore's {@code MULTI_ROOM_CHANCE} already partitions
 * buildings ≥ 7 cells in a dimension into 2 rooms separated by a wall +
 * doorway) and emits a second tactical node — kind
 * {@link TacticalNode.Kind#INNER_POSITION} — anchored in the chamber that
 * doesn't contain the COMMAND_POST anchor. The result is that the keep
 * carries an interior garrison position separate from the throne-room
 * garrison: marines clear the entry chamber's defenders en route to the
 * COMMAND_POST, so the storming sequence has real combat depth without
 * adding a separate compound-graph entity.
 *
 * <p>Why INNER_POSITION (not BARRACKS): the antechamber is part of the
 * same logical keep compound as the COMMAND_POST — it shouldn't gate
 * walk-in supply on its own (capturing it would otherwise <em>delay</em>
 * supply retirement, not accelerate it, since it'd add another BARRACKS
 * to the pool) and it shouldn't show its own capture-state marker
 * (player should read "one keep, one compound" visually). {@link
 * CompoundService#isCompound} already returns false for INNER_POSITION
 * so the supply layer, the capture renderer, and the conquest-objective
 * check all naturally ignore it. {@link TacticalLinker}'s compound-leaf
 * fallback pass also skips it, so the throne-room COMMAND_POST never
 * gets a same-leaf FALLBACK_TO link to the antechamber — partition
 * orientation is random (BuildingShellCore picks whichever axis ≥
 * MULTI_ROOM_MIN_DIM and a randomized split point) so we can't rely on
 * the antechamber being on the attacker's side, but a fallback link
 * between two rooms of the same building is wrong regardless of which
 * side either room faces. The defender allocator still picks
 * INNER_POSITION up the same as any other tactical kind, so the
 * garrison lands and fights.
 *
 * <p>Detection method: flood-fill from the COMMAND_POST anchor over
 * walkable cells inside the building's leaf bbox. Cells in the bbox
 * <em>not</em> reached by the flood live in a different room (separated
 * by the partition wall) — that's the entry chamber. Single-room
 * buildings have zero unreached cells and skip emission gracefully.
 * Same lineage as the {@link CompoundPerimeterDefenderStamper}: a
 * post-fill pass that reads what the fillers built and emits tactical
 * anchors without re-carving topology.
 *
 * <p>V1 ships single-extra-chamber detection (one INNER_POSITION per
 * keep) even when {@link com.dillon.starsectormarines.battle.mapgen.bsp.fill.BuildingShellCore}'s
 * MULTI_ROOM partition lands. The doc's full 3-chamber (entry / inner /
 * throne) design needs the BSP carve to support 3-way partitioning,
 * which is a follow-up. Garrison cell pools still come from the
 * doorway-unaware {@code BattleSetup.pickCellsNear}, so the chamber's
 * spawn pool can overlap the throne room's — patch 3 of the central-keep
 * fix sequence (room-bounded spawn pools via {@code RoomFinder})
 * addresses that.
 */
public final class KeepEntryChamberStamper {

    /** Minimum number of walkable cells in the "other" room for it to qualify as an entry chamber. Single-cell pockets aren't large enough to read as a chamber; they're just irregular building geometry. */
    private static final int MIN_CHAMBER_CELLS = 3;

    /** Priority for the entry-chamber INNER_POSITION — below the COMMAND_POST (95) so the allocator fills the throne-room garrison first and the chamber takes whatever remains after the doctrine-elite slot lands. Slightly above the perimeter-GUARDPOST (50) so the chamber gets manned before perimeter lookouts. */
    private static final int ENTRY_CHAMBER_PRIORITY = 60;
    /** Garrison size for the entry chamber — antechamber-scale squad, smaller than the throne-room garrison. */
    private static final int ENTRY_CHAMBER_GARRISON = 3;

    private KeepEntryChamberStamper() {}

    /**
     * Emit one INNER_POSITION tactical node per COMMAND_POST whose sub-building
     * has a multi-room partition. New nodes are appended to {@code tactical}
     * in-place; {@link TacticalLinker} (which runs after this stamper) does NOT
     * wire INNER_POSITION into its compound-leaf FALLBACK_TO pass — interior
     * fallback is goal-AI territory, not the link graph.
     */
    public static void stamp(NavigationGrid grid, List<TacticalNode> tactical) {
        if (grid == null || tactical == null) return;
        List<TacticalNode> initial = new ArrayList<>(tactical);
        for (TacticalNode node : initial) {
            if (node.kind != TacticalNode.Kind.COMMAND_POST) continue;
            int[] entryAnchor = findEntryChamberAnchor(node, grid);
            if (entryAnchor == null) continue;
            tactical.add(new TacticalNode(TacticalNode.Kind.INNER_POSITION,
                    entryAnchor[0], entryAnchor[1],
                    node.left, node.top, node.right, node.bottom,
                    Faction.DEFENDER,
                    ENTRY_CHAMBER_PRIORITY,
                    ENTRY_CHAMBER_GARRISON));
        }
    }

    /**
     * Flood-fill from the COMMAND_POST anchor over walkable cells bounded
     * to the leaf bbox; collect walkable cells in the bbox not reached. If
     * the unreached set is ≥ {@link #MIN_CHAMBER_CELLS}, pick a representative
     * anchor (the geometric centroid of the unreached cells, snapped to the
     * nearest unreached cell so the anchor is itself walkable). Returns
     * {@code null} when the building has a single connected interior — no
     * entry chamber to emit.
     */
    private static int[] findEntryChamberAnchor(TacticalNode commandPost, NavigationGrid grid) {
        int left = commandPost.left;
        int top = commandPost.top;
        int right = commandPost.right;
        int bottom = commandPost.bottom;

        // Anchor may not be inside the bbox (defensive), or may be on a wall
        // or doorway cell. Skip gracefully if so — the building has no
        // resolvable throne-room flood seed.
        int seedX = commandPost.anchorX;
        int seedY = commandPost.anchorY;
        if (seedX < left || seedX > right || seedY < top || seedY > bottom) return null;
        if (!grid.inBounds(seedX, seedY) || !grid.isWalkable(seedX, seedY)) return null;
        if (grid.isDoorway(seedX, seedY)) return null;

        int w = right - left + 1;
        int h = bottom - top + 1;
        boolean[][] reached = new boolean[w][h];
        Deque<int[]> q = new ArrayDeque<>();
        q.add(new int[]{seedX, seedY});
        reached[seedX - left][seedY - top] = true;
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!q.isEmpty()) {
            int[] p = q.poll();
            for (int[] d : dirs) {
                int nx = p[0] + d[0];
                int ny = p[1] + d[1];
                if (nx < left || nx > right || ny < top || ny > bottom) continue;
                int lx = nx - left;
                int ly = ny - top;
                if (reached[lx][ly]) continue;
                if (!grid.isWalkable(nx, ny)) continue;
                // Doorways are the room boundary. A flood from the throne-room
                // anchor that crosses through the partition doorway would
                // reach the entry chamber too, defeating the room split. Stop
                // at doorways so each room is detected as its own flood
                // component. Perimeter doorways (building exits) are also
                // doorway-flagged, but those sit at the bbox edge and the
                // bbox bound already filters outside cells, so the flood
                // can't leak past them either way.
                if (grid.isDoorway(nx, ny)) continue;
                reached[lx][ly] = true;
                q.add(new int[]{nx, ny});
            }
        }

        // Collect walkable cells in the bbox NOT reached by the throne-room
        // flood. Those are the "other room" cells, separated from the seed
        // by an interior wall + doorway. Exclude doorway cells themselves so
        // the centroid isn't pulled toward the partition boundary — we want
        // the anchor inside the room proper, not on the threshold.
        List<int[]> otherRoom = new ArrayList<>();
        long sumX = 0, sumY = 0;
        for (int y = top; y <= bottom; y++) {
            for (int x = left; x <= right; x++) {
                if (reached[x - left][y - top]) continue;
                if (!grid.isWalkable(x, y)) continue;
                if (grid.isDoorway(x, y)) continue;
                otherRoom.add(new int[]{x, y});
                sumX += x;
                sumY += y;
            }
        }
        if (otherRoom.size() < MIN_CHAMBER_CELLS) return null;

        // Centroid, snapped to the nearest other-room cell. The raw centroid
        // can land on a wall or on the seed-room side of the partition; the
        // snap guarantees an actual entry-chamber cell.
        float cx = sumX / (float) otherRoom.size();
        float cy = sumY / (float) otherRoom.size();
        int[] best = otherRoom.get(0);
        float bestD2 = Float.MAX_VALUE;
        for (int[] cell : otherRoom) {
            float dx = cell[0] - cx;
            float dy = cell[1] - cy;
            float d2 = dx * dx + dy * dy;
            if (d2 < bestD2) { bestD2 = d2; best = cell; }
        }
        return best;
    }
}
