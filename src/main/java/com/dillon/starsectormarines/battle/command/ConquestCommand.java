package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.BattleSimulation;
import com.dillon.starsectormarines.battle.Faction;
import com.dillon.starsectormarines.battle.Squad;
import com.dillon.starsectormarines.battle.ai.goap.world.ZoneQueries;
import com.dillon.starsectormarines.battle.mapgen.TraversalAxis;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.zone.NavigationZone;
import com.dillon.starsectormarines.battle.nav.zone.ZoneGraph;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Marine-side strategic commander for CONQUEST — the land-war pattern
 * (total map control along the {@link TraversalAxis}, push enemies toward
 * the far side). Partitions the map into {@link #STRIP_COUNT} lateral
 * strips perpendicular to the traversal axis at first tick, sticky-assigns
 * each marine squad to one strip, then per slow tick assigns each squad
 * a {@link AssignmentKind#CLEAR_ZONE} pointed at the
 * <em>nearest defender-occupied zone in its strip</em> (with a positive-
 * forward bias on ties). The slow-tick cycle then iterates the squad
 * one defender position at a time as each is cleared — fixes the
 * "drop-off then drive inland" bug where targeting the strip's deepest
 * defender pulled squads past LZ-side defenders along zone-graph BFS
 * paths that took the shortest open route.
 *
 * <p>Distinct partition strategy from {@link SabotageCommand}'s
 * objective-cluster shape. SabotageCommand has N sectors centered on named
 * targets (charge sites); ConquestCommand has N lateral strips with no
 * named targets and the forward edge of the defender presence as the
 * implicit goal. Both implement {@link MissionCommand} and both ultimately
 * write {@code Squad.assignedObjective}; the per-mission specialization is
 * just in how sectors are computed.
 *
 * <p><b>First-pass shape — fixed strips, sticky assignment.</b> Strips
 * are equal-width along the lateral axis, computed once at first tick from
 * the live {@link ZoneGraph}. A squad's strip is fixed at first
 * observation (by its centroid's lateral coordinate) and doesn't change
 * even if the squad drifts laterally during the battle. Mobility across
 * strips queues for the heatmap-driven follow-up (see "Improvement path"
 * in {@code roadmap/ai/12-squad-of-squads.md} — bulge detection on the
 * influence map from {@code roadmap/ai/15-perception-and-influence.md}
 * is the right place to introduce cross-strip reallocation, since "this
 * strip is bulging" is what justifies the migration).
 *
 * <p>When the assigned strip is clear of defenders, the squad's
 * assignment is cleared (set to {@code null}); the squad falls through
 * to {@code EliminateEnemiesGoal} and engages whatever's nearest. The
 * mission's {@code ConquestObjective} closes the battle when every
 * defender supply compound (COMMAND_POST / BARRACKS / ARMORY) is
 * MARINE_HELD — not "last defender drops"; reinforcement keeps
 * spawning fresh militia from intact compounds, so the win condition
 * is now about dismantling supply infrastructure
 * (see {@code roadmap/conquest/central-keep.md}).
 */
public final class ConquestCommand implements MissionCommand {

    /**
     * Fixed strip count regardless of squad count. Three is reasonable for
     * the current CONQUEST shuttle counts (3–5 marine squads) — gets visible
     * lateral spread without making strips so thin that a 1-squad strip
     * can't hold its lane. Tunable; map-size-driven derivation queues
     * behind playtest.
     */
    public static final int STRIP_COUNT = 3;

    private final TraversalAxis axis;

    /** Lazy: built on first {@link #tick}. {@link ZoneGraph} isn't reliably populated at construction time (defender placement runs after sim creation), so we defer the partition until the first slow-tick where every spawn has settled. */
    private boolean initialized = false;
    /**
     * Per-strip zone lists, sorted forward-to-back (so the first
     * defender-occupied zone in the list is the forward-most one).
     * Indices are zone ids. Zones whose centroid falls outside any
     * partition bucket are excluded entirely.
     */
    private List<List<Integer>> stripZones;
    /**
     * Per-zone forward-axis centroid (y for SOUTH_TO_NORTH, x for WEST_TO_EAST),
     * cached at strip-build time. Indexed directly by zone id — zone ids are
     * dense (0..zoneCount-1) per the existing {@code ZoneGraph} contract, see
     * {@code ZoneQueries.zonePathBfs}. {@code fastutil-core} doesn't ship an
     * int-keyed float-valued map; a {@code float[]} skips both autobox and
     * hash entirely.
     */
    private float[] zoneForwardCoord;
    /** Sticky squad → strip-index assignment. First observation by centroid lateral coord wins; survives squad death-and-respawn since squad ids are monotonic. Sentinel-default {@code -1} stands in for "no assignment yet." */
    private final Int2IntOpenHashMap squadStripIdx = new Int2IntOpenHashMap();
    {
        squadStripIdx.defaultReturnValue(-1);
    }
    /** Lateral extent of the map cached at init time so {@link #stripFor} can classify squads without needing the grid. */
    private int lateralExtent = 0;

    public ConquestCommand(TraversalAxis axis) {
        this.axis = axis;
    }

    @Override
    public Faction faction() {
        return Faction.MARINE;
    }

    @Override
    public void tick(BattleSimulation sim) {
        if (!initialized) {
            initializePartition(sim);
            initialized = true;
        }
        for (Squad squad : sim.getSquads()) {
            if (squad.faction != Faction.MARINE) continue;
            if (squad.aliveMembers <= 0) continue;
            int stripIdx = stripFor(squad);
            int targetZone = nearestDefenderZoneInStrip(squad, stripIdx, sim);
            if (targetZone < 0) {
                // No defender-occupied zone in this strip — squad has
                // cleared their lane. Let EliminateEnemiesGoal handle
                // ambient cleanup.
                squad.assignedObjective = null;
                continue;
            }
            // Note: we DO NOT null out when targetZone == currentZone.
            // ClearAssignedZoneGoal keeps firing while in the target zone
            // (it emits a ClearZone-only customPlan in that case); nulling
            // the assignment would cause oscillation as the goal yields,
            // a different goal takes over, the squad drifts out of zone,
            // commander reticks and re-assigns the same zone, etc.
            ObjectiveAssignment cur = squad.assignedObjective;
            if (cur == null
                    || cur.kind() != AssignmentKind.CLEAR_ZONE
                    || cur.targetZoneId() != targetZone) {
                squad.assignedObjective = ObjectiveAssignment.clearZone(squad.id, targetZone);
            }
        }
    }

    /**
     * Lazy strip partition. Equal-width along the lateral axis (x for
     * SOUTH_TO_NORTH push, y for WEST_TO_EAST push); each zone is bucketed
     * by its centroid's lateral coordinate. Within each strip, zones are
     * sorted forward-to-back so {@link #forwardMostDefenderZone} can short-
     * circuit on the first defender-occupied entry.
     */
    private void initializePartition(BattleSimulation sim) {
        NavigationGrid grid = sim.getGrid();
        ZoneGraph graph = sim.getZoneGraph();
        int gridW = grid.getWidth();
        int gridH = grid.getHeight();
        this.lateralExtent = (axis == TraversalAxis.SOUTH_TO_NORTH) ? gridW : gridH;

        stripZones = new ArrayList<>(STRIP_COUNT);
        for (int i = 0; i < STRIP_COUNT; i++) stripZones.add(new ArrayList<>());
        zoneForwardCoord = new float[graph.getZones().size()];
        Arrays.fill(zoneForwardCoord, 0f);

        for (NavigationZone zone : graph.getZones()) {
            int[] cells = zone.getCellIndices();
            if (cells.length == 0) continue;
            float sumX = 0f, sumY = 0f;
            for (int cellIdx : cells) {
                sumX += (cellIdx % gridW);
                sumY += (cellIdx / gridW);
            }
            float cx = sumX / cells.length;
            float cy = sumY / cells.length;
            float lateral = (axis == TraversalAxis.SOUTH_TO_NORTH) ? cx : cy;
            float forward = (axis == TraversalAxis.SOUTH_TO_NORTH) ? cy : cx;
            if (zone.getZoneId() >= 0 && zone.getZoneId() < zoneForwardCoord.length) {
                zoneForwardCoord[zone.getZoneId()] = forward;
            }

            int stripIdx = stripIndexForLateral(lateral, this.lateralExtent);
            if (stripIdx < 0 || stripIdx >= STRIP_COUNT) continue;
            stripZones.get(stripIdx).add(zone.getZoneId());
        }

        Comparator<Integer> forwardDescending = (a, b) ->
                Float.compare(zoneForwardCoord[b], zoneForwardCoord[a]);
        for (List<Integer> strip : stripZones) {
            strip.sort(forwardDescending);
        }
    }

    /**
     * Lateral coordinate {@code lateral} → strip index in {@code [0, STRIP_COUNT)}.
     * Equal-width buckets across the full lateral extent. The {@code Math.min}
     * clamp catches the right-edge boundary (a coord exactly at {@code lateralExtent}
     * would land in bucket {@code STRIP_COUNT}, which doesn't exist).
     */
    private static int stripIndexForLateral(float lateral, int lateralExtent) {
        if (lateralExtent <= 0) return -1;
        float fractional = lateral / lateralExtent;
        if (fractional < 0f) return -1;
        if (fractional >= 1f) return STRIP_COUNT - 1;
        return Math.min((int) (fractional * STRIP_COUNT), STRIP_COUNT - 1);
    }

    /**
     * Squad → strip index. Sticky on first observation, looked up thereafter.
     * Returns the strip the squad's centroid currently falls in for the first
     * call, which is then memoized; lateral drift after first observation
     * doesn't move the squad to a new strip (per the doc 12 first-pass
     * "no cross-strip migration in v1" decision).
     */
    private int stripFor(Squad squad) {
        int cached = squadStripIdx.get(squad.id);
        if (cached >= 0) return cached;
        float lateral = (axis == TraversalAxis.SOUTH_TO_NORTH) ? squad.centroidX : squad.centroidY;
        int idx = stripIndexForLateral(lateral, lateralExtent);
        if (idx < 0) idx = 0;
        if (idx >= STRIP_COUNT) idx = STRIP_COUNT - 1;
        squadStripIdx.put(squad.id, idx);
        return idx;
    }

    /**
     * Walk this strip's zones and return the nearest defender-occupied one
     * to the squad's current centroid on the forward axis, with a
     * positive-forward bias: if both forward and backward defender
     * positions exist, the forward one wins on ties (and is preferred
     * outright when forward positions exist).
     *
     * <p>Forward bias matters because CONQUEST is a directional push. A
     * squad that's already moved past a flanking defender shouldn't be
     * pulled back to clear them — the next strip-neighbor squad picks
     * them up if they're in their strip, or {@code EliminateEnemiesGoal}
     * handles them ambiently when in LoS.
     *
     * <p>{@code -1} when the strip has no defender-occupied zones, or
     * when the only defender zone is the squad's <em>current</em> zone
     * (in which case {@link
     * com.dillon.starsectormarines.battle.ai.goap.goals.ClearAssignedZoneGoal}
     * returns relevance 0 anyway via its {@code currentZone == targetZone}
     * gate, so callers see consistent "no plan to execute" behavior and
     * the squad falls through to {@code EliminateEnemiesGoal} for in-zone
     * engagement).
     */
    private int nearestDefenderZoneInStrip(Squad squad, int stripIdx, BattleSimulation sim) {
        if (stripIdx < 0 || stripIdx >= stripZones.size()) return -1;
        float squadForward = (axis == TraversalAxis.SOUTH_TO_NORTH) ? squad.centroidY : squad.centroidX;

        int bestForwardZone = -1;
        float bestForwardDist = Float.MAX_VALUE;
        int bestBackwardZone = -1;
        float bestBackwardDist = Float.MAX_VALUE;
        for (int zoneId : stripZones.get(stripIdx)) {
            if (ZoneQueries.zoneClear(zoneId, Faction.DEFENDER, sim)) continue;
            float zoneForward = zoneForwardCoord[zoneId];
            float delta = zoneForward - squadForward;
            if (delta >= 0f) {
                if (delta < bestForwardDist) {
                    bestForwardDist = delta;
                    bestForwardZone = zoneId;
                }
            } else {
                float absDelta = -delta;
                if (absDelta < bestBackwardDist) {
                    bestBackwardDist = absDelta;
                    bestBackwardZone = zoneId;
                }
            }
        }
        return bestForwardZone >= 0 ? bestForwardZone : bestBackwardZone;
    }

    // ---- Test/debug accessors ----

    /** Strip the named squad is anchored to, or {@code -1} if it hasn't been observed yet. Public for tests + the future debug overlay. */
    public int stripIndexOf(int squadId) {
        return squadStripIdx.get(squadId);
    }

    /** Zone ids in the named strip, sorted forward-to-back. Returns an empty list for an out-of-range index. Test/debug only. */
    public List<Integer> zonesInStrip(int stripIdx) {
        if (stripZones == null || stripIdx < 0 || stripIdx >= stripZones.size()) {
            return List.of();
        }
        return List.copyOf(stripZones.get(stripIdx));
    }
}
