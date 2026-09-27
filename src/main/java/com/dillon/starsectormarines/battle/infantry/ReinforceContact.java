package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.squad.SquadAlertLevel;
import com.dillon.starsectormarines.battle.squad.BelievedContact;
import com.dillon.starsectormarines.battle.squad.AudibleBearing;
import com.dillon.starsectormarines.battle.decision.goap.Goal;
import com.dillon.starsectormarines.battle.decision.goap.Predicate;
import com.dillon.starsectormarines.battle.squad.SquadPlan;
import com.dillon.starsectormarines.battle.decision.goap.WorldState;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.nav.BoundedStepReachability;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.Paths;
import com.dillon.starsectormarines.battle.profile.TickInnerProfile;

import java.util.Collection;
import java.util.List;

/**
 * <b>Fire-team fix and flank.</b> An infantry squad that has a contact beyond
 * immediate engagement distance approaches from a flanking angle. One intact
 * fire team maneuvers while its siblings hold the contact axis.
 *
 * <p>{@link Priority#ENGAGEMENT} — reinforcing a firefight is a combat
 * maneuver, not an objective whose completion should outrank survival. The
 * handoff remains clean: {@code RoutinePatrol} yields (returns 0) when
 * SUSPICIOUS + valid lastSeenEnemy, and this goal takes over. On arrival
 * the squad goes ENGAGED and {@link EliminateEnemiesGoal} picks up the
 * actual engagement from the flanking position.
 *
 * <p>Custom-plan: computes a flanking waypoint ~90° off the friendly
 * engagement axis and emits a single {@link FlankApproach} step. One team
 * moves to the waypoint while the remaining teams establish reachable support
 * lines and take legal shots. Arrival or structural failure hands the squad
 * back to ordinary contact doctrine rather than recreating the same flank.
 *
 * <p>Its engagement priority means both {@link SurviveContact} and
 * {@link RecoverFromAmbush} preempt it. A mauled patrol retreats; an exposed
 * patrol breaks the firing lane and resumes its mission from safety.
 */
public final class ReinforceContact implements Goal {

    public static final ReinforceContact INSTANCE = new ReinforceContact();

    /** Cells from contact to place the flanking waypoint along the perpendicular axis. */
    static final float FLANK_RADIUS = 10f;
    /** Cells from contact within which the squad is "already at the fight" and should just engage. */
    static final float ALREADY_AT_CONTACT_RADIUS = 13f;
    /** Search radius for an engaged friendly squad near the contact point. */
    static final float FRIENDLY_SEARCH_RADIUS = 25f;
    /** Spiral search radius when snapping a waypoint to a walkable cell. */
    static final int WALKABLE_SNAP_RADIUS = 5;
    /** Reject a nominal flank that requires routing around a large structure to reach it. */
    static final float MAX_FLANK_DETOUR_RATIO = 1.75f;
    /** Small fixed allowance keeps short routes around a corner from being rejected. */
    static final int MAX_FLANK_DETOUR_SLACK = 4;
    /** Absolute dogleg allowance; prevents a farther candidate gaming the ratio denominator. */
    static final int MAX_FLANK_EXTRA_STEPS = 8;
    /** Same-build exhaustive candidate-search control; pruning preserves the winning cell exactly. */
    public static final String PRUNE_FLANK_CANDIDATES_PROPERTY = "battle.pathfinding.pruneFlankCandidates";
    /** Same-build control for detour-bounded route proofs, independent of candidate pruning. */
    public static final String BOUND_FLANK_PROOFS_PROPERTY = "battle.pathfinding.boundFlankProofs";
    /** Independent control for shared, rejection-only minimum-step proofs. */
    public static final String FLANK_STEP_GATE_PROPERTY = "battle.pathfinding.flankStepGate";
    public static final String RETAIN_FLANK_PLANS_PROPERTY = "battle.goap.retainFlankPlans";
    private static final ThreadLocal<BoundedStepReachability> STEP_GATE =
            ThreadLocal.withInitial(BoundedStepReachability::new);

