package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.combat.Crippled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What a fitted engine makes of a hull's speed. */
class VehicleEngineTest {

    private static float topSpeedAfter(float factor, boolean crippled) {
        BicycleBody body = new BicycleBody(2f, 30f, 90f, 20f, 20f, 4f);
        body.speedFactor = factor;
        body.crippled = crippled;
        for (int i = 0; i < 200; i++) body.tickManual(1f, 0f, 0.1f);
        return body.speed;
    }

    @Test
    void anEngineScalesTheHullsTopSpeed() {
        assertEquals(4f, topSpeedAfter(VehicleEngine.STOCK.speedFactor(), false), 1e-4f);
        assertEquals(4.4f, topSpeedAfter(VehicleEngine.UPRATED.speedFactor(), false), 1e-4f);
        assertEquals(4.8f, topSpeedAfter(VehicleEngine.MILITARY.speedFactor(), false), 1e-4f);
    }

    @Test
    void aShotOutDriveStillCripplesAFittedEngineToTheSameShare() {
        float fitted = topSpeedAfter(VehicleEngine.MILITARY.speedFactor(), false);
        float crippled = topSpeedAfter(VehicleEngine.MILITARY.speedFactor(), true);
        assertTrue(crippled < fitted);
        assertEquals(fitted * Crippled.SPEED_SHARE, crippled, 1e-4f);
    }
}
