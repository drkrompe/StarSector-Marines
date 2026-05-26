package com.dillon.starsectormarines.battle.turret;

import com.dillon.starsectormarines.battle.damage.DamageService;
import com.dillon.starsectormarines.battle.damage.HitResponseService;
import com.dillon.starsectormarines.battle.fx.PendingDetonation;
import com.dillon.starsectormarines.battle.fx.Projectile;
import com.dillon.starsectormarines.battle.fx.ShotEvent;
import com.dillon.starsectormarines.battle.map.CellTopology;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.shots.ShotService;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.Unit;
import com.dillon.starsectormarines.battle.weapons.ShotRaycast;

import java.util.Random;

/**
 * Turret-kind fire procedure — accuracy roll, scatter, wall raycast, damage
 * application, detonation/projectile queuing, and shot-event posting. Extracted
 * from {@code BattleSimulation.fireShotFrom} so the sim doesn't own weapon
 * logic. Implements {@link TurretFireSink} so consumers (AirSystem,
 * GroundSystem, TurretBehavior) receive the same functional interface they
 * already depend on.
 *
 * <p>Hit-response is delegated to the constructor-injected
 * {@link HitResponseService}.
 */
public final class TurretFireService implements TurretFireSink {

    private static final float SHOT_LIFETIME = 0.15f;
    private static final float MISS_OFFSET_MIN = 0.5f;
    private static final float MISS_OFFSET_MAX = 2.0f;

    private final Random rng;
    private final NavigationGrid grid;
    private final CellTopology topology;
    private final ShotService shots;
    private final DamageService damageService;
    private final DetonationSink detonationSink;
    private final HitResponseService hitResponse;

    @FunctionalInterface
    public interface DetonationSink {
        void queue(PendingDetonation det);
    }

    public TurretFireService(Random rng, NavigationGrid grid, CellTopology topology,
                             ShotService shots, DamageService damageService,
                             DetonationSink detonationSink,
                             HitResponseService hitResponse) {
        this.rng = rng;
        this.grid = grid;
        this.topology = topology;
        this.shots = shots;
        this.damageService = damageService;
        this.detonationSink = detonationSink;
        this.hitResponse = hitResponse;
    }

    @Override
    public void fire(float fromX, float fromY, Faction shooterFaction,
                     TurretKind kind, Unit target, boolean aerialShooter, boolean hasLos) {
        float distToTarget = (float) Math.sqrt(
                (target.getCellX() + 0.5f - fromX) * (target.getCellX() + 0.5f - fromX) +
                (target.getCellY() + 0.5f - fromY) * (target.getCellY() + 0.5f - fromY));
        float effectiveAccuracy = kind.accuracy;
        if (kind.indirectFire) {
            float distNorm = Math.min(1f, distToTarget / Math.max(0.0001f, kind.range));
            float distFalloff = Math.max(0f, 1f - distNorm * distNorm);
            float losMult = hasLos ? 1f : kind.noLosAccuracyMult;
            effectiveAccuracy *= distFalloff * losMult;
        }

        if (kind.cellsPerSec() > 0f) {
            spawnProjectile(fromX, fromY, shooterFaction, kind, target, aerialShooter,
                    distToTarget, effectiveAccuracy);
            return;
        }

        boolean hit = rng.nextFloat() < effectiveAccuracy;
        boolean isAoe = kind.aoeRadius > 0f;
        boolean aerialDelivery = aerialShooter || kind.arcHeight > 0f;
        float effectiveSpread = kind.hitSpread * Math.min(1f, distToTarget / kind.range);

        float toX, toY;
        if (hit) {
            toX = target.getCellX() + 0.5f;
            toY = target.getCellY() + 0.5f;
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
            toX = target.getCellX() + 0.5f + (float) Math.cos(angle) * spread;
            toY = target.getCellY() + 0.5f + (float) Math.sin(angle) * spread;
        }

        ShotRaycast.Result snapped = ShotRaycast.resolve(
                grid, kind.raycastShots, fromX, fromY, toX, toY, hit);
        toX = snapped.toX();
        toY = snapped.toY();
        hit = snapped.hit();

        if (!isAoe && hit) {
            if (!aerialDelivery || !topology.isRoofIntact(target.getCellX(), target.getCellY())) {
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

    private void spawnProjectile(float fromX, float fromY, Faction shooterFaction,
                                 TurretKind kind, Unit target, boolean aerialShooter,
                                 float distToTarget, float effectiveAccuracy) {
        boolean aerialDelivery = aerialShooter || kind.arcHeight > 0f;

        float distScale = Math.min(1f, distToTarget / Math.max(0.0001f, kind.range));
        float accScatterMult = 2f - Math.max(0f, Math.min(1f, effectiveAccuracy));
        float scatterRadius = kind.hitSpread * distScale * accScatterMult;
        float angle = rng.nextFloat() * (float) (Math.PI * 2);
        float r = rng.nextFloat() * scatterRadius;
        float toX = target.getCellX() + 0.5f + (float) Math.cos(angle) * r;
        float toY = target.getCellY() + 0.5f + (float) Math.sin(angle) * r;

        float flightTime = distToTarget / kind.cellsPerSec();

        PendingDetonation onArrival = new PendingDetonation(
                toX, toY, flightTime,
                kind.aoeRadius, kind.damage, /*vsTurretMult*/ 1f,
                kind.wallDamage, shooterFaction, aerialDelivery,
                kind.wallDamageRadius, /*spawnDustOnWallBreak*/ true, /*friendlyFireImmune*/ false);
        shots.queueProjectile(new Projectile(fromX, fromY, toX, toY,
                kind.hasBoostRamp(), kind.arcHeight,
                shooterFaction, aerialDelivery, flightTime, onArrival));
        shots.postShot(new ShotEvent(fromX, fromY, toX, toY, /*hit*/ true, shooterFaction,
                flightTime, kind, null, null));
    }
}