    private ReinforceContact() {}

    @Override public String name() { return "ReinforceContact"; }

    @Override
    public Priority priority() {
        return Priority.ENGAGEMENT;
    }

    @Override
    public float relevance(WorldState state, Squad squad, BattleView sim) {
        if (squad.holdsFireUntilKillZone) return 0f;
        if (squad.alertLevel == SquadAlertLevel.UNAWARE) return 0f;
        if (squad.lastSeenEnemyX < 0 || squad.lastSeenEnemyY < 0) return 0f;
        if (state.get(Predicate.MORALE_BROKEN)) return 0f;
        // Identity-backed reinforcement requires a still-live hostile. A
        // source-less audible bearing remains a legitimate investigation
        // cue, but a dead identity's legacy last-seen projection must not
        // restart the flank after WorldState has rejected that contact.
        if (!state.get(Predicate.HAS_TARGET)
                && squad.audibleBearing() == null) return 0f;

        // Direct contact belongs to the ordinary contact doctrine. An
        // already-running flank may finish its maneuver, but once it hands
        // off, registry-order ties must not recreate the same flank forever.
        if (squad.alertLevel == SquadAlertLevel.ENGAGED
                && squad.currentGoal != INSTANCE) return 0f;
        if (squad.currentGoal == INSTANCE && squad.currentPlan != null
                && squad.currentPlan.isComplete()) return 0f;
        if (squad.contactPicture.primaryEngageableFireTeams() > 0) return 0f;

        float contactX = squad.lastSeenEnemyX + 0.5f;
        float contactY = squad.lastSeenEnemyY + 0.5f;
        for (int i = 0, n = sim.squadMemberCount(squad.id); i < n; i++) {
            long member = sim.squadMemberAt(squad.id, i);
            if (!squad.participatesInPlan(member)) continue;
            float dx = sim.world().x(member) - contactX;
            float dy = sim.world().y(member) - contactY;
            if (Math.sqrt(dx * dx + dy * dy) <= ALREADY_AT_CONTACT_RADIUS) {
                return 0f;
            }
        }

        return 1.0f;
    }

    @Override
    public WorldState desiredState(Squad squad, BattleView sim) {
        return WorldState.EMPTY;
    }

    @Override
    public SquadPlan customPlan(Squad squad, BattleView sim) {
        boolean retain = Boolean.parseBoolean(System.getProperty(RETAIN_FLANK_PLANS_PROPERTY, "true"));
        if (!retain || squad.currentGoal != INSTANCE || squad.currentPlan == null
                || squad.currentPlan.isComplete() || squad.currentPlan != squad.retainedFlankPlan) {
            squad.retainedFlankWaypoint.clear();
        }
        TickInnerProfile profile = TickInnerProfile.currentIfBound();
        if (profile != null) profile.enterAction(0L, squad.id, "ReinforceContact");
        try {
            int[] wp = computeFlankWaypoint(squad, sim, retain);
            SquadPlan plan = new SquadPlan(List.of(new SquadPlan.Step(new FlankApproach(wp[0], wp[1]))));
            squad.retainedFlankPlan = retain ? plan : null;
            return plan;
        } finally {
            if (profile != null) profile.exitAction();
        }
    }

    // ---- Flanking waypoint algorithm ----

    static int[] computeFlankWaypoint(Squad squad, BattleView sim) {
        return computeFlankWaypoint(squad, sim, false);
    }

