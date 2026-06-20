package com.dillon.starsectormarines.battle.infantry;
import com.dillon.starsectormarines.battle.decision.TacticalScoring;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.battle.unit.Entity;

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
    public static boolean tickAimAndShortCircuit(Entity unit, BattleControl sim) {
        World w = sim.world();
        long id = unit.entityId;
        // Presence-gate before any SECONDARY_WEAPON read: a unit without the
        // capability lacks the component (the timer read would fail loud).
        if (!w.hasSecondaryWeapon(id) || w.secondaryActionTimer(id) <= 0f) return false;
        MarineSecondary sec = w.secondaryWeapon(id);
        w.setSecondaryActionTimer(id, w.secondaryActionTimer(id) - BattleSimulation.TICK_DT);
        w.setMoveProgress(id, 0f);
        unit.setRenderPos(w.cellX(id), w.cellY(id));
        float fireAt = sec.aimDuration * 0.5f;
        if (!w.secondaryFired(id) && w.secondaryActionTimer(id) <= fireAt) {
            Entity aimTarget = sim.resolveUnit(w.secondaryAimTargetId(id));
            if (aimTarget != null) {
                sim.fireSecondary(unit, aimTarget);
            }
            w.setSecondaryFired(id, true);
            w.setSecondaryCooldownTimer(id, sec.cooldown);
        }
        if (w.secondaryActionTimer(id) <= 0f) {
            w.setSecondaryActionTimer(id, 0f);
            w.setSecondaryAimTargetId(id, 0L);
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
    public static void tickCooldowns(Entity unit, World world) {
        long id = unit.entityId;
        float cd = world.cooldownTimer(id);
        if (cd > 0f) world.setCooldownTimer(id, cd - BattleSimulation.TICK_DT);
        if (world.hasSecondaryWeapon(id)) {
            float scd = world.secondaryCooldownTimer(id);
            if (scd > 0f) world.setSecondaryCooldownTimer(id, scd - BattleSimulation.TICK_DT);
        }
        float rcd = world.repositionCooldown(id);
        if (rcd > 0f) world.setRepositionCooldown(id, rcd - BattleSimulation.TICK_DT);
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
    public static boolean tryOpportunityRocket(Entity unit, BattleView sim) {
        long id = unit.entityId;
        if (!sim.world().hasSecondaryWeapon(id)) return false;
        if (sim.world().secondaryAmmo(id) <= 0) return false;
        if (sim.world().secondaryCooldownTimer(id) > 0f) return false;
        if (sim.world().secondaryActionTimer(id) > 0f) return false;

        MarineSecondary sec = sim.world().secondaryWeapon(id);
        float range = sec.range;
        // Hardened-target scan: any MapTurret, DroneHubUnit, or HEAVY_MECH in
        // rocket range with LoS that the squad-coordination gate doesn't
        // block. Closest one wins — tilts toward turrets / hubs (typically
        // closer in a defensive posture) while still letting a near mech
        // earn the shot if it's the nearest hardened threat.
        Entity bestHardened = null;
        float bestDistSq = Float.MAX_VALUE;
        ArrayList<Entity> scratch = new ArrayList<>();
        sim.getUnitIndex().gather(sim.world().cellX(unit.entityId), sim.world().cellY(unit.entityId), range, scratch);
        for (int i = 0, n = scratch.size(); i < n; i++) {
            Entity other = scratch.get(i);
            if (!TacticalScoring.isHardened(other)) continue;
            if (!sim.world().isAlive(other.entityId)) continue;
            if (other.faction == unit.faction) continue;
            float dx = sim.world().cellX(other.entityId) - sim.world().cellX(unit.entityId);
            float dy = sim.world().cellY(other.entityId) - sim.world().cellY(unit.entityId);
            float d2 = dx * dx + dy * dy;
            if (d2 > range * range) continue;
            if (d2 >= bestDistSq) continue;
            if (!sim.getGrid().hasLineOfSight(sim.world().cellX(unit.entityId), sim.world().cellY(unit.entityId), sim.world().cellX(other.entityId), sim.world().cellY(other.entityId))) continue;
            if (!sim.getTacticalScoring().shouldCommitRocket(unit, other)) continue;
            bestHardened = other;
            bestDistSq = d2;
        }
        if (bestHardened == null) return false;

        sim.world().setSecondaryActionTimer(id, sec.aimDuration);
        sim.world().setSecondaryFired(id, false);
        sim.world().setSecondaryAimTargetId(id, Entity.idOf(bestHardened));
        // Freeze movement state for this tick — the next tick's
        // tickAimAndShortCircuit will keep doing it. Mirrors what that method
        // does on its own entry path so the visible behavior is consistent
        // from the first frame of the aim window.
        sim.world().setMoveProgress(unit.entityId, 0f);
        unit.setRenderPos(sim.world().cellX(unit.entityId), sim.world().cellY(unit.entityId));
        return true;
    }
}
