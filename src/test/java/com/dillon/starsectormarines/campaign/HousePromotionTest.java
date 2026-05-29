package com.dillon.starsectormarines.campaign;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Coverage for {@link HousePromotion} — the shared bump-and-promote primitive
 * the player path and the coming {@code AutonomousPromotionSystem} both route
 * through. Verifies threshold crossing, remainder carry, multi-tier cascade,
 * suppression (negative delta), and the TIER_4 terminal.
 */
public class HousePromotionTest {

    private static int house(CampaignState s, HouseRank rank, int progress) {
        long id = s.addHouse(1, 1, HouseFlavor.FEUDAL, rank,
                HouseStatus.ACTIVE, PatronArchetype.NEWCOMER, "H");
        int row = s.houseIndex(id);
        s.housePromotionProgress[row] = (short) progress;
        return row;
    }

    @Test
    public void bumpBelowThresholdDoesNotPromote() {
        CampaignState s = new CampaignState();
        int row = house(s, HouseRank.TIER_1, 50);

        int promotions = HousePromotion.addProgressAndPromote(s, row, 15, 10);

        assertEquals(0, promotions);
        assertEquals(HouseRank.TIER_1, HouseRank.fromByte(s.houseRank[row]));
        assertEquals(65, s.housePromotionProgress[row]);
    }

    @Test
    public void crossingThresholdPromotesAndCarriesRemainder() {
        CampaignState s = new CampaignState();
        int row = house(s, HouseRank.TIER_1, 90); // threshold 100

        int promotions = HousePromotion.addProgressAndPromote(s, row, 15, 10);

        assertEquals(1, promotions);
        assertEquals(HouseRank.TIER_2, HouseRank.fromByte(s.houseRank[row]));
        assertEquals(5, s.housePromotionProgress[row], "remainder carries forward, not reset to zero");
    }

    @Test
    public void landingExactlyOnThresholdPromotesWithZeroCarry() {
        CampaignState s = new CampaignState();
        int row = house(s, HouseRank.TIER_1, 85); // threshold 100

        int promotions = HousePromotion.addProgressAndPromote(s, row, 15, 10); // 85 + 15 == 100 exactly

        assertEquals(1, promotions, "progress == threshold must promote (>=, not >)");
        assertEquals(HouseRank.TIER_2, HouseRank.fromByte(s.houseRank[row]));
        assertEquals(0, s.housePromotionProgress[row], "exact landing carries zero remainder");
    }

    @Test
    public void largeDeltaCascadesMultipleTiers() {
        CampaignState s = new CampaignState();
        int row = house(s, HouseRank.TIER_1, 0); // thresholds: T1=100, T2=300

        // 450 crosses 100 (→T2, carry 350) then 300 (→T3, carry 50).
        int promotions = HousePromotion.addProgressAndPromote(s, row, 450, 10);

        assertEquals(2, promotions);
        assertEquals(HouseRank.TIER_3, HouseRank.fromByte(s.houseRank[row]));
        assertEquals(50, s.housePromotionProgress[row]);
    }

    @Test
    public void tier4IsTerminal() {
        CampaignState s = new CampaignState();
        int row = house(s, HouseRank.TIER_4, 0);

        int promotions = HousePromotion.addProgressAndPromote(s, row, 40_000, 10); // overflows short

        assertEquals(0, promotions, "TIER_4 never auto-promotes — T3 endgame owns the next step");
        assertEquals(HouseRank.TIER_4, HouseRank.fromByte(s.houseRank[row]));
        assertEquals(Short.MAX_VALUE, s.housePromotionProgress[row], "progress clamps to the short ceiling");
    }

    @Test
    public void negativeDeltaSuppressesWithoutUnderflow() {
        CampaignState s = new CampaignState();
        int row = house(s, HouseRank.TIER_2, 10);

        int promotions = HousePromotion.addProgressAndPromote(s, row, -50, 10);

        assertEquals(0, promotions);
        assertEquals(0, s.housePromotionProgress[row], "progress floors at zero, never negative");
        assertEquals(HouseRank.TIER_2, HouseRank.fromByte(s.houseRank[row]));
    }
}
