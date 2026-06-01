package com.dillon.starsectormarines.battle.decision.goap.world;

import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.zone.NavigationZone;
import com.dillon.starsectormarines.battle.nav.zone.ZoneGraph;
import com.dillon.starsectormarines.battle.decision.TacticalNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Stateless helper that decides which navigation zones make up a compound's
 * <em>garrison area</em> — the rooms a squad holding the compound should clear
 * and patrol, as opposed to the open ground it merely crosses.
 *
 * <p>This is the single home of the AABB gate first introduced for story 17's
 * {@code SecureCompoundGoal} fix: a zone counts as part of the garrison iff it
 * is small enough relative to the compound footprint (size gate, O(1)) and sits
 * mostly inside that footprint (containment gate, O(cells) — only on size-gate
 * survivors). The unbounded outdoor flood-fill fails the size gate via a single
 * field read, so it is never mistaken for a clearable room.
 *
 * <p>Two entry points: {@link #isGarrisonZone} tests one zone against an
 * explicit box (used where the caller already has a box — e.g. a per-building
 * bbox), and {@link #garrisonZones} enumerates the whole graph against a node's
 * footprint expanded by a margin (used by the garrison behaviors that patrol a
 * multi-building compound).
 */
public final class GarrisonArea {

    private GarrisonArea() {}

    /**
     * Multiplier on the footprint area above which a zone is treated as open
     * ground to transit, not a room to clear. The outdoor flood dwarfs any
     * building footprint and is rejected by this gate alone; the slack absorbs
     * door/edge cells that spill just outside the box.
     */
    public static final float MAX_GARRISON_AREA_RATIO = 1.25f;

    /**
     * Minimum fraction of a zone's cells that must fall inside the footprint
     * for the zone to count as part of the garrison.
     */
    public static final float MIN_INSIDE_FRACTION = 0.5f;

    /**
     * Zone ids that make up {@code node}'s garrison area — its compound
     * footprint ({@link TacticalNode#compoundLeft()} … the persisted union bbox
     * of the whole base, or the node's own bbox for a standalone post) expanded
     * by {@code margin} cells, then filtered through the size + containment
     * gate. Sorted descending by cell count so callers that want the dominant
     * room first get it cheaply. Returns an empty list for a null node/sim.
     *
     * <p>{@code margin} absorbs the perimeter wall ring / parade-ground rim so
     * rooms whose cells spill a cell or two past the raw footprint still
     * qualify; keep it small (≈2) so it never drags the open exterior across
     * the size gate.
     */
    public static List<Integer> garrisonZones(TacticalNode node, int margin, BattleView sim) {
        if (node == null || sim == null) return List.of();
        int boxL = node.compoundLeft() - margin;
        int boxT = node.compoundTop() - margin;
        int boxR = node.compoundRight() + margin;
        int boxB = node.compoundBottom() + margin;

        ZoneGraph graph = sim.getZoneGraph();
        NavigationGrid grid = sim.getGrid();
        List<Integer> out = new ArrayList<>();
        for (NavigationZone zone : graph.getZones()) {
            // Skip 1-cell doorway micro-zones — they're portals between rooms,
            // not rooms to patrol, and would otherwise inflate the room count
            // (and so the multi-building check) of a single-building footprint.
            if (zone.getCellCount() == 1 && grid.isDoorwayAt(zone.getCellIndices()[0])) continue;
            if (isGarrisonZone(zone, boxL, boxT, boxR, boxB, grid)) {
                out.add(zone.getZoneId());
            }
        }
        out.sort((a, b) -> Integer.compare(
                graph.zoneById(b).getCellCount(), graph.zoneById(a).getCellCount()));
        return out;
    }

    /**
     * True iff {@code zone} is small enough and sits mostly inside the box
     * {@code [boxL..boxR] × [boxT..boxB]} — i.e. it's a room the garrison should
     * clear, not open ground it crosses. Two gates, cheap one first:
     *
     * <ol>
     *   <li><b>Size (O(1)):</b> {@link NavigationZone#getCellCount()} is a field
     *       read, so a zone meaningfully larger than the footprint (the outdoor
     *       flood) bails here without its cells ever being iterated.</li>
     *   <li><b>Containment (O(cells), size-gate survivors only):</b> point-in-
     *       rect per cell; the zone qualifies when at least
     *       {@link #MIN_INSIDE_FRACTION} of its cells fall inside the box.</li>
     * </ol>
     */
    public static boolean isGarrisonZone(NavigationZone zone,
                                         int boxL, int boxT, int boxR, int boxB,
                                         NavigationGrid grid) {
        if (zone == null) return false;
        long boxArea = (long) (boxR - boxL + 1) * (boxB - boxT + 1);
        if (zone.getCellCount() > MAX_GARRISON_AREA_RATIO * boxArea) return false;

        int width = grid.getWidth();
        int[] cells = zone.getCellIndices();
        int inside = 0;
        for (int idx : cells) {
            int x = idx % width;
            int y = idx / width;
            if (x >= boxL && x <= boxR && y >= boxT && y <= boxB) inside++;
        }
        return inside >= MIN_INSIDE_FRACTION * cells.length;
    }
}
