package com.dillon.starsectormarines.battle.weapon.fx;

import com.dillon.starsectormarines.battle.combat.ShotEvent;
import com.dillon.starsectormarines.battle.combat.fx.ImpactFx;
import com.dillon.starsectormarines.battle.weapon.WeaponDef;
import org.json.JSONException;
import org.json.JSONObject;

/** Shared definition-driven routing from any weapon shot to authored FX slots. */
public final class WeaponFxRuntime {

    /** Compatibility presentation for anonymous legacy shots with no weapon definition. */
    private static final WeaponFxDef LEGACY_RIFLE = legacyRifle();

    private WeaponFxRuntime() {}

    /** Resolves the authoritative authored composition carried by a shot. */
    public static WeaponFxDef definition(ShotEvent shot) {
        WeaponDef weapon = shot.weaponDef();
        return weapon != null ? weapon.fx : LEGACY_RIFLE;
    }

    /** Emits launch and muzzle composition at their carrier-appropriate origins. */
    public static void spawnMuzzle(ImpactFx backend, ShotEvent shot) {
        WeaponFxDef fx = definition(shot);
        backend.spawnAuthored(fx, FxSlot.LAUNCH, launchContext(shot));
        backend.spawnAuthored(fx, FxSlot.MUZZLE, muzzleContext(shot));
    }

    /** Emits both the immediate impact and its delayed authored aftermath. */
    public static void spawnImpactAndAftermath(ImpactFx backend, ShotEvent shot,
                                                boolean wallImpact) {
        WeaponFxDef fx = definition(shot);
        FxCompositionContext context = impactContext(shot, wallImpact);
        backend.spawnAuthored(fx, FxSlot.IMPACT, context);
        backend.spawnAuthored(fx, FxSlot.AFTERMATH, context);
    }

    /** Emits one authored trail sample at the resolved projectile tail. */
    public static void spawnTrail(ImpactFx backend, ShotEvent shot,
                                  float x, float y, float bearingDegrees) {
        backend.spawnAuthored(definition(shot), FxSlot.TRAIL,
                new FxCompositionContext(x, y, bearingDegrees, false, shot.lifetime));
    }

    static FxCompositionContext muzzleContext(ShotEvent shot) {
        float dx = shot.toX - shot.fromX;
        float dy = shot.toY - shot.fromY;
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        float offset = shot.turretKind != null ? shot.turretKind.mount().muzzleOffsetCells : 0f;
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

    static FxCompositionContext launchContext(ShotEvent shot) {
        return new FxCompositionContext(shot.fromX, shot.fromY,
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

    private static WeaponFxDef legacyRifle() {
        try {
            return WeaponFxDef.parse("fx.legacy-rifle", new JSONObject("""
                    {"impact":[
                      {"kind":"glow","radius":0.28,"lifetime":0.10,"color":"FFE080"},
                      {"kind":"dust","radius":0.22,"lifetime":0.18}
                    ]}
                    """));
        } catch (JSONException e) {
            throw new ExceptionInInitializerError(e);
        }
    }
}
