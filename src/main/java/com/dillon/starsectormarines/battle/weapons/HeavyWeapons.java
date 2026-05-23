package com.dillon.starsectormarines.battle.weapons;

import com.dillon.starsectormarines.battle.BattleSimulation;
import com.dillon.starsectormarines.battle.MechLoadoutState;
import com.dillon.starsectormarines.battle.MechWeapon;
import com.dillon.starsectormarines.battle.PendingDetonation;
import com.dillon.starsectormarines.battle.Projectile;
import com.dillon.starsectormarines.battle.ShotEvent;
import com.dillon.starsectormarines.battle.Unit;
import com.dillon.starsectormarines.battle.fx.ImpactProfile;

/**
 * Chassis-mounted weapons on motorized / heavy units. Today that's just the
 * HEAVY_MECH walker's three-weapon loadout (chaingun + SRM pod + LRM
 * artillery); future tanks, hovercraft, and additional mech chassis hook in
 * here through the same {@link MechLoadoutState} state bag.
 *
 * <p>The split from {@link InfantryWeapons} is along the unit's character —
 * handheld squad weapons vs vehicle-mounted hardpoints — not along weapon
 * class. A future "infantry rocket launcher" would still live in infantry;
 * a hypothetical mech-mounted rifle would live here.
 *
 * <p>Three concurrent firing tracks per mech: chaingun (kinetic, instant
 * damage, fall-back roll), SRM pod (HE, queues detonation), LRM artillery
 * (HE, queues detonation, indirect-fire-capable). Continuation pumps the
 * queued bursts / salvos at per-weapon spacing in {@link #tick}.
 *
 * <p>Smoking-wreck spawn for dead mechs also lives here — idempotent via
 * the {@code wreckSpawned} latch on {@link MechLoadoutState} so any kill
 * path (chaingun crossfire, marine rocket, flyby strafe, debug damage)
 * lands exactly one wreck.
 */
public class HeavyWeapons {

    /** Sim seconds a kinetic tracer stays visible after being fired. Matches the legacy {@code SHOT_LIFETIME} on BattleSimulation. */
    private static final float SHOT_LIFETIME = 0.15f;

    /**
     * Per-tick pass: drains queued chaingun / SRM / LRM rounds for every mech,
     * then walks the unit list to emit a smoking-wreck for any just-died mech.
     */
    public void tick(WeaponSimContext ctx) {
        advanceMechWeapons(ctx);
        spawnMechWrecks(ctx);
    }

    /**
     * Convenience overload — full accuracy. Used by all the precision-fire
     * code paths (chaingun, SRM, line-of-sight LRMs).
     */
    public void fireMechWeapon(WeaponSimContext ctx, Unit shooter, Unit target, MechWeapon weapon) {
        fireMechWeapon(ctx, shooter, target, weapon, 1.0f);
    }

    /**
     * Fires one round of a mech chassis weapon. Damage / accuracy / vsTurret
     * pull from the {@link MechWeapon} parameter rather than the shooter's
     * baked Unit stats — a single mech runs three concurrent weapon tracks
     * with very different numbers, so the weapon's profile drives the math.
     * Caller is responsible for cooldown / ammo / range gating before calling.
     *
     * <p>{@code accuracyMult} scales the weapon's base accuracy at the hit
     * roll. Set to 1.0 for line-of-sight fire; the LRM indirect-fire path
     * passes {@link MechWeapon#LRM_NO_LOS_ACC_MULT}.
     */
    public void fireMechWeapon(WeaponSimContext ctx, Unit shooter, Unit target, MechWeapon weapon, float accuracyMult) {
        boolean hit = shooter.rng.nextFloat() < weapon.accuracy * accuracyMult;
        boolean isAoe = weapon.aoeRadius > 0f;
        float moraleImpact = shooter.type != null ? shooter.type.moraleImpact : 1.0f;

        // Muzzle origin tracks the SHOOTER'S CURRENT RENDER POSITION so a
        // chaingun burst follows the walking mech instead of pinning the
        // muzzle flash to the cell where the burst started. Mirrors the
        // infantry-side fix in InfantryWeapons.fireShot.
        float fromX = shooter.renderX + 0.5f;
        float fromY = shooter.renderY + 0.5f;
        // Distance-scaled spread — see RangeFalloff for the physical model.
        // Shared with the infantry-side primaries so chaingun saturation and
        // SMG burst-spread use the same math, just with different per-weapon
        // hitSpread numbers.
        float distToTarget = RangeFalloff.dist(shooter.getCellX(), shooter.getCellY(), target.getCellX(), target.getCellY());
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
                ctx.getGrid(), weapon.raycastShots, fromX, fromY, toX, toY, hit);
        toX = snapped.toX();
        toY = snapped.toY();
        hit = snapped.hit();