    private static int[] computeFlankWaypoint(Squad squad, BattleView sim, boolean retain) {
        int contactX = squad.lastSeenEnemyX;
        int contactY = squad.lastSeenEnemyY;
        // Vector math runs in continuous space: the contact CELL's center vs
        // the (center-based) squad centroids.
        float contactCX = contactX + 0.5f;
        float contactCY = contactY + 0.5f;

        Squad garrison = findEngagedFriendlyNearContact(squad, sim);

        float axisX, axisY;
        if (garrison != null) {
            axisX = contactCX - garrison.centroidX;
            axisY = contactCY - garrison.centroidY;
        } else {
            axisX = contactCX - squad.centroidX;
            axisY = contactCY - squad.centroidY;
        }

        float len = (float) Math.sqrt(axisX * axisX + axisY * axisY);
        if (len < 0.01f) {
            squad.retainedFlankWaypoint.clear();
            return new int[]{contactX, contactY};
        }
        axisX /= len;
        axisY /= len;

        float perpX = -axisY;
        float perpY = axisX;

        float toPatrolX = squad.centroidX - contactCX;
        float toPatrolY = squad.centroidY - contactCY;
        float dot = toPatrolX * perpX + toPatrolY * perpY;
        if (dot < 0) {
            perpX = -perpX;
            perpY = -perpY;
        }

        int rawX = (int) Math.floor(contactCX + perpX * FLANK_RADIUS);
        int rawY = (int) Math.floor(contactCY + perpY * FLANK_RADIUS);

        NavigationGrid grid = sim.getGrid();
        int[] origin = squadOrigin(squad, sim);
        long contactId = projectedContactId(squad);
        int tick = sim.getSimTickIndex();
        boolean cardinal = GridPathfinder.USE_CARDINAL_NAVIGATION;
        TickInnerProfile profile = TickInnerProfile.currentIfBound();
        RetainedFlankWaypoint.Cell saved = retain ? squad.retainedFlankWaypoint.lookup(
                grid, squad.assignmentForExecution(), contactId, contactX, contactY,
                origin[0], origin[1], rawX, rawY, cardinal, tick) : null;
        if (saved != null) {
            if (profile != null) profile.recordCount(TickInnerProfile.Bucket.FLANK_PLAN_REUSE, 1);
            return new int[]{saved.x(), saved.y()};
        }
        if (profile != null) profile.recordCount(TickInnerProfile.Bucket.FLANK_PLAN_SELECT, 1);
        int[] waypoint = snapToReachable(rawX, rawY, grid, origin[0], origin[1],
                Boolean.parseBoolean(System.getProperty(PRUNE_FLANK_CANDIDATES_PROPERTY, "true")),
                Boolean.parseBoolean(System.getProperty(BOUND_FLANK_PROOFS_PROPERTY, "true")),
                Boolean.parseBoolean(System.getProperty(FLANK_STEP_GATE_PROPERTY, "true")));
        if (retain) squad.retainedFlankWaypoint.remember(grid, squad.assignmentForExecution(),
                contactId, contactX, contactY, origin[0], origin[1], rawX, rawY, cardinal, tick,
                waypoint[0], waypoint[1]);
        return waypoint;
    }

    /** Match the freshest belief/audio projection that supplies lastSeenEnemy, not a different primary. */
    private static long projectedContactId(Squad squad) {
        BelievedContact freshest = null;
        for (BelievedContact contact : squad.believedContacts()) {
            if (freshest == null || contact.lastSeenTick() > freshest.lastSeenTick()) freshest = contact;
        }
        AudibleBearing heard = squad.audibleBearing();
        return freshest != null && (heard == null || freshest.lastSeenTick() >= heard.heardTick())
                ? freshest.unitId() : heard == null ? 0L : heard.sourceUnitId();
    }

    private static Squad findEngagedFriendlyNearContact(Squad self, BattleView sim) {
        Collection<Squad> squads = sim.getSquads();
        Squad best = null;
        float bestDist = Float.MAX_VALUE;
        for (Squad s : squads) {
            if (s.id == self.id) continue;
            if (s.faction != self.faction) continue;
            if (s.alertLevel != SquadAlertLevel.ENGAGED) continue;
            if (s.aliveMembers <= 0) continue;
            float dx = s.centroidX - (self.lastSeenEnemyX + 0.5f);
            float dy = s.centroidY - (self.lastSeenEnemyY + 0.5f);
            float dist = (float) Math.sqrt(dx * dx + dy * dy);
            if (dist > FRIENDLY_SEARCH_RADIUS) continue;
            if (dist < bestDist) {
                bestDist = dist;
                best = s;
            }
        }
        return best;
    }

