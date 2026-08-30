package com.dillon.starsectormarines.battle.decision.goap.action;

import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.squad.SquadPlan;
import com.dillon.starsectormarines.battle.squad.SquadContactPicture.ContactInitiative;
import com.dillon.starsectormarines.battle.squad.SquadContactPicture.Doctrine;
import com.dillon.starsectormarines.battle.squad.SquadContactPicture.Posture;
import com.dillon.starsectormarines.battle.combat.FireStance;
import com.dillon.starsectormarines.battle.decision.TacticalScoring;
import com.dillon.starsectormarines.battle.infantry.SmokeTactics;
import com.dillon.starsectormarines.battle.decision.goap.Action;
import com.dillon.starsectormarines.battle.decision.goap.ActionStatus;
import com.dillon.starsectormarines.battle.decision.goap.Planner;
import com.dillon.starsectormarines.battle.decision.goap.WorldState;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.nav.SharedGoalPolicy;
import com.dillon.starsectormarines.battle.nav.Paths;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.zone.NavigationZone;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Shared base for the squad-push actions
 * ({@link EnterZone}, {@link ClearZone}, {@link HoldZone}, {@link AttackMove}).
 * They take no part in the backward-chaining planner (empty
 * preconditions/effects, flat cost) and share one body of advance behaviour:
 * the contact-scored commit in {@link #advanceIntoZone}, the fire-team bound
 * in {@link #executeBounding}, and the open-ground echelon in
 * {@link #holdsForQuietEchelon}.
 *
 * <p><b>Advancing under contact is not a zone concept, and the destination is
 * a parameter rather than a field.</b> Three of the family push toward a zone
 * and answer {@link #memberInZone} to know they have arrived; {@link AttackMove}
 * pushes toward a bare cell and often begins and ends inside one enormous
 * outdoor zone. Bounding lived inside {@code EnterZone} while it was the only
 * action that did it, which meant the order most obviously about fighting your
 * way somewhere was the one that walked there in a single file.
 *
 * <p>The zone family adds one rule of its own: <b>a member isn't performing a
 * zone action until it is actually inside the target zone.</b>
 * That rule is the zone-entry precondition shared across those three —
 * {@link #memberInZone}. A member standing outside {@code targetZoneId} must
 * first consolidate <em>into</em> it via {@link #advanceIntoZone} rather than
 * engaging from an adjacent room: without this, a member with a firing
 * solution across a portal sits at the threshold trading fire forever, and a
 * compound capture stays permanently contested while the squad fights the
 * room from the doorway instead of pushing in.
 *
 * <p>The one tactical knob between members of the family is whether the
 * advance may <em>commit</em> to contact. {@link EnterZone} is the approach
 * step: it scores local force, retreat posture, and distance from the advance
 * axis every tick, then either presses with shots of opportunity or fights
 * inside a bounded off-axis leash. {@link ClearZone}/{@link HoldZone} are
 * commitment steps: the squad is taking the room, so they push through contact
 * while firing suppressively instead of freezing at the threshold. That's the
 * {@code haltOnContact} flag, not duplicated movement code.
 */
abstract class AbstractZoneAction implements Action {

    /**
     * Minimum sim-seconds since the last squad replan before a contact-halt is
     * allowed to force another. Without this throttle a pinned squad would
     * replan every tick — the planner would re-pick the same approach action
     * (no morale break, no other relevant goal), the marine would halt again,
     * replan would fire again, ad infinitum. 1.0s gives a clean
     * one-replan-per-contact-event under the 2.0s base period. Only consulted
     * on the {@code haltOnContact} path.
     */
    protected static final float CONTACT_HALT_REPLAN_THROTTLE = 1.0f;
    /** Raw threat weight that flips a pressing squad into committed contact. */
    static final float ADVANCE_COMMIT_THRESHOLD = 0.55f;
    /** Lower threshold that releases a committed squad back onto the objective route. */
    static final float ADVANCE_RELEASE_THRESHOLD = 0.30f;
    /** Minimum useful off-axis firing-position radius once the squad commits. */
    static final float ADVANCE_LEASH_MIN = 4f;
    /** Maximum off-axis firing-position radius at full threat weight. */
    static final float ADVANCE_LEASH_MAX = 12f;

    /** Role-slot prefix for the fire-team partition a bounding advance moves in. */
    static final String FIRE_TEAM = "fireteam:";
    /** Test/fixture aliases for the first two organizational teams. */
    static final String TEAM_A = FIRE_TEAM + "0";
    static final String TEAM_B = FIRE_TEAM + "1";
    /** Cells gained by each maneuvering fire team before the role rotates. */
    static final float BOUNDING_STRIDE = 6f;
    /** Open-ground progress required before the next quiet-advance team releases. */
    static final float ECHELON_RELEASE_DISTANCE = 2f;
    /** Formation authority yields unless this local square radius is fully open. */
    static final int ECHELON_OPEN_CLEARANCE = 2;

    protected final int targetZoneId;

    protected AbstractZoneAction(int targetZoneId) {
        this.targetZoneId = targetZoneId;
    }

    public final int targetZoneId() { return targetZoneId; }

    @Override public WorldState preconditions() { return WorldState.EMPTY; }
    @Override public WorldState effects() { return WorldState.EMPTY; }
    @Override public float cost(WorldState s, Squad squad, BattleView sim) { return 1f; }
    @Override public int requiredMembers() { return 1; }

    /** True iff {@code member}'s logical cell lies inside {@link #targetZoneId}. */
    protected final boolean memberInZone(long member, BattleView sim) {
        return sim.getZoneGraph().zoneIdAt(sim.world().cellX(member), sim.world().cellY(member)) == targetZoneId;
    }

    /**
     * Advance a member that is standing <em>outside</em> the target zone toward
     * {@code (destX, destY)} — an interior cell — taking opportunistic shots at
     * a visible in-range enemy while it moves. Caller invokes this only when
     * {@link #memberInZone} is false and then returns {@link ActionStatus#RUNNING}.
     *
     * <p>Target handling mirrors the in-zone engage paths: a stale-but-alive
     * target that's no longer worth pursuing is dropped and re-picked via
     * {@link TacticalScoring#findBestTarget}, so a member that fixated on the
     * approach doesn't walk past fresh shooters.
     *
     * @param haltOnContact when true (approach semantics), the squad's
     *        threat-scored advance leash may stop the member to prosecute a
     *        route contact; when false (commitment semantics), the member keeps
     *        pushing into the zone while firing.
     */
    protected final void advanceIntoZone(long member, Squad squad, BattleControl sim,
                                         int destX, int destY, boolean haltOnContact) {
        boolean committed = false;
        boolean doctrineHold = false;
        boolean prosecuteContact = false;
        float engageLeash = 0f;
        long advanceThreat = 0L;
        int threatAnchorX = -1;
        int threatAnchorY = -1;
        if (haltOnContact) {
            updateAdvanceThreat(squad, sim, destX, destY);
            committed = squad.advanceEngageCommitted;
            Doctrine doctrine = squad.contactPicture.doctrine();
            boolean advancingPicture = squad.contactPicture.posture() == Posture.ADVANCING;
            if (advancingPicture && doctrine == Doctrine.DISENGAGE) {
                BreakContact.INSTANCE.execute(member, squad, sim);
                return;
            }
            doctrineHold = TacticalScoring.shouldHardHoldAdvance(squad,
                    squad.contactPicture, sim.getSimTickIndex());
            prosecuteContact = advancingPicture && doctrine == Doctrine.HOLD
                    && squad.contactPicture.contactInitiative()
                    == ContactInitiative.PROSECUTE
                    && sim.resolveUnit(squad.contactPicture.primaryContactId()) != 0L;
            committed |= doctrineHold;
            committed |= prosecuteContact;
            engageLeash = squad.advanceEngageLeash;
            advanceThreat = squad.advanceThreatId;
            threatAnchorX = squad.advanceThreatAnchorX;
            threatAnchorY = squad.advanceThreatAnchorY;
            if (prosecuteContact) {
                advanceThreat = squad.contactPicture.primaryContactId();
                threatAnchorX = Math.round(squad.centroidX - 0.5f);
                threatAnchorY = Math.round(squad.centroidY - 0.5f);
                engageLeash = ADVANCE_LEASH_MAX;
            }
        }

        long target = sim.targetOf(member);
        if (committed && sim.resolveUnit(advanceThreat) != 0L) {
            target = advanceThreat;
            sim.world().setTargetId(member, target);
        } else if (target == 0L || !sim.getTacticalScoring().shouldKeepPursuing(member, target)) {
            target = sim.getTacticalScoring().findBestTarget(member);
            sim.world().setTargetId(member, target);
        }

        boolean inContact = false;
        if (target != 0L) {
            float d = TacticalScoring.cellDistance(sim.world().x(member), sim.world().y(member),
                    sim.world().x(target), sim.world().y(target));
            boolean clearShot = sim.getTacticalScoring().hasClearShot(member, target);
            inContact = d <= sim.world().attackRange(member) && clearShot;
        }

        if (inContact) {
            sim.combat().setFireIntent(member, target,
                    committed ? FireStance.STANCED : FireStance.MOVING,
                    committed);
        } else {
            // Opportunistic return fire while advancing. The pursuit target
            // is out of range/LoS (or absent) — across the open approach
            // that left members marching past enemies they could hit,
            // eating shots without returning any. Fire on the nearest enemy
            // actually in range and LoS, MOVING stance, without halting or
            // touching the pursuit target: the squad still commits to the
            // zone push, the trigger just stops it being a sitting duck.
            // FiringSystem's beginBurst tracks the intent target, so the
            // follow-up burst tracks the enemy we shot, not the pursuit target.
            long opportune = sim.getTacticalScoring().closestEnemyInAttackRange(
                    member, sim.combat().reflexTargetId(member),
                    TacticalScoring.OPPORTUNITY_RETARGET_DISTANCE_MARGIN);
            if (opportune != 0L) {
                sim.combat().setFireIntent(member, opportune, FireStance.MOVING, false);
            }
        }

        if (committed && inContact) {
            int[] path = sim.world().path(member);
            // A committed contact suppresses the long objective route, but a
            // successful shot may have authored RepositionToCover's short,
            // strictly-better-cover move. Its per-member cooldown is the
            // ownership marker: advance that staggered micro-move while it is
            // live; otherwise plant on the firing line as before.
            boolean activeCoverReposition = sim.world().repositionCooldown(member) > 0f
                    && sim.world().pathIdx(member) < Paths.cellCount(path);
            if (activeCoverReposition) {
                sim.advanceMovement(member);
            } else if (!Paths.isEmpty(path)) {
                sim.clearPath(member);
            }
            if (squad.timeSinceReplan >= CONTACT_HALT_REPLAN_THROTTLE) {
                squad.timeSinceReplan = Planner.REPLAN_PERIOD;
            }
            return;
        }

        if (committed && target != 0L && threatAnchorX >= 0 && threatAnchorY >= 0) {
            int[] firingPos = sim.getTacticalScoring().findFiringPositionWithin(
                    member, target, threatAnchorX, threatAnchorY, engageLeash);
            if (firingPos != null) {
                if (sim.movement().mayRepath(member)) {
                    sim.setPath(member, GridPathfinder.findPath(sim.getGrid(),
                            sim.world().cellX(member), sim.world().cellY(member),
                            firingPos[0], firingPos[1], sim.getOccupancyMap()));
                }
                sim.advanceMovement(member);
                return;
            }
        }

        // A flank/rear or adverse-odds picture can order a contact line even
        // when no believed enemy lies close enough to the literal route
        // segment to provide a firing-position anchor. Plant instead of
        // silently resuming the objective path; opportunity fire above still
        // answers any target that becomes legal.
        if (doctrineHold) {
            if (!Paths.isEmpty(sim.world().path(member))) sim.clearPath(member);
            return;
        }

        if (sim.movement().mayRepath(member)) {
            // Every member ordered into this zone walks to the same interior
            // cell, so this is the dense same-destination case a shared
            // reverse field exists for: one field per zone serves the whole
            // push instead of one A* per member per repath. The firing-position
            // route above stays on A* — that destination is picked per member.
            int memberX = sim.world().cellX(member);
            int memberY = sim.world().cellY(member);
            sim.setPath(member,
                    SharedGoalPolicy.usesSharedGoalFields(sim.liveUnitCount())
                            ? sim.findSharedPathToGoal(memberX, memberY, destX, destY)
                            : GridPathfinder.findPath(sim.getGrid(),
                                    memberX, memberY, destX, destY,
                                    sim.getOccupancyMap()));
        }
        sim.advanceMovement(member);
    }

    /**
     * Recomputes the squad-level route threat at most once per sim tick and
     * applies commit/release hysteresis. The synchronized section is required
     * because members of one squad can execute in parallel; the tally itself
     * is member-independent, so whichever member arrives first may author the
     * cache without making behavior order-dependent.
     */
    protected static void updateAdvanceThreat(Squad squad, BattleControl sim, int destX, int destY) {
        int tick = sim.getSimTickIndex();
        if (squad.advanceThreatTick == tick) return;
        synchronized (squad.lock) {
            if (squad.advanceThreatTick == tick) return;
            TacticalScoring.AdvanceThreat threat = sim.getTacticalScoring()
                    .assessAdvanceThreat(squad, destX, destY, tick);
            squad.advanceEngageWeight = threat.weight();
            squad.advanceEngageCommitted = shouldCommitAdvance(
                    squad.advanceEngageCommitted, threat.weight());
            squad.advanceEngageLeash = squad.advanceEngageCommitted
                    ? Math.max(ADVANCE_LEASH_MIN, ADVANCE_LEASH_MAX * threat.weight())
                    : 0f;
            squad.advanceThreatId = threat.primaryThreatId();
            squad.advanceThreatFoes = threat.foes();
            squad.advanceThreatFriends = threat.friends();
            squad.advanceThreatAnchorX = threat.axisAnchorX();
            squad.advanceThreatAnchorY = threat.axisAnchorY();
            squad.advanceThreatRetreating = threat.primaryRetreating();
            squad.advanceThreatTick = tick;
        }
    }

    static boolean shouldCommitAdvance(boolean wasCommitted, float weight) {
        return wasCommitted ? weight >= ADVANCE_RELEASE_THRESHOLD
                : weight >= ADVANCE_COMMIT_THRESHOLD;
    }

    /**
     * Representative interior cell of {@code zone} — the middle entry in its
     * flat cell-index array. {@code cellIndices} is in detection order (roughly
     * scan-line), so the middle usually lands deep in the zone rather than at a
     * portal edge. Returns {@code null} for a missing or empty zone.
     */
    protected static int[] interiorCell(NavigationZone zone, NavigationGrid grid) {
        if (zone == null) return null;
        int[] cells = zone.getCellIndices();
        if (cells.length == 0) return null;
        int pick = cells[cells.length / 2];
        return new int[]{ pick % grid.getWidth(), pick / grid.getWidth() };
    }

    /** {@link #interiorCell} resolved from a zone id against the live graph/grid. */
    protected static int[] interiorCellOf(int zoneId, BattleView sim) {
        return interiorCell(sim.getZoneGraph().zoneById(zoneId), sim.getGrid());
    }
    protected final boolean holdsForQuietEchelon(long member, Squad squad,
                                                BattleControl sim,
                                                int destX, int destY) {
        if (squad.isMechSquad() || squad.contactPicture.hasContacts()) return false;
        SquadPlan plan = squad.currentPlan;
        SquadPlan.Step step = plan != null ? plan.currentStep() : null;
        if (step == null || step.action != this) return false;
        List<List<Long>> teams = liveTeams(step, sim);
        int teamIndex = teamIndexContaining(teams, member);
        if (teamIndex <= 0 || teams.size() < 2) return false;
        List<Long> currentTeam = teams.get(teamIndex);
        for (long teammate : currentTeam) {
            if (sim.movement().has(teammate) && !sim.movement().settled(teammate)) {
                return false; // once released, the whole team completes its movement
            }
            if (!locallyOpenForEchelon(teammate, sim)) return false;
        }

        float axisX = destX + 0.5f - squad.centroidX;
        float axisY = destY + 0.5f - squad.centroidY;
        float axisLength = (float) Math.sqrt(axisX * axisX + axisY * axisY);
        if (axisLength <= ECHELON_RELEASE_DISTANCE) return false;
        axisX /= axisLength;
        axisY /= axisLength;
        float predecessor = teamProjection(teams.get(teamIndex - 1), axisX, axisY, sim);
        float current = teamProjection(currentTeam, axisX, axisY, sim);
        return predecessor - current < ECHELON_RELEASE_DISTANCE;
    }

    private static float teamProjection(List<Long> team, float axisX,
                                        float axisY, BattleView sim) {
        float projection = 0f;
        for (long member : team) {
            projection += sim.world().x(member) * axisX
                    + sim.world().y(member) * axisY;
        }
        return projection / team.size();
    }

    private static boolean locallyOpenForEchelon(long member, BattleView sim) {
        NavigationGrid grid = sim.getGrid();
        int cx = sim.world().cellX(member);
        int cy = sim.world().cellY(member);
        if (grid.isDoorway(cx, cy)) return false;
        for (int y = cy - ECHELON_OPEN_CLEARANCE;
             y <= cy + ECHELON_OPEN_CLEARANCE; y++) {
            for (int x = cx - ECHELON_OPEN_CLEARANCE;
                 x <= cx + ECHELON_OPEN_CLEARANCE; x++) {
                if (!grid.inBounds(x, y) || !grid.isWalkable(x, y)
                        || grid.isDoorway(x, y)) return false;
            }
        }
        return true;
    }
    protected final boolean executeBounding(long member, Squad squad,
                                           BattleControl sim,
                                           int destX, int destY) {
        SquadPlan plan = squad.currentPlan;
        SquadPlan.Step step = plan != null ? plan.currentStep() : null;
        if (step == null || step.action != this) return false;
        String memberTeam = step.slotOf(member);
        if (memberTeam == null || !memberTeam.startsWith(FIRE_TEAM)) return false;

        // Bound only inside the threat's beaten zone. Commitment reaches much
        // further than fire does — the advance-threat score looks tens of cells
        // down the route — so a squad can be committed to a contact that cannot
        // touch it, and bounding that stretch buys nothing for the price of
        // moving half the squad at a time. Enabling the bound on attack moves
        // without this cost reinforced-south 7300 ticks and 46 defender kills.
        if (!sim.getTacticalScoring().threatReaches(squad.advanceThreatId,
                squad.centroidX, squad.centroidY, BOUNDING_STRIDE)) {
            clearBounding(squad);
            return false;
        }

        if (SmokeTactics.holdForAdvanceSmoke(squad, squad.advanceThreatId,
                destX, destY, sim)) {
            if (!Paths.isEmpty(sim.world().path(member))) sim.clearPath(member);
            return true;
        }

        List<List<Long>> teams = liveTeams(step, sim);
        if (teams.size() < 2) {
            clearBounding(squad);
            return false;
        }

        BoundingState state;
        synchronized (squad.lock) {
            long threat = squad.advanceThreatId;
            if (squad.boundingActive && !matchesCurrentAdvance(squad, threat, destX, destY)) {
                squad.clearBoundingOverwatch();
            }

            if (squad.boundingActive && allBoundersArrived(squad, sim)) {
                if (!beginPhase(squad, sim, teams, squad.boundingPhase + 1, threat, destX, destY)) {
                    squad.clearBoundingOverwatch();
                    squad.boundingAttemptTick = sim.getSimTickIndex();
                    return false;
                }
            }

            if (!squad.boundingActive) {
                if (squad.boundingAttemptTick == sim.getSimTickIndex()) return false;
                if (!beginPhase(squad, sim, teams, 0, threat, destX, destY)) return false;
            }
            state = new BoundingState(squad.boundingPhase, squad.boundingThreatId,
                    squad.boundingMemberIds, squad.boundingTargetXs, squad.boundingTargetYs);
        }

        int memberTeamIndex = teamIndexContaining(teams, member);
        int maneuverTeamIndex = Math.floorMod(state.phase + 1, teams.size());
        if (memberTeamIndex != maneuverTeamIndex) {
            holdOverwatch(member, state.threat, sim);
            return true;
        }
        return moveBounder(member, state, sim);
    }

    private boolean beginPhase(Squad squad, BattleControl sim,
                               List<List<Long>> teams, int phase, long threat,
                               int destX, int destY) {
        squad.boundingAttemptTick = sim.getSimTickIndex();
        int maneuverTeam = Math.floorMod(phase + 1, teams.size());
        List<Long> bounders = teams.get(maneuverTeam);
        List<Long> suppressors = new ArrayList<>();
        for (int i = 0; i < teams.size(); i++) {
            if (i != maneuverTeam) suppressors.addAll(teams.get(i));
        }
        int[] stride = nextStrideCell(squad, phase > 0, destX, destY);
        if (stride == null) return false;
        boolean smokeScreensStride = sim.resolveUnit(threat) != 0L
                && sim.getGrid().hasTransientOpacityOnLine(sim.world().cellX(threat),
                sim.world().cellY(threat), stride[0], stride[1]);
        if (!smokeScreensStride && !hasFiringMember(suppressors, threat, sim)) return false;
        List<TacticalScoring.BoundingPosition> positions = sim.getTacticalScoring()
                .findBoundingPositions(bounders, threat, stride[0], stride[1], destX, destY);
        if (positions.size() != bounders.size()) return false;

        long[] memberIds = new long[positions.size()];
        int[] xs = new int[positions.size()];
        int[] ys = new int[positions.size()];
        for (int i = 0; i < positions.size(); i++) {
            TacticalScoring.BoundingPosition position = positions.get(i);
            memberIds[i] = position.memberId();
            xs[i] = position.x();
            ys[i] = position.y();
        }

        squad.boundingActive = true;
        squad.boundingPhase = phase;
        squad.boundingTargetZoneId = targetZoneId;
        squad.boundingDestX = destX;
        squad.boundingDestY = destY;
        squad.boundingThreatId = threat;
        squad.boundingStrideX = stride[0];
        squad.boundingStrideY = stride[1];
        squad.boundingMemberIds = memberIds;
        squad.boundingTargetXs = xs;
        squad.boundingTargetYs = ys;
        return true;
    }

    private int[] nextStrideCell(Squad squad, boolean fromPreviousStride,
                                 int destX, int destY) {
        float startX = fromPreviousStride ? squad.boundingStrideX + 0.5f : squad.centroidX;
        float startY = fromPreviousStride ? squad.boundingStrideY + 0.5f : squad.centroidY;
        float dx = destX + 0.5f - startX;
        float dy = destY + 0.5f - startY;
        float distance = (float) Math.sqrt(dx * dx + dy * dy);
        if (distance < TacticalScoring.BOUNDING_MIN_FORWARD_PROGRESS) return null;
        float stride = Math.min(BOUNDING_STRIDE, distance);
        int x = (int) Math.floor(startX + dx / distance * stride);
        int y = (int) Math.floor(startY + dy / distance * stride);
        return new int[]{x, y};
    }

    private static boolean hasFiringMember(List<Long> members, long threat, BattleControl sim) {
        for (long member : members) {
            if (canFireAt(member, threat, sim)) return true;
        }
        return false;
    }

    private static boolean canFireAt(long member, long threat, BattleView sim) {
        if (sim.resolveUnit(member) == 0L || sim.resolveUnit(threat) == 0L) return false;
        float distance = TacticalScoring.cellDistance(sim.world().x(member), sim.world().y(member),
                sim.world().x(threat), sim.world().y(threat));
        return distance <= sim.world().attackRange(member)
                && sim.getTacticalScoring().hasClearShot(member, threat);
    }

    private static void holdOverwatch(long member, long threat, BattleControl sim) {
        if (!Paths.isEmpty(sim.world().path(member))) sim.clearPath(member);
        if (canFireAt(member, threat, sim)) {
            sim.world().setTargetId(member, threat);
            sim.combat().setFireIntent(member, threat, FireStance.STANCED, false);
        }
    }

    private static boolean moveBounder(long member, BoundingState state, BattleControl sim) {
        int index = boundingTargetIndex(state.memberIds, member);
        if (index < 0) return false;
        int x = state.targetXs[index];
        int y = state.targetYs[index];
        if (sim.movement().atCell(member, x, y)) {
            if (!Paths.isEmpty(sim.world().path(member))) sim.clearPath(member);
            return true;
        }
        if (sim.movement().mayRepath(member)) {
            sim.setPath(member, GridPathfinder.findPath(sim.getGrid(),
                    sim.world().cellX(member), sim.world().cellY(member),
                    x, y, sim.getOccupancyMap()));
        }
        sim.advanceMovement(member);
        return true;
    }

    private static boolean allBoundersArrived(Squad squad, BattleView sim) {
        boolean anyLive = false;
        for (int i = 0; i < squad.boundingMemberIds.length; i++) {
            long member = squad.boundingMemberIds[i];
            if (sim.resolveUnit(member) == 0L) continue;
            anyLive = true;
            if (!sim.movement().atCell(member,
                    squad.boundingTargetXs[i], squad.boundingTargetYs[i])) return false;
        }
        return anyLive;
    }

    private static int boundingTargetIndex(long[] memberIds, long member) {
        for (int i = 0; i < memberIds.length; i++) {
            if (memberIds[i] == member) return i;
        }
        return -1;
    }

    private boolean matchesCurrentAdvance(Squad squad, long threat,
                                          int destX, int destY) {
        return squad.boundingTargetZoneId == targetZoneId
                && squad.boundingDestX == destX
                && squad.boundingDestY == destY
                && squad.boundingThreatId == threat;
    }

    private static List<Long> liveMembers(List<Long> assigned, BattleView sim) {
        if (assigned == null || assigned.isEmpty()) return List.of();
        List<Long> live = new ArrayList<>(assigned.size());
        for (long member : assigned) {
            if (sim.resolveUnit(member) != 0L) live.add(member);
        }
        return live;
    }

    private static List<List<Long>> liveTeams(SquadPlan.Step step, BattleView sim) {
        List<List<Long>> teams = new ArrayList<>();
        for (Map.Entry<String, List<Long>> entry : step.assignments.entrySet()) {
            if (!entry.getKey().startsWith(FIRE_TEAM)) continue;
            List<Long> live = liveMembers(entry.getValue(), sim);
            if (!live.isEmpty()) teams.add(live);
        }
        return teams;
    }

    private static int teamIndexContaining(List<List<Long>> teams, long member) {
        for (int i = 0; i < teams.size(); i++) {
            for (long candidate : teams.get(i)) if (candidate == member) return i;
        }
        return -1;
    }

    protected static void clearBounding(Squad squad) {
        if (!squad.boundingActive && squad.boundingThreatId == 0L) return;
        squad.clearBoundingOverwatch();
    }

    private record BoundingState(int phase, long threat,
                                 long[] memberIds, int[] targetXs, int[] targetYs) {}
}
