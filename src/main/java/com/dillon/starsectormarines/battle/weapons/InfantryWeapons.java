package com.dillon.starsectormarines.battle.weapons;

import com.dillon.starsectormarines.battle.BattleSimulation;
import com.dillon.starsectormarines.battle.MapTurret;
import com.dillon.starsectormarines.battle.MarineSecondary;
import com.dillon.starsectormarines.battle.MarineWeapon;
import com.dillon.starsectormarines.battle.PendingDetonation;
import com.dillon.starsectormarines.battle.ShotEvent;
import com.dillon.starsectormarines.battle.TurretKind;
import com.dillon.starsectormarines.battle.Unit;

/**
 * Handheld squad weapons — rifles, SMGs, DMRs (primary line tracers / kinetic
 * bullets) and rocket launchers (secondary, AoE). Owns the firing math + the
 * per-tick burst continuation pass for every infantry-class unit:
 * marines, militia, aliens, and any future squaddie wielding a
 * {@link MarineWeapon}.
 *
 * <p>Stateless today — burst continuation state lives on each {@link Unit}
 * ({@code burstRemaining} / {@code burstTimer} / {@code burstTarget}) so a
 * shared subsystem instance can serve every unit without per-shooter scratch
 * space. The {@link WeaponSimContext} parameter on each public call is the
 * deliberate seam: the subsystem reads grid/rng/unit-list through it and
 * pushes events (shots, detonations, deaths, fall-backs) back without ever
 * holding a {@code BattleSimulation} reference directly.
 *
 * <p>Parallel structure to {@link com.dillon.starsectormarines.battle.air.AirSystem}:
 * the sim owns one instance and pumps it once per tick via {@link #tick}.
 * Heavy weapons (mech chassis, eventually tanks/hovercraft) get the same
 * treatment in a follow-up pass.
 */
public class InfantryWeapons {

    /** Sim seconds a tracer stays visible after being fired. Matches the legacy {@code SHOT_LIFETIME} on BattleSimulation. */
    private static final float SHOT_LIFETIME = 0.15f;
    /** Min/max near-miss offset (cells) from target cell-center on a missed shot. */
    private static final float MISS_OFFSET_MIN = 0.5f;
    private static final float MISS_OFFSET_MAX = 2.0f;

    /**
     * Per-tick burst-fire pass: every unit with {@code burstRemaining > 0}
     * decrements its {@code burstTimer}; on expiry, fires another shot at the
     * locked burst target (if still alive) and decrements the count. Lets the
     * AI emit a fire-once decision and this subsystem spread the remaining
     * rounds over a few ticks at weapon-defined spacing.
     */
    public void tick(WeaponSimContext ctx) {
        for (Unit u : ctx.getUnits()) {
            if (u.burstRemaining <= 0 || !u.isAlive()) continue;
            u.burstTimer -= BattleSimulation.TICK_DT;
            if (u.burstTimer > 0f) continue;
            if (u.burstTarget == null || !u.burstTarget.isAlive() || u.primaryWeapon == null) {
                u.burstRemaining = 0;
                u.burstTarget = null;
                continue;
            }
            fireShot(ctx, u, u.burstTarget);
            u.burstRemaining--;
            u.burstTimer = u.primaryWeapon.burstSpacing;
            if (u.burstRemaining == 0) u.burstTarget = null;
        }
    }

