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
        } else if (executeBounding(member, squad, sim, destX, destY)) {
            return ActionStatus.RUNNING;
        }

        if (holdsForQuietEchelon(member, squad, sim, destX, destY)) {
            if (!Paths.isEmpty(sim.world().path(member))) sim.clearPath(member);
            return ActionStatus.RUNNING;
        }
        advanceIntoZone(member, squad, sim, destX, destY, true);
        return ActionStatus.RUNNING;
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

}
