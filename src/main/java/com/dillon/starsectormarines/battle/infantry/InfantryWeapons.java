package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.combat.DamageService;
import com.dillon.starsectormarines.battle.combat.HitResponseService;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.turret.MapTurret;
import com.dillon.starsectormarines.battle.combat.PendingDetonation;
import com.dillon.starsectormarines.battle.combat.Projectile;
import com.dillon.starsectormarines.battle.combat.ShotEvent;
import com.dillon.starsectormarines.battle.combat.ShotService;
import com.dillon.starsectormarines.battle.combat.FireStance;
import com.dillon.starsectormarines.battle.combat.RangeFalloff;
import com.dillon.starsectormarines.battle.combat.ShotEndpoint;
import com.dillon.starsectormarines.battle.turret.TurretKind;
import com.dillon.starsectormarines.battle.unit.Entity;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.sim.World;

import java.util.ArrayList;
import java.util.List;

/**
 * Handheld squad weapons — rifles, SMGs, DMRs (primary line tracers / kinetic
 * bullets) and rocket launchers (secondary, AoE). Owns the firing math + the
 * per-tick burst continuation pass for every infantry-class unit:
 * marines, militia, aliens, and any future squaddie wielding a
 * {@link MarineWeapon}.
 *
 * <p>Burst continuation state lives on each {@link Entity}
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

    private final UnitRosterService roster;
    private final DamageService damageService;
    private final HitResponseService hitResponse;
    private final ShotService shots;

    /**
     * Reused per-tick gather of the units with an active burst before the
     * continuation pass. fireShot resolves damage inline in this serial phase,
     * so a killing round releases its target and swap-and-pops the registry;
     * gathering first makes the iteration a snapshot so that release can't
     * reshuffle the slots out from under it. Only mid-burst units are gathered
     * (a small fraction), so the copy is cheap.
     */
    private final List<Entity> burstScratch = new ArrayList<>();

    public InfantryWeapons(UnitRosterService roster,
                           DamageService damageService, HitResponseService hitResponse,
                           ShotService shots) {
        this.roster = roster;
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
        // Gather the mid-burst units first (read-only over the dense registry),
        // then run the continuation pass over the snapshot — see burstScratch.
        burstScratch.clear();
        World world = roster.world();
        Entity[] dense = roster.denseArray();
        for (int i = 0, n = roster.liveCount(); i < n; i++) {
            if (world.burstRemaining(dense[i].entityId) > 0) burstScratch.add(dense[i]);
        }
        for (int i = 0, n = burstScratch.size(); i < n; i++) {
            Entity u = burstScratch.get(i);
            if (!roster.isAliveById(u.entityId)) continue; // killed earlier this pass
            long id = u.entityId;
            if (world.burstRemaining(id) <= 0) continue; // cleared earlier this pass
            float timer = world.burstTimer(id) - BattleSimulation.TICK_DT;
            world.setBurstTimer(id, timer);
            if (timer > 0f) continue;
            Entity burstTarget = roster.getOrNull(world.burstTargetId(id));
            if (burstTarget == null || u.primaryWeapon == null) {
                world.setBurstRemaining(id, 0);
                world.setBurstTargetId(id, 0L);
                continue;
            }
            // Burst follow-up: use the unit's current motion state. If they
            // walked off the firing position mid-burst, the remaining rounds
            // get the MOVING accuracy penalty — same rule a hand-rolled
            // moving-fire callsite gets. moveProgress lives in the world's
            // MOVEMENT component, read by id.
            //
            // Invariant: this read is unguarded but safe under MOVEMENT
            // membership-narrowing because only a mover reaches this pass. A
            // static emplacement (turret/hub) has no MOVEMENT, but it also never
            // writes the COMBAT burst columns — Entity.beginBurst is infantry/
            // mech/drone-only, and a MapTurret tracks its burst on its own shadow
            // fields — so its burstRemaining stays 0 and it never enters
            // burstScratch above. If turrets are ever rewired to burst via the
            // COMBAT columns, gate this on world.hasMovement(id) (a non-mover
            // is always STANCED).
            fireShot(u, burstTarget, FireStance.stanceFor(world.moveProgress(id)));
            // Combat state is keyed by entity id, so a killing round that
            // swap-and-pops the dense registry (relocating u's slot) can't
            // invalidate these post-fire writes — no slot re-resolve needed.
            int remaining = world.burstRemaining(id) - 1;
            world.setBurstRemaining(id, remaining);
            world.setBurstTimer(id, u.primaryWeapon.burstSpacing);
            if (remaining == 0) world.setBurstTargetId(id, 0L);
        }
        burstScratch.clear();
    }

    /**
     * Fires the shooter's primary at the target. Per-shot accuracy / damage /
     * vsTurret pull from the marine's {@link MarineWeapon} when assigned;
     * otherwise from the {@link Entity}'s baked-in stats (militia, aliens,
     * turrets — all the "no MarineWeapon" callers). Accuracy is multiplied
     * by {@code stance.accuracyMult} — STANCED preserves the base roll,
     * MOVING applies the on-the-move suppression penalty.
     *
     * <p>Public because behaviors call this when firing; fall-back is also
     * rolled here, which can mutate the target's path via the context.
     */
    public void fireShot(Entity shooter, Entity target, FireStance stance) {
        World world = roster.world();
        float accuracy = world.accuracy(shooter.entityId);
        float damage   = world.attackDamage(shooter.entityId);
        float vsTurretMult = 1f;
        // Distance-scaled accuracy + spread only apply when the shooter has
        // a per-weapon profile (marines). Militia / aliens / turrets fall
        // through to their baked Entity stats with flat accuracy and the
        // baseline miss-scatter ring — preserves the legacy behavior for
        // every "no MarineWeapon" caller.
        float dist = RangeFalloff.dist(world.cellX(shooter.entityId), world.cellY(shooter.entityId),
                world.cellX(target.entityId), world.cellY(target.entityId));
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
        float fromX = world.renderX(shooter.entityId) + 0.5f;
        float fromY = world.renderY(shooter.entityId) + 0.5f;
        ShotEndpoint.Endpoint ep = ShotEndpoint.resolve(
                world.renderX(target.entityId), world.renderY(target.entityId),
                hit, effectiveSpread, shooter.rng);
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
     * ({@link com.dillon.starsectormarines.battle.decision.TacticalScoring#shouldCommitRocket})
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
    public void fireSecondary(Entity shooter, Entity target) {
        World world = roster.world();
        long shooterId = shooter.entityId;
        if (!world.hasSecondaryWeapon(shooterId)) return;
        MarineSecondary sec = world.secondaryWeapon(shooterId);
        int ammo = world.secondaryAmmo(shooterId);
        if (ammo <= 0) return;
        world.setSecondaryAmmo(shooterId, ammo - 1);
        boolean hit = shooter.rng.nextFloat() < sec.accuracy;
        // Rocket launches from the marine's current sprite position so the
        // launch FX glue to the sprite if the marine is mid-step. Endpoint
        // resolves through ShotEndpoint with effectiveSpread=0 — secondaries
        // don't carry their own hitSpread today, so the universal hit-jitter
        // + miss-ring still apply but no weapon-specific scatter.
        float fromX = world.renderX(shooter.entityId) + 0.5f;
        float fromY = world.renderY(shooter.entityId) + 0.5f;
        ShotEndpoint.Endpoint ep = ShotEndpoint.resolve(
                world.renderX(target.entityId), world.renderY(target.entityId),
                hit, 0f, shooter.rng);
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
