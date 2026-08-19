package com.dillon.starsectormarines.battle.decision.goap.action;

import com.dillon.starsectormarines.battle.combat.FireStance;
import com.dillon.starsectormarines.battle.decision.TacticalScoring;
import com.dillon.starsectormarines.battle.decision.goap.scoring.RoleAssigner;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.nav.Paths;
import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.squad.SquadPlan;
import com.dillon.starsectormarines.battle.decision.goap.ActionStatus;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.zone.NavigationZone;

import java.util.ArrayList;
import java.util.List;

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

    static final String TEAM_A = "team:a";
    static final String TEAM_B = "team:b";
    /** Cells gained by each half-squad before the roles swap. */
    static final float BOUNDING_STRIDE = 6f;

    /** Destination cell inside the target zone — chosen at construction so all members aim at the same spot and the pathfinder routes them through the portal naturally. */
    private final int destX;
    private final int destY;

    public EnterZone(int targetZoneId, int destX, int destY) {
        super(targetZoneId);
        this.destX = destX;
        this.destY = destY;
    }

    /**
     * Picks a representative interior cell for {@code zone} (see
     * {@link AbstractZoneAction#interiorCell}) and builds an EnterZone aimed at
     * it. Falls back to cell (0,0) for a degenerate empty zone — the pathfinder
     * then no-ops and the next replan re-synthesizes.
     */
    public static EnterZone forZone(NavigationZone zone, NavigationGrid grid) {
        int[] c = interiorCell(zone, grid);
        int x = c != null ? c[0] : 0;
        int y = c != null ? c[1] : 0;
        return new EnterZone(zone.getZoneId(), x, y);
    }

    public int destX() { return destX; }
    public int destY() { return destY; }

    @Override public String name() { return "EnterZone[" + targetZoneId + "]"; }

    @Override
    public List<RoleAssigner.Slot<Long>> roles(Squad squad, BattleView sim) {
        int teamA = (squad.aliveMembers + 1) / 2;
        int teamB = squad.aliveMembers / 2;
        return List.of(
                new RoleAssigner.Slot<>(TEAM_A, teamA, member -> 0f),
                new RoleAssigner.Slot<>(TEAM_B, teamB, member -> 0f));
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
            clearBounding(squad);
            return ActionStatus.SUCCESS;
        }

        updateAdvanceThreat(squad, sim, destX, destY);
        if (!squad.advanceEngageCommitted || sim.resolveUnit(squad.advanceThreatId) == 0L) {
            clearBounding(squad);
        } else if (executeBounding(member, squad, sim)) {
            return ActionStatus.RUNNING;
        }

        advanceIntoZone(member, squad, sim, destX, destY, true);
        return ActionStatus.RUNNING;
    }

    @Override
    public List<int[]> highlightCells(Squad squad, BattleView sim) {
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
        if (!TEAM_A.equals(memberTeam) && !TEAM_B.equals(memberTeam)) return false;

        List<Long> teamA = liveMembers(step.assignments.get(TEAM_A), sim);
        List<Long> teamB = liveMembers(step.assignments.get(TEAM_B), sim);
        if (teamA.isEmpty() || teamB.isEmpty()) {
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
                String nextOverwatch = overwatchTeam(squad.boundingPhase + 1);
                List<Long> suppressors = TEAM_A.equals(nextOverwatch) ? teamA : teamB;
                List<Long> bounders = TEAM_A.equals(nextOverwatch) ? teamB : teamA;
                if (!beginPhase(squad, sim, suppressors, bounders,
                        squad.boundingPhase + 1, threat)) {
                    squad.clearBoundingOverwatch();
                    squad.boundingAttemptTick = sim.getSimTickIndex();
                    return false;
                }
            }

            if (!squad.boundingActive) {
                if (squad.boundingAttemptTick == sim.getSimTickIndex()) return false;
                if (!beginPhase(squad, sim, teamA, teamB, 0, threat)) return false;
            }
            state = new BoundingState(squad.boundingPhase, squad.boundingThreatId,
                    squad.boundingMemberIds, squad.boundingTargetXs, squad.boundingTargetYs);
        }

        String overwatch = overwatchTeam(state.phase);
        if (overwatch.equals(memberTeam)) {
            holdOverwatch(member, state.threat, sim);
            return true;
        }
        return moveBounder(member, state, sim);
    }

    private boolean beginPhase(Squad squad, BattleControl sim,
                               List<Long> suppressors, List<Long> bounders,
                               int phase, long threat) {
        squad.boundingAttemptTick = sim.getSimTickIndex();
        if (!hasFiringMember(suppressors, threat, sim)) return false;

        int[] stride = nextStrideCell(squad, phase > 0);
        if (stride == null) return false;
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
                && sim.getGrid().hasLineOfSight(sim.world().cellX(member), sim.world().cellY(member),
                sim.world().cellX(threat), sim.world().cellY(threat));
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

    private static String overwatchTeam(int phase) {
        return (phase & 1) == 0 ? TEAM_A : TEAM_B;
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

    private static void clearBounding(Squad squad) {
        if (!squad.boundingActive && squad.boundingThreatId == 0L) return;
        squad.clearBoundingOverwatch();
    }

    private record BoundingState(int phase, long threat,
                                 long[] memberIds, int[] targetXs, int[] targetYs) {}
}
