package com.dillon.starsectormarines.battle.weapons;

import com.dillon.starsectormarines.battle.damage.DamageService;
import com.dillon.starsectormarines.battle.damage.HitResponseService;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.turret.MapTurret;
import com.dillon.starsectormarines.battle.fx.PendingDetonation;
import com.dillon.starsectormarines.battle.fx.Projectile;
import com.dillon.starsectormarines.battle.fx.ShotEvent;
import com.dillon.starsectormarines.battle.shots.ShotService;
import com.dillon.starsectormarines.battle.turret.TurretKind;
import com.dillon.starsectormarines.battle.unit.Unit;
import com.dillon.starsectormarines.battle.unit.UnitRegistry;

import java.util.List;

/**
 * Handheld squad weapons — rifles, SMGs, DMRs (primary line tracers / kinetic
 * bullets) and rocket launchers (secondary, AoE). Owns the firing math + the
 * per-tick burst continuation pass for every infantry-class unit:
 * marines, militia, aliens, and any future squaddie wielding a
 * {@link MarineWeapon}.
 *
 * <p>Burst continuation state lives on each {@link Unit}
 * ({@code burstRemaining} / {@code burstTimer} / {@code burstTargetId}) so a
 * shared subsystem instance can serve every unit without per-shooter scratch
 * space. Services are constructor-injected; the subsystem pushes events
 * (shots, detonations, deaths, fall-backs) through them without ever
 * holding a {@code BattleSimulation} reference.
 *
 * <p>Parallel structure to {@link com.dillon.starsectormarines.battle.air.AirSystem}:
 * the sim owns one instance and pumps it once per tick via {@link #tick}.
 */
public class InfantryWeapons {

    private static final float SHOT_LIFETIME = 0.15f;

    private final List<Unit> units;
    private final UnitRegistry registry;
    private final DamageService damageService;
    private final HitResponseService hitResponse;
    private final ShotService shots;

    public InfantryWeapons(List<Unit> units, UnitRegistry registry,
                           DamageService damageService, HitResponseService hitResponse,
                           ShotService shots) {
        this.units = units;
        this.registry = registry;
        this.damageService = damageService;
        this.hitResponse = hitResponse;
        this.shots = shots;
    }

    /**
     * Per-tick burst-fire pass: every unit with {@code burstRemaining > 0}
     * decrements its {@code burstTimer}; on expiry, fires another shot at the
     * locked burst target (if still alive) and decrements the count.
     */
    public void tick() {
        for (Unit u : units) {
            if (u.burstRemaining <= 0 || !u.isAlive()) continue;
            u.burstTimer -= BattleSimulation.TICK_DT;
            if (u.burstTimer > 0f) continue;
            Unit burstTarget = registry.getOrNull(u.burstTargetId);
            if (burstTarget == null || u.primaryWeapon == null) {
                u.burstRemaining = 0;
                u.burstTargetId = 0L;
                continue;
            }
            // Burst follow-up: use the unit's current motion state. If they
            // walked off the firing position mid-burst, the remaining rounds
            // get the MOVING accuracy penalty — same rule a hand-rolled
            // moving-fire callsite gets.
            fireShot(u, burstTarget, FireStance.stanceFor(u));
            u.burstRemaining--;
            u.burstTimer = u.primaryWeapon.burstSpacing;
            if (u.burstRemaining == 0) u.burstTargetId = 0L;
        }
    }

    /**
     * Fires the shooter's primary at the target. Per-shot accuracy / damage /
     * vsTurret pull from the marine's {@link MarineWeapon} when assigned;
     * otherwise from the {@link Unit}'s baked-in stats (militia, aliens,
     * turrets — all the "no MarineWeapon" callers). Accuracy is multiplied
     * by {@code stance.accuracyMult} — STANCED preserves the base roll,
     * MOVING applies the on-the-move suppression penalty.
     *
     * <p>Public because behaviors call this when firing; fall-back is also
     * rolled here, which can mutate the target's path via the context.
     */
    public void fireShot(Unit shooter, Unit target, FireStance stance) {
        float accuracy = shooter.accuracy;
        float damage   = shooter.attackDamage;
        float vsTurretMult = 1f;
        // Distance-scaled accuracy + spread only apply when the shooter has
        // a per-weapon profile (marines). Militia / aliens / turrets fall
        // through to their baked Unit stats with flat accuracy and the
        // baseline miss-scatter ring — preserves the legacy behavior for
        // every "no MarineWeapon" caller.
        float dist = RangeFalloff.dist(shooter.getCellX(), shooter.getCellY(), target.getCellX(), target.getCellY());
        float effectiveSpread = 0f;
        if (shooter.primaryWeapon != null) {
            MarineWeapon w = shooter.primaryWeapon;
            accuracy = RangeFalloff.accuracy(w.accuracy, w.accuracyFalloff, dist, w.range);
            damage   = w.damage;
            vsTurretMult = w.vsTurretMult;
            effectiveSpread = RangeFalloff.spread(w.hitSpread, dist, w.range);
        }
        accuracy *= stance.accuracyMult;
        boolean hit = shooter.rng.nextFloat() < accuracy;
        float moraleImpact = shooter.type != null ? shooter.type.moraleImpact : 1.0f;
        if (hit) {
            damageService.applyDamage(target, damage, vsTurretMult, moraleImpact);
            hitResponse.rollFallbackOnHit(target);
            hitResponse.rollReprioritizeOnHit(target, shooter);
        }

        // Muzzle origin tracks the SHOOTER'S RENDER POSITION so the flash
        // glues to the sprite across a moving burst. Tracer endpoint and
        // miss-scatter both resolve through ShotEndpoint so all three
        // weapon paths (infantry primary / secondary / mech) live by the
        // same hit-jitter + miss-ring rules.
        float fromX = shooter.getRenderX() + 0.5f;
        float fromY = shooter.getRenderY() + 0.5f;
        ShotEndpoint.Endpoint ep = ShotEndpoint.resolve(target, hit, effectiveSpread, shooter.rng);
        float toX = ep.x();
        float toY = ep.y();
        TurretKind tk = (shooter instanceof MapTurret) ? ((MapTurret) shooter).kind : null;
        // Primary weapons with their own projectile sprite (SMG bullets) use
        // the weapon's flightSec so a slow round visibly travels — line tracers
        // keep the default SHOT_LIFETIME since they're drawn full-length instantly.
        float lifetime = SHOT_LIFETIME;
        if (shooter.primaryWeapon != null && shooter.primaryWeapon.projectileSpritePath != null
                && shooter.primaryWeapon.flightSec > 0f) {
            lifetime = shooter.primaryWeapon.flightSec;
        }
        shots.postShot(new ShotEvent(fromX, fromY, toX, toY, hit, shooter.faction, lifetime,
                tk, shooter.primaryWeapon, null, null, moraleImpact));
    }

