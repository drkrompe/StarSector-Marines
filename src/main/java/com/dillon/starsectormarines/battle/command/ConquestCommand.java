package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.decision.goap.world.ZoneQueries;
import com.dillon.starsectormarines.battle.command.compound.CompoundService;
import com.dillon.starsectormarines.battle.world.gen.TraversalAxis;
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

    /**
     * Forward-axis cells of lookahead beyond the squad's current position
     * within which a compound zone is considered "ripe" for capture. Without
     * this the assignment only fires after the squad passes the compound; a
     * few cells of margin lets the squad begin diverting before it overshoots.
     */
    public static final float COMPOUND_LOOKAHEAD = 6f;

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
    /**
     * Zone id of the open exterior — the largest zone by cell count, cached at
     * init. Never handed out as a {@code CLEAR_ZONE} target: the exterior flood
     * spans the whole map, always holds a stray defender, and so reads as
     * "never clear" — a squad ordered to clear it charges the map forever
     * instead of doing focused area control. Outdoor defenders are still
     * engaged ambiently via {@code EliminateEnemiesGoal} when in LoS. We key on
     * largest-by-cells rather than id 0 because the flood-fill ids zones by
     * scan order, so id 0 can land on an indoor region. Only set when the
     * largest zone <em>dominates</em> — at least {@link #EXTERIOR_DOMINANCE_RATIO}×
     * the second-largest — so a map whose zones are all comparable in size
     * (no single open expanse) excludes nothing. {@code -1} until init / when
     * nothing dominates.
     */
    private int exteriorZoneId = -1;
    /**
     * The largest zone is only treated as the open exterior when it is at least
     * this many times bigger than the next-largest zone. The real outdoor flood
     * dwarfs every building; a map of similarly-sized rooms has no exterior to
     * exclude.
     */
    private static final float EXTERIOR_DOMINANCE_RATIO = 2.0f;
    /**
     * Per-compound records mapped to the zone containing their anchor cell.
     * Built at init time by looking up each compound record's anchor in the
     * zone graph. Only compounds whose anchor resolves to a valid zone are
     * included — a wall-cell anchor (rare) is silently skipped.
     */
    private final List<CompoundZone> compoundZones = new ArrayList<>();

    private record CompoundZone(CompoundService.Record record, int zoneId, int stripIdx) {}

    public ConquestCommand(TraversalAxis axis) {
        this.axis = axis;
    }

    @Override
    public Faction faction() {
        return Faction.MARINE;
    }

    @Override
    public void tick(BattleView sim) {
        if (!initialized) {
            initializePartition(sim);
            initialized = true;
        }

        // Compound garrisons are NOT assigned here. The dedicated holding squad
        // is shipped in by CompoundGarrisonSystem and born with HOLD_NODE at
        // deboard (see AirSystem.tryDeboardMarine), so it holds without the
        // commander pinning whichever assault squad happened to be standing in
        // the compound at capture. The loop below leaves any HOLD_NODE squad
        // alone (the skip), and the capturing assault squad keeps advancing.
        for (Squad squad : sim.getSquads()) {
            if (squad.faction != Faction.MARINE) continue;
            if (squad.aliveMembers <= 0) continue;
            if (squad.assignedObjective != null
                    && squad.assignedObjective.kind() == AssignmentKind.HOLD_NODE) continue;
            int stripIdx = stripFor(squad);
            float squadForward = (axis == TraversalAxis.SOUTH_TO_NORTH)
                    ? squad.centroidY : squad.centroidX;

            // Pass 1: uncaptured compound at or behind the squad's forward
            // position. Securing compounds the front line has reached (or
            // bypassed) is highest priority — an uncaptured compound in
            // the rear keeps spawning defenders behind you.
            CompoundZone ripe = nearestRipeCompound(stripIdx, squadForward, sim);
            if (ripe != null) {
                ObjectiveAssignment cur = squad.assignedObjective;
                if (cur == null
                        || cur.kind() != AssignmentKind.SECURE_COMPOUND
                        || cur.targetZoneId() != ripe.zoneId) {
                    squad.assignedObjective = ObjectiveAssignment.secureCompound(
                            squad.id, ripe.zoneId, ripe.record.node);
                }
                continue;
            }

            // Pass 2: fall through to nearest defender-occupied zone for
            // the street-by-street push.
            int targetZone = nearestDefenderZoneInStrip(squad, stripIdx, sim);
            if (targetZone < 0) {
                squad.assignedObjective = null;
                continue;
            }
            ObjectiveAssignment cur = squad.assignedObjective;
            if (cur == null
                    || cur.kind() != AssignmentKind.CLEAR_ZONE
                    || cur.targetZoneId() != targetZone) {
                squad.assignedObjective = ObjectiveAssignment.clearZone(squad.id, targetZone);
            }
        }
    }

    /**
     * Finds the nearest uncaptured compound in this strip whose zone is at
     * or behind the squad's forward position. "At or behind" means the
     * compound zone's forward coordinate is {@code <=} the squad's, with a
     * small lookahead margin so a squad approaching the compound building
     * doesn't overshoot before the assignment triggers.
     *
     * <p>Returns {@code null} when no ripe compound exists in the strip —
     * caller falls through to the generic {@link #nearestDefenderZoneInStrip}
     * for the street-by-street push.
     */
    private CompoundZone nearestRipeCompound(int stripIdx, float squadForward, BattleView sim) {
        CompoundZone best = null;
        float bestDist = Float.MAX_VALUE;
        for (CompoundZone cz : compoundZones) {
            if (cz.stripIdx != stripIdx) continue;
            if (cz.record.state == CompoundService.CompoundState.MARINE_HELD) continue;
            float compoundForward = zoneForwardCoord[cz.zoneId];
            // Ripe = compound is at or behind the squad's forward line,
            // with a small lookahead so the assignment fires a few cells
            // before the squad arrives rather than after it passes.
            if (compoundForward > squadForward + COMPOUND_LOOKAHEAD) continue;
            float dist = Math.abs(compoundForward - squadForward);
            if (dist < bestDist) {
                bestDist = dist;
                best = cz;
            }
        }
        return best;
    }

    /**
     * Lazy strip partition. Equal-width along the lateral axis (x for
     * SOUTH_TO_NORTH push, y for WEST_TO_EAST push); each zone is bucketed
     * by its centroid's lateral coordinate. Within each strip, zones are
     * sorted forward-to-back so {@link #nearestDefenderZoneInStrip} can short-
     * circuit on the first defender-occupied entry.
     */
    private void initializePartition(BattleView sim) {
        NavigationGrid grid = sim.getGrid();
        ZoneGraph graph = sim.getZoneGraph();
        int gridW = grid.getWidth();
        int gridH = grid.getHeight();
        this.lateralExtent = (axis == TraversalAxis.SOUTH_TO_NORTH) ? gridW : gridH;

        stripZones = new ArrayList<>(STRIP_COUNT);
        for (int i = 0; i < STRIP_COUNT; i++) stripZones.add(new ArrayList<>());
        zoneForwardCoord = new float[graph.getZones().size()];
        Arrays.fill(zoneForwardCoord, 0f);

        int largestCells = -1, secondCells = -1, largestZone = -1;
        for (NavigationZone zone : graph.getZones()) {
            int[] cells = zone.getCellIndices();
            if (cells.length == 0) continue;
            if (cells.length > largestCells) {
                secondCells = largestCells;
                largestCells = cells.length;
                largestZone = zone.getZoneId();
            } else if (cells.length > secondCells) {
                secondCells = cells.length;
            }
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

        // Only flag an exterior when one zone dominates — the real outdoor
        // flood does; a map of comparable rooms has nothing to exclude.
        if (largestZone >= 0
                && (secondCells <= 0 || largestCells >= EXTERIOR_DOMINANCE_RATIO * secondCells)) {
            exteriorZoneId = largestZone;
        }

        Comparator<Integer> forwardDescending = (a, b) ->
                Float.compare(zoneForwardCoord[b], zoneForwardCoord[a]);
        for (List<Integer> strip : stripZones) {
            strip.sort(forwardDescending);
        }

        CompoundService compounds = sim.getCompoundService();
        if (compounds != null) {
            for (CompoundService.Record r : compounds.getRecords()) {
                int zoneId = graph.zoneIdAt(r.node.anchorX, r.node.anchorY);
                if (zoneId < 0) continue;
                float lateral = (axis == TraversalAxis.SOUTH_TO_NORTH)
                        ? r.node.anchorX : r.node.anchorY;
                int si = stripIndexForLateral(lateral, this.lateralExtent);
                if (si < 0) si = 0;
                if (si >= STRIP_COUNT) si = STRIP_COUNT - 1;
                compoundZones.add(new CompoundZone(r, zoneId, si));
            }
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
     * com.dillon.starsectormarines.battle.infantry.ClearAssignedZoneGoal}
     * returns relevance 0 anyway via its {@code currentZone == targetZone}
     * gate, so callers see consistent "no plan to execute" behavior and
     * the squad falls through to {@code EliminateEnemiesGoal} for in-zone
     * engagement).
     */
    private int nearestDefenderZoneInStrip(Squad squad, int stripIdx, BattleView sim) {
        if (stripIdx < 0 || stripIdx >= stripZones.size()) return -1;
        float squadForward = (axis == TraversalAxis.SOUTH_TO_NORTH) ? squad.centroidY : squad.centroidX;

        int bestForwardZone = -1;
        float bestForwardDist = Float.MAX_VALUE;
        int bestBackwardZone = -1;
        float bestBackwardDist = Float.MAX_VALUE;
        for (int zoneId : stripZones.get(stripIdx)) {
            if (zoneId == exteriorZoneId) continue;
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
