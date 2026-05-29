package com.dillon.starsectormarines.battle.decision.goap.action;

import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.Unit;
import com.dillon.starsectormarines.battle.decision.TacticalScoring;
import com.dillon.starsectormarines.battle.decision.goap.Action;
import com.dillon.starsectormarines.battle.decision.goap.ActionStatus;
import com.dillon.starsectormarines.battle.decision.goap.WorldState;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.combat.FireStance;

/**
 * <b>Squad posture: break contact and hold in cover.</b> Each member picks an
 * out-of-LOS, cover-biased cell via
 * {@link TacticalScoring#findFallbackPosition}, paths there while firing on
 * the move (MOVING penalty), and on arrival holds position firing stanced at
 * anything that drifts into LOS. The destination is cached on
 * {@link Unit#getFallbackCellX()}/{@link Unit#getFallbackCellY()} — historically shared
 * with the per-unit {@code FallbackBehavior}, which now skips GOAP-driven
 * units (squad members without a mech loadout), leaving these fields owned
 * by BreakContact for marines and other infantry squad members.
 *
 * <p>Runs perpetually ({@link ActionStatus#RUNNING}). A below-half-strength
 * squad doesn't recover above half, so {@link
 * com.dillon.starsectormarines.battle.infantry.SurviveContact} stays
 * relevant for the rest of the battle; periodic replan (2s) gives the squad
 * a fresh BreakContact step each cycle, which is when destinations would be
 * re-evaluated if a member's cached cell has been compromised.
 *
 * <p>Empty preconditions/effects — not used by the backward-chaining planner.
 * Only emitted by SurviveContact's customPlan.
 */
public final class BreakContact implements Action {

    public static final BreakContact INSTANCE = new BreakContact();

    private BreakContact() {}

    @Override public String name() { return "BreakContact"; }
    @Override public WorldState preconditions() { return WorldState.EMPTY; }
    @Override public WorldState effects() { return WorldState.EMPTY; }
    @Override public float cost(WorldState s, Squad squad, BattleSimulation sim) { return 1f; }
    @Override public int requiredMembers() { return 1; }

    @Override
    public ActionStatus execute(Unit member, Squad squad, BattleSimulation sim) {
        if (sim.getTacticalScoring().fallbackDestinationNeedsRefresh(member)) {
            int[] dest = sim.getTacticalScoring().findFallbackPosition(member);
            member.setFallbackCell(dest[0], dest[1]);
        }

        boolean atDest = member.getCellX() == member.getFallbackCellX()
                      && member.getCellY() == member.getFallbackCellY();
        if (!atDest) {
            // Transit — opportunistic suppression while pulling back.
            opportunisticFire(member, sim, FireStance.MOVING);
            if (member.getMoveProgress() == 0f) {
                sim.setPath(member, GridPathfinder.findPath(sim.getGrid(),
                        member.getCellX(), member.getCellY(),
                        member.getFallbackCellX(), member.getFallbackCellY(),
                        sim.getOccupancyMap()));
            }
            sim.advanceMovement(member);
        } else {
            // In position — hold and fire stanced at anything that drifts in.
            if (!member.pathEmpty()) sim.clearPath(member);
            member.setMoveProgress(0f);
            member.setRenderPos(member.getCellX(), member.getCellY());
            opportunisticFire(member, sim, FireStance.STANCED);
        }
        return ActionStatus.RUNNING;
    }

    /**
     * One-shot fire pass: pick a target, fire if in LOS + range with cooldown
     * ready. Mirrors {@code HoldPortalCordon.opportunisticFire} structurally —
     * shared lift if a fourth caller shows up; for now duplication is cheaper
     * than another helper class.
     */
    private static void opportunisticFire(Unit member, BattleControl sim, FireStance stance) {
        Unit target = sim.targetOf(member);
        if (target == null
                || !sim.getTacticalScoring().shouldKeepPursuing(member, target)) {
            target = sim.getTacticalScoring().findBestTarget(member);
            member.setTarget(target);
        }
        if (target == null || member.getCooldownTimer() > 0f) return;
        float d = TacticalScoring.cellDistance(member.getCellX(), member.getCellY(),
                target.getCellX(), target.getCellY());
        if (d > member.getAttackRange()) return;
        if (!sim.getGrid().hasLineOfSight(member.getCellX(), member.getCellY(),
                target.getCellX(), target.getCellY())) return;
        sim.fireShot(member, target, stance);
        member.setCooldownTimer(member.attackCooldown);
        member.beginBurst(target);
    }
}
