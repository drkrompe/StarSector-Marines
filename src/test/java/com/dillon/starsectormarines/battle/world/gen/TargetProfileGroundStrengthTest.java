package com.dillon.starsectormarines.battle.world.gen;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The two defence numbers the bridge carries: how they default, how they
 * normalize, and that a faction swap does not disturb them.
 */
class TargetProfileGroundStrengthTest {

    /**
     * The generator's own callers describe a world, not a garrison, so the
     * eight-argument shape has to keep working and read as "no defence stated".
     */
    @Test
    void aProfileThatStatesNoDefenceReadsAsNone() {
        TargetProfile profile = new TargetProfile(5, 6, 2, 1, "hegemony",
                EnumSet.noneOf(EconomicFunction.class),
                SurfacePalette.ROCK, SettlementLink.ROAD);

        assertEquals(0f, profile.groundDefence());
        assertEquals(0f, profile.stationedStrength());
    }

    @Test
    void theNeutralReadHasNoDefenceOfItsOwn() {
        assertEquals(0f, TargetProfile.NEUTRAL.groundDefence());
        assertEquals(0f, TargetProfile.NEUTRAL.stationedStrength());
    }

    /** Defence strengths are magnitudes; nonsense normalizes rather than escapes. */
    @Test
    void aNegativeOrAbsentStrengthNormalizesToNone() {
        TargetProfile profile = market(-40f, Float.NaN);

        assertEquals(0f, profile.groundDefence());
        assertEquals(0f, profile.stationedStrength());
    }

    /**
     * A faction swap is a controlled comparison: it changes who is standing on
     * the battlefield and nothing about the market underneath them.
     */
    @Test
    void aFactionSwapCarriesTheMarketsOwnStrength() {
        TargetProfile swapped = market(420f, 37.5f).withFactionId("pirates");

        assertEquals("pirates", swapped.factionId());
        assertEquals(420f, swapped.groundDefence());
        assertEquals(37.5f, swapped.stationedStrength());
    }

    private static TargetProfile market(float groundDefence, float stationedStrength) {
        return new TargetProfile(5, 6, 2, 1, "hegemony",
                EnumSet.noneOf(EconomicFunction.class),
                SurfacePalette.ROCK, SettlementLink.ROAD,
                groundDefence, stationedStrength);
    }
}
