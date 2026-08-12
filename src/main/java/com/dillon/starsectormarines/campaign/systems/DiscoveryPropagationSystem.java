package com.dillon.starsectormarines.campaign.systems;

import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.CampaignSystem;
import com.dillon.starsectormarines.campaign.CampaignTable;
import com.dillon.starsectormarines.campaign.ChainState;
import com.dillon.starsectormarines.campaign.ChainArchetype;
import com.dillon.starsectormarines.campaign.ChronicleBand;
import com.dillon.starsectormarines.campaign.HouseRank;
import com.dillon.starsectormarines.campaign.ThroneClaimState;

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

    static final int ACTIVE_DISCOVERY_CADENCE_DAYS = 7;

    @Override
    public String name() {
        return "DiscoveryPropagation";
    }

    @Override
    public EnumSet<CampaignTable> reads() {
        return EnumSet.of(CampaignTable.CHAINS, CampaignTable.PLAYER_REP,
                CampaignTable.THRONE_CLAIMS);
    }

    @Override
    public EnumSet<CampaignTable> writes() {
        return EnumSet.of(CampaignTable.CHAINS, CampaignTable.CHRONICLE);
    }

    @Override
    public void tick(CampaignState state, int day) {
        if (state == null) return;
        for (int chainRow = 0; chainRow < state.chainCount; chainRow++) {
            ChainState outcome = ChainState.fromByte(state.chainState[chainRow]);
            if (outcome == ChainState.ACTIVE) {
                processActiveRumor(state, chainRow, day);
                continue;
            }
            if (state.chainDiscoveryProcessedTick[chainRow] >= 0) continue;
            if (hasPreparedHandoff(state, chainRow)) continue;

            state.chainDiscoveryProcessedTick[chainRow] = day;
            ChronicleBand band = newsBand(state, chainRow);
            if (band == null) continue;

            state.addChronicleChainOutcome(state.chainId[chainRow], outcome, band,
                    state.chainActorHouseId[chainRow], state.chainTarget[chainRow],
                    state.chainMarketId[chainRow], state.chainIndustryId[chainRow],
                    state.chainResolvedTick[chainRow], day);
        }
    }

    private static boolean hasPreparedHandoff(CampaignState state, int chainRow) {
        if (ChainArchetype.fromByte(state.chainArchetype[chainRow])
                != ChainArchetype.CIVIL_WAR) {
            return false;
        }
        long chainId = state.chainId[chainRow];
        for (int claimRow = 0; claimRow < state.throneClaimCount; claimRow++) {
            if (state.throneClaimSourceChainId[claimRow] == chainId
                    && ThroneClaimState.fromByte(state.throneClaimState[claimRow])
                        == ThroneClaimState.PREPARED) {
                return true;
            }
        }
        return false;
    }

    private static void processActiveRumor(CampaignState state, int chainRow, int day) {
        if (state.chainPatron[chainRow] != -1L
                || state.chainDiscoveredTick[chainRow] >= 0) {
            return;
        }
        ChronicleBand band = newsBand(state, chainRow);
        if (band == null) return;

        int initiated = state.chainInitiatedTick[chainRow];
        int age = day - initiated;
        if (age < ACTIVE_DISCOVERY_CADENCE_DAYS) return;
        int window = age / ACTIVE_DISCOVERY_CADENCE_DAYS;
        int lastCheck = state.chainLastDiscoveryCheckTick[chainRow];
        if (lastCheck >= initiated
                && (lastCheck - initiated) / ACTIVE_DISCOVERY_CADENCE_DAYS >= window) {
            return;
        }
        state.chainLastDiscoveryCheckTick[chainRow] = day;

        int chance = effectiveDiscoveryRisk(state, chainRow);
        if (chance <= 0 || discoveryRoll(state.chainId[chainRow], window) >= chance) return;

        state.chainDiscoveredTick[chainRow] = day;
        state.addChronicleChainRumor(state.chainId[chainRow], band,
                state.chainActorHouseId[chainRow], state.chainTarget[chainRow],
                state.chainMarketId[chainRow], state.chainIndustryId[chainRow],
                initiated, day);
    }

    static int effectiveDiscoveryRisk(CampaignState state, int chainRow) {
        int base = state.chainDiscoveryRisk[chainRow] & 0xFF;
        int threshold = Math.max(1, state.chainThreshold[chainRow]);
        int progress = Math.max(0, state.chainProgress[chainRow]);
        int progressExposure = (int) Math.min(base,
                (long) base * progress / threshold);
        return Math.min(255, base + progressExposure);
    }

    static int discoveryRoll(long chainId, int window) {
        long mixed = chainId * 0x9e3779b97f4a7c15L;
        mixed ^= (long) window * 0xbf58476d1ce4e5b9L;
        mixed ^= mixed >>> 30;
        mixed *= 0x94d049bb133111ebL;
        mixed ^= mixed >>> 27;
        return (int) (mixed & 0xFFL);
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