    /**
     * Fires the shooter's primary at the target. Per-shot accuracy / damage /
     * vsTurret pull from the marine's {@link MarineWeapon} when assigned;
     * otherwise from the {@link Unit}'s baked-in stats (militia, aliens,
     * turrets — all the "no MarineWeapon" callers).
     *
     * <p>Public because behaviors call this when firing; fall-back is also
     * rolled here, which can mutate the target's path via the context.
     */
    public void fireShot(WeaponSimContext ctx, Unit shooter, Unit target) {
        float accuracy = shooter.accuracy;
        float damage   = shooter.attackDamage;
        float vsTurretMult = 1f;
        if (shooter.primaryWeapon != null) {
            accuracy = shooter.primaryWeapon.accuracy;
            damage   = shooter.primaryWeapon.damage;
            vsTurretMult = shooter.primaryWeapon.vsTurretMult;
        }
        boolean hit = ctx.getRng().nextFloat() < accuracy;
        if (hit) {
            ctx.applyDamage(target, damage, vsTurretMult);
            // Fall-back roll fires only on hit; the context decides eligibility
            // (turrets, already-falling-back units, dead units are all skipped).
            ctx.rollFallbackOnHit(target);
        }

        float fromX = shooter.cellX + 0.5f;
        float fromY = shooter.cellY + 0.5f;
        float toX, toY;
        if (hit) {
            toX = target.cellX + 0.5f;
            toY = target.cellY + 0.5f;
        } else {
            float angle = ctx.getRng().nextFloat() * (float) (Math.PI * 2);
            float spread = MISS_OFFSET_MIN + ctx.getRng().nextFloat() * (MISS_OFFSET_MAX - MISS_OFFSET_MIN);
            toX = target.cellX + 0.5f + (float) Math.cos(angle) * spread;
            toY = target.cellY + 0.5f + (float) Math.sin(angle) * spread;
        }
        TurretKind tk = (shooter instanceof MapTurret) ? ((MapTurret) shooter).kind : null;
        // Primary weapons with their own projectile sprite (SMG bullets) use
        // the weapon's flightSec so a slow round visibly travels — line tracers
        // keep the default SHOT_LIFETIME since they're drawn full-length instantly.
        float lifetime = SHOT_LIFETIME;
        if (shooter.primaryWeapon != null && shooter.primaryWeapon.projectileSpritePath != null
                && shooter.primaryWeapon.flightSec > 0f) {
            lifetime = shooter.primaryWeapon.flightSec;
        }
        ctx.postShot(new ShotEvent(fromX, fromY, toX, toY, hit, shooter.faction, lifetime,
                tk, shooter.primaryWeapon, null));
    }

    /**
     * Fires the shooter's secondary (rocket launcher today). Rolls accuracy
     * to determine the impact endpoint, decrements ammo, and queues a
     * {@link PendingDetonation} — damage resolves on arrival via the AoE
     * pipeline, not at fire time. A marine who moves between launch and
     * impact escapes the splash.
     *
     * <p>Caller is responsible for verifying ammo &gt; 0 and within-range
     * before calling.
     */
    public void fireSecondary(WeaponSimContext ctx, Unit shooter, Unit target) {
        MarineSecondary sec = shooter.secondaryWeapon;
        if (sec == null || shooter.secondaryAmmo <= 0) return;
        shooter.secondaryAmmo--;
        boolean hit = ctx.getRng().nextFloat() < sec.accuracy;
        float fromX = shooter.cellX + 0.5f;
        float fromY = shooter.cellY + 0.5f;
        float toX, toY;
        if (hit) {
            toX = target.cellX + 0.5f;
            toY = target.cellY + 0.5f;
        } else {
            float angle = ctx.getRng().nextFloat() * (float) (Math.PI * 2);
            float spread = MISS_OFFSET_MIN + ctx.getRng().nextFloat() * (MISS_OFFSET_MAX - MISS_OFFSET_MIN);
            toX = target.cellX + 0.5f + (float) Math.cos(angle) * spread;
            toY = target.cellY + 0.5f + (float) Math.sin(angle) * spread;
        }
        ctx.queueDetonation(new PendingDetonation(
                toX, toY, sec.flightSec,
                sec.aoeRadius, sec.damage, sec.vsTurretMult,
                sec.wallDamage, shooter.faction));
        // Secondary uses its per-weapon flightSec so rockets visibly travel.
        ctx.postShot(new ShotEvent(fromX, fromY, toX, toY, hit, shooter.faction, sec.flightSec,
                null, null, sec));
    }
}
