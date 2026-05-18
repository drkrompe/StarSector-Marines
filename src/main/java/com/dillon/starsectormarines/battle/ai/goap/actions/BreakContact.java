package com.dillon.starsectormarines.battle.ai.goap.actions;

import com.dillon.starsectormarines.battle.BattleSimulation;
import com.dillon.starsectormarines.battle.Squad;
import com.dillon.starsectormarines.battle.Unit;
import com.dillon.starsectormarines.battle.ai.TacticalScoring;
import com.dillon.starsectormarines.battle.ai.goap.Action;
import com.dillon.starsectormarines.battle.ai.goap.ActionStatus;
import com.dillon.starsectormarines.battle.ai.goap.WorldState;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.weapons.FireStance;

/**
 * <b>Squad posture: break contact and hold in cover.</b> Each member picks an
 * out-of-LOS, cover-biased cell via
 * {@link TacticalScoring#findFallbackPosition}, paths there while firing on
 * the move (MOVING penalty), and on arrival holds position firing stanced at
 * anything that drifts into LOS. The destination is cached on
 * {@link Unit#fallbackCellX}/{@link Unit#fallbackCellY} — historically shared
 * with the per-unit {@code FallbackBehavior}, which now skips GOAP-driven
 * units (squad members without a mech loadout), leaving these fields owned
 * by BreakContact for marines and other infantry squad members.
 *
 * <p>Runs perpetually ({@link ActionStatus#RUNNING}). A below-half-strength
 * squad doesn't recover above half, so {@link
 * com.dillon.starsectormarines.battle.ai.goap.goals.SurviveContact} stays
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
        if (needsNewDestination(member, sim)) {
            int[] dest = TacticalScoring.findFallbackPosition(member, sim);
            member.fallbackCellX = dest[0];
            member.fallbackCellY = dest[1];
        }

        boolean atDest = member.cellX == member.fallbackCellX
                      && member.cellY == member.fallbackCellY;
        if (!atDest) {
            // Transit — opportunistic suppression while pulling back.
            opportunisticFire(member, sim, FireStance.MOVING);
            if (member.moveProgress == 0f) {
                sim.setPath(member, GridPathfinder.findPath(sim.getGrid(),
                        member.cellX, member.cellY,
                        member.fallbackCellX, member.fallbackCellY,
                        sim.getOccupancyMap()));
            }
            sim.advanceMovement(member);
        } else {
            // In position — hold and fire stanced at anything that drifts in.
            if (!member.pathEmpty()) sim.clearPath(member);
            member.moveProgress = 0f;
            member.renderX = member.cellX;
            member.renderY = member.cellY;
            opportunisticFire(member, sim, FireStance.STANCED);
        }
        return ActionStatus.RUNNING;
    }

    /**
     * True when the cached destination is unset or — while still in transit —
     * has become visible to an enemy (a threat repositioned). Once the member
     * has actually arrived, the cell sticks even if LoS opens up: this is the
     * "we paid the cost to get here, now hold" rule. Firing from cover with
     * partial LoS is preferable to scampering between cells under fire.
     */
    private static boolean needsNewDestination(Unit member, BattleSimulation sim) {
        if (member.fallbackCellX < 0) return true;
        boolean atDest = member.cellX == member.fallbackCellX
                      && member.cellY == member.fallbackCellY;
        if (atDest) return false;
        return !TacticalScoring.isHiddenFromAllEnemies(
                member, member.fallbackCellX, member.fallbackCellY, sim);
    }

    /**
     * One-shot fire pass: pick a target, fire if in LOS + range with cooldown
     * ready. Mirrors {@code HoldPortalCordon.opportunisticFire} structurally —
     * shared lift if a fourth caller shows up; for now duplication is cheaper
     * than another helper class.
     */
    private static void opportunisticFire(Unit member, BattleSimulation sim, FireStance stance) {
        if (member.target == null
                || !member.target.isAlive()
                || !TacticalScoring.shouldKeepPursuing(member, member.target, sim)) {
            member.target = TacticalScoring.findBestTarget(member, sim);
        }
        if (member.target == null || member.cooldownTimer > 0f) return;
        float d = TacticalScoring.cellDistance(member.cellX, member.cellY,
                member.target.cellX, member.target.cellY);
        if (d > member.attackRange) return;
        if (!sim.getGrid().hasLineOfSight(member.cellX, member.cellY,
                member.target.cellX, member.target.cellY)) return;
        sim.fireShot(member, member.target, stance);
        member.cooldownTimer = member.attackCooldown;
        if (member.primaryWeapon != null && member.primaryWeapon.burstCount > 1) {
            member.burstRemaining = member.primaryWeapon.burstCount - 1;
            member.burstTimer = member.primaryWeapon.burstSpacing;
            member.burstTarget = member.target;
        }
    }
}