        // KINETIC PATH — chaingun direct-fire. Applied AFTER raycast so a
        // wall-blocked round correctly counts as a miss. AoE-kind shots skip
        // this and resolve at endpoint via the Detonations pipeline below.
        if (!isAoe && hit) {
            ctx.applyDamage(target, weapon.damage, weapon.vsTurretMult, moraleImpact);
            ctx.rollFallbackOnHit(target);
            // Mech-vs-mech reprio — rare today (both sides have mech-only
            // squads on their own faction) but consistent with the
            // infantry-side rifle path. Gated to once per sim-tick inside
            // the impl.
            ctx.rollReprioritizeOnHit(target, shooter);
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
                ctx.queueProjectile(new Projectile(
                        fromX, fromY, toX, toY,
                        /*hasBoostRamp*/ true, weapon.arcHeight,
                        shooter.faction, aerial, weapon.flightSec, onArrival));
            } else {
                // Kinetic-splash kinds (chaingun) keep the legacy AoE-tracer
                // path — no in-flight queryable entity is useful for a bullet,
                // and the boost-curve visual would read wrong.
                ctx.queueDetonation(onArrival);
            }
        }

        float lifetime = weapon.flightSec > 0f ? weapon.flightSec : SHOT_LIFETIME;
        ctx.postShot(new ShotEvent(fromX, fromY, toX, toY, hit, shooter.faction, lifetime,
                null, null, null, weapon, moraleImpact));
    }

    /**
     * Per-tick mech-weapon continuation — runs the three chassis tracks
     * (chaingun burst, SRM salvo, LRM salvo) for every unit with a
     * {@link MechLoadoutState}. Mirrors {@link InfantryWeapons#tick} for the
     * marine primary side; lives separate because the mech burst state is on
     * the loadout, not the unit.
     *
     * <p>The trigger decisions (start a burst / launch a salvo / lob an LRM)
     * happen inside {@code MechCombatantBehavior.tryFireMechWeapons}. This pass
     * only handles continuation — emitting queued rounds at their proper
     * spacing — and ticks down the per-weapon cooldowns so the next trigger
     * decision sees the right gating.
     */
    private void advanceMechWeapons(WeaponSimContext ctx) {
        for (Unit u : ctx.getUnits()) {
            MechLoadoutState m = u.mech;
            if (m == null || !u.isAlive()) continue;

            if (m.chaingunCooldown > 0f) m.chaingunCooldown -= BattleSimulation.TICK_DT;
            if (m.srmCooldown      > 0f) m.srmCooldown      -= BattleSimulation.TICK_DT;
            if (m.lrmCooldown      > 0f) m.lrmCooldown      -= BattleSimulation.TICK_DT;

            // Chaingun burst continuation.
            if (m.chaingunBurstRemaining > 0) {
                m.chaingunBurstTimer -= BattleSimulation.TICK_DT;
                if (m.chaingunBurstTimer <= 0f) {
                    if (m.chaingunBurstTarget == null || !m.chaingunBurstTarget.isAlive()) {
                        m.chaingunBurstRemaining = 0;
                        m.chaingunBurstTarget = null;
                    } else {
                        fireMechWeapon(ctx, u, m.chaingunBurstTarget, m.chaingun);
                        m.chaingunBurstRemaining--;
                        m.chaingunBurstTimer = m.chaingun.burstSpacing;
                        if (m.chaingunBurstRemaining == 0) m.chaingunBurstTarget = null;
                    }
                }
            }

            // SRM salvo continuation.
            if (m.srmSalvoRemaining > 0) {
                m.srmSalvoTimer -= BattleSimulation.TICK_DT;
                if (m.srmSalvoTimer <= 0f) {
                    if (m.srmSalvoTarget == null || !m.srmSalvoTarget.isAlive()) {
                        m.srmSalvoRemaining = 0;
                        m.srmSalvoTarget = null;
                    } else {
                        fireMechWeapon(ctx, u, m.srmSalvoTarget, m.srmPod);
                        m.srmSalvoRemaining--;
                        m.srmSalvoTimer = m.srmPod.burstSpacing;
                        if (m.srmSalvoRemaining == 0) m.srmSalvoTarget = null;
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
                    if (m.lrmSalvoTarget == null || !m.lrmSalvoTarget.isAlive()) {
                        m.lrmSalvoRemaining = 0;
                        m.lrmSalvoTarget = null;
                    } else {
                        boolean hasLos = ctx.getGrid().hasLineOfSight(
                                u.getCellX(), u.getCellY(),
                                m.lrmSalvoTarget.getCellX(), m.lrmSalvoTarget.getCellY());
                        float accMult = hasLos ? 1.0f : MechWeapon.LRM_NO_LOS_ACC_MULT;
                        fireMechWeapon(ctx, u, m.lrmSalvoTarget, m.lrmArtillery, accMult);
                        m.lrmSalvoRemaining--;
                        m.lrmSalvoTimer = m.lrmArtillery.burstSpacing;
                        if (m.lrmSalvoRemaining == 0) m.lrmSalvoTarget = null;
                    }
                }
            }
        }
    }

    /**
     * Walks the unit list and emits a smoking-wreck on the cell of any
     * just-died mech ({@link MechLoadoutState#wreckSpawned} is the
     * idempotency latch). Catches mech deaths from every code path — primary
     * fire, mech crossfire, marine rockets, flyby strafing — without
     * duplicating spawn logic at each kill site.
     */
    private void spawnMechWrecks(WeaponSimContext ctx) {
        for (Unit u : ctx.getUnits()) {
            if (u.isAlive()) continue;
            if (u.mech == null || u.mech.wreckSpawned) continue;
            ctx.spawnSmokingWreck(u.getCellX(), u.getCellY());
            u.mech.wreckSpawned = true;
        }
    }
}
