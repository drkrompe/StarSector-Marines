package com.dillon.starsectormarines.battle.decision.goap.action;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.Unit;
import com.dillon.starsectormarines.battle.decision.goap.ActionStatus;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.zone.NavigationZone;

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
    public ActionStatus execute(Unit member, Squad squad, BattleSimulation sim) {
        if (memberInZone(member, sim)) {
            return ActionStatus.SUCCESS;
        }
        advanceIntoZone(member, squad, sim, destX, destY, true);
        return ActionStatus.RUNNING;
    }
}
