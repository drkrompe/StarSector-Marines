package com.dillon.starsectormarines.campaign.systems;

import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.CampaignSystem;
import com.dillon.starsectormarines.campaign.CampaignTable;

import java.util.EnumSet;

/**
 * Tick phase 5: propagate "exposed" chain discoveries and surface
 * player-visible campaign events to the intel feed.
 *
 * <p>Per <code>mechanics.md</code>: when a chain's discovery roll fires,
 * the target house and its visible peers (via rank-ladder visibility)
 * learn about it; affinity to the patron tanks across that set.
 *
 * <p>Stub — no discovery rolls fire yet.
 */
public final class DiscoveryPropagationSystem implements CampaignSystem {

    @Override
    public String name() {
        return "DiscoveryPropagation";
    }

    @Override
    public EnumSet<CampaignTable> reads() {
        return EnumSet.of(CampaignTable.CHAINS, CampaignTable.HOUSES);
    }

    @Override
    public EnumSet<CampaignTable> writes() {
        return EnumSet.of(CampaignTable.RELATIONSHIPS, CampaignTable.PLAYER_REP);
    }

    @Override
    public void tick(CampaignState state, int day) {
        // Pending: walk recently-resolved chains, propagate discovery to
        // visible peers per rank-ladder visibility rules.
    }
}
