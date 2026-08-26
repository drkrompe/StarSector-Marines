package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.command.compound.CompoundService;
import com.dillon.starsectormarines.battle.command.influence.CommanderContact;
import com.dillon.starsectormarines.battle.command.influence.CommanderInfluenceSnapshot;
import com.dillon.starsectormarines.battle.command.ConquestFrontSnapshot.AssignmentReason;
import com.dillon.starsectormarines.battle.command.ConquestFrontSnapshot.Phase;
import com.dillon.starsectormarines.battle.command.ConquestFrontSnapshot.SquadDirective;
import com.dillon.starsectormarines.battle.command.ConquestFrontSnapshot.TrackState;
import com.dillon.starsectormarines.battle.world.gen.TraversalAxis;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Marine-side strategic commander for CONQUEST — the land-war pattern
 * (total map control along the {@link TraversalAxis}, push enemies toward
 * the far side). The command evaluates these layers each slow tick:
 *
 * <ol>
 *   <li><b>Deliberate compound capture (map-global).</b> Conquest is won
 *       only when every supply compound is {@code MARINE_HELD}, so capture
 *       is treated as the objective it is rather than an accident of the
 *       front line washing over a building. A <em>measured detachment</em>
 *       (one squad, two for a multi-room keep) is peeled off to
 *       {@link AssignmentKind#SECURE_COMPOUND} a compound the moment it is
 *       <em>uncontested</em> — squads without actionable front work go first,
 *       and at least one executable actionable squad remains on the front.
 *       The budget is global across compounds. A compound that
 *       still holds defenders is only assigned to a squad already in/adjacent
 *       to it (commit incidental presence; never feed a lone squad into a
 *       defended building). "Contested" is judged over the compound's
 *       {@code GarrisonArea} room set — the AABB-gated rooms — so a
 *       defender merely loitering in the open street nearby never blocks a
 *       capture order, and the unbounded outdoor flood never counts as "in"
 *       the compound. See {@code conquest-nouns.md}.</li>
 *   <li><b>Track front push.</b> Every squad not pulled for capture
 *       keeps a sticky preferred track, but may support one neighboring track
 *       when its own has no actionable target. Tracks coordinate the front;
 *       they are not ownership fences. A discrete hostile-contact zone wins;
 *       otherwise open-ground belief may produce a bounded own-force staging
 *       marker behind the hostile line.</li>
 *   <li><b>Keep convergence.</b> Once the canonical command post is the only
 *       uncaptured compound, every mobile assault squad converges on its
 *       {@link AssignmentKind#SECURE_COMPOUND} objective. Born-holding
 *       garrisons remain excluded.</li>
 *   <li><b>Final-compound convergence.</b> If the keep is already held and a
 *       contested non-keep compound is the sole remaining objective, capture
 *       quota remains deliberate while every other mobile squad receives
 *       room-clear support across any track. More than one recapture reopens
 *       the ordinary front.</li>
 * </ol>
 *
 * <p>Distinct partition strategy from {@link SabotageCommand}'s
 * objective-cluster shape. SabotageCommand has N sectors centered on named
 * targets (charge sites); ConquestCommand has N lateral strips and compound
 * objectives, with the forward edge of known hostile presence as its lane
 * target. Migrated Conquest planning emits proposals from a frozen command
 * frame; the assignment arbiter alone applies accepted directives.
 *
 * <p><b>Command shape — fixed preferred tracks, soft support.</b> Tracks
 * are equal-width along the lateral axis, computed once at first plan from
 * frozen public topology. A squad's preferred track is fixed at first
 * observation (by its centroid's lateral coordinate) and doesn't change
 * even if the squad drifts laterally during the battle. The effective track
 * can temporarily be either adjacent track when the preferred track is idle;
 * the command snapshot publishes both identities and the belief-derived
 * pressure/progress picture that explains the order.
 *
 * <p>When the preferred and adjacent tracks have no actionable defender
 * zone, open-ground resistance ahead may yield an {@code ADVANCE_TRACK}
 * staging order. Without that faction-local belief the assignment is cleared
 * (set to {@code null}) and the squad falls through to
 * {@code EliminateEnemiesGoal}. The
 * mission's {@code ConquestObjective} closes the battle when every
 * defender supply compound (COMMAND_POST / BARRACKS / ARMORY) is
 * MARINE_HELD — not "last defender drops"; reinforcement keeps
 * spawning fresh militia from intact compounds, so the win condition
 * is now about dismantling supply infrastructure
 * (see {@code conquest-nouns.md}).
 */
public final class ConquestCommand implements ConquestFrontCommand,
        AutonomousMissionCommand<ConquestCommandFrame, ConquestFrontSnapshot> {

    /**
     * Fixed strip count regardless of squad count. Three is reasonable for
     * the current CONQUEST shuttle counts (3–5 marine squads) — gets visible
     * lateral spread without making strips so thin that a 1-squad strip
     * can't hold its lane. Tunable; map-size-driven derivation queues
     * behind playtest.
     */
    public static final int STRIP_COUNT = ConquestTrackLayout.DEFAULT_TRACK_COUNT;

    /**
     * Garrison-zone room count at or above which a compound rates a two-squad
     * capture detachment instead of one — the "scale by size" rule. A
     * standalone ARMORY/BARRACKS resolves to one or two rooms (one squad); a
     * multi-chamber central keep resolves to several (two squads, so a single
     * counter-drop mid-capture doesn't lose the take). Tunable.
     */
    public static final int LARGE_COMPOUND_ROOMS = 3;

    /** Keep at least one executable, actionable squad on a live front. */
    public static final int MIN_FRONT_RESERVE_SQUADS = 1;

    /** Stand this many cells behind the nearest believed hostile in a track. */
    static final int TRACK_LINE_STANDOFF_CELLS = 8;
    /** A staging marker may lead the current friendly line by at most this much. */
    static final int TRACK_LINE_LEAD_CELLS = 8;
    /** One published staging order cannot pull a rear squad farther than this. */
    static final int TRACK_LINE_MAX_STRIDE_CELLS = 24;
    /** Ignore marker changes too small to produce meaningful forward motion. */
    static final int TRACK_LINE_MIN_ADVANCE_CELLS = 3;
    /** Quantization keeps small belief jitter from rewriting a stable order. */
    static final int TRACK_LINE_BAND_CELLS = 4;
    private static final int TRACK_LINE_SNAP_RADIUS = 12;

    /**
     * Cells of slack added around a compound's footprint when resolving its
     * garrison zones (see {@code GarrisonArea.garrisonZones}). Small on purpose
     * — just enough to absorb the perimeter wall ring without dragging the open
     * exterior across the size gate.
     */
    public static final int GARRISON_MARGIN = 2;

    private final TraversalAxis axis;
    /** Shared production geometry; lazily synthesized only by the legacy axis constructor used in tests. */
    private ConquestTrackLayout trackLayout;

    /** Lazy: built from the first frozen command frame after battle setup settles. */
    private boolean initialized = false;
    /**
     * Per-strip zone lists, sorted forward-to-back (so the first
     * known hostile-contact zone in the list is the forward-most one).
     * Indices are zone ids. Zones whose centroid falls outside any
     * partition bucket are excluded entirely.
     */
    private List<List<Integer>> stripZones;
    /**
     * Per-zone forward-axis centroid (y for SOUTH_TO_NORTH, x for WEST_TO_EAST),
     * cached at strip-build time. Indexed directly by zone id — zone ids are
     * dense (0..zoneCount-1) per the existing zone-graph contract.
     * {@code fastutil-core} doesn't ship an
     * int-keyed float-valued map; a {@code float[]} skips both autobox and
     * hash entirely.
     */
    private float[] zoneForwardCoord;
    /** Per-zone lateral centroid used for adjacent-track target distance. */
    private float[] zoneLateralCoord;
    private float[] zoneCentroidX;
    private float[] zoneCentroidY;
    /** Walkable representative cells for debug/action explanation markers. */
    private int[] zoneMarkerX;
    private int[] zoneMarkerY;
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
     * instead of doing focused area control. Outdoor defenders are engaged
     * ambiently via {@code EliminateEnemiesGoal} when in LoS; farther commander
     * belief may move an idle squad toward a safe lane staging line. We key on
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
     * the garrison-zone set is frozen here; objective state and faction-local
     * contact evidence refresh each command frame. Compounds whose anchor sits
     * on a wall cell (rare) are skipped.
     */
    private final List<CompoundTarget> compoundTargets = new ArrayList<>();

    /** Once-per-command-tick explanation consumed by diagnostics and UI. */
    private volatile ConquestFrontSnapshot frontSnapshot;

    private record CompoundTarget(CompoundService.CompoundState state,
                                  TacticalNode node, int anchorZoneId,
                                  int[] garrisonZones, int desiredSquads) {}

    /** Mutable working copy; never exposes or mutates a live {@code Squad}. */
    private static final class PlanningSquad {
        final int id;
        final Faction faction;
        final int aliveMembers;
        final float centroidX;
        final float centroidY;
        final int anchorCellX;
        final int anchorCellY;
        final int currentZoneId;
        final UnitRole role;
        final boolean localContact;
        final String executionSuspension;
        final ObjectiveAssignment originalAssignment;
        ObjectiveAssignment assignedObjective;

        PlanningSquad(CommandSquadState state) {
            id = state.squadId();
            faction = state.faction();
            aliveMembers = state.aliveMembers();
            centroidX = state.centroidX();
            centroidY = state.centroidY();
            anchorCellX = state.anchorCellX();
            anchorCellY = state.anchorCellY();
            currentZoneId = state.currentZoneId();
            role = state.role();
            localContact = state.localContact();
            executionSuspension = state.executionSuspension();
            originalAssignment = state.assignment();
            assignedObjective = state.assignment();
        }
    }

    public ConquestCommand(TraversalAxis axis) {
        this.axis = axis;
        this.frontSnapshot = ConquestFrontSnapshot.empty(axis);
    }

    public ConquestCommand(ConquestTrackLayout trackLayout) {
        this.trackLayout = trackLayout;
        this.axis = trackLayout.axis();
        this.frontSnapshot = ConquestFrontSnapshot.empty(Faction.MARINE, axis);
    }

    public ConquestFrontSnapshot frontSnapshot() {
        return frontSnapshot;
    }

    @Override
    public Faction faction() {
        return Faction.MARINE;
    }

    @Override
    public String strategyId() {
        return "conquest-attacker";
    }

    @Override
    public CommandPlan<ConquestFrontSnapshot> plan(ConquestCommandFrame frame) {
        if (!initialized) {
            initializePartition(frame);
            initialized = true;
        }
        refreshCompoundTargets(frame);

        // Candidate squads for assignment: alive marines, minus any born-holding
        // garrison squad. Compound garrisons are NOT assigned here — the dedicated
        // holding squad is shipped in by CompoundGarrisonSystem born with HOLD_NODE
        // at deboard (see AirSystem.tryDeboardMarine), so it holds without the
        // commander pinning whichever assault squad happened to be standing in the
        // compound at capture. Skipping HOLD_NODE here leaves the garrison on
        // station and lets the capturing assault squad keep advancing.
        List<PlanningSquad> squads = new ArrayList<>();
        Map<Integer, PlanningSquad> allSquads = new TreeMap<>();
        Map<Integer, SquadDirective> directives = new TreeMap<>();
        for (CommandSquadState state : frame.squads()) {
            PlanningSquad squad = new PlanningSquad(state);
            allSquads.put(squad.id, squad);
            if (squad.aliveMembers <= 0) continue;
            if (state.directive() != null
                    && state.directive().authority().priority()
                    > CommandAuthority.MISSION_COMMAND.priority()) {
                int preferred = stripFor(squad);
                directives.put(squad.id, directive(squad, preferred, preferred,
                        squad.role == UnitRole.GARRISON
                                || (squad.originalAssignment != null
                                && squad.originalAssignment.kind()
                                == AssignmentKind.HOLD_NODE)
                                ? AssignmentReason.GARRISON_HOLD
                                : AssignmentReason.EXTERNAL_OWNERSHIP_PRESERVED));
                continue;
            }
            squads.add(squad);
        }

        IntOpenHashSet committed = new IntOpenHashSet();
        CompoundTarget keep = canonicalKeep();
        int remainingCompounds = remainingCompounds();
        CompoundTarget soleRemaining = remainingCompounds == 1
                ? soleRemainingCompound() : null;
        boolean keepConvergence = keep != null
                && keep.state != CompoundService.CompoundState.MARINE_HELD
                && remainingCompounds == 1;
        boolean finalCompoundConvergence = keep != null
                && keep.state == CompoundService.CompoundState.MARINE_HELD
                && soleRemaining != null
                && soleRemaining != keep
                && isContested(soleRemaining, frame);
        Phase phase = keepConvergence ? Phase.KEEP_CONVERGENCE
                : finalCompoundConvergence
                ? Phase.FINAL_COMPOUND_CONVERGENCE : Phase.LANE_ADVANCE;

        IntOpenHashSet deferredCaptures = new IntOpenHashSet();
        if (keepConvergence) {
            for (PlanningSquad squad : squads) {
                if (reachableZone(squad, keep.anchorZoneId, frame)) {
                    commitCapture(squad, keep, committed, directives,
                            AssignmentReason.KEEP_APPROACH);
                } else {
                    squad.assignedObjective = null;
                    int preferred = stripFor(squad);
                    directives.put(squad.id, directive(squad, preferred, preferred,
                            AssignmentReason.NO_REACHABLE_COMPOUND_TARGET));
                }
            }
        } else {
            // Pass 1: deliberate compound capture. Pulls a capped detachment
            // off the front while preserving ordinary compound quotas.
            assignCompoundCaptures(squads, committed, deferredCaptures,
                    directives, frame);
        }

        if (!keepConvergence) {
            // Pass 2: preferred tracks remain sticky, but an idle track is a
            // coordination gap rather than an ownership fence. Borrow useful
            // work from one neighboring track without permanently re-homing.
            for (PlanningSquad squad : squads) {
                if (committed.contains(squad.id)) continue;
                int preferredTrack = stripFor(squad);
                TargetChoice choice = finalCompoundConvergence
                        ? finalCompoundSupportChoice(squad, soleRemaining, frame)
                        : targetChoice(squad, preferredTrack, frame);
                if (choice.targetZoneId < 0) {
                    TrackStage stage = !finalCompoundConvergence
                            ? laneStageChoice(squad, preferredTrack, frame)
                            : null;
                    if (stage != null) {
                        ObjectiveAssignment cur = squad.assignedObjective;
                        if (cur == null
                                || cur.kind() != AssignmentKind.ADVANCE_TRACK
                                || cur.targetCellX() != stage.cellX()
                                || cur.targetCellY() != stage.cellY()) {
                            squad.assignedObjective = ObjectiveAssignment.advanceTrack(
                                    squad.id, stage.cellX(), stage.cellY());
                        }
                        SquadDirective planned = directive(squad,
                                preferredTrack, stage.trackIndex(),
                                AssignmentReason.TRACK_LINE_ADVANCE);
                        if (deferredCaptures.contains(squad.id)) {
                            planned = planned.withDistantCaptureDeferred();
                        }
                        directives.put(squad.id, planned);
                        continue;
                    }
                    squad.assignedObjective = null;
                    SquadDirective planned = directive(squad, preferredTrack,
                            preferredTrack,
                            AssignmentReason.NO_ACTIONABLE_TRACK_TARGET);
                    if (deferredCaptures.contains(squad.id)) {
                        planned = planned.withDistantCaptureDeferred();
                    }
                    directives.put(squad.id, planned);
                    continue;
                }
                ObjectiveAssignment cur = squad.assignedObjective;
                if (cur == null
                        || cur.kind() != AssignmentKind.CLEAR_ZONE
                        || cur.targetZoneId() != choice.targetZoneId) {
                    squad.assignedObjective = ObjectiveAssignment.clearZone(
                            squad.id, choice.targetZoneId);
                }
                AssignmentReason reason = choice.trackIndex == preferredTrack
                        ? AssignmentReason.TRACK_ADVANCE
                        : AssignmentReason.ADJACENT_TRACK_SUPPORT;
                if (finalCompoundConvergence) {
                    reason = AssignmentReason.FINAL_COMPOUND_SUPPORT;
                    phase = Phase.FINAL_COMPOUND_CONVERGENCE;
                } else if (reason == AssignmentReason.ADJACENT_TRACK_SUPPORT) {
                    phase = Phase.FRONT_ADJUST;
                }
                SquadDirective planned = directive(squad, preferredTrack,
                        choice.trackIndex, reason);
                if (deferredCaptures.contains(squad.id)) {
                    planned = planned.withDistantCaptureDeferred();
                }
                directives.put(squad.id, planned);
            }
        }

        ConquestFrontSnapshot detail = buildFrontSnapshot(frame, phase,
                remainingCompounds, keep, directives, allSquads);
        List<CommandProposal> proposals = buildProposals(
                frame, allSquads, directives, phase);
        return new CommandPlan<>(faction(), strategyId(), phase.name(), frame.tick(),
                frame.influence() != null ? frame.influence().updatedTick() : -1,
                squads.size(), 0,
                List.of("remaining compounds=" + remainingCompounds),
                proposals, detail);
    }

    @Override
    public CommanderSnapshot<ConquestFrontSnapshot> reconcile(
            CommanderSnapshot<ConquestFrontSnapshot> snapshot) {
        return snapshot.withDetail(snapshot.detail().reconcileStableDirectives(
                snapshot, frontSnapshot, strategyId()));
    }

    @Override
    public void publish(CommanderSnapshot<ConquestFrontSnapshot> snapshot) {
        frontSnapshot = snapshot.detail();
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
     *   <li><b>Adjacent commit.</b> Fill remaining slots with squads already
     *       in/adjacent to the compound. They have reached the objective, so
     *       they do not consume the distant-detachment allowance. This is the
     *       only way a fresh assignment enters a contested compound.</li>
     *   <li><b>Uncontested distant fill.</b> Greedily assign nearest pairs up
     *       to the ordinary per-compound quotas. While an uncommitted squad
     *       can act on front resistance, fresh distant departures are globally
     *       bounded so at least one executable actionable squad remains on the
     *       front. With no actionable resistance the ordinary quotas apply.</li>
     * </ol>
     */
    private void assignCompoundCaptures(List<PlanningSquad> squads,
                                        IntOpenHashSet committed,
                                        IntOpenHashSet deferredCaptures,
                                        Map<Integer, SquadDirective> directives,
                                        ConquestCommandFrame frame) {
        if (compoundTargets.isEmpty() || squads.isEmpty()) return;

        int n = compoundTargets.size();
        int[] slots = new int[n];        // remaining capture slots per compound
        boolean[] contested = new boolean[n];
        for (int i = 0; i < n; i++) {
            CompoundTarget t = compoundTargets.get(i);
            if (t.state == CompoundService.CompoundState.MARINE_HELD) {
                slots[i] = 0;            // already ours — no detachment
                continue;
            }
            slots[i] = t.desiredSquads;
            contested[i] = isContested(t, frame);
        }

        // Phase 1: preserve in-flight captures even when today's front state
        // would not authorize starting the same order again.
        for (PlanningSquad squad : squads) {
            ObjectiveAssignment a = squad.assignedObjective;
            if (a == null || a.kind() != AssignmentKind.SECURE_COMPOUND) continue;
            int idx = targetIndexForAnchorZone(a.targetZoneId());
            if (idx < 0 || slots[idx] <= 0) continue;
            if (!reachableZone(squad, compoundTargets.get(idx).anchorZoneId, frame)) {
                continue;
            }
            slots[idx]--;
            committed.add(squad.id);
            putCompoundDirective(squad, compoundTargets.get(idx), directives,
                    AssignmentReason.COMPOUND_CAPTURE_PRESERVED);
        }

        // Phase 2: a squad that has physically reached a compound commits
        // before distant allocation, whether or not current belief sees a
        // defender there. Adjacent squads do not consume the distant cap.
        for (int i = 0; i < n; i++) {
            if (slots[i] <= 0) continue;
            CompoundTarget t = compoundTargets.get(i);
            for (PlanningSquad squad : squads) {
                if (slots[i] <= 0) break;
                if (committed.contains(squad.id)) continue;
                if (!squadAdjacentToCompound(squad, t, frame)) continue;
                if (!reachableZone(squad, t.anchorZoneId, frame)) continue;
                commitCapture(squad, t, committed, directives,
                        AssignmentReason.COMPOUND_ASSAULT_ADJACENT);
                slots[i]--;
            }
        }

        IntOpenHashSet actionableFrontSquads = actionableFrontSquads(
                squads, committed, frame);
        int actionableRemaining = actionableFrontSquads.size();

        // Phase 3: greedy nearest-pair fill of uncontested compounds.
        // Non-actionable squads are preferred for capture. An actionable
        // squad may depart only while another executable actionable squad
        // remains, making the cap global across every compound and replan.
        while (true) {
            int bestSquad = -1, bestTarget = -1;
            int bestActionableRank = Integer.MAX_VALUE;
            float bestDist = Float.MAX_VALUE;
            for (PlanningSquad squad : squads) {
                if (committed.contains(squad.id)) continue;
                boolean actionable = actionableFrontSquads.contains(squad.id);
                if (actionable
                        && actionableRemaining <= MIN_FRONT_RESERVE_SQUADS) {
                    continue;
                }
                for (int i = 0; i < n; i++) {
                    if (slots[i] <= 0 || contested[i]) continue;
                    if (!reachableZone(squad, compoundTargets.get(i).anchorZoneId,
                            frame)) continue;
                    float d = distSq(squad, compoundTargets.get(i));
                    int actionableRank = actionable ? 1 : 0;
                    if (actionableRank < bestActionableRank
                            || (actionableRank == bestActionableRank
                            && (d < bestDist || (d == bestDist
                            && (bestSquad < 0 || squad.id < bestSquad))))) {
                        bestActionableRank = actionableRank;
                        bestDist = d;
                        bestSquad = squad.id;
                        bestTarget = i;
                    }
                }
            }
            if (bestSquad < 0) break;
            commitCapture(squadById(squads, bestSquad),
                    compoundTargets.get(bestTarget), committed, directives,
                    AssignmentReason.COMPOUND_CAPTURE_UNCONTESTED);
            if (actionableFrontSquads.contains(bestSquad)) {
                actionableRemaining--;
            }
            slots[bestTarget]--;
        }

        // Explain squads that could have filled a still-open distant capture
        // slot but were retained because live front work exhausted the cap.
        if (actionableRemaining > 0
                && actionableRemaining <= MIN_FRONT_RESERVE_SQUADS) {
            for (PlanningSquad squad : squads) {
                if (committed.contains(squad.id)) continue;
                if (!actionableFrontSquads.contains(squad.id)) continue;
                for (int i = 0; i < n; i++) {
                    if (slots[i] <= 0 || contested[i]) continue;
                    CompoundTarget t = compoundTargets.get(i);
                    if (squadAdjacentToCompound(squad, t, frame)) continue;
                    if (!reachableZone(squad, t.anchorZoneId, frame)) continue;
                    deferredCaptures.add(squad.id);
                    break;
                }
            }
        }
    }

    private IntOpenHashSet actionableFrontSquads(List<PlanningSquad> squads,
                                                 IntOpenHashSet committed,
                                                 ConquestCommandFrame frame) {
        IntOpenHashSet actionable = new IntOpenHashSet();
        for (PlanningSquad squad : squads) {
            if (committed.contains(squad.id)) continue;
            if (squad.executionSuspension != null) continue;
            if (squad.localContact) {
                actionable.add(squad.id);
                continue;
            }
            int preferredTrack = stripFor(squad);
            if (targetChoice(squad, preferredTrack, frame).targetZoneId >= 0
                    || laneStageChoice(squad, preferredTrack, frame) != null) {
                actionable.add(squad.id);
            }
        }
        return actionable;
    }

    private void commitCapture(PlanningSquad squad, CompoundTarget t,
                               IntOpenHashSet committed,
                               Map<Integer, SquadDirective> directives,
                               AssignmentReason reason) {
        committed.add(squad.id);
        ObjectiveAssignment cur = squad.assignedObjective;
        if (cur == null
                || cur.kind() != AssignmentKind.SECURE_COMPOUND
                || cur.targetZoneId() != t.anchorZoneId) {
            squad.assignedObjective = ObjectiveAssignment.secureCompound(
                    squad.id, t.anchorZoneId, t.node);
        }
        putCompoundDirective(squad, t, directives, reason);
    }

    /** True iff any of the compound's garrison rooms holds a live defender. The AABB-gated garrison-zone set excludes the open exterior, so a defender loitering in the street outside doesn't read as contesting the compound. */
    private boolean isContested(CompoundTarget t, ConquestCommandFrame frame) {
        CommanderInfluenceSnapshot influence = frame.influence();
        if (influence == null) return false;
        for (CommanderContact contact : influence.contacts()) {
            int zoneId = frame.topology().zoneIdAt(contact.cellX(), contact.cellY());
            if (containsZone(t.garrisonZones, zoneId)) return true;
        }
        return false;
    }

    /** True iff the squad currently stands in, or in a zone bordering, one of the compound's garrison rooms — the "already there, commit the capture" gate for contested compounds. */
    private boolean squadAdjacentToCompound(PlanningSquad squad, CompoundTarget t,
                                            ConquestCommandFrame frame) {
        int cz = frame.topology().zoneIdAt(squad.anchorCellX, squad.anchorCellY);
        if (cz < 0) return false;
        if (containsZone(t.garrisonZones, cz)) return true;
        return squad.anchorCellX >= t.node.compoundLeft() - 1
                && squad.anchorCellX <= t.node.compoundRight() + 1
                && squad.anchorCellY >= t.node.compoundTop() - 1
                && squad.anchorCellY <= t.node.compoundBottom() + 1;
    }

    private int targetIndexForAnchorZone(int anchorZoneId) {
        for (int i = 0; i < compoundTargets.size(); i++) {
            if (compoundTargets.get(i).anchorZoneId == anchorZoneId) return i;
        }
        return -1;
    }

    private static float distSq(PlanningSquad squad, CompoundTarget t) {
        float dx = squad.centroidX - (t.node.anchorX + 0.5f);
        float dy = squad.centroidY - (t.node.anchorY + 0.5f);
        return dx * dx + dy * dy;
    }

    private static boolean containsZone(int[] zones, int zoneId) {
        for (int z : zones) if (z == zoneId) return true;
        return false;
    }

    private static PlanningSquad squadById(List<PlanningSquad> squads, int id) {
        for (PlanningSquad s : squads) if (s.id == id) return s;
        return null;
    }

    private CompoundTarget canonicalKeep() {
        CompoundTarget keep = null;
        for (CompoundTarget target : compoundTargets) {
            if (target.node.kind != TacticalNode.Kind.COMMAND_POST) continue;
            if (keep != null) return null;
            keep = target;
        }
        return keep;
    }

    private int remainingCompounds() {
        int remaining = 0;
        for (CompoundTarget target : compoundTargets) {
            if (target.state != CompoundService.CompoundState.MARINE_HELD) {
                remaining++;
            }
        }
        return remaining;
    }

    private CompoundTarget soleRemainingCompound() {
        CompoundTarget remaining = null;
        for (CompoundTarget target : compoundTargets) {
            if (target.state == CompoundService.CompoundState.MARINE_HELD) {
                continue;
            }
            if (remaining != null) return null;
            remaining = target;
        }
        return remaining;
    }

    private void putCompoundDirective(PlanningSquad squad, CompoundTarget target,
                                      Map<Integer, SquadDirective> directives,
                                      AssignmentReason reason) {
        int preferred = stripFor(squad);
        int effective = trackForZone(target.anchorZoneId);
        directives.put(squad.id, directive(squad, preferred, effective, reason));
    }

    private SquadDirective directive(PlanningSquad squad, int preferredTrack,
                                     int effectiveTrack,
                                     AssignmentReason reason) {
        ObjectiveAssignment assignment = squad.assignedObjective;
        int targetCellX = -1;
        int targetCellY = -1;
        int markerCellX = -1;
        int markerCellY = -1;
        if (assignment != null) {
            targetCellX = assignment.targetCellX();
            targetCellY = assignment.targetCellY();
            markerCellX = targetCellX;
            markerCellY = targetCellY;
            if (markerCellX < 0 && assignment.targetNode() != null) {
                markerCellX = assignment.targetNode().anchorX;
                markerCellY = assignment.targetNode().anchorY;
            } else if (markerCellX < 0 && assignment.targetZoneId() >= 0
                    && zoneMarkerX != null
                    && assignment.targetZoneId() < zoneMarkerX.length) {
                markerCellX = zoneMarkerX[assignment.targetZoneId()];
                markerCellY = zoneMarkerY[assignment.targetZoneId()];
            }
        }
        return new SquadDirective(squad.id, preferredTrack, effectiveTrack,
                reason, assignment != null ? assignment.kind() : null,
                assignment != null ? assignment.targetZoneId() : -1,
                targetCellX, targetCellY, markerCellX, markerCellY);
    }

    /**
     * Lazy strip partition. Equal-width along the lateral axis (x for
     * SOUTH_TO_NORTH push, y for WEST_TO_EAST push); each zone is bucketed
     * by its centroid's lateral coordinate. Within each strip, zones are
     * sorted forward-to-back so {@link #nearestDefenderZoneInStrip} can short-
     * circuit on the first defender-occupied entry.
     */
    private void initializePartition(ConquestCommandFrame frame) {
        CommandTopology topology = frame.topology();
        if (trackLayout == null) {
            trackLayout = new ConquestTrackLayout(axis,
                    topology.width(), topology.height());
        }
        int gridW = topology.width();
        this.lateralExtent = trackLayout.lateralExtent();

        stripZones = new ArrayList<>(STRIP_COUNT);
        for (int i = 0; i < STRIP_COUNT; i++) stripZones.add(new ArrayList<>());
        zoneForwardCoord = new float[topology.zones().size()];
        zoneLateralCoord = new float[topology.zones().size()];
        zoneCentroidX = new float[topology.zones().size()];
        zoneCentroidY = new float[topology.zones().size()];
        zoneMarkerX = new int[topology.zones().size()];
        zoneMarkerY = new int[topology.zones().size()];
        Arrays.fill(zoneMarkerX, -1);
        Arrays.fill(zoneMarkerY, -1);
        Arrays.fill(zoneForwardCoord, 0f);

        int largestCells = -1, secondCells = -1, largestZone = -1;
        for (CommandTopology.Zone zone : topology.zones()) {
            int[] cells = zone.cells();
            if (cells.length == 0) continue;
            if (cells.length > largestCells) {
                secondCells = largestCells;
                largestCells = cells.length;
                largestZone = zone.id();
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
            if (zone.id() >= 0 && zone.id() < zoneForwardCoord.length) {
                zoneForwardCoord[zone.id()] = forward;
                zoneLateralCoord[zone.id()] = lateral;
                zoneCentroidX[zone.id()] = cx;
                zoneCentroidY[zone.id()] = cy;
                int marker = nearestCell(cells, gridW, cx, cy);
                zoneMarkerX[zone.id()] = marker % gridW;
                zoneMarkerY[zone.id()] = marker / gridW;
            }

            int stripIdx = stripIndexForLateral(lateral);
            if (stripIdx < 0 || stripIdx >= STRIP_COUNT) continue;
            stripZones.get(stripIdx).add(zone.id());
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

    }

    private static int nearestCell(int[] cells, int gridWidth,
                                   float centerX, float centerY) {
        int best = cells[0];
        float bestDistance = Float.MAX_VALUE;
        for (int cell : cells) {
            float dx = cell % gridWidth - centerX;
            float dy = cell / gridWidth - centerY;
            float distance = dx * dx + dy * dy;
            if (distance < bestDistance
                    || (distance == bestDistance && cell < best)) {
                best = cell;
                bestDistance = distance;
            }
        }
        return best;
    }

    private void refreshCompoundTargets(ConquestCommandFrame frame) {
        compoundTargets.clear();
        for (ConquestCommandFacts.Compound fact : frame.facts().compounds()) {
            int anchorZone = fact.anchorZoneId();
            if (anchorZone < 0) continue;
            int[] garrisonZones = fact.garrisonZoneIds();
            if (garrisonZones.length == 0) garrisonZones = new int[]{anchorZone};
            int desiredSquads = garrisonZones.length >= LARGE_COMPOUND_ROOMS ? 2 : 1;
            compoundTargets.add(new CompoundTarget(fact.state(), fact.node(),
                    anchorZone, garrisonZones, desiredSquads));
        }
        compoundTargets.sort(Comparator
                .comparingInt((CompoundTarget target) -> target.anchorZoneId)
                .thenComparingInt(target -> target.node.anchorX)
                .thenComparingInt(target -> target.node.anchorY));
    }

    /**
     * Lateral coordinate {@code lateral} → strip index in {@code [0, STRIP_COUNT)}.
     * Equal-width buckets across the full lateral extent. The {@code Math.min}
     * clamp catches the right-edge boundary (a coord exactly at {@code lateralExtent}
     * would land in bucket {@code STRIP_COUNT}, which doesn't exist).
     */
    private int stripIndexForLateral(float lateral) {
        return trackLayout.trackForLateral(lateral);
    }

    /**
     * Squad → strip index. Sticky on first observation, looked up thereafter.
     * Returns the strip the squad's centroid currently falls in for the first
     * call, which is then memoized; lateral drift after first observation
     * doesn't move the squad to a new preferred track. Temporary support in
     * an adjacent effective track does not rewrite this sticky identity.
     */
    private int stripFor(PlanningSquad squad) {
        int cached = squadStripIdx.get(squad.id);
        if (cached >= 0) return cached;
        float lateral = (axis == TraversalAxis.SOUTH_TO_NORTH) ? squad.centroidX : squad.centroidY;
        int idx = stripIndexForLateral(lateral);
        if (idx < 0) idx = 0;
        if (idx >= STRIP_COUNT) idx = STRIP_COUNT - 1;
        squadStripIdx.put(squad.id, idx);
        return idx;
    }

    private int trackForZone(int zoneId) {
        if (zoneId < 0 || zoneId >= zoneLateralCoord.length) return -1;
        return stripIndexForLateral(zoneLateralCoord[zoneId]);
    }

    private record TargetChoice(int trackIndex, int targetZoneId) { }
    private record TrackStage(int trackIndex, int cellX, int cellY) { }

    /**
     * Gives a contact-free rear squad an own-force destination behind its
     * preferred track's believed hostile frontier. Specific zone work is
     * selected before this fallback; local contact also suppresses it so the
     * tactical engagement planner owns the squad without a competing marker.
     */
    private TrackStage laneStageChoice(PlanningSquad squad, int track,
                                       ConquestCommandFrame frame) {
        if (squad.localContact || track < 0 || track >= STRIP_COUNT) return null;
        CommanderInfluenceSnapshot influence = frame.influence();
        if (influence == null) return null;

        int nearestHostileForward = Integer.MAX_VALUE;
        for (CommanderContact contact : influence.contacts()) {
            if (trackLayout.trackForCell(contact.cellX(), contact.cellY()) != track) {
                continue;
            }
            int forward = Math.round(trackLayout.forwardCoordinate(
                    contact.cellX(), contact.cellY()));
            nearestHostileForward = Math.min(nearestHostileForward, forward);
        }
        if (nearestHostileForward == Integer.MAX_VALUE) return null;

        int squadForward = Math.round(trackLayout.forwardCoordinate(
                squad.centroidX, squad.centroidY));
        int friendlyLead = friendlyLeadForward(track, squadForward, frame);
        int safeFront = nearestHostileForward - TRACK_LINE_STANDOFF_CELLS;
        int supportedFront = friendlyLead + TRACK_LINE_LEAD_CELLS;
        int strideFront = squadForward + TRACK_LINE_MAX_STRIDE_CELLS;
        int desiredForward = Math.min(safeFront,
                Math.min(supportedFront, strideFront));
        desiredForward = Math.max(0, Math.min(trackLayout.forwardExtent() - 1,
                desiredForward));
        desiredForward = desiredForward / TRACK_LINE_BAND_CELLS
                * TRACK_LINE_BAND_CELLS;
        if (desiredForward < squadForward + TRACK_LINE_MIN_ADVANCE_CELLS) {
            return null;
        }

        int lateral = Math.round(trackLayout.lateralCoordinate(
                squad.centroidX, squad.centroidY));
        lateral = Math.max(trackLayout.lateralStartInclusive(track),
                Math.min(trackLayout.lateralEndInclusive(track), lateral));
        return reachableTrackStage(squad, track, lateral, desiredForward, frame);
    }

    private int friendlyLeadForward(int track, int fallback,
                                    ConquestCommandFrame frame) {
        int lead = fallback;
        for (CommandSquadState other : frame.squads()) {
            if (other.aliveMembers() <= 0 || other.role() == UnitRole.GARRISON) continue;
            int physicalTrack = trackLayout.trackForLateral(
                    trackLayout.lateralCoordinate(other.centroidX(), other.centroidY()));
            if (physicalTrack != track) continue;
            lead = Math.max(lead, Math.round(trackLayout.forwardCoordinate(
                    other.centroidX(), other.centroidY())));
        }
        return lead;
    }

    /** Searches only at or behind the safe line and fails closed. */
    private TrackStage reachableTrackStage(PlanningSquad squad, int track,
                                           int desiredLateral, int desiredForward,
                                           ConquestCommandFrame frame) {
        CommandTopology topology = frame.topology();
        for (int radius = 0; radius <= TRACK_LINE_SNAP_RADIUS; radius++) {
            for (int rear = 0; rear <= radius; rear++) {
                int lateralDelta = radius - rear;
                int forward = desiredForward - rear;
                TrackStage left = validTrackStage(squad, track,
                        desiredLateral - lateralDelta, forward, topology);
                if (left != null) return left;
                if (lateralDelta != 0) {
                    TrackStage right = validTrackStage(squad, track,
                            desiredLateral + lateralDelta, forward, topology);
                    if (right != null) return right;
                }
            }
        }
        return null;
    }

    private TrackStage validTrackStage(PlanningSquad squad, int track,
                                       int lateral, int forward,
                                       CommandTopology topology) {
        if (!topology.inBounds(squad.anchorCellX, squad.anchorCellY)
                || !topology.isWalkable(
                squad.anchorCellX, squad.anchorCellY)) return null;
        int squadForward = Math.round(trackLayout.forwardCoordinate(
                squad.centroidX, squad.centroidY));
        if (forward < squadForward + TRACK_LINE_MIN_ADVANCE_CELLS) return null;
        int x = trackLayout.cellX(lateral, forward);
        int y = trackLayout.cellY(lateral, forward);
        if (!topology.inBounds(x, y) || !topology.isWalkable(x, y)
                || trackLayout.trackForCell(x, y) != track) return null;
        boolean atTarget = squad.anchorCellX == x && squad.anchorCellY == y;
        if (!atTarget && !topology.reachable(
                squad.anchorCellX, squad.anchorCellY, x, y)) return null;
        return new TrackStage(track, x, y);
    }

    /**
     * When a recaptured non-keep compound is the sole territorial objective,
     * capture quota still owns SECURE_COMPOUND. Remaining mobile squads may
     * support the assault across any empty track by clearing an occupied,
     * reachable room in that compound's authored footprint.
     */
    private TargetChoice finalCompoundSupportChoice(PlanningSquad squad,
                                                     CompoundTarget target,
                                                     ConquestCommandFrame frame) {
        int bestZone = -1;
        float bestDistance = Float.MAX_VALUE;
        for (int zoneId : target.garrisonZones) {
            if (!hasKnownHostileInZone(zoneId, frame)) continue;
            if (!reachableZone(squad, zoneId, frame)) {
                continue;
            }
            float dx = squad.centroidX - zoneCentroidX[zoneId];
            float dy = squad.centroidY - zoneCentroidY[zoneId];
            float distance = dx * dx + dy * dy;
            if (distance < bestDistance
                    || (distance == bestDistance && zoneId < bestZone)) {
                bestDistance = distance;
                bestZone = zoneId;
            }
        }
        return new TargetChoice(bestZone >= 0 ? trackForZone(bestZone) : -1,
                bestZone);
    }

    private TargetChoice targetChoice(PlanningSquad squad, int preferredTrack,
                                      ConquestCommandFrame frame) {
        int home = nearestDefenderZoneInStrip(squad, preferredTrack, frame);
        if (home >= 0) return new TargetChoice(preferredTrack, home);

        int bestTrack = -1;
        int bestZone = -1;
        float bestDistance = Float.MAX_VALUE;
        for (int track = Math.max(0, preferredTrack - 1);
             track <= Math.min(STRIP_COUNT - 1, preferredTrack + 1); track++) {
            if (track == preferredTrack) continue;
            int zone = nearestDefenderZoneInStrip(squad, track, frame);
            if (zone < 0) continue;
            float dx = squad.centroidX - zoneCentroidX[zone];
            float dy = squad.centroidY - zoneCentroidY[zone];
            float distance = dx * dx + dy * dy;
            if (distance < bestDistance
                    || (distance == bestDistance && track < bestTrack)) {
                bestDistance = distance;
                bestTrack = track;
                bestZone = zone;
            }
        }
        return new TargetChoice(bestTrack, bestZone);
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
     * engagement, or receives a later lane-stage fallback when the believed
     * contact is farther ahead in the open exterior).
     */
    private int nearestDefenderZoneInStrip(PlanningSquad squad, int stripIdx,
                                           ConquestCommandFrame frame) {
        if (stripIdx < 0 || stripIdx >= stripZones.size()) return -1;
        float squadForward = (axis == TraversalAxis.SOUTH_TO_NORTH) ? squad.centroidY : squad.centroidX;

        int bestForwardZone = -1;
        float bestForwardDist = Float.MAX_VALUE;
        int bestBackwardZone = -1;
        float bestBackwardDist = Float.MAX_VALUE;
        for (int zoneId : stripZones.get(stripIdx)) {
            if (zoneId == exteriorZoneId) continue;
            if (!hasKnownHostileInZone(zoneId, frame)) continue;
            if (!reachableZone(squad, zoneId, frame)) continue;
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

    private boolean hasKnownHostileInZone(int zoneId, ConquestCommandFrame frame) {
        CommanderInfluenceSnapshot influence = frame.influence();
        if (influence == null) return false;
        for (CommanderContact contact : influence.contacts()) {
            if (frame.topology().zoneIdAt(contact.cellX(), contact.cellY()) == zoneId) {
                return true;
            }
        }
        return false;
    }

    private boolean reachableZone(PlanningSquad squad, int zoneId,
                                  ConquestCommandFrame frame) {
        if (squad.anchorCellX < 0 || squad.anchorCellY < 0) return false;
        if (zoneId < 0 || zoneId >= zoneCentroidX.length) return false;
        int currentZone = frame.topology().zoneIdAt(
                squad.anchorCellX, squad.anchorCellY);
        return frame.topology().areZonesConnected(currentZone, zoneId);
    }

    private ConquestFrontSnapshot buildFrontSnapshot(
            ConquestCommandFrame frame, Phase phase,
            int remainingCompounds, CompoundTarget keep,
            Map<Integer, SquadDirective> directives,
            Map<Integer, PlanningSquad> allSquads) {
        int[] preferredSquads = new int[STRIP_COUNT];
        int[] effectiveSquads = new int[STRIP_COUNT];
        int[] effectiveMembers = new int[STRIP_COUNT];
        int[] preferredMembers = new int[STRIP_COUNT];
        float[] bodyProgressSum = new float[STRIP_COUNT];
        float[] leadProgress = new float[STRIP_COUNT];
        Arrays.fill(leadProgress, -1f);
        int[] targetZones = new int[STRIP_COUNT];
        Arrays.fill(targetZones, -1);

        int forwardExtent = axis == TraversalAxis.SOUTH_TO_NORTH
                ? frame.topology().height() : frame.topology().width();
        for (PlanningSquad squad : allSquads.values()) {
            if (squad.aliveMembers <= 0) continue;
            SquadDirective directive = directives.get(squad.id);
            if (directive != null
                    && directive.reason() == AssignmentReason.GARRISON_HOLD) {
                continue;
            }
            int preferred = directive != null
                    ? directive.preferredTrack() : stripFor(squad);
            if (preferred >= 0 && preferred < STRIP_COUNT) {
                preferredSquads[preferred]++;
                preferredMembers[preferred] += squad.aliveMembers;
                float forward = axis == TraversalAxis.SOUTH_TO_NORTH
                        ? squad.centroidY : squad.centroidX;
                float progress = normalizedProgress(forward, forwardExtent);
                bodyProgressSum[preferred] += progress * squad.aliveMembers;
                leadProgress[preferred] = Math.max(leadProgress[preferred], progress);
            }
            if (directive == null) continue;
            int effective = directive.effectiveTrack();
            if (effective < 0 || effective >= STRIP_COUNT) continue;
            effectiveSquads[effective]++;
            effectiveMembers[effective] += squad.aliveMembers;
            if (targetZones[effective] < 0 && directive.targetZoneId() >= 0) {
                targetZones[effective] = directive.targetZoneId();
            }
        }

        CommanderInfluenceSnapshot influence = frame.influence();
        float[] knownHostileFront = new float[STRIP_COUNT];
        Arrays.fill(knownHostileFront, -1f);
        int[] knownContacts = new int[STRIP_COUNT];
        float[] friendlyPressure = new float[STRIP_COUNT];
        float[] hostilePressure = new float[STRIP_COUNT];
        int influenceTick = -1;
        if (influence != null) {
            influenceTick = influence.updatedTick();
            for (CommanderContact contact : influence.contacts()) {
                int lateral = axis == TraversalAxis.SOUTH_TO_NORTH
                        ? contact.cellX() : contact.cellY();
                int track = stripIndexForLateral(lateral);
                if (track < 0 || track >= STRIP_COUNT) continue;
                int forward = axis == TraversalAxis.SOUTH_TO_NORTH
                        ? contact.cellY() : contact.cellX();
                float progress = normalizedProgress(forward, forwardExtent);
                knownContacts[track]++;
                if (knownHostileFront[track] < 0f) {
                    knownHostileFront[track] = progress;
                } else {
                    knownHostileFront[track] = Math.min(
                            knownHostileFront[track], progress);
                }
            }
            for (int by = 0; by < influence.height(); by++) {
                for (int bx = 0; bx < influence.width(); bx++) {
                    int worldX = influence.blockWorldX(bx)
                            + influence.blockWorldWidth(bx) / 2;
                    int worldY = influence.blockWorldY(by)
                            + influence.blockWorldHeight(by) / 2;
                    int lateral = axis == TraversalAxis.SOUTH_TO_NORTH
                            ? worldX : worldY;
                    int track = stripIndexForLateral(lateral);
                    if (track < 0 || track >= STRIP_COUNT) continue;
                    friendlyPressure[track] += influence.friendlyAt(bx, by);
                    hostilePressure[track] += influence.hostileAt(bx, by);
                }
            }
        }

        List<TrackState> tracks = new ArrayList<>(STRIP_COUNT);
        for (int track = 0; track < STRIP_COUNT; track++) {
            int lateralStart = trackLayout.lateralStartInclusive(track);
            int lateralEnd = trackLayout.lateralEndInclusive(track);
            float bodyProgress = preferredMembers[track] > 0
                    ? bodyProgressSum[track] / preferredMembers[track] : -1f;
            tracks.add(new TrackState(track, lateralStart, lateralEnd,
                    preferredSquads[track], effectiveSquads[track],
                    effectiveMembers[track], bodyProgress, leadProgress[track],
                    knownHostileFront[track], knownContacts[track],
                    friendlyPressure[track], hostilePressure[track],
                    targetZones[track]));
        }
        return new ConquestFrontSnapshot(frame.tick(),
                influenceTick, axis, phase, remainingCompounds,
                keep != null ? keep.anchorZoneId : -1,
                keep != null ? keep.state : null,
                tracks, squadStates(allSquads),
                new ArrayList<>(directives.values()));
    }

    private static List<ConquestFrontSnapshot.SquadState> squadStates(
            Map<Integer, PlanningSquad> squads) {
        List<ConquestFrontSnapshot.SquadState> states = new ArrayList<>(squads.size());
        for (PlanningSquad squad : squads.values()) {
            states.add(new ConquestFrontSnapshot.SquadState(
                    squad.id, squad.aliveMembers, squad.centroidX,
                    squad.centroidY, squad.currentZoneId,
                    squad.executionSuspension, squad.localContact));
        }
        return states;
    }

    private List<CommandProposal> buildProposals(
            ConquestCommandFrame frame,
            Map<Integer, PlanningSquad> squads,
            Map<Integer, SquadDirective> directives,
            Phase phase) {
        List<CommandProposal> proposals = new ArrayList<>();
        for (Map.Entry<Integer, SquadDirective> entry : directives.entrySet()) {
            int squadId = entry.getKey();
            PlanningSquad planned = squads.get(squadId);
            CommandSquadState frozen = frame.squad(squadId);
            if (planned == null || frozen == null) continue;
            CommandDirective incumbent = frozen.directive();
            CommandStabilityBreak stabilityBreak = stabilityBreak(
                    frame, planned, incumbent, phase,
                    entry.getValue().reason());
            if (incumbent != null
                    && incumbent.authority().priority()
                    > CommandAuthority.MISSION_COMMAND.priority()) {
                proposals.add(CommandProposal.retain(squadId,
                        CommandAuthority.MISSION_COMMAND,
                        entry.getValue().reason().name()));
            } else if (planned.assignedObjective != null) {
                proposals.add(CommandProposal.assign(planned.assignedObjective,
                        CommandAuthority.MISSION_COMMAND,
                        entry.getValue().reason().name(), stabilityBreak));
            } else if (frozen.assignment() != null) {
                proposals.add(CommandProposal.release(squadId,
                        CommandAuthority.MISSION_COMMAND,
                        entry.getValue().reason().name(), stabilityBreak));
            } else {
                proposals.add(CommandProposal.retain(squadId,
                        CommandAuthority.MISSION_COMMAND,
                        entry.getValue().reason().name()));
            }
        }
        return proposals;
    }

    private CommandStabilityBreak stabilityBreak(
            ConquestCommandFrame frame, PlanningSquad squad,
            CommandDirective incumbent, Phase phase,
            AssignmentReason reason) {
        if (incumbent == null || incumbent.assignment() == null
                || incumbent.issuer() == null
                || !strategyId().equals(incumbent.issuer())
                || Objects.equals(incumbent.assignment(), squad.assignedObjective)) {
            return CommandStabilityBreak.NONE;
        }
        ObjectiveAssignment old = incumbent.assignment();
        if (frontSnapshot != null && frontSnapshot.phase() != phase) {
            return CommandStabilityBreak.OBJECTIVE_COMPLETED;
        }
        if (old.kind() == AssignmentKind.SECURE_COMPOUND) {
            CompoundTarget target = compoundTarget(old.targetZoneId());
            if (target == null
                    || target.state == CompoundService.CompoundState.MARINE_HELD) {
                return CommandStabilityBreak.OBJECTIVE_COMPLETED;
            }
            if (!reachableZone(squad, old.targetZoneId(), frame)) {
                return CommandStabilityBreak.TARGET_UNREACHABLE;
            }
        } else if (old.kind() == AssignmentKind.CLEAR_ZONE) {
            if (!reachableZone(squad, old.targetZoneId(), frame)) {
                return CommandStabilityBreak.TARGET_UNREACHABLE;
            }
            if (!hasKnownHostileInZone(old.targetZoneId(), frame)
                    || reason == AssignmentReason.NO_ACTIONABLE_TRACK_TARGET) {
                return CommandStabilityBreak.CONTEXT_INVALIDATED;
            }
        } else if (old.kind() == AssignmentKind.ADVANCE_TRACK) {
            if (reason != AssignmentReason.TRACK_LINE_ADVANCE) {
                return CommandStabilityBreak.CONTEXT_INVALIDATED;
            }
            if (!frame.topology().inBounds(old.targetCellX(), old.targetCellY())
                    || !frame.topology().isWalkable(
                    old.targetCellX(), old.targetCellY())) {
                return CommandStabilityBreak.TARGET_UNREACHABLE;
            }
            boolean atTarget = squad.anchorCellX == old.targetCellX()
                    && squad.anchorCellY == old.targetCellY();
            if (!atTarget && !frame.topology().reachable(
                    squad.anchorCellX, squad.anchorCellY,
                    old.targetCellX(), old.targetCellY())) {
                return CommandStabilityBreak.TARGET_UNREACHABLE;
            }
        }
        if (reason == AssignmentReason.NO_REACHABLE_COMPOUND_TARGET) {
            return CommandStabilityBreak.TARGET_UNREACHABLE;
        }
        return CommandStabilityBreak.NONE;
    }

    private CompoundTarget compoundTarget(int zoneId) {
        for (CompoundTarget target : compoundTargets) {
            if (target.anchorZoneId == zoneId) return target;
        }
        return null;
    }

    private static float normalizedProgress(float forward, int extent) {
        if (extent <= 1) return 0f;
        return Math.max(0f, Math.min(1f, forward / (extent - 1f)));
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
