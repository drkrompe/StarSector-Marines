package com.dillon.starsectormarines.battle.turret;

import com.dillon.starsectormarines.battle.combat.DamageService;
import com.dillon.starsectormarines.battle.combat.BallisticResolver;
import com.dillon.starsectormarines.battle.combat.HitResponseSystem;
import com.dillon.starsectormarines.battle.combat.PendingDetonation;
import com.dillon.starsectormarines.battle.combat.Projectile;
import com.dillon.starsectormarines.battle.combat.ShotEvent;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.combat.ShotService;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.sim.CombatTelemetryService;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.battle.weapon.WeaponDef;

import java.util.Random;

/**
 * Definition-driven turret fire procedure. Ground-level burst mounts resolve physical
 * rounds; aerial and indirect mounts retain their existing accuracy/scatter
 * procedures. The system owns payload queuing and shot-event posting. Extracted
 * from {@code BattleSimulation.fireShotFrom} so the sim doesn't own weapon logic.
 * Implements {@link TurretFireSink} so consumers (AirSystem,
 * GroundSystem, TurretBehavior) receive the same functional interface they
 * already depend on.
 *
 * <p>A stateless per-shot <b>System</b> — it owns no state (every field is an
 * injected collaborator), resolving one shot per {@link #fire} call. Named
 * {@code *System}, not {@code *Service}, under the
 * Service(data-owner)/System(processor) convention — see
 * {@code ecs-nouns.md}.
 *
 * <p>Hit-response is delegated to the constructor-injected
 * {@link HitResponseSystem}.
 */
public final class TurretFireSystem implements TurretFireSink {

    private static final float SHOT_LIFETIME = 0.15f;
    private static final float MISS_OFFSET_MIN = 0.5f;
    private static final float MISS_OFFSET_MAX = 2.0f;

    private final Random rng;
    private final CellTopology topology;
    private final ShotService shots;
    private final DamageService damageService;
    private final DetonationSink detonationSink;
    private final HitResponseSystem hitResponse;
    private final World world;
    private final BallisticResolver resolver;
    private final CombatTelemetryService telemetry;

    @FunctionalInterface
    public interface DetonationSink {
        void queue(PendingDetonation det);
    }

    public TurretFireSystem(Random rng, CellTopology topology,
                            ShotService shots, DamageService damageService,
                            DetonationSink detonationSink,
                            HitResponseSystem hitResponse, World world,
                            BallisticResolver resolver,
                            CombatTelemetryService telemetry) {
        this.rng = rng;
        this.topology = topology;
        this.shots = shots;
        this.damageService = damageService;
        this.detonationSink = detonationSink;
        this.hitResponse = hitResponse;
        this.world = world;
        this.resolver = resolver;
        this.telemetry = telemetry;
    }

