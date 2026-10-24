package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.turret.FluxShield;
import com.dillon.starsectormarines.battle.turret.FluxShieldSpec;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TurretFluxHeatTest {

    private static final float DT = 1f / 30f;
    private static final FluxShieldSpec SPEC = new FluxShieldSpec(2f, 120f, 100f, 0f, 0f, 1f, 0f,
            0.5f, 0.5f, 2f, 90f, 1f);

    private static FluxShield raised() {
        FluxShield shield = new FluxShield(SPEC, 0f);
        for (int i = 0; i < 20; i++) shield.tick(DT, 0f);
        return shield;
    }

    @Test
    void theRadiatorsHeatWithTheFluxAndStrainOnlyNearFull() {
        FluxShield shield = raised();
        shield.absorb(0f, 50f);
        assertEquals(0.5f, TurretFluxHeat.heat(shield), 1e-4f);
        assertEquals(0f, TurretFluxHeat.strain(shield));
        shield.absorb(0f, 40f);
        assertTrue(TurretFluxHeat.strain(shield) > 0.5f);
    }

    @Test
    void aBreakVentsTheHeatAndTheRadiatorsCoolThroughTheOverload() {
        FluxShield shield = raised();
        shield.absorb(0f, 200f);
        assertEquals(1f, TurretFluxHeat.heat(shield), 1e-4f);
        assertEquals(0f, TurretFluxHeat.strain(shield));
        for (int i = 0; i < 30; i++) shield.tick(DT, 0f);
        assertEquals(0.5f, TurretFluxHeat.heat(shield), 0.02f);
    }

    @Test
    void theLampsLightInOrderWithTheCharge() {
        FluxShield shield = raised();
        shield.absorb(0f, 50f);
        // Half full: three of six lamps lit, the rest dark.
        assertEquals(1f, TurretFluxHeat.lamp(1L, shield, 0L, 0), 1e-4f);
        assertEquals(1f, TurretFluxHeat.lamp(1L, shield, 0L, 2), 1e-4f);
        assertEquals(0f, TurretFluxHeat.lamp(1L, shield, 0L, 3), 1e-4f);
        assertEquals(0f, TurretFluxHeat.lamp(1L, shield, 0L, TurretFluxHeat.LAMPS - 1), 1e-4f);
    }
}
