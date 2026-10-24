package com.dillon.starsectormarines.battle.turret;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FluxShieldTest {

    private static final float DT = 1f / 30f;
    /** A 120-degree arc, 100 flux, shedding 10/s with 5/s upkeep; up in 0.5s, down in 0.5s, 2s overload. */
    private static final FluxShieldSpec SPEC = new FluxShieldSpec(2f, 120f, 100f, 10f, 5f, 1f, 2f,
            0.5f, 0.5f, 2f, 90f, 1f);

    private static FluxShield raised() {
        FluxShield shield = new FluxShield(SPEC, 0f);
        run(shield, 0.6f, 0f);
        return shield;
    }

    private static void run(FluxShield shield, float seconds, float threat) {
        for (float t = 0f; t < seconds; t += DT) shield.tick(DT, threat);
    }

    @Test
    void itIsDownUntilThreatenedAndGrowsInOverItsRaiseTime() {
        FluxShield shield = new FluxShield(SPEC, 0f);
        run(shield, 1f, Float.NaN);
        assertEquals(0f, shield.extent());
        assertFalse(shield.covers(0f));
        run(shield, 0.25f, 0f);
        assertTrue(shield.extent() > 0.4f && shield.extent() < 0.6f);
        assertTrue(shield.covers(20f), "half grown, it covers the middle of its arc");
        assertFalse(shield.covers(50f), "and not yet its edge");
        run(shield, 0.3f, 0f);
        assertEquals(1f, shield.extent());
    }

    @Test
    void itBlocksTheFrontArcAndLeavesFlanksAndRearOpen() {
        FluxShield shield = raised();
        assertTrue(shield.absorb(0f, 10f));
        assertTrue(shield.absorb(-55f, 10f));
        assertFalse(shield.absorb(90f, 10f), "flank");
        assertFalse(shield.absorb(180f, 10f), "rear");
    }

    @Test
    void aBlockedRoundChargesTheCapacitorsAndUpkeepIsUnderDissipation() {
        FluxShield shield = raised();
        assertEquals(0f, shield.flux(), 1e-4f, "held up idle, it does not fill");
        shield.absorb(0f, 30f);
        assertEquals(30f, shield.flux(), 1e-4f);
        run(shield, 1f, 0f);
        assertEquals(25f, shield.flux(), 0.5f, "sheds dissipation less upkeep while up");
    }

    @Test
    void fullCapacitorsBreakItSilenceTheGunAndVentBeforeItRisesAgain() {
        FluxShield shield = raised();
        assertTrue(shield.absorb(0f, 150f), "the round that fills it is still stopped");
        assertTrue(shield.overloaded());
        assertEquals(0f, shield.extent());
        assertEquals(0f, shield.sinceBreak());
        assertFalse(shield.absorb(0f, 10f), "broken, it stops nothing");
        run(shield, 1f, 0f);
        assertTrue(shield.overloaded());
        assertEquals(50f, shield.flux(), 2f, "venting through the overload");
        run(shield, 1.1f, 0f);
        assertFalse(shield.overloaded());
        assertEquals(0f, shield.flux(), 1e-4f);
        run(shield, 0.6f, 0f);
        assertTrue(shield.covers(0f), "threatened still, it goes back up");
    }

    @Test
    void itLingersAfterTheLastThreatThenFolds() {
        FluxShield shield = raised();
        run(shield, 0.9f, Float.NaN);
        assertEquals(1f, shield.extent(), "still within linger");
        run(shield, 0.7f, Float.NaN);
        assertEquals(0f, shield.extent());
    }

    @Test
    void itSwingsTowardTheThreatAtItsTurnRate() {
        FluxShield shield = raised();
        run(shield, 0.5f, 180f);
        assertEquals(45f, Math.abs(shield.facingDegrees()), 3.5f);
        assertFalse(shield.covers(180f), "the rear is open until the arc comes round");
        run(shield, 2f, 180f);
        assertTrue(shield.covers(180f));
    }

    @Test
    void firingCostsFluxOnlyWithTheShieldUp() {
        FluxShield down = new FluxShield(SPEC, 0f);
        down.noteRound();
        down.tick(DT, Float.NaN);
        assertEquals(0f, down.flux());
        FluxShield up = raised();
        for (int i = 0; i < 10; i++) up.noteRound();
        up.tick(DT, 0f);
        assertEquals(20f, up.flux(), 0.5f);
    }
}
