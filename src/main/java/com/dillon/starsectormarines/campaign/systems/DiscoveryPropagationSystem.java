package com.dillon.starsectormarines.campaign.systems;

import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.CampaignSystem;
import com.dillon.starsectormarines.campaign.CampaignTable;
import com.dillon.starsectormarines.campaign.ChainState;
import com.dillon.starsectormarines.campaign.ChronicleBand;
import com.dillon.starsectormarines.campaign.HouseRank;

import java.util.EnumSet;

/**
 * Tick phase 5: classify terminal chain outcomes into learned Chronicle events.
 *
 * <p>The first editor rule implements the living-world two-band discipline:
 * outcomes involving a house the player has touched are intimate; untouched
 * Tier-3+ outcomes are epic; the middle is deliberately silent. Active-chain
 * rumor rolls and relationship consequences remain later discovery layers.
 */
public final class DiscoveryPropagationSystem implements CampaignSystem {

    @Override
    public String name() {
        return "DiscoveryPropagation";
    }

    @Override
    public EnumSet<CampaignTable> reads() {
        return EnumSet.of(CampaignTable.CHAINS, CampaignTable.PLAYER_REP);
    }

    @Override
    public EnumSet<CampaignTable> writes() {
        return EnumSet.of(CampaignTable.CHAINS, CampaignTable.CHRONICLE);
    }

    @Override
    public void tick(CampaignState state, int day) {
        if (state == null) return;
        for (int chainRow = 0; chainRow < state.chainCount; chainRow++) {
            if (state.chainDiscoveryProcessedTick[chainRow] >= 0) continue;
            ChainState outcome = ChainState.fromByte(state.chainState[chainRow]);
            if (outcome == ChainState.ACTIVE) continue;

            state.chainDiscoveryProcessedTick[chainRow] = day;
            ChronicleBand band = newsBand(state, chainRow);
            if (band == null) continue;

            state.addChronicleChainOutcome(state.chainId[chainRow], outcome, band,
                    state.chainActorHouseId[chainRow], state.chainTarget[chainRow],
                    state.chainMarketId[chainRow], state.chainIndustryId[chainRow],
                    state.chainResolvedTick[chainRow], day);
        }
    }

    static ChronicleBand newsBand(CampaignState state, int chainRow) {
        if (state.repIndex(state.chainActorHouseId[chainRow]) >= 0
                || state.repIndex(state.chainTarget[chainRow]) >= 0) {
            return ChronicleBand.INTIMATE;
        }
        HouseRank tier = HouseRank.fromByte(state.chainTier[chainRow]);
        return tier.ordinal() >= HouseRank.TIER_3.ordinal() ? ChronicleBand.EPIC : null;
    }
}
