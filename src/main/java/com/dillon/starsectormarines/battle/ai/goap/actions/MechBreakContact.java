package com.dillon.starsectormarines.battle.ai.goap.actions;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Squad;
import com.dillon.starsectormarines.battle.unit.Unit;
import com.dillon.starsectormarines.battle.ai.MechCombatantBehavior;
import com.dillon.starsectormarines.battle.ai.TacticalScoring;
import com.dillon.starsectormarines.battle.ai.goap.Action;
import com.dillon.starsectormarines.battle.ai.goap.ActionStatus;
import com.dillon.starsectormarines.battle.ai.goap.WorldState;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;

/**
 * <b>Mech squad posture: pull back to cover, keep firing all three weapon
 * tracks.</b> The mech sibling of {@link BreakContact} — same fallback-cell
 * picker and pathing flow, but opportunistic fire routes through
 * {@link MechCombatantBehavior#tryFireMechWeapons} so the chaingun / SRM /
 * LRM tracks all stay live during the retreat. A mech withdrawing under fire
 * still has a long-range artillery card to play even when the chassis is
 * past LRM-range-only.
 *
 * <p>Runs perpetually ({@link ActionStatus#RUNNING}); the squad-level
 * 2-second replan re-evaluates whether MORALE_BROKEN still holds. Once
 * mech-side morale recovers past
 * {@link com.dillon.starsectormarines.battle.squad.SquadMoraleSystem#MECH_MORALE_CLEAR_THRESHOLD} the per-mech flags
 * clear, the squad aggregator drops {@link Squad#moraleBroken}, and
 * {@code MechSurviveContact} goes inactive — the squad falls back to its
 * role goal (or {@code MechEliminateEnemies} as the engagement floor).
 *
 * <p>Empty preconditions/effects — only emitted by
 * {@code MechSurviveContact}'s customPlan.
 */
public final class MechBreakContact implements Action {

    public static final MechBreakContact INSTANCE = new MechBreakContact();

    private MechBreakContact() {}

    @Override public String name() { return "MechBreakContact"; }
    @Override public WorldState preconditions() { return WorldState.EMPTY; }
    @Override public WorldState effects() { return WorldState.EMPTY; }
    @Override public float cost(WorldState s, Squad squad, BattleSimulation sim) { return 1f; }
    @Override public int requiredMembers() { return 1; }

    @Override
    public ActionStatus execute(Unit member, Squad squad, BattleSimulation sim) {
        if (sim.getTacticalScoring().fallbackDestinationNeedsRefresh(member)) {
            int[] dest = sim.getTacticalScoring().findFallbackPosition(member);
            member.fallbackCellX = dest[0];
            member.fallbackCellY = dest[1];
        }

        boolean atDest = member.getCellX() == member.fallbackCellX
                      && member.getCellY() == member.fallbackCellY;
        if (!atDest) {
            opportunisticMechFire(member, sim);
            if (member.getMoveProgress() == 0f) {
                sim.setPath(member, GridPathfinder.findPath(sim.getGrid(),
                        member.getCellX(), member.getCellY(),
                        member.fallbackCellX, member.fallbackCellY,
                        sim.getOccupancyMap()));
            }
            sim.advanceMovement(member);
        } else {
            if (!member.pathEmpty()) sim.clearPath(member);
            member.setMoveProgress(0f);
            member.setRenderPos(member.getCellX(), member.getCellY());
            opportunisticMechFire(member, sim);
        }
        return ActionStatus.RUNNING;
    }

    /**
     * Pick a target (or keep the current one), then run the same three-track
     * mech fire pass {@link MechCombatantBehavior#tryFireMechWeapons} uses
     * for parity engagement. LRMs may fire without LOS at the indirect-fire
     * accuracy penalty — preserves the "arc artillery over cover while
     * pulling back" read.
     */
    private static void opportunisticMechFire(Unit u, BattleSimulation sim) {
        Unit target = sim.targetOf(u);
        if (target == null
                || !sim.getTacticalScoring().shouldKeepPursuing(u, target)) {
            target = sim.getTacticalScoring().findBestTarget(u);
            u.setTarget(target);
        }
        if (target == null) return;
        float dist = TacticalScoring.cellDistance(u.getCellX(), u.getCellY(),
                target.getCellX(), target.getCellY());
        if (dist > u.attackRange) return;
        boolean visible = sim.getGrid().hasLineOfSight(u.getCellX(), u.getCellY(),
                target.getCellX(), target.getCellY());
        MechCombatantBehavior.tryFireMechWeapons(u, target, dist, sim, visible);
    }
}
