package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.turret.FluxShield;
import com.dillon.starsectormarines.battle.turret.FluxShieldSpec;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TurretFluxCapacitorsTest {

    private static final float DT = 1f / 30f;
    private static final FluxShieldSpec SPEC = new FluxShieldSpec(2f, 120f, 100f, 0f, 0f, 1f, 0f,
            0.5f, 0.5f, 2f, 90f, 1f);

    private static FluxShield raised() {
        FluxShield shield = new FluxShield(SPEC, 0f);
        for (int i = 0; i < 20; i++) shield.tick(DT, 0f);
        return shield;
    }

    @Test
    void theCellsFollowTheFluxAndStrainOnlyNearFull() {
        FluxShield shield = raised();
        shield.absorb(0f, 50f);
        assertEquals(TurretFluxCapacitors.Phase.CHARGE, TurretFluxCapacitors.phase(shield));
        assertEquals(0.5f, TurretFluxCapacitors.gauge(shield), 1e-4f);
        assertEquals(0f, TurretFluxCapacitors.strain(shield));
        shield.absorb(0f, 40f);
        assertTrue(TurretFluxCapacitors.strain(shield) > 0.5f);
    }

    @Test
    void aBreakDumpsTheCellsThenTheyRechargeThroughTheCooldown() {
        FluxShield shield = raised();
        shield.absorb(0f, 200f);
        assertEquals(TurretFluxCapacitors.Phase.DUMP, TurretFluxCapacitors.phase(shield));
        assertEquals(1f, TurretFluxCapacitors.gauge(shield), 1e-4f);
        for (int i = 0; i < 15; i++) shield.tick(DT, 0f);
        assertEquals(TurretFluxCapacitors.Phase.RECHARGE, TurretFluxCapacitors.phase(shield));
        assertEquals(0.25f, TurretFluxCapacitors.gauge(shield), 0.02f);
        assertEquals(0f, TurretFluxCapacitors.strain(shield));
        for (int i = 0; i < 46; i++) shield.tick(DT, 0f);
        assertEquals(TurretFluxCapacitors.Phase.CHARGE, TurretFluxCapacitors.phase(shield));
    }
}
