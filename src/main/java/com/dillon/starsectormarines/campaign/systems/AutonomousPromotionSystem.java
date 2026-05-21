package com.dillon.starsectormarines.campaign.systems;

import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.CampaignSystem;
import com.dillon.starsectormarines.campaign.CampaignTable;

import java.util.EnumSet;

/**
 * Tick phase 1: autonomous house promotion via stake-based progress.
 *
 * <p>Per <code>mechanics.md</code>: every ACTIVE house accrues
 * {@code promotionProgress} based on its current stake holdings vs the
 * market's total. Crossing {@code rankThreshold} promotes the house.
 *
 * <p>Currently a stub — the {@code stakeBasedDelta} formula isn't wired and
 * no stakes are seeded yet, so no promotion actually fires.
 */
public final class AutonomousPromotionSystem implements CampaignSystem {

    @Override
    public String name() {
        return "AutonomousPromotion";
    }

    @Override
    public EnumSet<CampaignTable> reads() {
        return EnumSet.of(CampaignTable.STAKES);
    }

    @Override
    public EnumSet<CampaignTable> writes() {
        return EnumSet.of(CampaignTable.HOUSES);
    }

    @Override
    public void tick(CampaignState state, int day) {
        // Pending: walk houses[], compute stakeBasedDelta(i), bump
        // housePromotionProgress[i], call promote(i) when over threshold.
    }
}
