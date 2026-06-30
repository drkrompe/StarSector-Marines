package com.dillon.starsectormarines.battle.decision.goap.action;

import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.Entity;
import com.dillon.starsectormarines.battle.decision.TacticalScoring;
import com.dillon.starsectormarines.battle.decision.goap.Action;
import com.dillon.starsectormarines.battle.decision.goap.ActionStatus;
import com.dillon.starsectormarines.battle.decision.goap.WorldState;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.nav.Paths;
import com.dillon.starsectormarines.battle.combat.FireStance;

/**
 * <b>Squad posture: break contact and hold in cover.</b> Each member picks an
 * out-of-LOS, cover-biased cell via
 * {@link TacticalScoring#findFallbackPosition}, paths there while firing on
 * the move (MOVING penalty), and on arrival holds position firing stanced at
 * anything that drifts into LOS. The destination is cached in the AI_STATE
 * fall-back cell ({@code world.fallbackCellX(id)}/{@code world.fallbackCellY(id)})
 * — historically shared with the per-unit {@code FallbackBehavior}, which now
 * skips GOAP-driven units (squad members without a mech loadout), leaving that
 * column owned by BreakContact for marines and other infantry squad members.
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
    @Override public float cost(WorldState s, Squad squad, BattleView sim) { return 1f; }
    @Override public int requiredMembers() { return 1; }

    @Override
    public ActionStatus execute(Entity member, Squad squad, BattleControl sim) {
        if (sim.getTacticalScoring().fallbackDestinationNeedsRefresh(member)) {
            int[] dest = sim.getTacticalScoring().findFallbackPosition(member);
            sim.world().setFallbackCell(member.entityId, dest[0], dest[1]);
        }

        boolean atDest = sim.world().cellX(member.entityId) == sim.world().fallbackCellX(member.entityId)
                      && sim.world().cellY(member.entityId) == sim.world().fallbackCellY(member.entityId);
        if (!atDest) {
            // Transit — opportunistic suppression while pulling back.
            opportunisticFire(member, sim, FireStance.MOVING);
            if (sim.world().moveProgress(member.entityId) == 0f) {
                sim.setPath(member, GridPathfinder.findPath(sim.getGrid(),
                        sim.world().cellX(member.entityId), sim.world().cellY(member.entityId),
                        sim.world().fallbackCellX(member.entityId), sim.world().fallbackCellY(member.entityId),
                        sim.getOccupancyMap()));
            }
            sim.advanceMovement(member);
        } else {
            // In position — hold and fire stanced at anything that drifts in.
            if (!Paths.isEmpty(sim.world().path(member.entityId))) sim.clearPath(member);
            sim.world().setMoveProgress(member.entityId, 0f);
            sim.world().setRenderPos(member.entityId, sim.world().cellX(member.entityId), sim.world().cellY(member.entityId));
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
    private static void opportunisticFire(Entity member, BattleControl sim, FireStance stance) {
        Entity target = sim.targetOf(member);
        if (target == null
                || !sim.getTacticalScoring().shouldKeepPursuing(member, target)) {
            target = sim.getTacticalScoring().findBestTarget(member);
            sim.world().setTargetId(member.entityId, Entity.idOf(target));
        }
        if (target == null || sim.world().cooldownTimer(member.entityId) > 0f) return;
        float d = TacticalScoring.cellDistance(sim.world().cellX(member.entityId), sim.world().cellY(member.entityId),
                sim.world().cellX(target.entityId), sim.world().cellY(target.entityId));
        if (d > sim.world().attackRange(member.entityId)) return;
        if (!sim.getGrid().hasLineOfSight(sim.world().cellX(member.entityId), sim.world().cellY(member.entityId),
                sim.world().cellX(target.entityId), sim.world().cellY(target.entityId))) return;
        sim.fireShot(member, target, stance);
        sim.combat().setCooldownTimer(member.entityId, sim.combat().attackCooldown(member.entityId));
        member.beginBurst(sim.combat(), target);
    }
}