    /**
     * Fires the shooter's secondary (rocket launcher today). Rolls accuracy
     * to determine the impact endpoint, decrements ammo, and spawns a
     * simulated-flight {@link Projectile} owning the AoE
     * {@link PendingDetonation} that fires on arrival. A marine who moves
     * between launch and impact escapes the splash.
     *
     * <p>Same Projectile shape that locust turrets use — the rocket is a
     * real in-flight entity, queryable by squad-coordination scorers
     * ({@link com.dillon.starsectormarines.battle.ai.TacticalScoring#shouldCommitRocket})
     * via {@code sim.getActiveProjectiles()} and (eventually) interceptable
     * by point defense. Flight time is the per-weapon
     * {@link MarineSecondary#flightSec} constant — marines fire over a
     * tighter range envelope than turrets, so the locust's
     * distance-scaled-velocity model doesn't earn its complexity here.
     *
     * <p>The paired {@link ShotEvent} stays — it's what the renderer reads
     * for sprite + contrail + audio + impact-FX dispatch (unchanged from
     * the legacy queueDetonation path). The Projectile is the sim-side
     * source of truth; the ShotEvent is the visual-side mirror.
     *
     * <p>Caller is responsible for verifying ammo &gt; 0 and within-range
     * before calling.
     */
    public void fireSecondary(Unit shooter, Unit target) {
        MarineSecondary sec = shooter.secondaryWeapon;
        if (sec == null || shooter.secondaryAmmo <= 0) return;
        shooter.secondaryAmmo--;
        boolean hit = shooter.rng.nextFloat() < sec.accuracy;
        // Rocket launches from the marine's current sprite position so the
        // launch FX glue to the sprite if the marine is mid-step. Endpoint
        // resolves through ShotEndpoint with effectiveSpread=0 — secondaries
        // don't carry their own hitSpread today, so the universal hit-jitter
        // + miss-ring still apply but no weapon-specific scatter.
        float fromX = shooter.getRenderX() + 0.5f;
        float fromY = shooter.getRenderY() + 0.5f;
        ShotEndpoint.Endpoint ep = ShotEndpoint.resolve(target, hit, 0f, shooter.rng);
        float toX = ep.x();
        float toY = ep.y();
        // Marine handheld rocket is direct-fire (no arc) — explodes wherever
        // the round lands. Reaches a roofed interior only via a doorway, in
        // which case the splash should damage the inside normally, not be
        // intercepted by the roof above.
        PendingDetonation onArrival = new PendingDetonation(
                toX, toY, sec.flightSec,
                sec.aoeRadius, sec.damage, sec.vsTurretMult,
                sec.wallDamage, shooter.faction, /*aerialDelivery*/ false,
                sec.wallDamageRadius, /*spawnDustOnWallBreak*/ true, /*friendlyFireImmune*/ false);
        // hasBoostRamp=true: marine rocket is a launched missile with a
        // booster, matches locust's accelerate-from-rest visual curve.
        // arcHeight=0: direct-fire, no parabolic lob.
        shots.queueProjectile(new Projectile(fromX, fromY, toX, toY,
                /*hasBoostRamp*/ true, /*arcHeight*/ 0f,
                shooter.faction, /*aerialDelivery*/ false,
                sec.flightSec, onArrival));
        shots.postShot(new ShotEvent(fromX, fromY, toX, toY, hit, shooter.faction, sec.flightSec,
                null, null, sec));
    }
}
