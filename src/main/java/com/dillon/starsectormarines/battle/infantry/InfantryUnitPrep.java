package com.dillon.starsectormarines.battle.infantry;
import com.dillon.starsectormarines.battle.decision.TacticalScoring;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.unit.Unit;

import java.util.ArrayList;

/**
 * Per-tick lifecycle housekeeping for the GOAP infantry dispatcher. Called
 * once by {@code GoapInfantryBehavior.prepareForAction} before delegating to
 * the plan's current action, so:
 * <ul>
 *   <li>Cooldowns tick during move + cohere just as they do during fire.</li>
 *   <li>A mid-aim marine doesn't get stuck in animation when the plan flips
 *       off {@code EngagePosture}.</li>
 *   <li>Per-action bodies stay focused on intent (fire / move / cohere) rather
 *       than re-implementing the same prep logic in every action.</li>
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
    public static boolean tickAimAndShortCircuit(Unit unit, BattleControl sim) {
        if (unit.getSecondaryActionTimer() <= 0f || unit.secondaryWeapon == null) return false;
        unit.setSecondaryActionTimer(unit.getSecondaryActionTimer() - BattleSimulation.TICK_DT);
        unit.setMoveProgress(0f);
        unit.setRenderPos(unit.getCellX(), unit.getCellY());
        float fireAt = unit.secondaryWeapon.aimDuration * 0.5f;
        if (!unit.secondaryFiredThisAction && unit.getSecondaryActionTimer() <= fireAt) {
            Unit aimTarget = sim.resolveUnit(unit.getSecondaryAimTargetId());
            if (aimTarget != null) {
                sim.fireSecondary(unit, aimTarget);
            }
            unit.secondaryFiredThisAction = true;
            unit.setSecondaryCooldownTimer(unit.secondaryWeapon.cooldown);
        }
        if (unit.getSecondaryActionTimer() <= 0f) {
            unit.setSecondaryActionTimer(0f);
            unit.setSecondaryAimTargetId(0L);
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
        if (unit.getCooldownTimer() > 0f) unit.setCooldownTimer(unit.getCooldownTimer() - BattleSimulation.TICK_DT);
        if (unit.getSecondaryCooldownTimer() > 0f) unit.setSecondaryCooldownTimer(unit.getSecondaryCooldownTimer() - BattleSimulation.TICK_DT);
        if (unit.getRepositionCooldown() > 0f) unit.setRepositionCooldown(unit.getRepositionCooldown() - BattleSimulation.TICK_DT);
    }

    /**
     * Reactive rocket-fire on a hardened-target-of-opportunity. When the unit
     * is mid-pathing (any posture — approach, regroup, even the engage
     * out-of-range fallback), has a loaded rocket and an idle aim, and an
     * enemy hardened target ({@link TacticalScoring#isHardened} — turrets,
     * drone hubs, heavy mechs) sits inside rocket range with LOS, this
     * initiates the aim window. The aim animation freezes movement (handled
     * by {@link #tickAimAndShortCircuit} on subsequent ticks); fire resolves
     * at the aim midpoint.
     *
     * <p>The squad-coordination gate ({@link TacticalScoring#shouldCommitRocket})
     * is what prevents the 4-marine volley failure: once one squadmate locks
     * onto a hardened target, the projected damage projection blocks the rest
     * from committing until the projection no longer kills.
     *
     * <p>Returns {@code true} when an aim was started (caller short-circuits the
     * rest of its tick — same convention as {@link #tickAimAndShortCircuit}).
     * Returns {@code false} when nothing changed.
     */
    public static boolean tryOpportunityRocket(Unit unit, BattleView sim) {
        if (unit.secondaryWeapon == null) return false;
        if (unit.secondaryAmmo <= 0) return false;
        if (unit.getSecondaryCooldownTimer() > 0f) return false;
        if (unit.getSecondaryActionTimer() > 0f) return false;

        float range = unit.secondaryWeapon.range;
        // Hardened-target scan: any MapTurret, DroneHubUnit, or HEAVY_MECH in
        // rocket range with LoS that the squad-coordination gate doesn't
        // block. Closest one wins — tilts toward turrets / hubs (typically
        // closer in a defensive posture) while still letting a near mech
        // earn the shot if it's the nearest hardened threat.
        Unit bestHardened = null;
        float bestDistSq = Float.MAX_VALUE;
        ArrayList<Unit> scratch = new ArrayList<>();
        sim.getUnitIndex().gather(unit.getCellX(), unit.getCellY(), range, scratch);
        for (int i = 0, n = scratch.size(); i < n; i++) {
            Unit other = scratch.get(i);
            if (!TacticalScoring.isHardened(other)) continue;
            if (!other.isAlive()) continue;
            if (other.faction == unit.faction) continue;
            float dx = other.getCellX() - unit.getCellX();
            float dy = other.getCellY() - unit.getCellY();
            float d2 = dx * dx + dy * dy;
            if (d2 > range * range) continue;
            if (d2 >= bestDistSq) continue;
            if (!sim.getGrid().hasLineOfSight(unit.getCellX(), unit.getCellY(), other.getCellX(), other.getCellY())) continue;
            if (!sim.getTacticalScoring().shouldCommitRocket(unit, other)) continue;
            bestHardened = other;
            bestDistSq = d2;
        }
        if (bestHardened == null) return false;

        unit.setSecondaryActionTimer(unit.secondaryWeapon.aimDuration);
        unit.secondaryFiredThisAction = false;
        unit.setSecondaryAimTarget(bestHardened);
        // Freeze movement state for this tick — the next tick's
        // tickAimAndShortCircuit will keep doing it. Mirrors what that method
        // does on its own entry path so the visible behavior is consistent
        // from the first frame of the aim window.
        unit.setMoveProgress(0f);
        unit.setRenderPos(unit.getCellX(), unit.getCellY());
        return true;
    }
}
