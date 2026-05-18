package com.dillon.starsectormarines.battle.ai;

import com.dillon.starsectormarines.battle.BattleSimulation;
import com.dillon.starsectormarines.battle.Unit;

/**
 * Per-tick lifecycle housekeeping shared by every infantry behavior path —
 * both the legacy {@link InfantryCombatantBehavior} and the GOAP dispatcher
 * (Stage 1 + Stage 2). Lives outside the action layer so:
 * <ul>
 *   <li>{@code GoapInfantryBehavior} can call it once before delegating to
 *       whichever action the plan picked — cooldowns tick during move + cohere
 *       just as they do during fire, and a mid-aim marine doesn't get stuck
 *       in animation when the plan flips off {@code EngagePosture}.</li>
 *   <li>Per-action bodies stay focused on intent (fire / move / cohere) rather
 *       than re-implementing the same prep logic three times.</li>
 * </ul>
 *
 * <p>Stateless; safe to call from any thread. Mutates only the passed unit.
 */
public final class InfantryUnitPrep {

    private InfantryUnitPrep() {}

    /**
     * If the unit is locked into the rocket aim animation, advances the timer,
     * launches the rocket at the aim midpoint, and returns {@code true} so the
     * caller short-circuits the rest of its update (no movement, no primary
     * fire, no re-target). Returns {@code false} when the unit is not aiming
     * and the caller should proceed normally.
     */
    public static boolean tickAimAndShortCircuit(Unit unit, BattleSimulation sim) {
        if (unit.secondaryActionTimer <= 0f || unit.secondaryWeapon == null) return false;
        unit.secondaryActionTimer -= BattleSimulation.TICK_DT;
        unit.moveProgress = 0f;
        unit.renderX = unit.cellX;
        unit.renderY = unit.cellY;
        float fireAt = unit.secondaryWeapon.aimDuration * 0.5f;
        if (!unit.secondaryFiredThisAction && unit.secondaryActionTimer <= fireAt) {
            if (unit.secondaryAimTarget != null && unit.secondaryAimTarget.isAlive()) {
                sim.fireSecondary(unit, unit.secondaryAimTarget);
            }
            unit.secondaryFiredThisAction = true;
            unit.secondaryCooldownTimer = unit.secondaryWeapon.cooldown;
        }
        if (unit.secondaryActionTimer <= 0f) {
            unit.secondaryActionTimer = 0f;
            unit.secondaryAimTarget = null;
        }
        return true;
    }

    /**
     * Decrements primary and secondary cooldown timers by one sim tick.
     * Idempotent at zero — already-expired timers stay at zero. Runs every
     * tick regardless of what the unit is doing so a long approach phase
     * doesn't freeze cooldown drain (otherwise the marine arrives at firing
     * range with a stale full cooldown and a perceived response lag).
     */
    public static void tickCooldowns(Unit unit) {
        if (unit.cooldownTimer > 0f) unit.cooldownTimer -= BattleSimulation.TICK_DT;
        if (unit.secondaryCooldownTimer > 0f) unit.secondaryCooldownTimer -= BattleSimulation.TICK_DT;
    }
}