    static int[] snapToWalkable(int x, int y, NavigationGrid grid, int fallbackX, int fallbackY) {
        if (grid.inBounds(x, y) && grid.isWalkable(x, y)) return new int[]{x, y};
        for (int r = 1; r <= WALKABLE_SNAP_RADIUS; r++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dx = -r; dx <= r; dx++) {
                    if (Math.abs(dx) != r && Math.abs(dy) != r) continue;
                    int cx = x + dx;
                    int cy = y + dy;
                    if (grid.inBounds(cx, cy) && grid.isWalkable(cx, cy)) {
                        return new int[]{cx, cy};
                    }
                }
            }
        }
        return new int[]{fallbackX, fallbackY};
    }

    /**
     * Selects the closest practical flank cell, not merely the first open
     * tile. Structure walls can make two adjacent-looking cells belong to
     * very different routes; a candidate that can improve the incumbent needs
     * an A* route from a live squad member, without an extreme detour.
     * When the building cannot support a flank, returning the squad's own
     * cell makes {@link FlankApproach} complete and hand control back to the
     * ordinary engagement planner instead of orbiting the structure.
     *
     * <p>Two callers read that refusal differently. {@link #computeFlankWaypoint}
     * asks once per squad at plan time and lets the action complete on it.
     * {@code AttackMove.maneuverAim} asks per member per tick, treats the
     * squad's own ground as "no flank here" and aims at the objective instead.
     * Its {@code FlankAimMemo} provides best-effort same-tick reuse, not
     * once-per-squad execution: parallel readers may both miss and compute.
     * Candidate work remains inside a
     * radius-{@value #WALKABLE_SNAP_RADIUS} square, and an admissible score
     * bound avoids route proofs that cannot improve the current winner. Each
     * remaining proof stops once its geometric-cost frontier exceeds every
     * route the existing detour-step limit could accept; rejected candidates
     * do not need a complete route around a distant end of the same wall.
     */
    public static int[] snapToReachable(int x, int y, Squad squad, BattleView sim) {
        NavigationGrid grid = sim.getGrid();
        int[] origin = squadOrigin(squad, sim);
        return snapToReachable(x, y, grid, origin[0], origin[1],
                Boolean.parseBoolean(System.getProperty(PRUNE_FLANK_CANDIDATES_PROPERTY, "true")),
                Boolean.parseBoolean(System.getProperty(BOUND_FLANK_PROOFS_PROPERTY, "true")),
                Boolean.parseBoolean(System.getProperty(FLANK_STEP_GATE_PROPERTY, "true")));
    }

    /**
     * Grid-only decision seam. The original score and ring tie order remain
     * authoritative: every route has at least its Chebyshev number of steps,
     * so a candidate whose lower bound ties or exceeds the incumbent cannot
     * win the strict-less comparison. Pruning neither asserts reachability nor
     * relaxes detour rejection; an absent incumbent still requires proof.
     */
    static int[] snapToReachable(int x, int y, NavigationGrid grid,
                                 int originX, int originY, boolean prune) {
        return snapToReachable(x, y, grid, originX, originY, prune, true);
    }

    static int[] snapToReachable(int x, int y, NavigationGrid grid,
                                 int originX, int originY, boolean prune, boolean boundProofs) {
        return snapToReachable(x, y, grid, originX, originY, prune, boundProofs, false);
    }

    static int[] snapToReachable(int x, int y, NavigationGrid grid,
                                 int originX, int originY, boolean prune, boolean boundProofs,
                                 boolean stepGate) {
        int bestX = originX;
        int bestY = originY;
        float bestScore = Float.MAX_VALUE;
        int proofs = 0;
        BoundedStepReachability gate = null;
        int maximumSteps = Math.max(Math.abs(x - originX), Math.abs(y - originY))
                + WALKABLE_SNAP_RADIUS + MAX_FLANK_EXTRA_STEPS;
        TickInnerProfile profile = TickInnerProfile.currentIfBound();
        for (int r = 0; r <= WALKABLE_SNAP_RADIUS; r++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dx = -r; dx <= r; dx++) {
                    if (r > 0 && Math.abs(dx) != r && Math.abs(dy) != r) continue;
                    int candidateX = x + dx;
                    int candidateY = y + dy;
                    if (!grid.inBounds(candidateX, candidateY)
                            || !grid.isWalkable(candidateX, candidateY)
                            || grid.isDoorway(candidateX, candidateY)) continue;
                    int directSteps = Math.max(
                            Math.abs(candidateX - originX),
                            Math.abs(candidateY - originY));
                    float rawDistance2 = dx * dx + dy * dy;
                    if (prune && rawDistance2 * 1000f + directSteps >= bestScore) continue;
                    int maxSteps = Math.min(directSteps + MAX_FLANK_EXTRA_STEPS,
                            (int) Math.floor(directSteps * MAX_FLANK_DETOUR_RATIO
                                    + MAX_FLANK_DETOUR_SLACK));
                    // Ordinary open-ground queries finish before this gate is needed.
                    // Cap both radius and expansion work: an incomplete proof may
                    // reject only completed BFS depths, never unknown territory.
                    if (stepGate && gate == null && proofs >= 4 && maximumSteps <= 96) {
                        gate = STEP_GATE.get();
                        long started = profile == null ? 0L : System.nanoTime();
                        gate.prepare(grid, originX, originY, GridPathfinder.USE_CARDINAL_NAVIGATION,
                                maximumSteps, 8192);
                        if (profile != null) {
                            profile.record(TickInnerProfile.Bucket.FLANK_STEP_FIELD, System.nanoTime() - started);
                            profile.recordCount(TickInnerProfile.Bucket.FLANK_STEP_EXPANDED, gate.expandedNodes());
                        }
                    }
                    if (gate != null && gate.canReject(candidateX, candidateY, maxSteps)) {
                        if (profile != null) profile.recordCount(TickInnerProfile.Bucket.FLANK_STEP_REJECT, 1);
                        continue;
                    }
                    proofs++;
                    if (profile != null) profile.routeReason("FLANK_SNAP");
                    int[] path;
                    try {
                        path = boundProofs
                                ? GridPathfinder.findPathWithinStepEnvelope(grid,
                                        originX, originY, candidateX, candidateY,
                                        GridPathfinder.USE_CARDINAL_NAVIGATION, maxSteps)
                                : GridPathfinder.findPath(grid,
                                        originX, originY, candidateX, candidateY);
                    } finally {
                        if (profile != null) profile.routeReason(null);
                    }
                    if (Paths.isEmpty(path)) continue;
                    int routeSteps = Math.max(0, Paths.cellCount(path) - 1);
                    if (routeSteps > directSteps * MAX_FLANK_DETOUR_RATIO
                            + MAX_FLANK_DETOUR_SLACK
                            || routeSteps - directSteps > MAX_FLANK_EXTRA_STEPS) continue;
                    float score = rawDistance2 * 1000f + routeSteps;
                    if (score < bestScore) {
                        bestScore = score;
                        bestX = candidateX;
                        bestY = candidateY;
                    }
                }
            }
        }
        return new int[]{bestX, bestY};
    }

    private static int[] squadOrigin(Squad squad, BattleView sim) {
        long leader = squad.autonomousLeader(sim);
        if (leader != 0L) {
            return new int[]{sim.world().cellX(leader), sim.world().cellY(leader)};
        }
        int x = Math.max(0, Math.min(sim.getGrid().getWidth() - 1,
                (int) Math.floor(squad.centroidX)));
        int y = Math.max(0, Math.min(sim.getGrid().getHeight() - 1,
                (int) Math.floor(squad.centroidY)));
        return new int[]{x, y};
    }
}
