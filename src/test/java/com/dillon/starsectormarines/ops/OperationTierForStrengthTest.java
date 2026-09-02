package com.dillon.starsectormarines.ops;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OperationTierForStrengthTest {

    @Test
    void anUnestimatedOrImpossibleLandingSitsAtTheFloor() {
        assertEquals(OperationTier.FIRST_CONTRACT,
                OperationTierForStrength.forGroundStrength(0f));
        assertEquals(OperationTier.FIRST_CONTRACT,
                OperationTierForStrength.forGroundStrength(-50f));
        assertEquals(OperationTier.FIRST_CONTRACT,
                OperationTierForStrength.forGroundStrength(Float.NaN));
    }

    /** A tier's own defender base is the largest landing it still holds. */
    @Test
    void eachTiersBaseIsTheTopOfItsBand() {
        for (OperationTier tier : OperationTier.values()) {
            assertEquals(tier, OperationTierForStrength.forGroundStrength(tier.defenderBase),
                    "at " + tier.defenderBase);
        }
    }

    @Test
    void oneOverABaseStepsToTheNextTier() {
        assertEquals(OperationTier.ESTABLISHED,
                OperationTierForStrength.forGroundStrength(15f));
        assertEquals(OperationTier.VETERAN,
                OperationTierForStrength.forGroundStrength(45f));
        assertEquals(OperationTier.REINFORCED,
                OperationTierForStrength.forGroundStrength(106f));
        assertEquals(OperationTier.FULL_STRENGTH,
                OperationTierForStrength.forGroundStrength(176f));
    }

    @Test
    void aLandingBiggerThanTheLadderCapsAtFullStrength() {
        assertEquals(OperationTier.FULL_STRENGTH,
                OperationTierForStrength.forGroundStrength(281f));
        assertEquals(OperationTier.FULL_STRENGTH,
                OperationTierForStrength.forGroundStrength(10_000f));
    }

    @Test
    void tierNeverFallsAsStrengthRises() {
        OperationTier previous = OperationTierForStrength.forGroundStrength(0f);
        for (float strength = 0.5f; strength <= 400f; strength += 0.5f) {
            OperationTier tier = OperationTierForStrength.forGroundStrength(strength);
            assertTrue(tier.ordinal() >= previous.ordinal(),
                    "tier fell at strength " + strength);
            previous = tier;
        }
        assertEquals(OperationTier.FULL_STRENGTH, previous);
    }
}
