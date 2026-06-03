package com.dillon.starsectormarines.battle.combat;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Entity;
import com.dillon.starsectormarines.battle.unit.UnitRegistry;
import com.dillon.starsectormarines.battle.mech.MechWeapon;
import com.dillon.starsectormarines.battle.mech.MechLoadoutState;
import com.dillon.starsectormarines.battle.component.ComponentStore;
import com.dillon.starsectormarines.battle.combat.fx.ImpactProfile;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Chassis-mounted weapons on motorized / heavy units. Today that's just the
 * HEAVY_MECH walker's three-weapon loadout (chaingun + SRM pod + LRM
 * artillery); future tanks, hovercraft, and additional mech chassis hook in
 * here through the same {@link MechLoadoutState} state bag.
 *
 * <p>The split from {@link com.dillon.starsectormarines.battle.infantry.InfantryWeapons} is along the unit's character —
 * handheld squad weapons vs vehicle-mounted hardpoints — not along weapon
 * class. A future "infantry rocket launcher" would still live in infantry;
 * a hypothetical mech-mounted rifle would live here.
 *
 * <p>Three concurrent firing tracks per mech: chaingun (kinetic, instant
 * damage, fall-back roll), SRM pod (HE, queues detonation), LRM artillery
 * (HE, queues detonation, indirect-fire-capable). Continuation pumps the
 * queued bursts / salvos at per-weapon spacing in {@link #tick}.
 *
 * <p>Smoking-wreck spawn for dead mechs is no longer here — it moved to the
 * {@code MechWreckSystem} death-event handler, so it reacts to the one death
 * seam instead of re-scanning the unit list each tick.
 */
public class HeavyWeapons {

    private static final float SHOT_LIFETIME = 0.15f;

    private final UnitRegistry registry;
    private final NavigationGrid grid;
    private final DamageService damageService;
    private final HitResponseService hitResponse;
    private final ShotService shots;
    private final Detonations detonations;
    /**
     * Mech-loadout component store. The continuation pass iterates this directly
     * (only mech entities occupy it) instead of scanning the whole dense
     * registry for a former {@code u.mech != null} field — capability-as-presence.
     */
    private final ComponentStore<MechLoadoutState> mechLoadouts;

    /**
     * Reused per-tick gather of the live mechs before the continuation pass.
     * The pass fires weapons, which can kill a target and release it from the
     * registry mid-pass (swap-and-pop); gathering first makes the iteration a
     * snapshot, so a release doesn't reshuffle the slots out from under it.
     * Only mechs are gathered (a handful per battle), so the copy is cheap.
     */
    private final List<Entity> mechScratch = new ArrayList<>();

    public HeavyWeapons(UnitRegistry registry, NavigationGrid grid,
                        DamageService damageService, HitResponseService hitResponse,
                        ShotService shots, Detonations detonations,
                        ComponentStore<MechLoadoutState> mechLoadouts) {
        this.registry = registry;
        this.grid = grid;
        this.damageService = damageService;
        this.hitResponse = hitResponse;
        this.shots = shots;
        this.detonations = detonations;
        this.mechLoadouts = mechLoadouts;
    }

    /** Per-tick pass: drains queued chaingun / SRM / LRM rounds for every mech. */
    public void tick() {
        advanceMechWeapons();
    }

    /**
     * Convenience overload — full accuracy. Used by all the precision-fire
     * code paths (chaingun, SRM, line-of-sight LRMs).
     */
    public void fireMechWeapon(Entity shooter, Entity target, MechWeapon weapon) {
        fireMechWeapon(shooter, target, weapon, 1.0f);
    }

    /**
     * Fires one round of a mech chassis weapon. Damage / accuracy / vsTurret
     * pull from the {@link MechWeapon} parameter rather than the shooter's
     * baked Entity stats — a single mech runs three concurrent weapon tracks
     * with very different numbers, so the weapon's profile drives the math.
     * Caller is responsible for cooldown / ammo / range gating before calling.
     *
     * <p>{@code accuracyMult} scales the weapon's base accuracy at the hit
     * roll. Set to 1.0 for line-of-sight fire; the LRM indirect-fire path
     * passes {@link MechWeapon#LRM_NO_LOS_ACC_MULT}.
     */
    public void fireMechWeapon(Entity shooter, Entity target, MechWeapon weapon, float accuracyMult) {
        boolean hit = shooter.rng.nextFloat() < weapon.accuracy * accuracyMult;
        boolean isAoe = weapon.aoeRadius > 0f;
        float moraleImpact = shooter.type != null ? shooter.type.moraleImpact : 1.0f;

        // Muzzle origin tracks the SHOOTER'S CURRENT RENDER POSITION so a
        // chaingun burst follows the walking mech instead of pinning the
        // muzzle flash to the cell where the burst started. Mirrors the
        // infantry-side fix in InfantryWeapons.fireShot.
        float fromX = shooter.getRenderX() + 0.5f;
        float fromY = shooter.getRenderY() + 0.5f;
        // Distance-scaled spread — see RangeFalloff for the physical model.
        // Shared with the infantry-side primaries so chaingun saturation and
        // SMG burst-spread use the same math, just with different per-weapon
        // hitSpread numbers.
        int shooterIdx = registry.requireLiveIndex(shooter.entityId);
        int targetIdx = registry.requireLiveIndex(target.entityId);
        float distToTarget = RangeFalloff.dist(registry.getCellX(shooterIdx), registry.getCellY(shooterIdx),
                registry.getCellX(targetIdx), registry.getCellY(targetIdx));
        float effectiveSpread = RangeFalloff.spread(weapon.hitSpread, distToTarget, weapon.range);

        // Endpoint resolves through ShotEndpoint — same hit-jitter +
        // miss-ring rules as the infantry primaries. effectiveSpread carries
        // the chaingun/LRM saturation widening; AoE weapons get their splash
        // center scattered through the same machinery so a salvo sprays the
        // impact zone instead of stacking on one cell.
        ShotEndpoint.Endpoint ep = ShotEndpoint.resolve(target, hit, effectiveSpread, shooter.rng);
        float toX = ep.x();
        float toY = ep.y();

        // Wall raycast — for ground-deployed area-spread weapons (chaingun's
        // dual-MG saturation), a scattered round that would fly past a wall
        // splatters on it instead. Shared with the turret-side raycast via
        // ShotRaycast so both sides see the same wall-snap convention.
        ShotRaycast.Result snapped = ShotRaycast.resolve(
                grid, weapon.raycastShots, fromX, fromY, toX, toY, hit);
        toX = snapped.toX();
        toY = snapped.toY();
        hit = snapped.hit();

        // KINETIC PATH — chaingun direct-fire. Applied AFTER raycast so a
        // wall-blocked round correctly counts as a miss. AoE-kind shots skip
        // this and resolve at endpoint via the Detonations pipeline below.
        if (!isAoe && hit) {
            damageService.applyDamage(target, weapon.damage, weapon.vsTurretMult, moraleImpact);
            hitResponse.rollFallbackOnHit(target);
            hitResponse.rollReprioritizeOnHit(target, shooter);
        }

        // AOE PATH — queue a detonation at the (possibly wall-snapped)
        // endpoint. Damage resolves on arrival via the Detonations pipeline.
        // Hit-vs-miss only affects WHERE the rocket lands; AoE math at impact
        // decides who's close enough to feel it.
        if (isAoe) {
            // Aerial delivery if the weapon visually arcs (LRM). SRM line-fires
            // even from a mech and explodes at endpoint cell; roofs don't shield
            // it if the rocket reached the interior through a doorway.
            boolean aerial = weapon.arcHeight > 0f;
            PendingDetonation onArrival = new PendingDetonation(
                    toX, toY, weapon.flightSec,
                    weapon.aoeRadius, weapon.damage, weapon.vsTurretMult,
                    weapon.wallDamage, shooter.faction, aerial,
                    weapon.wallDamageRadius, /*spawnDustOnWallBreak*/ true, /*friendlyFireImmune*/ false);
            if (weapon.impactProfile == ImpactProfile.HE) {
                // HE rockets (SRM_POD, LRM_ARTILLERY) ride the modeled Projectile
                // entity, same as marine handheld rockets (ad53835) and locust
                // turrets. The Projectile owns the arrival payload directly —
                // queryable mid-flight (point-defense future) and visible to
                // TacticalScoring.shouldCommitRocket's volley coordination so a
                // marine rocketeer no longer ignores an inbound mech SRM volley
                // against the same turret.
                shots.queueProjectile(new Projectile(
                        fromX, fromY, toX, toY,
                        /*hasBoostRamp*/ true, weapon.arcHeight,
                        shooter.faction, aerial, weapon.flightSec, onArrival));
            } else {
                // Kinetic-splash kinds (chaingun) keep the legacy AoE-tracer
                // path — no in-flight queryable entity is useful for a bullet,
                // and the boost-curve visual would read wrong.
                detonations.queue(onArrival);
            }
        }

        float lifetime = weapon.flightSec > 0f ? weapon.flightSec : SHOT_LIFETIME;
        shots.postShot(new ShotEvent(fromX, fromY, toX, toY, hit, shooter.faction, lifetime,
                null, null, null, weapon, moraleImpact));
    }

    /**
     * Per-tick mech-weapon continuation — runs the three chassis tracks
     * (chaingun burst, SRM salvo, LRM salvo) for every unit with a
     * {@link MechLoadoutState}. Mirrors {@link com.dillon.starsectormarines.battle.infantry.InfantryWeapons#tick} for the
     * marine primary side; lives separate because the mech burst state is on
     * the loadout, not the unit.
     *
     * <p>The trigger decisions (start a burst / launch a salvo / lob an LRM)
     * happen inside {@code MechCombatantBehavior.tryFireMechWeapons}. This pass
     * only handles continuation — emitting queued rounds at their proper
     * spacing — and ticks down the per-weapon cooldowns so the next trigger
     * decision sees the right gating.
     */
    private void advanceMechWeapons() {
        // Gather the live mechs first (iterating the loadout store — only mech
        // entities occupy it, so no scan over the whole registry), then run the
        // continuation pass over the snapshot. fireMechWeapon resolves damage
        // inline in this serial phase, so a kill releases its target and
        // swap-and-pops the registry; iterating a snapshot keeps that from
        // corrupting the pass. getOrNull filters entries whose entity is already
        // released (a just-dead mech still lingers in the store until its wreck
        // drains at DEMOLISH).
        mechScratch.clear();
        for (Map.Entry<Long, MechLoadoutState> e : mechLoadouts.entries()) {
            Entity u = registry.getOrNull(e.getKey());
            if (u != null) mechScratch.add(u);
        }
        for (int i = 0, n = mechScratch.size(); i < n; i++) {
            Entity u = mechScratch.get(i);
            if (!registry.isAliveById(u.entityId)) continue; // killed earlier in this same pass
            MechLoadoutState m = mechLoadouts.get(u.entityId);

            if (m.chaingunCooldown > 0f) m.chaingunCooldown -= BattleSimulation.TICK_DT;
            if (m.srmCooldown      > 0f) m.srmCooldown      -= BattleSimulation.TICK_DT;
            if (m.lrmCooldown      > 0f) m.lrmCooldown      -= BattleSimulation.TICK_DT;

            // Chaingun burst continuation. getOrNull resolves a released (or
            // 0L = none) target to null, so the lock drops without an isAlive()
            // on a dangling ref.
            if (m.chaingunBurstRemaining > 0) {
                m.chaingunBurstTimer -= BattleSimulation.TICK_DT;
                if (m.chaingunBurstTimer <= 0f) {
                    Entity cgTarget = registry.getOrNull(m.chaingunBurstTargetId);
                    if (cgTarget == null) {
                        m.chaingunBurstRemaining = 0;
                        m.chaingunBurstTargetId = 0L;
                    } else {
                        fireMechWeapon(u, cgTarget, m.chaingun);
                        m.chaingunBurstRemaining--;
                        m.chaingunBurstTimer = m.chaingun.burstSpacing;
                        if (m.chaingunBurstRemaining == 0) m.chaingunBurstTargetId = 0L;
                    }
                }
            }

            // SRM salvo continuation.
            if (m.srmSalvoRemaining > 0) {
                m.srmSalvoTimer -= BattleSimulation.TICK_DT;
                if (m.srmSalvoTimer <= 0f) {
                    Entity srmTarget = registry.getOrNull(m.srmSalvoTargetId);
                    if (srmTarget == null) {
                        m.srmSalvoRemaining = 0;
                        m.srmSalvoTargetId = 0L;
                    } else {
                        fireMechWeapon(u, srmTarget, m.srmPod);
                        m.srmSalvoRemaining--;
                        m.srmSalvoTimer = m.srmPod.burstSpacing;
                        if (m.srmSalvoRemaining == 0) m.srmSalvoTargetId = 0L;
                    }
                }
            }

            // LRM salvo continuation — same pattern as SRM. Locked target is
            // held across the whole 5-rocket wave so a single salvo reads as
            // one coordinated barrage instead of scatter fire across enemies.
            // LOS is recomputed per rocket: if marines pop into LOS mid-salvo,
            // the later rockets get full accuracy; if LOS drops mid-salvo, the
            // remaining rockets eat the indirect-fire penalty.
            if (m.lrmSalvoRemaining > 0) {
                m.lrmSalvoTimer -= BattleSimulation.TICK_DT;
                if (m.lrmSalvoTimer <= 0f) {
                    Entity lrmTarget = registry.getOrNull(m.lrmSalvoTargetId);
                    if (lrmTarget == null) {
                        m.lrmSalvoRemaining = 0;
                        m.lrmSalvoTargetId = 0L;
                    } else {
                        int uIdx = registry.requireLiveIndex(u.entityId);
                        int lrmIdx = registry.requireLiveIndex(lrmTarget.entityId);
                        boolean hasLos = grid.hasLineOfSight(
                                registry.getCellX(uIdx), registry.getCellY(uIdx),
                                registry.getCellX(lrmIdx), registry.getCellY(lrmIdx));
                        float accMult = hasLos ? 1.0f : MechWeapon.LRM_NO_LOS_ACC_MULT;
                        fireMechWeapon(u, lrmTarget, m.lrmArtillery, accMult);
                        m.lrmSalvoRemaining--;
                        m.lrmSalvoTimer = m.lrmArtillery.burstSpacing;
                        if (m.lrmSalvoRemaining == 0) m.lrmSalvoTargetId = 0L;
                    }
                }
            }
        }
    }
}
