package com.dillon.starsectormarines.campaign.systems;

import com.dillon.starsectormarines.campaign.ContractType;
import com.dillon.starsectormarines.campaign.HouseRank;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ContractOfferTemplateTest {

    @Test
    void tierOneAlwaysKeepsShippedStrikeShape() {
        ContractOfferTemplate template = ContractOfferTemplate.roll(
                HouseRank.TIER_1, floatRandom(0f));

        assertEquals(ContractType.STRIKE, template.type);
        assertEquals(25_000, template.payout);
        assertEquals(60, template.salvageBaseline & 0xFF);
        assertEquals(1, template.phasesTotal & 0xFF);
    }

    @Test
    void tierTwoCanRollEscortOrStrike() {
        ContractOfferTemplate escort = ContractOfferTemplate.roll(
                HouseRank.TIER_2, sequenceRandom(new float[]{0.50f, 0.10f}, false));
        ContractOfferTemplate strike = ContractOfferTemplate.roll(
                HouseRank.TIER_2, sequenceRandom(new float[]{0.50f, 0.90f}, false));

        assertEquals(ContractType.ESCORT, escort.type);
        assertEquals(45_000, escort.payout);
        assertEquals(10, escort.salvageBaseline & 0xFF);
        assertEquals(ContractType.STRIKE, strike.type);
        assertEquals(37_500, strike.payout);
        assertEquals(60, strike.salvageBaseline & 0xFF);
    }

    @Test
    void tierTwoStationingBranchSplitsGarrisonAndCadre() {
        ContractOfferTemplate garrison = ContractOfferTemplate.roll(
                HouseRank.TIER_2, sequenceRandom(new float[]{0.10f}, true));
        ContractOfferTemplate cadre = ContractOfferTemplate.roll(
                HouseRank.TIER_2, sequenceRandom(new float[]{0.10f}, false));

        assertEquals(ContractType.GARRISON, garrison.type);
        assertEquals(0, garrison.payout);
        assertEquals(25, garrison.salvageBaseline & 0xFF);
        assertEquals(ContractType.CADRE, cadre.type);
        assertEquals(0, cadre.payout);
        assertEquals(5, cadre.salvageBaseline & 0xFF);
    }

    @Test
    void tierThreeUsesThreeTimesBaselineAndTierFourHasNoStandardOffer() {
        ContractOfferTemplate escort = ContractOfferTemplate.roll(
                HouseRank.TIER_3, sequenceRandom(new float[]{0.50f, 0.50f, 0f}, false));

        assertEquals(90_000, escort.payout);
        assertNull(ContractOfferTemplate.roll(HouseRank.TIER_4, floatRandom(0f)));
        assertNull(ContractOfferTemplate.roll(null, floatRandom(0f)));
    }

    @Test
    void tierThreeCanRollThreeToFivePhasePlanetaryAssault() {
        ContractOfferTemplate assault = ContractOfferTemplate.roll(
                HouseRank.TIER_3, sequenceRandom(new float[]{0.10f}, false, 2));

        assertEquals(ContractType.PLANETARY_ASSAULT, assault.type);
        assertEquals(180_000, assault.payout);
        assertEquals(80, assault.salvageBaseline & 0xFF);
        assertEquals(5, assault.phasesTotal & 0xFF);
        assertNull(ContractOfferTemplate.forType(
                HouseRank.TIER_2, ContractType.PLANETARY_ASSAULT));
    }

    private static Random floatRandom(float value) {
        return new Random() {
            @Override
            public float nextFloat() {
                return value;
            }
        };
    }

    private static Random sequenceRandom(float[] values, boolean booleanValue) {
        return sequenceRandom(values, booleanValue, 0);
    }

    private static Random sequenceRandom(float[] values, boolean booleanValue, int intValue) {
        return new Random() {
            private int index;

            @Override
            public float nextFloat() {
                return values[Math.min(index++, values.length - 1)];
            }

            @Override
            public boolean nextBoolean() {
                return booleanValue;
            }

            @Override
            public int nextInt(int bound) {
                return Math.floorMod(intValue, bound);
            }
        };
    }
}
