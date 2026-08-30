package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.combat.fx.ImpactFx;
import com.dillon.starsectormarines.battle.combat.fx.OrdnanceRelease;
import com.dillon.starsectormarines.battle.weapon.fx.FxCompositionContext;
import com.dillon.starsectormarines.battle.weapon.fx.FxSlot;
import com.dillon.starsectormarines.battle.weapon.fx.WeaponFxDef;

/**
 * Routes a delivered round to its authored particle composition and its ground
 * light, at release and again at arrival.
 *
 * <p>Sibling of {@code WeaponFxRuntime} and deliberately the same shape: the
 * composition is resolved from the delivery, both ends are separate calls
 * because they happen at different times, and neither call knows what carried
 * the round.
 *
 * <p>A delivery that declares no composition takes the shared heavy-blast
 * recipe instead — the same one an airframe cooking off on its stand and
 * orbital fire already use. A bomb is not a big shell with more particles; it
 * is the blast the game already knows how to draw, put where the bomb landed.
 */
public final class OrdnanceFxRuntime {

    private OrdnanceFxRuntime() {}

    /** Muzzle composition and flash at the point the round left. */
    public static void spawnRelease(ImpactFx particles, GroundLightService lights,
                                    OrdnanceRelease release) {
        OrdnanceFx fx = OrdnanceFx.of(release.delivery());
        WeaponFxDef composition = fx.fx();
        if (composition == null) return;
        if (particles != null) {
            particles.spawnAuthored(composition, FxSlot.MUZZLE, context(release, true));
        }
        if (lights != null) {
            lights.spawnImpact(composition, release.fromX(), release.fromY());
        }
    }

    /** Impact composition, aftermath, and light where the round landed. */
    public static void spawnArrival(ImpactFx particles, GroundLightService lights,
                                    OrdnanceRelease release) {
        OrdnanceFx fx = OrdnanceFx.of(release.delivery());
        if (fx.heavyImpact()) {
            if (particles != null) {
                particles.spawnHeavyImpact(release.toX(), release.toY(), release.radiusCells());
            }
            if (lights != null) {
                lights.spawnHeavyImpact(release.toX(), release.toY(), release.radiusCells());
            }
            return;
        }
        WeaponFxDef composition = fx.fx();
        if (composition == null) return;
        FxCompositionContext context = context(release, false);
        if (particles != null) {
            particles.spawnAuthored(composition, FxSlot.IMPACT, context);
            particles.spawnAuthored(composition, FxSlot.AFTERMATH, context);
        }
        if (lights != null) {
            lights.spawnImpact(composition, release.toX(), release.toY());
        }
    }

    private static FxCompositionContext context(OrdnanceRelease release, boolean atMuzzle) {
        return new FxCompositionContext(
                atMuzzle ? release.fromX() : release.toX(),
                atMuzzle ? release.fromY() : release.toY(),
                release.bearingDegrees(), /*wallImpact*/ false, release.seed());
    }
}
