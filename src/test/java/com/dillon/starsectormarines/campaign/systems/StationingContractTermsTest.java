package com.dillon.starsectormarines.campaign.systems;

import com.dillon.starsectormarines.campaign.ContractType;
import com.dillon.starsectormarines.campaign.HouseRank;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class StationingContractTermsTest {

    @Test
    void garrisonUsesRiskPremiumAndRankMultiplier() {
        StationingContractTerms terms = StationingContractTerms.create(
                ContractType.GARRISON, HouseRank.TIER_2, 100, 6);

        assertEquals(100, terms.committedMarines);
        assertEquals(90, terms.termDays);
        assertEquals(3_300, terms.monthlyRetainer);
        assertEquals(25, terms.salvageBaseline & 0xFF);
    }

    @Test
    void cadreUsesLowerRetainerAndTierThreeTermCap() {
        StationingContractTerms terms = StationingContractTerms.create(
                ContractType.CADRE, HouseRank.TIER_3, 50, 12);

        assertEquals(180, terms.termDays);
        assertEquals(1_200, terms.monthlyRetainer);
        assertEquals(5, terms.salvageBaseline & 0xFF);
    }

    @Test
    void rejectsUnsupportedTerms() {
        assertNull(StationingContractTerms.create(
                ContractType.STRIKE, HouseRank.TIER_1, 100, 1));
        assertNull(StationingContractTerms.create(
                ContractType.GARRISON, HouseRank.TIER_4, 100, 1));
        assertNull(StationingContractTerms.create(
                ContractType.GARRISON, HouseRank.TIER_1, 0, 1));
    }
}
