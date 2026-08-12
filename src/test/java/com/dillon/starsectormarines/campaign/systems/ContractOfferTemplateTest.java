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
    }

    @Test
    void tierTwoCanRollEscortOrStrike() {
        ContractOfferTemplate escort = ContractOfferTemplate.roll(
                HouseRank.TIER_2, floatRandom(0.10f));
        ContractOfferTemplate strike = ContractOfferTemplate.roll(
                HouseRank.TIER_2, floatRandom(0.90f));

        assertEquals(ContractType.ESCORT, escort.type);
        assertEquals(45_000, escort.payout);
        assertEquals(10, escort.salvageBaseline & 0xFF);
        assertEquals(ContractType.STRIKE, strike.type);
        assertEquals(37_500, strike.payout);
        assertEquals(60, strike.salvageBaseline & 0xFF);
    }

    @Test
    void tierThreeUsesThreeTimesBaselineAndTierFourHasNoStandardOffer() {
        ContractOfferTemplate escort = ContractOfferTemplate.roll(
                HouseRank.TIER_3, floatRandom(0f));

        assertEquals(90_000, escort.payout);
        assertNull(ContractOfferTemplate.roll(HouseRank.TIER_4, floatRandom(0f)));
        assertNull(ContractOfferTemplate.roll(null, floatRandom(0f)));
    }

    private static Random floatRandom(float value) {
        return new Random() {
            @Override
            public float nextFloat() {
                return value;
            }
        };
    }
}
