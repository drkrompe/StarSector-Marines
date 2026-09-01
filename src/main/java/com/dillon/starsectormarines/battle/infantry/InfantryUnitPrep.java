package com.dillon.starsectormarines.battle.infantry;
import com.dillon.starsectormarines.battle.decision.TacticalScoring;
import com.dillon.starsectormarines.battle.combat.FireStance;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.battle.unit.LongBucket;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.marine.SpecialActivation;
import com.dillon.starsectormarines.marine.SpecialAiPolicy;
import com.dillon.starsectormarines.marine.SpecialEquipmentDef;


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
     * If the unit is locked into a special-equipment aim animation, advances
     * the timer, fires at the aim midpoint, and returns {@code true} so the
     * caller short-circuits the rest of its update (no movement, no primary
     * fire, no re-target). Returns {@code false} when the unit is not aiming
     * and the caller should proceed normally.
     */
    public static boolean tickAimAndShortCircuit(long unit, BattleControl sim) {
        World w = sim.world();
        long id = unit;
        // Presence-gate before any SECONDARY_WEAPON read: a unit without the
        // capability lacks the component (the timer read would fail loud).
        if (!w.hasSecondaryWeapon(id) || w.secondaryActionTimer(id) <= 0f) return false;
        SpecialEquipmentDef sec = w.specialEquipment(id);
        if (sec.activation() == SpecialActivation.UTILITY_SMOKE) {
            return tickSmokeThrow(unit, sec, sim);
        }
        if (sec.activation() == SpecialActivation.UTILITY_SATCHEL) {
            return tickSatchelPlant(unit, sec, sim);
        }
        if (sec.activation() == SpecialActivation.ARC_EXPLOSIVE) {
            return tickFragThrow(unit, sec, sim);
        }
        if (sec.activation() == SpecialActivation.UTILITY_DEPLOYABLE) {
            return DeployableTactics.tickPlacement(unit, sec, sim);
        }
        if (sec.activation() == SpecialActivation.CLOSE_CONTACT) {
            return CloseContactTactics.tickChannel(unit, sec, sim);
        }
        w.setSecondaryActionTimer(id, w.secondaryActionTimer(id) - BattleSimulation.TICK_DT);
        float fireAt = sec.aimDuration() * 0.5f;
        if (!w.secondaryFired(id) && w.secondaryActionTimer(id) <= fireAt) {
            long aimTarget = sim.resolveUnit(w.secondaryAimTargetId(id));
            if (legalSpecialShot(unit, aimTarget, sec, sim)) {
                sim.fireSecondary(unit, aimTarget);
                w.setSecondaryCooldownTimer(id, sec.cooldown());
            }
            w.setSecondaryFired(id, true);
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
    public static void tickCooldowns(long unit, World world) {
        long id = unit;
        float cd = world.cooldownTimer(id);
        if (cd > 0f) world.setCooldownTimer(id, cd - BattleSimulation.TICK_DT);
        if (world.hasSecondaryWeapon(id)) {
            float scd = world.secondaryCooldownTimer(id);
            if (scd > 0f) world.setSecondaryCooldownTimer(id, scd - BattleSimulation.TICK_DT);
        }
        float rcd = world.repositionCooldown(id);
        if (rcd > 0f) world.setRepositionCooldown(id, rcd - BattleSimulation.TICK_DT);
        float sidestep = world.sidestepTimer(id);
        if (sidestep > 0f) world.setSidestepTimer(id, sidestep - BattleSimulation.TICK_DT);
    }

    /**
     * Authors a primary-weapon shot when the active action did not already
     * choose one and a visible enemy is inside attack range. This is the
     * common safety net for movement/regroup/replan branches: individual
     * actions retain first choice of target and stance, while an uncovered
     * branch no longer makes a defender ignore a free shot.
     *
     * <p>The helper deliberately does not replace the unit's pursuit target.
     * A passing shot should not pull a patrol, room-clear, or fallback path
     * away from its objective. {@link FireStance#stanceFor(boolean)} is sampled
     * after the action ran, so a unit that advanced this tick receives the
     * moving-fire penalty and a planted unit receives the stanced roll.
     * Cooldown/range/LOS are revalidated by the consume-once firing system.
     */
    public static boolean tryOpportunityPrimary(long unit, BattleView sim) {
        if (sim.combat().fireTargetId(unit) != 0L) return false;
        long target = sim.getTacticalScoring().closestEnemyInAttackRange(unit,
                sim.combat().reflexTargetId(unit),
                TacticalScoring.OPPORTUNITY_RETARGET_DISTANCE_MARGIN);
        if (target == 0L) return false;
        sim.combat().setFireIntent(unit, target,
                FireStance.stanceFor(!sim.movement().settled(unit)), false);
        return true;
    }

    /**
     * Reactive special-equipment use against a hardened target of opportunity.
     * Direct-fire equipment begins its ordinary aim when a legal target is in
     * range. A satchel is stricter: it considers only a target already inside
     * contact range, creates no approach path, and channels the plant through
     * the same movement-freezing action window. Close-contact tools are
     * stricter still and delegate to {@link CloseContactTactics}, which reaches
     * only across a crossable cell boundary.
     *
     * <p>For direct-fire equipment, the squad-coordination gate
     * ({@link TacticalScoring#shouldCommitSpecial})
     * is what prevents the 4-marine volley failure: once one squadmate locks
     * onto a hardened target, the projected damage projection blocks the rest
     * from committing until the projection no longer kills.
     *
     * <p>Returns {@code true} when an aim was started (caller short-circuits the
     * rest of its tick — same convention as {@link #tickAimAndShortCircuit}).
     * Satchels instead use one atomic target reservation. Returns {@code false}
     * when nothing changed.
     */
    public static boolean tryOpportunitySpecial(long unit, BattleControl sim) {
        long id = unit;
        if (!sim.world().hasSecondaryWeapon(id)) return false;
        if (sim.world().secondaryCooldownTimer(id) > 0f) return false;
        if (sim.world().secondaryActionTimer(id) > 0f) return false;

        SpecialEquipmentDef sec = sim.world().specialEquipment(id);
        if (!sec.hasAvailableUse(sim.world().secondaryAmmo(id))) return false;
        if (sec.aiPolicy() == SpecialAiPolicy.SQUAD_SMOKE_SCREEN) return false;
        if (sec.aiPolicy() == SpecialAiPolicy.CONTACT_DEMOLITION) {
            return tryOpportunitySatchel(unit, sec, sim);
        }
        if (sec.aiPolicy() == SpecialAiPolicy.SOFT_CLUSTER_INDIRECT) {
            return FragGrenadeTactics.tryCommitThrow(unit, sec, sim);
        }
        if (sec.aiPolicy() == SpecialAiPolicy.AREA_DENIAL_EMPLACEMENT) {
            return DeployableTactics.tryCommitPlacement(unit, sec, sim);
        }
        if (sec.aiPolicy() == SpecialAiPolicy.DIRECTIONAL_COVER_SCREEN) {
            return DeployableTactics.tryCommitCoverPlacement(unit, sec, sim);
        }
        if (sec.activation() == SpecialActivation.CLOSE_CONTACT) {
            return CloseContactTactics.tryCommit(unit, sec, sim);
        }
        return tryHardenedDirectFire(unit, sec, sim);
    }

    /**
     * The screen half of {@link #tryOpportunitySpecial}, reachable on its own so
     * a marine crossing ground can still get something between themselves and a
     * threat that has only just arrived.
     *
     * <p>The move-only path withholds deployables because planting one freezes
     * the carrier, and a squad's moving half must not be diverted mid-bound.
     * That reasoning holds for building a revetment at a position somebody has
     * chosen to fight from. It inverts for the two onset moments: a marine who
     * has just come face to face with somebody, or who has just been taken as a
     * target from down a lane, is in trouble <em>because</em> they are in the
     * open and moving, and the freeze is the response rather than the cost of
     * it. Same argument as the rocket at an emplacement above — the thing that
     * would divert the advance is the thing the advance is in trouble over.
     *
     * <p>Only the cover screen. A satchel, a frag, a mine and a close-contact
     * tool are all still diversions at these moments, and none of them puts
     * anything between a marine and a bearing.
     */
    public static boolean tryOnsetScreen(long unit, BattleControl sim) {
        if (!sim.world().hasSecondaryWeapon(unit)) return false;
        if (sim.world().secondaryCooldownTimer(unit) > 0f) return false;
        if (sim.world().secondaryActionTimer(unit) > 0f) return false;
        SpecialEquipmentDef sec = sim.world().specialEquipment(unit);
        if (sec.aiPolicy() != SpecialAiPolicy.DIRECTIONAL_COVER_SCREEN) return false;
        if (!sec.hasAvailableUse(sim.world().secondaryAmmo(unit))) return false;
        return DeployableTactics.tryCommitCoverPlacement(unit, sec, sim);
    }

    /**
     * The hardened-target half of {@link #tryOpportunitySpecial}, reachable on
     * its own so a move-only coordinated role can still answer a turret.
     *
     * <p>An advancing squad suppresses ordinary opportunity fire because a
     * passing shot must not divert the moving half of a bound. A rocket at an
     * emplacement is the opposite case: the emplacement is the reason the
     * advance is in trouble, and the marine carrying the only weapon that
     * meaningfully hurts it is the one being told to hold his fire. Splitting
     * the scan out lets {@link com.dillon.starsectormarines.battle.decision.goap.Action#permitsOpportunityFire}
     * keep its narrow meaning while this stays available underneath it.
     *
     * <p>Deliberately excludes the satchel, frag, deployable, and close-contact
     * policies: each of those either freezes the carrier to plant something or
     * spends a squad resource, which is a diversion in a way a direct-fire shot
     * is not.
     */
    public static boolean tryHardenedOpportunity(long unit, BattleControl sim) {
        if (!sim.world().hasSecondaryWeapon(unit)) return false;
        if (sim.world().secondaryCooldownTimer(unit) > 0f) return false;
        if (sim.world().secondaryActionTimer(unit) > 0f) return false;

        SpecialEquipmentDef sec = sim.world().specialEquipment(unit);
        if (!sec.hasAvailableUse(sim.world().secondaryAmmo(unit))) return false;
        if (sec.aiPolicy() == SpecialAiPolicy.SQUAD_SMOKE_SCREEN) return false;
        if (sec.aiPolicy() == SpecialAiPolicy.CONTACT_DEMOLITION) return false;
        if (sec.aiPolicy() == SpecialAiPolicy.SOFT_CLUSTER_INDIRECT) return false;
        if (sec.aiPolicy() == SpecialAiPolicy.AREA_DENIAL_EMPLACEMENT) return false;
        if (sec.aiPolicy() == SpecialAiPolicy.DIRECTIONAL_COVER_SCREEN) return false;
        if (sec.activation() == SpecialActivation.CLOSE_CONTACT) return false;
        return tryHardenedDirectFire(unit, sec, sim);
    }

    private static boolean tryHardenedDirectFire(long unit, SpecialEquipmentDef sec,
                                                 BattleControl sim) {
        long id = unit;
        float range = sec.range();
        // Hardened-target scan: any MapTurret, drone hub, or HEAVY_MECH in
        // special range with LoS that the squad-coordination gate doesn't
        // block. Closest one wins — tilts toward turrets / hubs (typically
        // closer in a defensive posture) while still letting a near mech
        // earn the shot if it's the nearest hardened threat.
        long bestHardened = 0L;
        float bestDistSq = Float.MAX_VALUE;
        LongBucket scratch = new LongBucket();
        sim.getUnitIndex().gather(sim.world().x(unit), sim.world().y(unit), range, scratch);
        for (int i = 0, n = scratch.size; i < n; i++) {
            long other = scratch.ids[i];
            if (!TacticalScoring.isHardened(sim.identity().type(other))) continue;
            if (!sim.world().isAlive(other)) continue;
            if (sim.identity().faction(other) == sim.identity().faction(unit)) continue;
            float dx = sim.world().x(other) - sim.world().x(unit);
            float dy = sim.world().y(other) - sim.world().y(unit);
            float d2 = dx * dx + dy * dy;
            if (d2 > range * range) continue;
            if (d2 >= bestDistSq) continue;
            if (!sim.getTacticalScoring().hasClearShot(unit, other)) continue;
            if (!sim.getTacticalScoring().shouldCommitSpecial(unit, other)) continue;
            bestHardened = other;
            bestDistSq = d2;
        }
        if (bestHardened == 0L) return false;

        sim.world().setSecondaryActionTimer(id, sec.aimDuration());
        sim.world().setSecondaryFired(id, false);
        sim.world().setSecondaryAimTargetId(id, bestHardened);
        return true;
    }

    /** Compatibility name retained for focused rocket behavior tests. */
    public static boolean tryOpportunityRocket(long unit, BattleControl sim) {
        return tryOpportunitySpecial(unit, sim);
    }

    private static boolean legalSpecialShot(long unit, long target,
                                            SpecialEquipmentDef special,
                                            BattleView sim) {
        if (target == 0L || !sim.isHardenedTarget(target)) {
            return false;
        }
        if (!sim.world().isAlive(target)
                || sim.identity().faction(target) == sim.identity().faction(unit)) {
            return false;
        }
        float dx = sim.world().x(target) - sim.world().x(unit);
        float dy = sim.world().y(target) - sim.world().y(unit);
        if (dx * dx + dy * dy > special.range() * special.range()) return false;
        return sim.getTacticalScoring().hasClearShot(unit, target);
    }

    private static boolean tickSmokeThrow(long unit, SpecialEquipmentDef special,
                                          BattleControl sim) {
        World world = sim.world();
        float duration = special.smokeGrenadeSpec().throwDuration();
        world.setSecondaryActionTimer(unit,
                world.secondaryActionTimer(unit) - BattleSimulation.TICK_DT);
        if (!world.secondaryFired(unit)
                && world.secondaryActionTimer(unit) <= duration * 0.5f) {
            Squad squad = sim.squadOf(unit);
            if (squad != null && squad.smokeCarrierId == unit
                    && squad.smokeTargetX >= 0 && squad.smokeTargetY >= 0) {
                sim.throwSmoke(unit, squad.smokeTargetX + 0.5f,
                        squad.smokeTargetY + 0.5f);
            }
            world.setSecondaryFired(unit, true);
        }
        if (world.secondaryActionTimer(unit) <= 0f) {
            world.setSecondaryActionTimer(unit, 0f);
            world.setSecondaryAimTargetId(unit, 0L);
        }
        return true;
    }

    private static boolean tickFragThrow(long unit, SpecialEquipmentDef special,
                                         BattleControl sim) {
        World world = sim.world();
        world.setSecondaryActionTimer(unit,
                world.secondaryActionTimer(unit) - BattleSimulation.TICK_DT);
        float releaseAt = special.aimDuration() * 0.5f;
        if (!world.secondaryFired(unit)
                && world.secondaryActionTimer(unit) <= releaseAt) {
            com.dillon.starsectormarines.battle.grenade.FragGrenadeService.Reservation reservation =
                    sim.fragGrenades().reservationFor(unit);
            if (reservation != null) {
                sim.throwFragmentationGrenade(unit,
                        reservation.targetX(), reservation.targetY());
            }
            world.setSecondaryFired(unit, true);
        }
        if (world.secondaryActionTimer(unit) <= 0f) {
            world.setSecondaryActionTimer(unit, 0f);
            world.setSecondaryAimTargetId(unit, 0L);
            sim.fragGrenades().release(unit);
        }
        return true;
    }

    private static boolean tryOpportunitySatchel(long unit, SpecialEquipmentDef special,
                                                  BattleControl sim) {
        float range = special.satchelChargeSpec().contactRange();
        long bestTarget = 0L;
        float bestDistanceSq = Float.MAX_VALUE;
        LongBucket scratch = new LongBucket();
        sim.getUnitIndex().gather(sim.world().x(unit), sim.world().y(unit), range, scratch);
        for (int i = 0; i < scratch.size; i++) {
            long target = scratch.ids[i];
            if (!legalSatchelTarget(unit, target, special, sim)) continue;
            float dx = sim.world().x(target) - sim.world().x(unit);
            float dy = sim.world().y(target) - sim.world().y(unit);
            float distanceSq = dx * dx + dy * dy;
            if (distanceSq >= bestDistanceSq) continue;
            if (sim.satchelCharges().hasChargeForTarget(target)) continue;
            bestTarget = target;
            bestDistanceSq = distanceSq;
        }
        if (bestTarget == 0L || !sim.satchelCharges().tryReserve(unit, bestTarget)) return false;
        sim.world().setSecondaryActionTimer(unit, special.aimDuration());
        sim.world().setSecondaryFired(unit, false);
        sim.world().setSecondaryAimTargetId(unit, bestTarget);
        return true;
    }

    private static boolean tickSatchelPlant(long unit, SpecialEquipmentDef special,
                                            BattleControl sim) {
        World world = sim.world();
        world.setSecondaryActionTimer(unit,
                world.secondaryActionTimer(unit) - BattleSimulation.TICK_DT);
        if (world.secondaryActionTimer(unit) > 0f) return true;
        long target = sim.resolveUnit(world.secondaryAimTargetId(unit));
        boolean planted = legalSatchelTarget(unit, target, special, sim)
                && sim.plantSatchel(unit, target);
        if (!planted) sim.satchelCharges().releaseReservation(unit);
        world.setSecondaryFired(unit, planted);
        world.setSecondaryActionTimer(unit, 0f);
        world.setSecondaryAimTargetId(unit, 0L);
        return true;
    }

    private static boolean legalSatchelTarget(long unit, long target,
                                               SpecialEquipmentDef special,
                                               BattleView sim) {
        if (target == 0L || !sim.isHardenedTarget(target)) {
            return false;
        }
        if (!sim.world().isAlive(target)
                || sim.identity().faction(target) == sim.identity().faction(unit)) return false;
        float dx = sim.world().x(target) - sim.world().x(unit);
        float dy = sim.world().y(target) - sim.world().y(unit);
        float range = special.satchelChargeSpec().contactRange();
        return dx * dx + dy * dy <= range * range
                && sim.getTacticalScoring().hasClearShot(unit, target);
    }
}