    @Override
    public void fire(long shooterId, float fromX, float fromY, Faction shooterFaction,
                     StructureDef structure, long target, boolean aerialShooter, boolean hasLos,
                     float mountFacingDegrees, int releaseIndex) {
        WeaponDef weapon = structure.mount.weapon;
        telemetry.recordRoundFired(shooterId);
        int tcx = world.cellX(target);
        int tcy = world.cellY(target);
        float facing = Float.isFinite(mountFacingDegrees)
                ? mountFacingDegrees
                : TurretAim.bearingTo(fromX, fromY, world.x(target), world.y(target));
        TurretMountGeometry.Point muzzle = TurretMountGeometry.muzzle(
                fromX, fromY, facing, structure.mount, releaseIndex);
        fromX = muzzle.x();
        fromY = muzzle.y();
        float distToTarget = (float) Math.sqrt(
                (tcx + 0.5f - fromX) * (tcx + 0.5f - fromX) +
                (tcy + 0.5f - fromY) * (tcy + 0.5f - fromY));
        float effectiveAccuracy = weapon.accuracy;
        if (weapon.indirectFire) {
            float distNorm = Math.min(1f, distToTarget / Math.max(0.0001f, weapon.range));
            float distFalloff = Math.max(0f, 1f - distNorm * distNorm);
            float losMult = hasLos ? 1f : weapon.noLosAccuracyMult;
            effectiveAccuracy *= distFalloff * losMult;
        }
        if (!aerialShooter && weapon.arcHeight <= 0f && weapon.projectileCellsPerSec() <= 0f) {
            fireGroundDirect(shooterId, fromX, fromY, shooterFaction,
                    structure, target, distToTarget, effectiveAccuracy);
            return;
        }

        if (weapon.projectileCellsPerSec() > 0f) {
            spawnProjectile(shooterId, fromX, fromY, shooterFaction, structure, tcx, tcy, aerialShooter,
                    distToTarget, effectiveAccuracy);
            return;
        }

        boolean hit = rng.nextFloat() < effectiveAccuracy;
        boolean isAoe = weapon.aoeRadius > 0f;
        boolean aerialDelivery = aerialShooter || weapon.arcHeight > 0f;
        float effectiveSpread = weapon.hitSpread * Math.min(1f, distToTarget / weapon.range);

        float toX, toY;
        if (hit) {
            toX = tcx + 0.5f;
            toY = tcy + 0.5f;
            if (effectiveSpread > 0f) {
                float angle = rng.nextFloat() * (float) (Math.PI * 2);
                float r = rng.nextFloat() * effectiveSpread;
                toX += (float) Math.cos(angle) * r;
                toY += (float) Math.sin(angle) * r;
            }
        } else {
            float angle = rng.nextFloat() * (float) (Math.PI * 2);
            float spread = MISS_OFFSET_MIN + rng.nextFloat() * (MISS_OFFSET_MAX - MISS_OFFSET_MIN);
            spread += effectiveSpread;
            toX = tcx + 0.5f + (float) Math.cos(angle) * spread;
            toY = tcy + 0.5f + (float) Math.sin(angle) * spread;
        }

        if (!isAoe && hit) {
            if (!aerialDelivery || !topology.isRoofIntact(tcx, tcy)) {
                telemetry.recordRoundHit(shooterId);
                damageService.applyDamage(target, shooterId, weapon.damage, weapon.penetration, 1f);
                hitResponse.rollFallbackOnHit(target);
            }
        }

        if (isAoe) {
            float flight = weapon.flightSec > 0f ? weapon.flightSec : SHOT_LIFETIME;
            detonationSink.queue(new PendingDetonation(
                    shooterId,
                    toX, toY, flight,
                    weapon.aoeRadius, weapon.damage, weapon.penetration,
                    weapon.wallDamage, shooterFaction, aerialDelivery,
                    weapon.wallDamageRadius, /*spawnDustOnWallBreak*/ true,
                    /*friendlyFireImmune*/ false, /*authoredAftermath*/ true));
        }
        float lifetime = weapon.flightSec > 0f ? weapon.flightSec : SHOT_LIFETIME;
        shots.postShot(new ShotEvent(shooterId, fromX, fromY, toX, toY, hit, shooterFaction,
                lifetime, structure, null, null));
    }

    /** Modeled ground-level path for Vulcan/Heavy-MG bursts and any future direct mount. */
    private void fireGroundDirect(long shooterId, float fromX, float fromY,
                                  Faction shooterFaction, StructureDef structure,
                                  long target, float distToTarget,
                                  float effectiveAccuracy) {
        WeaponDef weapon = structure.mount.weapon;
        float effectiveSpread = weapon.hitSpread
                * Math.min(1f, distToTarget / Math.max(0.0001f, weapon.range));
        BallisticResolver.Source source = new BallisticResolver.Source(
                shooterId, fromX, fromY, 0f, shooterFaction);
        BallisticResolver.Resolution res = resolver.resolve(
                source, target, effectiveAccuracy, effectiveSpread,
                weapon.directRoundVelocity(), rng);

        if (weapon.aoeRadius > 0f && res.impacts()) {
            queueGroundDetonation(shooterId, shooterFaction, structure, res);
        } else if (weapon.aoeRadius <= 0f && res.victimId() != 0L) {
            float appliedDamage = res.friendlyHit()
                    ? weapon.damage * BallisticResolver.FRIENDLY_FIRE_DAMAGE_MULT
                    : weapon.damage;
            shots.queueImpact(new ShotService.PendingImpact(
                    res.victimId(), shooterId, res.flightTime(), appliedDamage,
                    weapon.penetration, /*moraleImpact*/ 1f, res.friendlyHit()));
        }

        shots.postShot(new ShotEvent(fromX, fromY, 0f,
                res.endX(), res.endY(), res.endZ(),
                res.hitIntended(), shooterFaction, Math.max(res.flightTime(), 0.05f),
                structure, null, null, null, /*moraleImpact*/ 1f,
                res.victimId() != 0L, res.kind(), shooterId));
    }

