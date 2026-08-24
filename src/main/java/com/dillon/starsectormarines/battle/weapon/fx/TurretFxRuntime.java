package com.dillon.starsectormarines.battle.weapon.fx;

import com.dillon.starsectormarines.battle.combat.ShotEvent;
import com.dillon.starsectormarines.battle.combat.fx.ImpactFx;

/** Shared runtime routing from turret shot events to their authored FX slots. */
public final class TurretFxRuntime {

    private TurretFxRuntime() {}

    /** Emits the launch composition at the mount-authored muzzle position. */
    public static void spawnMuzzle(ImpactFx backend, ShotEvent shot) {
        if (shot.turretKind == null) return;
        backend.spawnAuthored(shot.turretKind.fx(), FxSlot.MUZZLE, muzzleContext(shot));
    }

    /** Emits both the immediate impact and its delayed authored aftermath. */
    public static void spawnImpactAndAftermath(ImpactFx backend, ShotEvent shot,
                                                boolean wallImpact) {
        if (shot.turretKind == null) return;
        FxCompositionContext context = impactContext(shot, wallImpact);
        backend.spawnAuthored(shot.turretKind.fx(), FxSlot.IMPACT, context);
        backend.spawnAuthored(shot.turretKind.fx(), FxSlot.AFTERMATH, context);
    }

    /** Emits one authored trail sample at the resolved projectile tail. */
    public static void spawnTrail(ImpactFx backend, ShotEvent shot,
                                  float x, float y, float bearingDegrees) {
        if (shot.turretKind == null) return;
        backend.spawnAuthored(shot.turretKind.fx(), FxSlot.TRAIL,
                new FxCompositionContext(x, y, bearingDegrees, false, shot.lifetime));
    }

    static FxCompositionContext muzzleContext(ShotEvent shot) {
        float dx = shot.toX - shot.fromX;
        float dy = shot.toY - shot.fromY;
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        float offset = shot.turretKind.mount().muzzleOffsetCells;
        float muzzleX = shot.fromX;
        float muzzleY = shot.fromY;
        if (length > 1e-6f) {
            muzzleX += dx / length * offset;
            muzzleY += dy / length * offset;
        }
        return new FxCompositionContext(muzzleX, muzzleY,
                bearingDeg(shot.fromX, shot.fromY, shot.toX, shot.toY),
                false, eventSeedTime(shot));
    }

    static FxCompositionContext impactContext(ShotEvent shot, boolean wallImpact) {
        return new FxCompositionContext(shot.toX, shot.visualToY(),
                bearingDeg(shot.fromX, shot.fromY, shot.toX, shot.toY),
                wallImpact, eventSeedTime(shot));
    }

    private static float eventSeedTime(ShotEvent shot) {
        return shot.fromX * 0.7548777f + shot.fromY * 0.5698403f
                + shot.toX * 0.438289f + shot.toY * 0.327491f
                + shot.lifetimeMax;
    }

    private static float bearingDeg(float fromX, float fromY, float toX, float toY) {
        float dx = toX - fromX;
        float dy = toY - fromY;
        if (dx == 0f && dy == 0f) return 0f;
        return (float) Math.toDegrees(Math.atan2(dy, dx)) - 90f;
    }
}
