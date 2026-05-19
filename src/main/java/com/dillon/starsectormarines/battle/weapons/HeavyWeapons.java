package com.dillon.starsectormarines.battle.weapons;

import com.dillon.starsectormarines.battle.BattleSimulation;
import com.dillon.starsectormarines.battle.MechLoadoutState;
import com.dillon.starsectormarines.battle.MechWeapon;
import com.dillon.starsectormarines.battle.PendingDetonation;
import com.dillon.starsectormarines.battle.ShotEvent;
import com.dillon.starsectormarines.battle.Unit;

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
    /** Min/max near-miss offset (cells) from target cell-center on a missed shot. */
    private static final float MISS_OFFSET_MIN = 0.5f;
    private static final float MISS_OFFSET_MAX = 2.0f;

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
        boolean hit = ctx.getRng().nextFloat() < weapon.accuracy * accuracyMult;
        boolean isAoe = weapon.aoeRadius > 0f;
        float moraleImpact = shooter.type != null ? shooter.type.moraleImpact : 1.0f;

        // KINETIC PATH — chaingun and any future no-AoE mech weapon. Damage
        // applies immediately to the locked target via the shared
        // applyDamage/rollFallbackOnHit primitives.
        if (!isAoe && hit) {
            ctx.applyDamage(target, weapon.damage, weapon.vsTurretMult, moraleImpact);
            ctx.rollFallbackOnHit(target);
        }

        float fromX = shooter.cellX + 0.5f;
        float fromY = shooter.cellY + 0.5f;
        float toX, toY;
        if (hit) {
            toX = target.cellX + 0.5f;
            toY = target.cellY + 0.5f;
            // Endpoint scatter — pure visual offset around the target cell.
            // For AoE weapons this also scatters the splash center, so a salvo
            // sprays the impact zone instead of stacking on one cell.
            if (weapon.hitSpread > 0f) {
                float angle = ctx.getRng().nextFloat() * (float) (Math.PI * 2);
                float r = ctx.getRng().nextFloat() * weapon.hitSpread;
                toX += (float) Math.cos(angle) * r;
                toY += (float) Math.sin(angle) * r;
            }
        } else {
            float angle = ctx.getRng().nextFloat() * (float) (Math.PI * 2);
            float spread = MISS_OFFSET_MIN + ctx.getRng().nextFloat() * (MISS_OFFSET_MAX - MISS_OFFSET_MIN);
            // Misses get the wider baseline scatter PLUS the weapon's hitSpread
            // — an indirect-fire weapon's misses scatter further than a rifle's.
            spread += weapon.hitSpread;
            toX = target.cellX + 0.5f + (float) Math.cos(angle) * spread;
            toY = target.cellY + 0.5f + (float) Math.sin(angle) * spread;
        }

        // AOE PATH — queue a detonation at the endpoint. Damage resolves on
        // arrival via the Detonations pipeline. Hit-vs-miss only affects
        // WHERE the rocket lands; AoE math at impact decides who's close
        // enough to feel it.
        if (isAoe) {
            ctx.queueDetonation(new PendingDetonation(
                    toX, toY, weapon.flightSec,
                    weapon.aoeRadius, weapon.damage, weapon.vsTurretMult,
                    weapon.wallDamage, shooter.faction));
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
                                u.cellX, u.cellY,
                                m.lrmSalvoTarget.cellX, m.lrmSalvoTarget.cellY);
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
            ctx.spawnSmokingWreck(u.cellX, u.cellY);
            u.mech.wreckSpawned = true;
        }
    }
}
