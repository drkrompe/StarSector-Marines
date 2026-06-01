package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.decision.goap.world.GarrisonArea;
import com.dillon.starsectormarines.battle.decision.goap.world.ZoneQueries;
import com.dillon.starsectormarines.battle.command.compound.CompoundService;
import com.dillon.starsectormarines.battle.world.gen.TraversalAxis;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.zone.NavigationZone;
import com.dillon.starsectormarines.battle.nav.zone.Portal;
import com.dillon.starsectormarines.battle.nav.zone.ZoneGraph;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Marine-side strategic commander for CONQUEST — the land-war pattern
 * (total map control along the {@link TraversalAxis}, push enemies toward
 * the far side). Two passes per slow tick:
 *
 * <ol>
 *   <li><b>Deliberate compound capture (map-global).</b> Conquest is won
 *       only when every supply compound is {@code MARINE_HELD}, so capture
 *       is treated as the objective it is rather than an accident of the
 *       front line washing over a building. A <em>measured detachment</em>
 *       (one squad, two for a multi-room keep) is peeled off to
 *       {@link AssignmentKind#SECURE_COMPOUND} a compound the moment it is
 *       <em>uncontested</em> — nearest squads assigned, capped per compound
 *       so the whole force is never stripped off the enemy. A compound that
 *       still holds defenders is only assigned to a squad already in/adjacent
 *       to it (commit incidental presence; never feed a lone squad into a
 *       defended building). "Contested" is judged over the compound's
 *       {@link GarrisonArea garrison zones} — the AABB-gated rooms — so a
 *       defender merely loitering in the open street nearby never blocks a
 *       capture order, and the unbounded outdoor flood never counts as "in"
 *       the compound. See {@code roadmap/conquest/stories/deliberate-compound-capture.md}.</li>
 *   <li><b>Strip clear-zone push.</b> Every squad not pulled for capture
 *       falls through to the lateral-strip search-and-destroy: it is
 *       sticky-assigned to one of {@link #STRIP_COUNT} strips at first
 *       observation and pointed at the <em>nearest defender-occupied zone in
 *       its strip</em> (positive-forward bias on ties), iterating one defender
 *       position at a time as each clears — fixes the "drop-off then drive
 *       inland" bug where targeting the strip's deepest defender pulled
 *       squads past LZ-side defenders along the shortest open BFS route.</li>
 * </ol>
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
     * Garrison-zone room count at or above which a compound rates a two-squad
     * capture detachment instead of one — the "scale by size" rule. A
     * standalone ARMORY/BARRACKS resolves to one or two rooms (one squad); a
     * multi-chamber central keep resolves to several (two squads, so a single
     * counter-drop mid-capture doesn't lose the take). Tunable.
     */
    public static final int LARGE_COMPOUND_ROOMS = 3;

    /**
     * Cells of slack added around a compound's footprint when resolving its
     * garrison zones (see {@link GarrisonArea#garrisonZones}). Small on purpose
     * — just enough to absorb the perimeter wall ring without dragging the open
     * exterior across the size gate.
     */
    public static final int GARRISON_MARGIN = 2;

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
     * Per-compound capture targets, built once at init. Each caches the
     * compound's anchor zone (the {@code SECURE_COMPOUND} push/hold target,
     * matching where {@code CompoundCaptureSystem} samples occupancy), its
     * garrison zones (the AABB-gated rooms used for the contested test), and
     * the size-scaled squad quota. Topology is static after spawn settle, so
     * the garrison-zone set is frozen here; only the per-tick {@code record.state}
     * and live defender occupancy vary. Compounds whose anchor sits on a wall
     * cell (rare) are skipped.
     */
    private final List<CompoundTarget> compoundTargets = new ArrayList<>();

    private record CompoundTarget(CompoundService.Record record, int anchorZoneId,
                                  int[] garrisonZones, int desiredSquads) {}

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

        // Candidate squads for assignment: alive marines, minus any born-holding
        // garrison squad. Compound garrisons are NOT assigned here — the dedicated
        // holding squad is shipped in by CompoundGarrisonSystem born with HOLD_NODE
        // at deboard (see AirSystem.tryDeboardMarine), so it holds without the
        // commander pinning whichever assault squad happened to be standing in the
        // compound at capture. Skipping HOLD_NODE here leaves the garrison on
        // station and lets the capturing assault squad keep advancing.
        List<Squad> squads = new ArrayList<>();
        for (Squad squad : sim.getSquads()) {
            if (squad.faction != Faction.MARINE) continue;
            if (squad.aliveMembers <= 0) continue;
            if (squad.assignedObjective != null
                    && squad.assignedObjective.kind() == AssignmentKind.HOLD_NODE) continue;
            squads.add(squad);
        }

        // Pass 1: deliberate compound capture. Pulls a capped detachment off
        // the hunt for each capturable compound; squads it commits are tracked
        // in `committed` and skipped by the strip push below.
        IntOpenHashSet committed = new IntOpenHashSet();
        assignCompoundCaptures(squads, committed, sim);

        // Pass 2: every uncommitted squad runs the lateral-strip search-and-
        // destroy push against the nearest defender-occupied zone in its strip.
        for (Squad squad : squads) {
            if (committed.contains(squad.id)) continue;
            int stripIdx = stripFor(squad);
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
     * Assign a measured capture detachment to each capturable compound.
     * Three phases over the candidate {@code squads}, marking each committed
     * squad in {@code committed} so the strip push skips it:
     *
     * <ol>
     *   <li><b>Preserve.</b> A squad already on {@code SECURE_COMPOUND} for a
     *       still-capturable compound keeps it (stability across replans) and
     *       fills one of that compound's slots — a squad mid-capture is by
     *       definition the "already adjacent" case.</li>
     *   <li><b>Uncontested fill.</b> For compounds with no defender in any
     *       garrison zone, greedily assign the nearest uncommitted squads up
     *       to the per-compound quota. Greedy nearest-pair so several
     *       compounds spread the squads rather than all piling on the closest
     *       one.</li>
     *   <li><b>Contested adjacent.</b> For compounds that still hold defenders,
     *       commit only uncommitted squads already in/adjacent to a garrison
     *       zone — convert incidental presence into a committed capture; never
     *       pull a fresh squad into a defended building.</li>
     * </ol>
     */
    private void assignCompoundCaptures(List<Squad> squads, IntOpenHashSet committed, BattleView sim) {
        if (compoundTargets.isEmpty() || squads.isEmpty()) return;

        int n = compoundTargets.size();
        int[] slots = new int[n];        // remaining capture slots per compound
        boolean[] contested = new boolean[n];
        for (int i = 0; i < n; i++) {
            CompoundTarget t = compoundTargets.get(i);
            if (t.record.state == CompoundService.CompoundState.MARINE_HELD) {
                slots[i] = 0;            // already ours — no detachment
                continue;
            }
            slots[i] = t.desiredSquads;
            contested[i] = isContested(t, sim);
        }

        // Phase 1: preserve in-flight captures.
        for (Squad squad : squads) {
            ObjectiveAssignment a = squad.assignedObjective;
            if (a == null || a.kind() != AssignmentKind.SECURE_COMPOUND) continue;
            int idx = targetIndexForAnchorZone(a.targetZoneId());
            if (idx < 0 || slots[idx] <= 0) continue;
            slots[idx]--;
            committed.add(squad.id);
        }

        // Phase 2: greedy nearest-pair fill of uncontested compounds.
        while (true) {
            int bestSquad = -1, bestTarget = -1;
            float bestDist = Float.MAX_VALUE;
            for (Squad squad : squads) {
                if (committed.contains(squad.id)) continue;
                for (int i = 0; i < n; i++) {
                    if (slots[i] <= 0 || contested[i]) continue;
                    float d = distSq(squad, compoundTargets.get(i));
                    if (d < bestDist) {
                        bestDist = d;
                        bestSquad = squad.id;
                        bestTarget = i;
                    }
                }
            }
            if (bestSquad < 0) break;
            commitCapture(squadById(squads, bestSquad), compoundTargets.get(bestTarget), committed);
            slots[bestTarget]--;
        }

        // Phase 3: commit already-adjacent squads to contested compounds.
        for (int i = 0; i < n; i++) {
            if (slots[i] <= 0 || !contested[i]) continue;
            CompoundTarget t = compoundTargets.get(i);
            for (Squad squad : squads) {
                if (slots[i] <= 0) break;
                if (committed.contains(squad.id)) continue;
                if (!squadAdjacentToCompound(squad, t, sim)) continue;
                commitCapture(squad, t, committed);
                slots[i]--;
            }
        }
    }

    private void commitCapture(Squad squad, CompoundTarget t, IntOpenHashSet committed) {
        committed.add(squad.id);
        ObjectiveAssignment cur = squad.assignedObjective;
        if (cur == null
                || cur.kind() != AssignmentKind.SECURE_COMPOUND
                || cur.targetZoneId() != t.anchorZoneId) {
            squad.assignedObjective = ObjectiveAssignment.secureCompound(
                    squad.id, t.anchorZoneId, t.record.node);
        }
    }

    /** True iff any of the compound's garrison rooms holds a live defender. The AABB-gated garrison-zone set excludes the open exterior, so a defender loitering in the street outside doesn't read as contesting the compound. */
    private boolean isContested(CompoundTarget t, BattleView sim) {
        for (int zoneId : t.garrisonZones) {
            if (!ZoneQueries.zoneClear(zoneId, Faction.DEFENDER, sim)) return true;
        }
        return false;
    }

    /** True iff the squad currently stands in, or in a zone bordering, one of the compound's garrison rooms — the "already there, commit the capture" gate for contested compounds. */
    private boolean squadAdjacentToCompound(Squad squad, CompoundTarget t, BattleView sim) {
        int cz = ZoneQueries.squadCurrentZone(squad, sim);
        if (cz < 0) return false;
        if (containsZone(t.garrisonZones, cz)) return true;
        ZoneGraph graph = sim.getZoneGraph();
        for (int portalId : ZoneQueries.portalsOf(cz, sim)) {
            Portal p = graph.portalById(portalId);
            if (p == null) continue;
            if (containsZone(t.garrisonZones, p.otherZone(cz))) return true;
        }
        return false;
    }

    private int targetIndexForAnchorZone(int anchorZoneId) {
        for (int i = 0; i < compoundTargets.size(); i++) {
            if (compoundTargets.get(i).anchorZoneId == anchorZoneId) return i;
        }
        return -1;
    }

    private static float distSq(Squad squad, CompoundTarget t) {
        float dx = squad.centroidX - t.record.node.anchorX;
        float dy = squad.centroidY - t.record.node.anchorY;
        return dx * dx + dy * dy;
    }

    private static boolean containsZone(int[] zones, int zoneId) {
        for (int z : zones) if (z == zoneId) return true;
        return false;
    }

    private static Squad squadById(List<Squad> squads, int id) {
        for (Squad s : squads) if (s.id == id) return s;
        return null;
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
                int anchorZone = graph.zoneIdAt(r.node.anchorX, r.node.anchorY);
                if (anchorZone < 0) continue;  // wall-cell anchor (rare) — skip
                int[] garrisonZones = resolveGarrisonZones(r, anchorZone, sim);
                int desiredSquads = garrisonZones.length >= LARGE_COMPOUND_ROOMS ? 2 : 1;
                compoundTargets.add(new CompoundTarget(r, anchorZone, garrisonZones, desiredSquads));
            }
        }
    }

    /**
     * The compound's garrison rooms via the AABB size+containment gate, as an
     * int array. Falls back to the anchor zone alone when the footprint
     * resolves to nothing (degenerate footprint or a synthetic test grid) so
     * the contested test always has at least the capture zone to look at.
     */
    private static int[] resolveGarrisonZones(CompoundService.Record r, int anchorZone, BattleView sim) {
        List<Integer> zones = GarrisonArea.garrisonZones(r.node, GARRISON_MARGIN, sim);
        if (zones.isEmpty()) return new int[] { anchorZone };
        int[] out = new int[zones.size()];
        for (int i = 0; i < out.length; i++) out[i] = zones.get(i);
        return out;
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
