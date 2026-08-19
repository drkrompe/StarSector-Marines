package com.dillon.starsectormarines.campaign.systems;

import com.dillon.starsectormarines.DebugOnly;
import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.ChainArchetype;
import com.dillon.starsectormarines.campaign.ChainState;
import com.dillon.starsectormarines.campaign.CivilWarAllegiance;
import com.dillon.starsectormarines.campaign.CivilWarPlayerConsequences;
import com.dillon.starsectormarines.campaign.HouseAmbition;
import com.dillon.starsectormarines.campaign.HouseRank;
import com.dillon.starsectormarines.campaign.HouseStatus;
import com.dillon.starsectormarines.campaign.ThroneClaimState;

/** Debug seam for a complete kingmaker capstone without external faction writes. */
@DebugOnly
public final class DebugKingmakerTestamentSpawner {

    private static final short DECISIVE_CONTRIBUTION = 60;

    private DebugKingmakerTestamentSpawner() {}

    /**
     * Produces a resolved claimant victory through the normal consequence,
     * moral-compass, testament, and discovery systems. Vanilla ownership and
     * diplomacy ports are intentionally not invoked by this debug seam.
     */
    public static long spawn(CampaignState state, int day) {
        if (state == null || day < 0) return -1L;

        int existing = earliestTestamentRow(state);
        if (existing >= 0) {
            new KingmakerTestamentSystem().tick(state, day);
            new DiscoveryPropagationSystem().tick(state, day);
            return state.kingmakerTestamentId[existing];
        }

        int[] pair = selectHousePair(state);
        if (pair == null) return -1L;
        int claimantRow = pair[0];
        int deposedRow = pair[1];
        int sourceFactionSlot = state.houseFactionId[claimantRow];
        int resultFactionSlot = state.factionRegistry.intern(
                ChainAdvancementSystem.CLAIMANT_FACTION_ID);

        long chainId = state.addAutonomousChain(
                state.houseId[claimantRow], state.houseId[deposedRow],
                state.houseMarketId[claimantRow], -1,
                HouseRank.TIER_3.toByte(), ChainArchetype.CIVIL_WAR,
                (short) 180, (byte) 128, day);
        int chainRow = state.chainIndex(chainId);
        state.chainState[chainRow] = ChainState.RESOLVED.toByte();
        state.chainResolvedTick[chainRow] = day;
        state.chainPlayerAllegiance[chainRow] = CivilWarAllegiance.CLAIMANT.toByte();
        state.chainPlayerContribution[chainRow] = DECISIVE_CONTRIBUTION;
        state.chainPlayerLastContributionTick[chainRow] = day;

        long claimId = state.prepareThroneClaim(chainId,
                state.houseId[claimantRow], sourceFactionSlot,
                resultFactionSlot, state.houseMarketId[claimantRow], day);
        int claimRow = state.throneClaimIndex(claimId);

        // Mirror ThroneClaimResolutionSystem.applyLocalResult after a successful
        // writeback, while deliberately leaving the external ports untouched.
        state.houseRank[claimantRow] = HouseRank.TIER_4.toByte();
        state.housePromotionProgress[claimantRow] = 0;
        state.houseFactionId[claimantRow] = resultFactionSlot;
        state.houseAmbition[claimantRow] = HouseAmbition.NONE.toByte();
        state.houseAmbitionTarget[claimantRow] = -1L;
        state.throneClaimState[claimRow] = ThroneClaimState.APPLIED.toByte();
        state.throneClaimAppliedTick[claimRow] = day;

        CivilWarPlayerConsequences.applyClaimant(state, claimRow, day);
        new MoralCompassSystem().tick(state, day);
        new KingmakerTestamentSystem().tick(state, day);
        new DiscoveryPropagationSystem().tick(state, day);

        int testamentRow = state.kingmakerTestamentIndexForClaim(claimId);
        return testamentRow >= 0 ? state.kingmakerTestamentId[testamentRow] : -1L;
    }

    private static int earliestTestamentRow(CampaignState state) {
        int selected = -1;
        for (int row = 0; row < state.kingmakerTestamentCount; row++) {
            if (state.kingmakerTestamentId[row] <= 0L) continue;
            if (selected < 0 || state.kingmakerTestamentId[row]
                    < state.kingmakerTestamentId[selected]) {
                selected = row;
            }
        }
        return selected;
    }

    /** Returns the lowest-id ordered pair satisfying the production prerequisites. */
    private static int[] selectHousePair(CampaignState state) {
        int claimant = -1;
        int deposed = -1;
        for (int actor = 0; actor < state.houseCount; actor++) {
            if (!validHouse(state, actor)) continue;
            for (int target = 0; target < state.houseCount; target++) {
                if (actor == target || !validHouse(state, target)
                        || state.houseMarketId[actor] != state.houseMarketId[target]
                        || state.houseFactionId[actor] != state.houseFactionId[target]) {
                    continue;
                }
                if (claimant < 0
                        || state.houseId[actor] < state.houseId[claimant]
                        || (state.houseId[actor] == state.houseId[claimant]
                            && state.houseId[target] < state.houseId[deposed])) {
                    claimant = actor;
                    deposed = target;
                }
            }
        }
        return claimant >= 0 ? new int[]{claimant, deposed} : null;
    }

    private static boolean validHouse(CampaignState state, int row) {
        if (HouseStatus.fromByte(state.houseStatus[row]) != HouseStatus.ACTIVE
                || state.marketRegistry.get(state.houseMarketId[row]) == null) {
            return false;
        }
        String factionId = state.factionRegistry.get(state.houseFactionId[row]);
        return factionId != null
                && !ChainAdvancementSystem.CLAIMANT_FACTION_ID.equals(factionId);
    }
}