    private void queueGroundDetonation(long shooterId, Faction shooterFaction,
                                       StructureDef structure,
                                       BallisticResolver.Resolution res) {
        WeaponDef weapon = structure.mount.weapon;
        boolean hasDirectPayload = res.victimId() != 0L && weapon.contactDamage > 0f;
        float directDamage = hasDirectPayload && res.friendlyHit()
                ? weapon.contactDamage * BallisticResolver.FRIENDLY_FIRE_DAMAGE_MULT
                : hasDirectPayload ? weapon.contactDamage : 0f;
        detonationSink.queue(new PendingDetonation(
                shooterId,
                res.endX(), res.endY(), res.flightTime(),
                weapon.aoeRadius, weapon.damage, weapon.penetration,
                weapon.wallDamage, shooterFaction, /*aerialDelivery*/ false,
                weapon.wallDamageRadius, /*spawnDustOnWallBreak*/ true,
                /*friendlyFireImmune*/ false,
                hasDirectPayload ? res.victimId() : 0L,
                directDamage, hasDirectPayload ? weapon.contactPenetration : 0f,
                /*authoredAftermath*/ true));
    }

    private void spawnProjectile(long shooterId, float fromX, float fromY, Faction shooterFaction,
                                 StructureDef structure, int tcx, int tcy, boolean aerialShooter,
                                 float distToTarget, float effectiveAccuracy) {
        WeaponDef weapon = structure.mount.weapon;
        boolean aerialDelivery = aerialShooter || weapon.arcHeight > 0f;

        float distScale = Math.min(1f, distToTarget / Math.max(0.0001f, weapon.range));
        boolean hit = rng.nextFloat() < effectiveAccuracy;
        float scatterRadius = weapon.hitSpread * distScale;
        if (!hit) {
            // Projectile kinds still resolve the same real accuracy roll as
            // instant/tracer kinds. A miss expands beyond the nominal impact
            // pattern instead of merely nudging every round by an
            // accuracy-dependent amount.
            scatterRadius += MISS_OFFSET_MIN
                    + rng.nextFloat() * (MISS_OFFSET_MAX - MISS_OFFSET_MIN);
        }
        float angle = rng.nextFloat() * (float) (Math.PI * 2);
        float r = rng.nextFloat() * scatterRadius;
        float toX = tcx + 0.5f + (float) Math.cos(angle) * r;
        float toY = tcy + 0.5f + (float) Math.sin(angle) * r;

        float flightTime = distToTarget / weapon.projectileCellsPerSec();

        PendingDetonation onArrival = new PendingDetonation(
                shooterId,
                toX, toY, flightTime,
                weapon.aoeRadius, weapon.damage, weapon.penetration,
                weapon.wallDamage, shooterFaction, aerialDelivery,
                weapon.wallDamageRadius, /*spawnDustOnWallBreak*/ true,
                /*friendlyFireImmune*/ false, /*authoredAftermath*/ true);
        shots.queueProjectile(new Projectile(fromX, fromY, toX, toY,
                weapon.boostRamp, weapon.arcHeight,
                shooterFaction, aerialDelivery, flightTime, onArrival, weapon.id,
                weapon.pointDefenseTarget));
        shots.postShot(new ShotEvent(shooterId, fromX, fromY, toX, toY, hit, shooterFaction,
                flightTime, structure, null, null));
    }
}
