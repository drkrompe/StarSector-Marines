package com.dillon.starsectormarines.battle.world.gen.bsp;

import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.map.CellTopology;
import com.dillon.starsectormarines.battle.map.RoomPurpose;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.tactical.TacticalNode;

import java.util.ArrayList;
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
 * <p>Detection method: {@link BuildingShellCore} now labels each chamber
 * at carve time via {@link RoomPurpose} — the COMMAND_POST anchor side
 * gets {@link RoomPurpose#KEEP_THRONE}, the antechamber side gets
 * {@link RoomPurpose#KEEP_ENTRY}. The stamper walks each COMMAND_POST's
 * leaf bbox and collects cells labeled {@code KEEP_ENTRY}; if the
 * collected set is ≥ {@link #MIN_CHAMBER_CELLS} it picks a representative
 * anchor (centroid snapped to the nearest member cell). Previous versions
 * inferred chambers via a transient {@link
 * com.dillon.starsectormarines.battle.nav.zone.ZoneGraph} flood and
 * deduced "the other zone is the antechamber"; the label-driven path
 * is cheaper and survives carvers that produce more than two chambers
 * (each chamber's purpose is known explicitly).
 *
 * <p>V1 ships single-extra-chamber detection (one INNER_POSITION per
 * keep) even when {@link com.dillon.starsectormarines.battle.world.gen.bsp.fill.BuildingShellCore}'s
 * MULTI_ROOM partition lands. The doc's full 3-chamber (entry / inner /
 * throne) design needs the BSP carve to support 3-way partitioning,
 * which is a follow-up.
 */
public final class KeepEntryChamberStamper {

    /** Minimum number of labeled cells in the entry chamber for it to qualify. Single-cell pockets aren't large enough to read as a chamber; they're just irregular building geometry. */
    private static final int MIN_CHAMBER_CELLS = 3;

    private static final int ENTRY_CHAMBER_PRIORITY = 60;
    private static final int ENTRY_CHAMBER_GARRISON = 3;
    private static final int INNER_CHAMBER_PRIORITY = 65;
    private static final int INNER_CHAMBER_GARRISON = 4;

    private KeepEntryChamberStamper() {}

    /**
     * Emit one INNER_POSITION tactical node per COMMAND_POST whose sub-building
     * has a multi-room partition. New nodes are appended to {@code tactical}
     * in-place; {@link TacticalLinker} (which runs after this stamper) does NOT
     * wire INNER_POSITION into its compound-leaf FALLBACK_TO pass — interior
     * fallback is goal-AI territory, not the link graph.
     */
    public static void stamp(NavigationGrid grid, CellTopology topology, List<TacticalNode> tactical) {
        if (grid == null || topology == null || tactical == null) return;
        List<TacticalNode> initial = new ArrayList<>(tactical);
        for (TacticalNode node : initial) {
            if (node.kind != TacticalNode.Kind.COMMAND_POST) continue;
            emitChamber(node, grid, topology, tactical, RoomPurpose.KEEP_INNER,
                    INNER_CHAMBER_PRIORITY, INNER_CHAMBER_GARRISON);
            emitChamber(node, grid, topology, tactical, RoomPurpose.KEEP_ENTRY,
                    ENTRY_CHAMBER_PRIORITY, ENTRY_CHAMBER_GARRISON);
        }
    }

    private static void emitChamber(TacticalNode commandPost,
                                    NavigationGrid grid, CellTopology topology,
                                    List<TacticalNode> tactical,
                                    RoomPurpose purpose, int priority, int garrison) {
        int[] anchor = findChamberAnchor(commandPost, grid, topology, purpose);
        if (anchor == null) return;
        tactical.add(new TacticalNode(TacticalNode.Kind.INNER_POSITION,
                anchor[0], anchor[1],
                commandPost.left, commandPost.top, commandPost.right, commandPost.bottom,
                Faction.DEFENDER, priority, garrison));
    }

    private static int[] findChamberAnchor(TacticalNode commandPost, NavigationGrid grid,
                                            CellTopology topology, RoomPurpose purpose) {
        int left = commandPost.left;
        int top = commandPost.top;
        int right = commandPost.right;
        int bottom = commandPost.bottom;

        List<int[]> cells = new ArrayList<>();
        long sumX = 0, sumY = 0;
        for (int y = top; y <= bottom; y++) {
            for (int x = left; x <= right; x++) {
                if (!grid.inBounds(x, y)) continue;
                if (topology.getRoomPurpose(x, y) != purpose) continue;
                // Re-check walkability — the labeler skipped non-walkable cells
                // at write time, but a downstream stamper (FortressWallStamper,
                // DefensePostStamper, future passes) could in principle mutate
                // a labeled cell to non-walkable. The mismatch would silently
                // bias the centroid; cheap to defend against here.
                if (!grid.isWalkable(x, y)) continue;
                cells.add(new int[]{x, y});
                sumX += x;
                sumY += y;
            }
        }
        if (cells.size() < MIN_CHAMBER_CELLS) return null;

        float cx = sumX / (float) cells.size();
        float cy = sumY / (float) cells.size();
        int[] best = cells.get(0);
        float bestD2 = Float.MAX_VALUE;
        for (int[] cell : cells) {
            float dx = cell[0] - cx;
            float dy = cell[1] - cy;
            float d2 = dx * dx + dy * dy;
            if (d2 < bestD2) { bestD2 = d2; best = cell; }
        }
        return best;
    }
}
