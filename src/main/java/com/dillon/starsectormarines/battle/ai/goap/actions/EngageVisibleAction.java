package com.dillon.starsectormarines.battle.ai.goap.actions;

import com.dillon.starsectormarines.battle.BattleSimulation;
import com.dillon.starsectormarines.battle.MapTurret;
import com.dillon.starsectormarines.battle.Squad;
import com.dillon.starsectormarines.battle.Unit;
import com.dillon.starsectormarines.battle.ai.InfantryCombatantBehavior;
import com.dillon.starsectormarines.battle.ai.TacticalScoring;
import com.dillon.starsectormarines.battle.ai.goap.Action;
import com.dillon.starsectormarines.battle.ai.goap.ActionStatus;
import com.dillon.starsectormarines.battle.ai.goap.Predicate;
import com.dillon.starsectormarines.battle.ai.goap.WorldState;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;

/**
 * Stage 1 parity action: per-member, run the full
 * {@link InfantryCombatantBehavior} body — fire when in range with LOS,
 * otherwise path toward a firing position (with cohesion override inline).
 *
 * <p>Precondition is the squad-wide "someone has LOS and someone is in
 * range"; planner picks this action whenever the squad can engage. Per-member
 * execution still handles the out-of-range case so squadmates who personally
 * lack LOS continue advancing — matches the pre-GOAP behavior where in-range
 * marines fired while out-of-range marines moved on the same tick.
 *
 * <p>Always returns {@link ActionStatus#RUNNING} during normal engagement;
 * the plan only invalidates when the target evaporates ({@link ActionStatus#FAILURE})
 * and otherwise advances on replan triggers (squad-state change, periodic
 * 2-second timer) rather than per-tick.
 */
public final class EngageVisibleAction implements Action {

    public static final EngageVisibleAction INSTANCE = new EngageVisibleAction();

    private static final WorldState PRE = WorldState.EMPTY
            .with(Predicate.HAS_LOS_TO_TARGET, true)
            .with(Predicate.IN_RANGE_OF_TARGET, true);
    private static final WorldState EFF = WorldState.EMPTY
            .with(Predicate.ENEMY_DAMAGED, true);

    /** Same per-shot reposition probability as {@link InfantryCombatantBehavior}. */
    private static final float REPOSITION_CHANCE = 0.30f;

    private EngageVisibleAction() {}

    @Override public String name() { return "EngageVisible"; }
    @Override public WorldState preconditions() { return PRE; }
    @Override public WorldState effects() { return EFF; }
    @Override public float cost(WorldState s, Squad squad, BattleSimulation sim) { return 1f; }
    @Override public int requiredMembers() { return 1; }

    @Override
    public ActionStatus execute(Unit member, Squad squad, BattleSimulation sim) {
        // Refresh target if dead or missing. Lost-target → plan invalidates;
        // replan will pick a null plan if no enemies remain.
        if (member.target == null || !member.target.isAlive()) {
            member.target = TacticalScoring.findBestTarget(member, sim);
        }
        if (member.target == null) return ActionStatus.FAILURE;

        // Cooldowns tick every frame regardless of branch, just like the
        // pre-GOAP behavior — otherwise a marine sitting at range with their
        // cooldown frozen would fire instantly when LOS opened up.
        if (member.cooldownTimer > 0f) member.cooldownTimer -= BattleSimulation.TICK_DT;
        if (member.secondaryCooldownTimer > 0f) member.secondaryCooldownTimer -= BattleSimulation.TICK_DT;

        // Mid-aim: locked into the rocket animation. Tick the timer, launch
        // at the midpoint, short-circuit the rest of the action body — no
        // movement, no primary fire, no re-target.
        if (member.secondaryActionTimer > 0f && member.secondaryWeapon != null) {
            member.secondaryActionTimer -= BattleSimulation.TICK_DT;
            member.moveProgress = 0f;
            member.renderX = member.cellX;
            member.renderY = member.cellY;
            float fireAt = member.secondaryWeapon.aimDuration * 0.5f;
            if (!member.secondaryFiredThisAction && member.secondaryActionTimer <= fireAt) {
                if (member.secondaryAimTarget != null && member.secondaryAimTarget.isAlive()) {
                    sim.fireSecondary(member, member.secondaryAimTarget);
                }
                member.secondaryFiredThisAction = true;
                member.secondaryCooldownTimer = member.secondaryWeapon.cooldown;
            }
            if (member.secondaryActionTimer <= 0f) {
                member.secondaryActionTimer = 0f;
                member.secondaryAimTarget = null;
            }
            return ActionStatus.RUNNING;
        }

        float dist = TacticalScoring.cellDistance(member.cellX, member.cellY,
                member.target.cellX, member.target.cellY);
        boolean inRange = dist <= member.attackRange;
        boolean visible = sim.getGrid().hasLineOfSight(member.cellX, member.cellY,
                member.target.cellX, member.target.cellY);

        if (inRange && visible) {
            boolean startedSecondary = false;
            if (member.secondaryWeapon != null && member.secondaryAmmo > 0
                    && member.secondaryCooldownTimer <= 0f
                    && member.target instanceof MapTurret
                    && dist <= member.secondaryWeapon.range) {
                member.secondaryActionTimer = member.secondaryWeapon.aimDuration;
                member.secondaryFiredThisAction = false;
                member.secondaryAimTarget = member.target;
                startedSecondary = true;
            }
            if (!startedSecondary && member.cooldownTimer <= 0f) {
                sim.fireShot(member, member.target);
                member.cooldownTimer = member.attackCooldown;
                if (member.primaryWeapon != null && member.primaryWeapon.burstCount > 1) {
                    member.burstRemaining = member.primaryWeapon.burstCount - 1;
                    member.burstTimer = member.primaryWeapon.burstSpacing;
                    member.burstTarget = member.target;
                }
                if (sim.getRng().nextFloat() < REPOSITION_CHANCE) {
                    int[] firingPos = TacticalScoring.findFiringPosition(member, member.target, sim,
                            member.cellX, member.cellY);
                    if (firingPos[0] != member.cellX || firingPos[1] != member.cellY) {
                        sim.setPath(member, GridPathfinder.findPath(sim.getGrid(),
                                member.cellX, member.cellY, firingPos[0], firingPos[1],
                                sim.getOccupancyMap()));
                    }
                }
            }
            if (member.pathIdx < member.pathCellCount()) {
                sim.advanceMovement(member);
            } else {
                member.moveProgress = 0f;
                member.renderX = member.cellX;
                member.renderY = member.cellY;
            }
        } else {
            // Out of range or no LOS — cohesion override first, otherwise
            // path to a firing position. Per-member; the squad-wide WorldState
            // can claim "HAS_LOS_TO_TARGET" because *some* squadmate has LOS.
            if (member.moveProgress == 0f) {
                int[] dest = InfantryCombatantBehavior.cohesionOverride(member, sim);
                if (dest == null) dest = TacticalScoring.findFiringPosition(member, member.target, sim);
                sim.setPath(member, GridPathfinder.findPath(sim.getGrid(),
                        member.cellX, member.cellY, dest[0], dest[1], sim.getOccupancyMap()));
            }
            sim.advanceMovement(member);
        }

        return ActionStatus.RUNNING;
    }
}
