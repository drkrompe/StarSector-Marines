package com.dillon.starsectormarines.campaign.systems;

import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.ChainArchetype;
import com.dillon.starsectormarines.campaign.ChainState;
import com.dillon.starsectormarines.campaign.CivilWarAllegiance;
import com.dillon.starsectormarines.campaign.CivilWarBand;
import com.dillon.starsectormarines.campaign.ContractState;
import com.dillon.starsectormarines.campaign.ContractType;
import com.dillon.starsectormarines.campaign.HouseFlavor;
import com.dillon.starsectormarines.campaign.HouseRank;
import com.dillon.starsectormarines.campaign.HouseStatus;
import com.dillon.starsectormarines.campaign.PatronArchetype;
import com.dillon.starsectormarines.campaign.ThroneClaimState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CivilWarPlayerConsequenceSystemTest {

    @Test
    void dailyScanAppliesClaimantOutcomeOnlyOnce() {
        CampaignState state = new CampaignState();
        long claimant = house(state, "Claimant");
        long incumbent = house(state, "Incumbent");
        long chain = state.addAutonomousChain(claimant, incumbent, 1, -1,
                HouseRank.TIER_3.toByte(), ChainArchetype.CIVIL_WAR,
                (short) 180, (byte) 128, 1);
        int chainRow = state.chainIndex(chain);
        state.chainState[chainRow] = ChainState.RESOLVED.toByte();
        state.chainResolvedTick[chainRow] = 100;
        state.chainPlayerAllegiance[chainRow] = CivilWarAllegiance.CLAIMANT.toByte();
        state.chainPlayerContribution[chainRow] = 45;
        state.chainPlayerLastContributionTick[chainRow] = 80;
        long claim = state.prepareThroneClaim(chain, claimant,
                state.factionRegistry.intern("source"),
                state.factionRegistry.intern("result"), 1, 100);
        int claimRow = state.throneClaimIndex(claim);
        state.throneClaimState[claimRow] = ThroneClaimState.APPLIED.toByte();
        CivilWarPlayerConsequenceSystem system =
                new CivilWarPlayerConsequenceSystem();

        system.tick(state, 110);
        system.tick(state, 111);

        assertEquals(10, state.repValue[state.repIndex(claimant)]);
        assertEquals(-15, state.repValue[state.repIndex(incumbent)]);
        assertEquals(110,
                state.throneClaimPlayerConsequenceAppliedTick[claimRow]);
    }

    @Test
    void completedDecisiveIncumbentContractFlowsThroughToReputation() {
        CampaignState state = new CampaignState();
        int market = state.marketRegistry.intern("market");
        long claimant = house(state, "Claimant");
        long incumbent = house(state, "Incumbent");
        long chain = state.addAutonomousChain(claimant, incumbent, market, -1,
                HouseRank.TIER_3.toByte(), ChainArchetype.CIVIL_WAR,
                (short) 180, (byte) 128, 1);
        int chainRow = state.chainIndex(chain);
        state.chainProgress[chainRow] = 120;
        long contract = state.addContract(incumbent, claimant, -1L,
                ContractType.PLANETARY_ASSAULT, ContractState.COMPLETED,
                80, -1, -1, (byte) 3, -1, market, 2,
                1_000, 0, (byte) 50, (byte) 50, (byte) 100);
        int contractRow = state.contractIndex(contract);
        state.contractOpposedChainId[contractRow] = chain;
        state.contractCivilWarBand[contractRow] = CivilWarBand.OPEN_CONFLICT.toByte();

        new CivilWarParticipationSystem().tick(state, 90);
        new CivilWarPlayerConsequenceSystem().tick(state, 90);

        assertEquals(ChainState.FAILED,
                ChainState.fromByte(state.chainState[chainRow]));
        assertEquals(15, state.repValue[state.repIndex(incumbent)]);
        assertEquals(-25, state.repValue[state.repIndex(claimant)]);
    }

    private static long house(CampaignState state, String name) {
        return state.addHouse(1, 1, HouseFlavor.FEUDAL, HouseRank.TIER_3,
                HouseStatus.ACTIVE, PatronArchetype.NEWCOMER, name);
    }
}
