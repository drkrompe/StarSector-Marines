package com.dillon.starsectormarines.battle.mapgen.bsp;

import com.dillon.starsectormarines.battle.Faction;
import com.dillon.starsectormarines.battle.mapgen.TraversalAxis;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.tactical.TacticalNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Slice-5 defence pass for the compound-as-supply model. Walks the
 * already-emitted COMMAND_POST / BARRACKS / ARMORY tactical nodes and
 * stamps one {@link TacticalNode.Kind#GUARDPOST} node on each compound's
 * attacker-facing perimeter. The defender allocator's Pass 1 picks the
 * new node up the same as any other tactical node — a small "lookout"
 * squad lands on the approach without any allocator changes.
 *
 * <p>Visual lineage: {@link DefensePostStamper} emits GUARDPOST nodes
 * for the biome-scattered defense posts (BEACH / PORT / kill-zone). This
 * pass is the same shape but anchored on existing compound nodes — no
 * embankment ring, no turret unit, just a tactical anchor for the
 * garrison allocator to fill. The visual hardpoints stamped at compound
 * corners by {@link com.dillon.starsectormarines.battle.mapgen.bsp.fill.MilitaryBaseFiller#stampGunEmplacements}
 * are non-walkable wall cells; this stamper adds the AI-aware position
 * that those visual emplacements imply.
 *
 * <p>V1 ships exactly one GUARDPOST per compound on the attacker-facing
 * edge. The design doc allows for risk-scaled 1-2 turrets per compound;
 * that's a tuning pass to do under playtest rather than guess in code.
 */
public final class CompoundPerimeterDefenderStamper {

    /** How many cells away from the compound bbox to look for a walkable cell to anchor the GUARDPOST. Tight so the guard sits *just outside* the compound, on the approach the attacker has to cross. */
    private static final int OUTSIDE_SCAN_DEPTH = 3;

    /** GUARDPOST priority for compound-perimeter posts — below the compound itself (BARRACKS=60, ARMORY=70, COMMAND_POST=95) so the compound's own garrison fills first. */
    private static final int PERIMETER_GUARDPOST_PRIORITY = 50;
    /** Squad size for the perimeter guardpost — a lookout team, not a full garrison. The compound's own node already carries the heavy garrison. */
    private static final int PERIMETER_GUARDPOST_GARRISON = 2;

    private CompoundPerimeterDefenderStamper() {}

    /**
     * Emit one perimeter GUARDPOST per compound in {@code tactical}. New nodes
     * are appended to the list in-place so {@link TacticalLinker} can wire
     * them with the same pass that handles the original compound nodes.
     *
     * <p>{@code axis} drives the choice of attacker-facing edge: defender
     * is at the "end" of the axis (NORTH for {@link TraversalAxis#SOUTH_TO_NORTH},
     * EAST for {@link TraversalAxis#WEST_TO_EAST}), so the attacker-facing
     * compound edge is the opposite side. A null axis defaults to the
     * compound's south edge — non-Conquest paths typically don't reach this
     * stamper, but the null-safety keeps the call site simple.
     */
    public static void stamp(NavigationGrid grid, TraversalAxis axis,
                             List<TacticalNode> tactical) {
        if (grid == null || tactical == null) return;
        // Snapshot the initial node list — appending while iterating would
        // re-process the GUARDPOSTs we just emitted.
        List<TacticalNode> initial = new ArrayList<>(tactical);
        for (TacticalNode node : initial) {
            if (!isCompoundKind(node.kind)) continue;
            int[] anchor = pickGuardpostAnchor(node, axis, grid);
            if (anchor == null) continue;
            tactical.add(new TacticalNode(TacticalNode.Kind.GUARDPOST,
                    anchor[0], anchor[1],
                    anchor[0], anchor[1], anchor[0], anchor[1],
                    Faction.DEFENDER,
                    PERIMETER_GUARDPOST_PRIORITY,
                    PERIMETER_GUARDPOST_GARRISON));
        }
    }

    /**
     * Walkable cell just outside the compound's bbox on the attacker-facing
     * side, or {@code null} when no walkable cell exists within
     * {@link #OUTSIDE_SCAN_DEPTH} (degenerate — compound abuts an unwalkable
     * map region; skip rather than crash).
     */
    private static int[] pickGuardpostAnchor(TacticalNode node, TraversalAxis axis,
                                             NavigationGrid grid) {
        int midX = (node.left + node.right) / 2;
        int midY = (node.top + node.bottom) / 2;
        // Resolve to a [dx, dy] step from the bbox edge cell outward. Defender
        // sits at the "end" of the axis; the attacker comes from the "start"
        // side, so the attacker-facing edge of the compound is on the start
        // side. SOUTH_TO_NORTH: attacker = south = low Y → attacker-facing
        // bbox edge = node.top (min Y). WEST_TO_EAST: attacker = west = low X
        // → attacker-facing bbox edge = node.left.
        int startX, startY, dx, dy;
        if (axis == TraversalAxis.WEST_TO_EAST) {
            startX = node.left - 1;
            startY = midY;
            dx = -1; dy = 0;
        } else {
            // Default + SOUTH_TO_NORTH.
            startX = midX;
            startY = node.top - 1;
            dx = 0; dy = -1;
        }
        // Step outward up to OUTSIDE_SCAN_DEPTH cells and return the first
        // walkable cell. The bbox edge cell itself is "inside" the compound
        // shell — we want the cell beyond the perimeter wall on the approach.
        int x = startX, y = startY;
        for (int i = 0; i < OUTSIDE_SCAN_DEPTH; i++) {
            if (grid.inBounds(x, y) && grid.isWalkable(x, y)) {
                return new int[]{x, y};
            }
            x += dx;
            y += dy;
        }
        return null;
    }

    private static boolean isCompoundKind(TacticalNode.Kind kind) {
        return kind == TacticalNode.Kind.COMMAND_POST
                || kind == TacticalNode.Kind.BARRACKS
                || kind == TacticalNode.Kind.ARMORY;
    }
}
