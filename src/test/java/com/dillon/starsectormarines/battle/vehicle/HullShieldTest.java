package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.mech.MechFlux;
import com.dillon.starsectormarines.battle.turret.TurretShieldService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HullShieldTest {

    private static final long HULL = 1L;
    private static final long AHEAD = 2L;

    /** The hull at the origin facing +Y (body facing 0), a shooter dead ahead of it. */
    private static final TurretShieldService.Positions POSITIONS = new TurretShieldService.Positions() {
        @Override public float x(long id) { return id == HULL || id == AHEAD ? 0f : Float.NaN; }
        @Override public float y(long id) { return id == HULL ? 0f : id == AHEAD ? 10f : Float.NaN; }
    };

    @Test
    void onlyAHullWhoseTypeCarriesAnEmitterIsFitted() {
        TurretShieldService shields = new TurretShieldService();
        MechFlux reservoir = new MechFlux(100f, 10f);
        assertFalse(HullShield.fit(shields, HULL, VehicleType.LASER_TANK, 0f, reservoir));
        assertNull(shields.fitted(HULL));
        assertFalse(HullShield.fit(shields, HULL, VehicleType.HEAVY_SKIMMER, 0f, null), "no reservoir, no shield");
        assertTrue(HullShield.fit(shields, HULL, VehicleType.HEAVY_SKIMMER, 0f, reservoir));
        assertNotNull(shields.fitted(HULL));
        assertFalse(HullShield.fit(shields, HULL, VehicleType.HEAVY_SKIMMER, 0f, reservoir), "fitted once");
    }

    @Test
    void itFacesTheNoseAndChargesTheHullsReservoirForWhatItStops() {
        TurretShieldService shields = new TurretShieldService();
        MechFlux reservoir = new MechFlux(100f, 0f);
        HullShield.fit(shields, HULL, VehicleType.HEAVY_SKIMMER, 0f, reservoir);
        // Raised with no threat to turn it: it still faces the nose, +Y.
        for (int i = 0; i < 30; i++) shields.tick(1f / 30f, POSITIONS, id -> id == HULL ? AHEAD : 0L);
        float before = reservoir.value();
        assertTrue(shields.absorbs(HULL, AHEAD, 10f, POSITIONS));
        assertEquals(before + 10f * HullShield.FRONTAL.fluxPerDamage(), reservoir.value(), 1e-4f);
    }

    @Test
    void aFullReservoirOverloadsShieldAndGunsTogether() {
        TurretShieldService shields = new TurretShieldService();
        MechFlux reservoir = new MechFlux(10f, 0f);
        HullShield.fit(shields, HULL, VehicleType.HEAVY_SKIMMER, 0f, reservoir);
        for (int i = 0; i < 30; i++) shields.tick(1f / 30f, POSITIONS, id -> id == HULL ? AHEAD : 0L);
        shields.absorbs(HULL, AHEAD, 100f, POSITIONS);
        assertTrue(reservoir.overloaded());
        assertTrue(shields.overloaded(HULL));
        assertFalse(reservoir.permits(1f, true), "the guns on the pool hold fire");
        assertFalse(shields.absorbs(HULL, AHEAD, 1f, POSITIONS), "the shield is down");
    }
}
