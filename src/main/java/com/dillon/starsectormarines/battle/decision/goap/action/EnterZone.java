package com.dillon.starsectormarines.battle.decision.goap.action;

import com.dillon.starsectormarines.battle.combat.FireStance;
import com.dillon.starsectormarines.battle.decision.TacticalScoring;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.nav.Paths;
import com.dillon.starsectormarines.battle.infantry.SmokeTactics;
import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.squad.SquadPlan;
import com.dillon.starsectormarines.battle.squad.FireTeamGroups;
import com.dillon.starsectormarines.battle.decision.goap.ActionStatus;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.zone.NavigationZone;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * <b>Squad posture: move into a target zone.</b> Each member paths to a
 * representative cell inside {@link #targetZoneId} and walks. The plan
 * advances to {@link ClearZone} as soon as the <em>first</em> member's
 * logical cell crosses into the target zone — matches Stage 1's
 * first-arrival semantics on {@link ApproachPosture}. Stragglers catch up
 * inside the next step ({@link ClearZone}/{@link HoldZone} pull members not
 * yet in zone in via the shared {@link AbstractZoneAction#advanceIntoZone}).
 *
 * <p>The approach member of the {@link AbstractZoneAction} family: it advances
 * with {@code haltOnContact = true} so a marine that runs into a garrison
 * ambush stops and fights in place (and accelerates the squad replan) rather
 * than charging through, letting an engagement-tier goal preempt. The
 * commitment steps ({@link ClearZone}/{@link HoldZone}) push through contact
 * instead.
 *
 * <p>Parameterized per-zone — Story K's customPlan creates one instance per
 * zone in the BFS path. Not a singleton (unlike Stage 1's postures), and not
 * registered in {@code GoapInfantryBehavior.INFANTRY_ACTIONS}: the
 * backward-chaining planner never sees these; they're emitted only by
 * {@link com.dillon.starsectormarines.battle.infantry.SecureObjectiveZone}'s
 * custom plan.
 */
public final class EnterZone extends AbstractZoneAction {

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

    /** Destination cell inside the target zone — chosen at construction so all members aim at the same spot and the pathfinder routes them through the portal naturally. */
    private final int destX;
    private final int destY;
    /** Final assault hops push through contact; ordinary transit remains cautious. */
    private final boolean commitThroughContact;

    public EnterZone(int targetZoneId, int destX, int destY) {
        this(targetZoneId, destX, destY, false);
    }

    private EnterZone(int targetZoneId, int destX, int destY,
                      boolean commitThroughContact) {
        super(targetZoneId);
        this.destX = destX;
        this.destY = destY;
        this.commitThroughContact = commitThroughContact;
    }

    /**
     * Picks a representative interior cell for {@code zone} (see
     * {@link AbstractZoneAction#interiorCell}) and builds an EnterZone aimed at
     * it. Falls back to cell (0,0) for a degenerate empty zone — the pathfinder
     * then no-ops and the next replan re-synthesizes.
     */
    public static EnterZone forZone(NavigationZone zone, NavigationGrid grid) {
        return forZone(zone, grid, false);
    }

    /** Builds the final room-taking hop, which cannot park at the threshold. */
    public static EnterZone committedForZone(NavigationZone zone,
                                             NavigationGrid grid) {
        return forZone(zone, grid, true);
    }

    private static EnterZone forZone(NavigationZone zone, NavigationGrid grid,
                                     boolean commitThroughContact) {
        int[] c = interiorCell(zone, grid);
        int x = c != null ? c[0] : 0;
        int y = c != null ? c[1] : 0;
        return new EnterZone(zone.getZoneId(), x, y, commitThroughContact);
    }

    public int destX() { return destX; }
    public int destY() { return destY; }
    public boolean commitsThroughContact() { return commitThroughContact; }

    @Override public String name() { return "EnterZone[" + targetZoneId + "]"; }

    @Override
    public Map<String, List<Long>> assignRoles(Squad squad, BattleView sim,
                                                List<Long> candidates) {
        return FireTeamGroups.assignments(FIRE_TEAM, candidates, sim.squad());
    }

    /**
     * EnterZone authors every legal primary shot itself. In particular, the
     * moving half of a bound must not receive the dispatcher's automatic shot
     * of opportunity after this action deliberately left its intent empty.
     */
    @Override public boolean permitsOpportunityFire() { return false; }

    @Override
    public ActionStatus execute(long member, Squad squad, BattleControl sim) {
        if (memberInZone(member, sim)) {
            squad.clearMechScreen();
            clearBounding(squad);
            return ActionStatus.SUCCESS;
        }

        // The final SECURE_COMPOUND hop is already the decision to take the
        // room. Its following ClearZone/HoldZone steps use commitment
        // semantics, so allowing this threshold step to re-enter the cautious
        // contact halt can preserve the same mission plan forever without ever
        // handing off to them.
        if (commitThroughContact) {
            squad.clearMechScreen();
            clearBounding(squad);
            advanceIntoZone(member, squad, sim, destX, destY, false);
            return ActionStatus.RUNNING;
        }

        updateAdvanceThreat(squad, sim, destX, destY);
        if (MechScreenAdvance.execute(member, squad, targetZoneId, destX, destY, sim)) {
            clearBounding(squad);
            return ActionStatus.RUNNING;
        }
        if (!squad.advanceEngageCommitted || sim.resolveUnit(squad.advanceThreatId) == 0L) {
            clearBounding(squad);
        } else if (executeBounding(member, squad, sim)) {
            return ActionStatus.RUNNING;
        }

        if (holdsForQuietEchelon(member, squad, sim)) {
            if (!Paths.isEmpty(sim.world().path(member))) sim.clearPath(member);
            return ActionStatus.RUNNING;
        }
        advanceIntoZone(member, squad, sim, destX, destY, true);
        return ActionStatus.RUNNING;
    }

    private boolean holdsForQuietEchelon(long member, Squad squad,
                                         BattleControl sim) {
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

    @Override
    public List<int[]> highlightCells(Squad squad, BattleView sim) {
        if (squad.screeningMechId != 0L) {
            int[] xs = squad.mechScreenTargetXs;
            int[] ys = squad.mechScreenTargetYs;
            int count = Math.min(xs.length, ys.length);
            List<int[]> cells = new ArrayList<>(count);
            for (int i = 0; i < count; i++) cells.add(new int[]{xs[i], ys[i]});
            return cells;
        }
        if (!squad.boundingActive) return List.of();
        int[] xs = squad.boundingTargetXs;
        int[] ys = squad.boundingTargetYs;
        int count = Math.min(xs.length, ys.length);
        List<int[]> cells = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            cells.add(new int[]{xs[i], ys[i]});
        }
        return cells;
    }

    private boolean executeBounding(long member, Squad squad, BattleControl sim) {
        SquadPlan plan = squad.currentPlan;
        SquadPlan.Step step = plan != null ? plan.currentStep() : null;
        if (step == null || step.action != this) return false;
        String memberTeam = step.slotOf(member);
        if (memberTeam == null || !memberTeam.startsWith(FIRE_TEAM)) return false;

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
            if (squad.boundingActive && !matchesCurrentAdvance(squad, threat)) {
                squad.clearBoundingOverwatch();
            }

            if (squad.boundingActive && allBoundersArrived(squad, sim)) {
                if (!beginPhase(squad, sim, teams, squad.boundingPhase + 1, threat)) {
                    squad.clearBoundingOverwatch();
                    squad.boundingAttemptTick = sim.getSimTickIndex();
                    return false;
                }
            }

            if (!squad.boundingActive) {
                if (squad.boundingAttemptTick == sim.getSimTickIndex()) return false;
                if (!beginPhase(squad, sim, teams, 0, threat)) return false;
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
                               List<List<Long>> teams, int phase, long threat) {
        squad.boundingAttemptTick = sim.getSimTickIndex();
        int maneuverTeam = Math.floorMod(phase + 1, teams.size());
        List<Long> bounders = teams.get(maneuverTeam);
        List<Long> suppressors = new ArrayList<>();
        for (int i = 0; i < teams.size(); i++) {
            if (i != maneuverTeam) suppressors.addAll(teams.get(i));
        }
        int[] stride = nextStrideCell(squad, phase > 0);
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

    private int[] nextStrideCell(Squad squad, boolean fromPreviousStride) {
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

    private boolean matchesCurrentAdvance(Squad squad, long threat) {
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

    private static void clearBounding(Squad squad) {
        if (!squad.boundingActive && squad.boundingThreatId == 0L) return;
        squad.clearBoundingOverwatch();
    }

    private record BoundingState(int phase, long threat,
                                 long[] memberIds, int[] targetXs, int[] targetYs) {}
}
