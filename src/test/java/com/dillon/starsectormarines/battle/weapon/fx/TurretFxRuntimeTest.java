package com.dillon.starsectormarines.battle.weapon.fx;

import com.dillon.starsectormarines.battle.combat.ShotEvent;
import com.dillon.starsectormarines.battle.turret.TurretKind;
import com.dillon.starsectormarines.battle.unit.Faction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class TurretFxRuntimeTest {

    private static final float EPS = 0.0001f;

    @Test
    void locustLaunchContextStaysAtMountAndBackblastRotatesOppositeEveryBearing()
            throws Exception {
        assertBackblast(4f, 5f, 9f, 5f, -90f, -1f, 0f);
        assertBackblast(4f, 5f, 4f, 10f, 0f, 0f, -1f);
        assertBackblast(4f, 5f, -1f, 5f, 90f, 1f, 0f);
        assertBackblast(4f, 5f, 4f, 0f, -180f, 0f, 1f);
    }

    private static void assertBackblast(float fromX, float fromY, float toX, float toY,
                                        float bearing, float expectedXSign,
                                        float expectedYSign) throws Exception {
        ShotEvent shot = new ShotEvent(fromX, fromY, toX, toY, true,
                Faction.DEFENDER, 0.15f, TurretKind.LOCUST);
        FxCompositionContext context = TurretFxRuntime.launchContext(shot);
        assertEquals(fromX, context.x(), EPS);
        assertEquals(fromY, context.y(), EPS);
        assertEquals(bearing, context.bearingDegrees(), EPS);
        FxParticleCommand command = WeaponFxComposer.compose(
                TurretKind.LOCUST.fx(), FxSlot.LAUNCH, context).get(0);
        assertFalse(TurretKind.LOCUST.fx().layers(FxSlot.MUZZLE).isEmpty());
        if (expectedXSign != 0f) {
            assertEquals(expectedXSign, Math.signum(command.velocityX()), EPS);
        }
        if (expectedYSign != 0f) {
            assertEquals(expectedYSign, Math.signum(command.velocityY()), EPS);
        }
    }
}
