package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.combat.BallisticResolver;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.combat.PendingDetonation;
import com.dillon.starsectormarines.battle.combat.Projectile;
import com.dillon.starsectormarines.battle.combat.ShotEvent;
import com.dillon.starsectormarines.battle.combat.ShotService;
import com.dillon.starsectormarines.battle.combat.FireStance;
import com.dillon.starsectormarines.battle.combat.RangeFalloff;
import com.dillon.starsectormarines.battle.combat.ShotEndpoint;
import com.dillon.starsectormarines.battle.combat.CoverAccuracyResolver;
import com.dillon.starsectormarines.battle.turret.TurretKind;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.sim.World;
import it.unimi.dsi.fastutil.longs.LongArrayList;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Handheld squad weapons — rifles, SMGs, DMRs (primary line tracers / kinetic
 * bullets) and rocket launchers (secondary, AoE). Owns the firing math + the
 * per-tick burst continuation pass for every infantry-class unit:
 * marines, militia, aliens, and any future squaddie wielding a
 * {@link MarineWeapon}.
 *
 * <p>Burst continuation state lives on each {@code Entity}
 * ({@code burstRemaining} / {@code burstTimer} / {@code burstTargetId}) so a
 * shared subsystem instance can serve every unit without per-shooter scratch
 * space. Services are constructor-injected; the subsystem pushes events
 * (shots, projectiles, resolved-round impacts) through them without ever
 * holding a {@code BattleSimulation} reference.
 *
 * <p>Parallel structure to {@link com.dillon.starsectormarines.battle.air.AirSystem}:
 * the sim owns one instance and pumps it once per tick via {@link #tick}.
 */
public class InfantryWeapons {

    private final UnitRosterService roster;
    private final BallisticResolver resolver;
    private final ShotService shots;
    private final CoverAccuracyResolver coverAccuracy;

    /**
     * Reused per-tick gather of the units with an active burst before the
     * continuation pass. Damage from {@code fireShot} now applies on a
     * delayed flight clock (see {@link ShotService.PendingImpact}), but a
     * unit can still die mid-loop from another source (detonation arrival,
     * a concurrently-resolving impact) and swap-and-pop the registry;
     * gathering first makes the iteration a snapshot so that release can't
     * reshuffle the slots out from under it. Only mid-burst units are gathered
     * (a small fraction), so the copy is cheap.
     */
    private final LongArrayList burstScratch = new LongArrayList();

    public InfantryWeapons(UnitRosterService roster, BallisticResolver resolver,
                           ShotService shots, CoverAccuracyResolver coverAccuracy) {
        this.roster = roster;
        this.resolver = resolver;
        this.shots = shots;
        this.coverAccuracy = coverAccuracy;
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
        long[] dense = roster.denseArray();
        for (int i = 0, n = roster.liveCount(); i < n; i++) {
            if (!roster.identity().type(dense[i]).combatant) continue; // non-combatants carry no COMBAT (no burst)
            if (world.burstRemaining(dense[i]) > 0) burstScratch.add(dense[i]);
        }
        for (int i = 0, n = burstScratch.size(); i < n; i++) {
            long u = burstScratch.getLong(i);
            if (!roster.isAliveById(u)) continue; // killed earlier this pass
            long id = u;
            // Alive + in burstScratch ⟹ combatant (gather gated on type.combatant),
            // so the COMBAT primary-weapon read is safe by id.
            MarineWeapon weapon = roster.combat().primaryWeapon(id);
            if (world.burstRemaining(id) <= 0) continue; // cleared earlier this pass
            float timer = world.burstTimer(id) - BattleSimulation.TICK_DT;
            world.setBurstTimer(id, timer);
            if (timer > 0f) continue;
            long burstTargetId = world.burstTargetId(id);
            if (!roster.isLive(burstTargetId) || weapon == null) {
                world.setBurstRemaining(id, 0);
                world.setBurstTargetId(id, 0L);
                continue;
            }
            // Burst follow-up: use the unit's current motion state. If they
            // walked off the firing position mid-burst, the remaining rounds
            // get the MOVING accuracy penalty — same rule a hand-rolled
            // moving-fire callsite gets.
            //
            // Invariant: this read is unguarded but safe under MOVEMENT
            // membership-narrowing because only a mover reaches this pass. A
            // static emplacement (turret/hub) has no MOVEMENT, but it also never
            // writes the COMBAT burst columns — CombatService.beginBurst is infantry/
            // mech/drone-only, and a turret tracks its burst on its own
            // TURRET_STATE component (battle.sim.TurretStateService) — so its
            // burstRemaining stays 0 and it never enters burstScratch above. If
            // turrets are ever rewired to burst via the COMBAT columns, gate
            // this on world.hasMovement(id) (a non-mover is always STANCED).
            fireShot(id, burstTargetId, FireStance.stanceFor(!roster.movement().settled(id)));
            // Combat state is keyed by entity id, so a killing round that
            // swap-and-pops the dense registry (relocating u's slot) can't
            // invalidate these post-fire writes — no slot re-resolve needed.
            int remaining = world.burstRemaining(id) - 1;
            world.setBurstRemaining(id, remaining);
            world.setBurstTimer(id, weapon.burstSpacing);
            if (remaining == 0) world.setBurstTargetId(id, 0L);
        }
        burstScratch.clear();
    }

    /**
     * Fires the shooter's primary at the target. Per-shot accuracy / damage /
     * vsTurret pull from the marine's {@link MarineWeapon} when assigned;
     * otherwise from the {@code Entity}'s baked-in stats (militia, aliens,
     * turrets — all the "no MarineWeapon" callers). Accuracy is multiplied
     * by {@code stance.accuracyMult} — STANCED preserves the base roll,
     * MOVING applies the on-the-move suppression penalty. Cover is NOT part
     * of this accuracy stack — {@link BallisticResolver} re-expresses it as
     * physical interception along the round's flight (see
     * {@code roadmap/ballistics/overview.md} §4).
     *
     * <p>The round's full flight is resolved once, here, at fire time; damage
     * and hit-response are NOT applied inline. A victim contact queues a
     * {@link ShotService.PendingImpact} that {@code BattleSimulation} drains
     * on the round's flight clock, in the serial SHOTS phase.
     *
     * <p>Public because behaviors call this when firing.
     */
    public void fireShot(long shooter, long target, FireStance stance) {
        World world = roster.world();
        Faction shooterFaction = roster.identity().faction(shooter);
        UnitType shooterType = roster.identity().type(shooter);
        // A shooter firing its primary is a combatant — the COMBAT primary-weapon
        // read is safe by id (null = militia/alien/turret, fall back to baked stats).
        MarineWeapon weapon = roster.combat().primaryWeapon(shooter);
        float accuracy = world.accuracy(shooter);
        float damage   = world.attackDamage(shooter);
        float vsTurretMult = 1f;
        // Distance-scaled accuracy + spread only apply when the shooter has
        // a per-weapon profile (marines). Militia / aliens / turrets fall
        // through to their baked Entity stats with flat accuracy and no
        // lateral spread.
        float dist = RangeFalloff.dist(world.x(shooter), world.y(shooter),
                world.x(target), world.y(target));
        float effectiveSpread = 0f;
        if (weapon != null) {
            float effectiveRange = world.attackRange(shooter);
            accuracy = RangeFalloff.accuracy(world.accuracy(shooter),
                    weapon.accuracyFalloff, dist, effectiveRange);
            damage   = world.attackDamage(shooter);
            vsTurretMult = weapon.vsTurretMult;
            effectiveSpread = RangeFalloff.spread(
                    InfantryCombatStats.spread(weapon,
                            roster.combat().equipmentGrade(shooter),
                            roster.combat().soldierProfile(shooter)),
                    dist, effectiveRange);
        }
        accuracy *= stance.accuracyMult;

        // Round velocity: per-weapon MarineWeapon.roundVelocity when set;
        // the null-weapon militia/alien/turret callers use the resolver's
        // flat default.
        float roundVelocity = weapon != null && weapon.roundVelocity > 0f
                ? weapon.roundVelocity
                : BallisticResolver.DEFAULT_ROUND_VELOCITY;

        BallisticResolver.Resolution res = resolver.resolve(shooter, target,
                accuracy, effectiveSpread, roundVelocity, ThreadLocalRandom.current());

        float moraleImpact = shooterType != null ? shooterType.moraleImpact : 1.0f;
        if (res.victimId() != 0L) {
            // Friendly-fire damage is pre-multiplied at queue time (see
            // PendingImpact's javadoc) — the sink applies it as-is.
            float appliedDamage = res.friendlyHit()
                    ? damage * BallisticResolver.FRIENDLY_FIRE_DAMAGE_MULT
                    : damage;
            shots.queueImpact(new ShotService.PendingImpact(res.victimId(), shooter,
                    res.flightTime(), appliedDamage, vsTurretMult, moraleImpact, res.friendlyHit()));
        }

        // Muzzle origin tracks the SHOOTER'S RENDER POSITION so the flash
        // glues to the sprite across a moving burst. Endpoint is wherever
        // the resolver's round physically stopped.
        float fromX = world.renderX(shooter);
        float fromY = world.renderY(shooter);
        TurretKind tk = shooterType.isTurret() ? roster.turretState().kind(shooter) : null;
        float lifetime = Math.max(res.flightTime(), 0.05f);
        // struckUnit: true whenever the round physically damaged someone
        // (victimId != 0 only on StopKind.UNIT_HIT), independent of whether
        // that victim was the locked target — near-miss morale must not
        // double-drain a round that actually connected.
        boolean struckUnit = res.victimId() != 0L;
        shots.postShot(new ShotEvent(fromX, fromY, 0f,
                res.endX(), res.endY(), res.endZ(),
                res.hitIntended(), shooterFaction, lifetime,
                tk, weapon, null, null, moraleImpact, struckUnit, res.kind()));
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
    public void fireSecondary(long shooter, long target) {
        World world = roster.world();
        Faction shooterFaction = roster.identity().faction(shooter);
        long shooterId = shooter;
        if (!world.hasSecondaryWeapon(shooterId)) return;
        MarineSecondary sec = world.secondaryWeapon(shooterId);
        int ammo = world.secondaryAmmo(shooterId);
        if (ammo <= 0) return;
        world.setSecondaryAmmo(shooterId, ammo - 1);
        float secondaryAccuracy = Math.min(1f, sec.accuracy
                * InfantryCombatStats.shooterAccuracyMult(
                        roster.combat().soldierProfile(shooter)));
        // Handheld rockets are direct-fire, so a target correctly tucked
        // behind cover is harder to center in the impact pattern too.
        secondaryAccuracy = coverAccuracy.apply(secondaryAccuracy,
                world.cellX(target), world.cellY(target),
                world.cellX(shooter), world.cellY(shooter));
        boolean hit = ThreadLocalRandom.current().nextFloat() < secondaryAccuracy;
        // Rocket launches from the marine's current sprite position so the
        // launch FX glue to the sprite if the marine is mid-step. Endpoint
        // resolves through ShotEndpoint with effectiveSpread=0 — secondaries
        // don't carry their own hitSpread today, so the universal hit-jitter
        // + miss-ring still apply but no weapon-specific scatter.
        float fromX = world.renderX(shooter);
        float fromY = world.renderY(shooter);
        ShotEndpoint.Endpoint ep = ShotEndpoint.resolve(
                world.renderX(target), world.renderY(target),
                hit, 0f, ThreadLocalRandom.current());
        float toX = ep.x();
        float toY = ep.y();
        // Marine handheld rocket is direct-fire (no arc) — explodes wherever
        // the round lands. Reaches a roofed interior only via a doorway, in
        // which case the splash should damage the inside normally, not be
        // intercepted by the roof above.
        PendingDetonation onArrival = new PendingDetonation(
                toX, toY, sec.flightSec,
                sec.aoeRadius, sec.damage, sec.vsTurretMult,
                sec.wallDamage, shooterFaction, /*aerialDelivery*/ false,
                sec.wallDamageRadius, /*spawnDustOnWallBreak*/ true, /*friendlyFireImmune*/ false);
        // hasBoostRamp=true: marine rocket is a launched missile with a
        // booster, matches locust's accelerate-from-rest visual curve.
        // arcHeight=0: direct-fire, no parabolic lob.
        shots.queueProjectile(new Projectile(fromX, fromY, toX, toY,
                /*hasBoostRamp*/ true, /*arcHeight*/ 0f,
                shooterFaction, /*aerialDelivery*/ false,
                sec.flightSec, onArrival));
        shots.postShot(new ShotEvent(fromX, fromY, toX, toY, hit, shooterFaction, sec.flightSec,
                null, null, sec));
    }
}
