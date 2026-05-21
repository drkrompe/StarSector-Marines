package com.dillon.starsectormarines.campaign.systems;

import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.CampaignSystem;
import com.dillon.starsectormarines.campaign.CampaignTable;

import java.util.EnumSet;

/**
 * Tick phase 3: advance active chains (player + autonomous).
 *
 * <p>Per <code>mechanics.md</code>: autonomous chains ({@code patron == -1})
 * advance on tick; player chains advance only on mission completion (the
 * mission resolver pokes them directly). This system handles the autonomous
 * side and the per-tick discovery-risk decay.
 *
 * <p>Stub — no chains exist yet.
 */
public final class ChainAdvancementSystem implements CampaignSystem {

    @Override
    public String name() {
        return "ChainAdvancement";
    }

    @Override
    public EnumSet<CampaignTable> reads() {
        return EnumSet.of(CampaignTable.HOUSES);
    }

    @Override
    public EnumSet<CampaignTable> writes() {
        return EnumSet.of(CampaignTable.CHAINS, CampaignTable.HOUSES);
    }

    @Override
    public void tick(CampaignState state, int day) {
        // Pending: walk chains[], advance autonomous progress, resolve at
        // threshold (bump target/patron promotion progress, mutate affinity).
    }
}
