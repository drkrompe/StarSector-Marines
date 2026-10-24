package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.turret.FluxShield;
import com.dillon.starsectormarines.battle.turret.FluxShieldSpec;
import com.dillon.starsectormarines.battle.turret.ShieldTint;
import com.dillon.starsectormarines.render2d.BattleCamera;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TurretShieldFieldTest {

    private static final FluxShieldSpec SPEC = new FluxShieldSpec(2f, 120f, 100f, 10f, 5f, 1f, 2f,
            0.5f, 0.5f, 2f, 90f, 1f);

    private static boolean describe(FluxShield shield, ShieldDomeDraw draw) {
        BattleCamera camera = new BattleCamera(100, 100);
        camera.setViewport(0, 0, 800, 600, 10);
        return TurretShieldField.describe(draw, 1L, 50f, 50f, shield, ShieldTint.BLUE, camera, camera.cellPxSize(), 0L, 1f);
    }

    private static FluxShield raisedFacingWest() {
        FluxShield shield = new FluxShield(SPEC, 180f);
        for (int i = 0; i < 20; i++) shield.tick(1f / 30f, 180f);
        return shield;
    }

    @Test
    void aFoldedShieldIsNotDrawn() {
        assertFalse(describe(new FluxShield(SPEC, 0f), new ShieldDomeDraw()));
    }

    @Test
    void aRaisedShieldIsItsArcInBlue() {
        ShieldDomeDraw draw = new ShieldDomeDraw();
        assertTrue(describe(raisedFacingWest(), draw));
        assertTrue(draw.arcDirX < -0.99f, "faces west on screen");
        assertEquals((float) Math.cos(Math.toRadians(60)), draw.arcCos, 1e-4f);
        assertArrayEquals(TurretShieldField.BLUE, draw.tint);
    }

    @Test
    void aBlockLightsTheRimOnTheShootersSide() {
        FluxShield shield = raisedFacingWest();
        shield.absorb(180f, 10f);
        ShieldDomeDraw draw = new ShieldDomeDraw();
        describe(shield, draw);
        assertEquals(1, draw.impactCount);
        assertTrue(draw.impacts[0] < -0.8f);
        assertTrue(draw.hitFlash > 0.9f);
    }

    @Test
    void aBrokenShieldShattersAcrossTheArcItHeldThenFlickersOut() {
        FluxShield shield = raisedFacingWest();
        shield.absorb(180f, 500f);
        ShieldDomeDraw draw = new ShieldDomeDraw();
        assertTrue(describe(shield, draw));
        assertTrue(draw.overloaded);
        assertTrue(draw.dropFlash > 0.9f);
        assertEquals((float) Math.cos(Math.toRadians(60)), draw.arcCos, 1e-4f);
        assertEquals(0f, TurretShieldField.shatter(TurretShieldField.SHATTER_SECONDS));
    }
}
