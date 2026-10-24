package com.dillon.starsectormarines.battle.turret;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TurretShieldServiceTest {

    private static final FluxShieldSpec SPEC = new FluxShieldSpec(2f, 120f, 100f, 10f, 5f, 1f, 2f,
            0.5f, 0.5f, 2f, 720f, 1f);
    private static final long GUN = 1L;
    private static final long EAST = 2L;
    private static final long WEST = 3L;

    /** The gun at the origin, one shooter east of it and one west. */
    private static final TurretShieldService.Positions POSITIONS = new TurretShieldService.Positions() {
        @Override public float x(long id) { return id == EAST ? 10f : id == WEST ? -10f : id == GUN ? 0f : Float.NaN; }
        @Override public float y(long id) { return id == GUN || id == EAST || id == WEST ? 0f : Float.NaN; }
    };

    @Test
    void itFacesTheGunsTargetAndStopsRoundsFromThatSideOnly() {
        TurretShieldService shields = new TurretShieldService();
        shields.fit(GUN, SPEC, ShieldTint.BLUE, 0f);
        for (int i = 0; i < 20; i++) shields.tick(1f / 30f, POSITIONS, id -> EAST);
        assertTrue(shields.absorbs(GUN, EAST, 5f, POSITIONS));
        assertFalse(shields.absorbs(GUN, WEST, 5f, POSITIONS));
        assertFalse(shields.absorbs(GUN, 0L, 5f, POSITIONS), "damage credited to no one is never stopped");
        assertFalse(shields.absorbs(GUN, 99L, 5f, POSITIONS), "nor a shooter with no position");
    }

    @Test
    void beingShotFromTheFlankSwingsItRound() {
        TurretShieldService shields = new TurretShieldService();
        shields.fit(GUN, SPEC, ShieldTint.BLUE, 0f);
        assertFalse(shields.absorbs(GUN, WEST, 5f, POSITIONS));
        for (int i = 0; i < 20; i++) shields.tick(1f / 30f, POSITIONS, id -> 0L);
        assertEquals(180f, Math.abs(shields.fitted(GUN).shield.facingDegrees()), 1e-3f);
        assertTrue(shields.absorbs(GUN, WEST, 5f, POSITIONS));
    }

    @Test
    void anOverloadSilencesTheGun() {
        TurretShieldService shields = new TurretShieldService();
        shields.fit(GUN, SPEC, ShieldTint.BLUE, 0f);
        for (int i = 0; i < 20; i++) shields.tick(1f / 30f, POSITIONS, id -> EAST);
        assertFalse(shields.silenced(GUN));
        shields.absorbs(GUN, EAST, 200f, POSITIONS);
        assertTrue(shields.silenced(GUN));
        assertFalse(shields.silenced(EAST), "an unfitted turret is never silenced");
    }
}
