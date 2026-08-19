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
import com.dillon.starsectormarines.battle.sim.World;

import java.util.Random;

/**
 * Turret-kind fire procedure. Ground-level burst mounts resolve physical
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
 * {@code roadmap/ecs-migration/stories/entity-field-migration.md}.
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

    @FunctionalInterface
    public interface DetonationSink {
        void queue(PendingDetonation det);
    }

    public TurretFireSystem(Random rng, CellTopology topology,
                            ShotService shots, DamageService damageService,
                            DetonationSink detonationSink,
                            HitResponseSystem hitResponse, World world,
                            BallisticResolver resolver) {
        this.rng = rng;
        this.topology = topology;
        this.shots = shots;
        this.damageService = damageService;
        this.detonationSink = detonationSink;
        this.hitResponse = hitResponse;
        this.world = world;
        this.resolver = resolver;
    }

    @Override
    public void fire(long shooterId, float fromX, float fromY, Faction shooterFaction,
                     TurretKind kind, long target, boolean aerialShooter, boolean hasLos) {
        int tcx = world.cellX(target);
        int tcy = world.cellY(target);
        float distToTarget = (float) Math.sqrt(
                (tcx + 0.5f - fromX) * (tcx + 0.5f - fromX) +
                (tcy + 0.5f - fromY) * (tcy + 0.5f - fromY));
        float effectiveAccuracy = kind.accuracy;
        if (kind.indirectFire) {
            float distNorm = Math.min(1f, distToTarget / Math.max(0.0001f, kind.range));
            float distFalloff = Math.max(0f, 1f - distNorm * distNorm);
            float losMult = hasLos ? 1f : kind.noLosAccuracyMult;
            effectiveAccuracy *= distFalloff * losMult;
        }
        if (!aerialShooter && kind.arcHeight <= 0f && kind.cellsPerSec() <= 0f) {
            fireGroundDirect(shooterId, fromX, fromY, shooterFaction,
                    kind, target, distToTarget, effectiveAccuracy);
            return;
        }

        if (kind.cellsPerSec() > 0f) {
            spawnProjectile(fromX, fromY, shooterFaction, kind, tcx, tcy, aerialShooter,
                    distToTarget, effectiveAccuracy);
            return;
        }

        boolean hit = rng.nextFloat() < effectiveAccuracy;
        boolean isAoe = kind.aoeRadius > 0f;
        boolean aerialDelivery = aerialShooter || kind.arcHeight > 0f;
        float effectiveSpread = kind.hitSpread * Math.min(1f, distToTarget / kind.range);

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
                damageService.applyDamage(target, kind.damage, 1f, 1f);
                hitResponse.rollFallbackOnHit(target);
            }
        }

        if (isAoe) {
            float flight = kind.flightSec > 0f ? kind.flightSec : SHOT_LIFETIME;
            detonationSink.queue(new PendingDetonation(
                    toX, toY, flight,
                    kind.aoeRadius, kind.damage, /*vsTurretMult*/ 1f,
                    kind.wallDamage, shooterFaction, aerialDelivery,
                    kind.wallDamageRadius, /*spawnDustOnWallBreak*/ true, /*friendlyFireImmune*/ false));
        }
        float lifetime = kind.flightSec > 0f ? kind.flightSec : SHOT_LIFETIME;
        shots.postShot(new ShotEvent(fromX, fromY, toX, toY, hit, shooterFaction,
                lifetime, kind, null, null));
    }

    /** Modeled ground-level path for Vulcan/Heavy-MG bursts and any future direct mount. */
    private void fireGroundDirect(long shooterId, float fromX, float fromY,
                                  Faction shooterFaction, TurretKind kind,
                                  long target, float distToTarget,
                                  float effectiveAccuracy) {
        float effectiveSpread = kind.hitSpread
                * Math.min(1f, distToTarget / Math.max(0.0001f, kind.range));
        BallisticResolver.Source source = new BallisticResolver.Source(
                shooterId, fromX, fromY, 0f, shooterFaction);
        BallisticResolver.Resolution res = resolver.resolve(
                source, target, effectiveAccuracy, effectiveSpread,
                kind.directRoundVelocity(), rng);

        if (kind.aoeRadius > 0f && res.impacts()) {
            detonationSink.queue(new PendingDetonation(
                    res.endX(), res.endY(), res.flightTime(),
                    kind.aoeRadius, kind.damage, /*vsTurretMult*/ 1f,
                    kind.wallDamage, shooterFaction, /*aerialDelivery*/ false,
                    kind.wallDamageRadius, /*spawnDustOnWallBreak*/ true,
                    /*friendlyFireImmune*/ false));
        } else if (kind.aoeRadius <= 0f && res.victimId() != 0L) {
            float appliedDamage = res.friendlyHit()
                    ? kind.damage * BallisticResolver.FRIENDLY_FIRE_DAMAGE_MULT
                    : kind.damage;
            shots.queueImpact(new ShotService.PendingImpact(
                    res.victimId(), shooterId, res.flightTime(), appliedDamage,
                    /*vsTurretMult*/ 1f, /*moraleImpact*/ 1f, res.friendlyHit()));
        }

        shots.postShot(new ShotEvent(fromX, fromY, 0f,
                res.endX(), res.endY(), res.endZ(),
                res.hitIntended(), shooterFaction, Math.max(res.flightTime(), 0.05f),
                kind, null, null, null, /*moraleImpact*/ 1f,
                res.victimId() != 0L, res.kind()));
    }

    private void spawnProjectile(float fromX, float fromY, Faction shooterFaction,
                                 TurretKind kind, int tcx, int tcy, boolean aerialShooter,
                                 float distToTarget, float effectiveAccuracy) {
        boolean aerialDelivery = aerialShooter || kind.arcHeight > 0f;

        float distScale = Math.min(1f, distToTarget / Math.max(0.0001f, kind.range));
        boolean hit = rng.nextFloat() < effectiveAccuracy;
        float scatterRadius = kind.hitSpread * distScale;
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

        float flightTime = distToTarget / kind.cellsPerSec();

        PendingDetonation onArrival = new PendingDetonation(
                toX, toY, flightTime,
                kind.aoeRadius, kind.damage, /*vsTurretMult*/ 1f,
                kind.wallDamage, shooterFaction, aerialDelivery,
                kind.wallDamageRadius, /*spawnDustOnWallBreak*/ true, /*friendlyFireImmune*/ false);
        shots.queueProjectile(new Projectile(fromX, fromY, toX, toY,
                kind.hasBoostRamp(), kind.arcHeight,
                shooterFaction, aerialDelivery, flightTime, onArrival));
        shots.postShot(new ShotEvent(fromX, fromY, toX, toY, hit, shooterFaction,
                flightTime, kind, null, null));
    }
}
