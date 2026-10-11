package com.dillon.starsectormarines.battle.warship;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The point-defence hit rule, asked of the pure functions with hand-built weapons. */
class WarshipPointDefenceTest {

    private static VanillaWeapon gun(float rangePx, float speedPx, float spread, int burst, boolean beam) {
        return new VanillaWeapon("t", 0f, "FRAGMENTATION", rangePx, burst, 0.1f, 1f, speedPx, spread, 60f,
                beam, 0f, 0f, false, true, 0f, 0f, null, null, null, null, 0f, 0f, null, 0f,
                null, null, null, null, null, null, null);
    }

    @Test
    void accuracyFallsToHalfAtFullReach() {
        VanillaWeapon w = gun(500f, 700f, 0f, 1, false);
        float reach = WarshipScale.cells(500f);
        float close = WarshipPointDefence.roundHitChance(w, 0f, 0f);
        float far = WarshipPointDefence.roundHitChance(w, reach, 0f);
        assertEquals(WarshipPointDefence.SHOT_ACCURACY, close, 1e-5);
        assertEquals(close * 0.5f, far, 1e-5);
    }

    @Test
    void fasterOrdnanceIsHarderToLeadWithAFloor() {
        VanillaWeapon w = gun(500f, 700f, 0f, 1, false);
        float roundSpeed = WarshipScale.cells(700f);
        float slow = WarshipPointDefence.roundHitChance(w, 0f, roundSpeed * 0.2f);
        float fast = WarshipPointDefence.roundHitChance(w, 0f, roundSpeed * 1.0f);
        float absurd = WarshipPointDefence.roundHitChance(w, 0f, roundSpeed * 100f);
        assertTrue(slow > fast);
        assertEquals(WarshipPointDefence.SHOT_ACCURACY * WarshipPointDefence.MIN_LEAD, absurd, 1e-5);
    }

    @Test
    void aBeamIsHitscanAndIgnoresLead() {
        VanillaWeapon beam = gun(500f, 0f, 0f, 1, true);
        float chance = WarshipPointDefence.roundHitChance(beam, 0f, 1000f);
        assertEquals(WarshipPointDefence.BEAM_ACCURACY, chance, 1e-5);
    }

    @Test
    void aWiderConeCostsAccuracyToAFloor() {
        float tight = WarshipPointDefence.roundHitChance(gun(500f, 700f, 0f, 1, false), 0f, 0f);
        float loose = WarshipPointDefence.roundHitChance(gun(500f, 700f, 10f, 1, false), 0f, 0f);
        float hopeless = WarshipPointDefence.roundHitChance(gun(500f, 700f, 90f, 1, false), 0f, 0f);
        assertTrue(tight > loose && loose > hopeless);
        assertEquals(tight * 0.25f, hopeless, 1e-5);
    }

    @Test
    void aBurstKillsIfAnyRoundDoes() {
        float one = WarshipPointDefence.engagementKillChance(gun(500f, 700f, 0f, 1, false), 5f, 10f);
        float three = WarshipPointDefence.engagementKillChance(gun(500f, 700f, 0f, 3, false), 5f, 10f);
        float p = WarshipPointDefence.roundHitChance(gun(500f, 700f, 0f, 1, false), 5f, 10f);
        assertEquals(p, one, 1e-5);
        assertEquals(1f - (float) Math.pow(1f - p, 3), three, 1e-5);
        assertTrue(three > one && three < 1f);
    }
}
