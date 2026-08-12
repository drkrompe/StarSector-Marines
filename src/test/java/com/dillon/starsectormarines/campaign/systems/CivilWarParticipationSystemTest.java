package com.dillon.starsectormarines.campaign.systems;

import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.ChainArchetype;
import com.dillon.starsectormarines.campaign.CivilWarAllegiance;
import com.dillon.starsectormarines.campaign.CivilWarBand;
import com.dillon.starsectormarines.campaign.ContractState;
import com.dillon.starsectormarines.campaign.ContractType;
import com.dillon.starsectormarines.campaign.HouseFlavor;
import com.dillon.starsectormarines.campaign.HouseRank;
import com.dillon.starsectormarines.campaign.HouseStatus;
import com.dillon.starsectormarines.campaign.PatronArchetype;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CivilWarParticipationSystemTest {

    @Test
    void completedStationingContributionIsRecoveredExactlyOnce() {
        CampaignState state = new CampaignState();
        int market = state.marketRegistry.intern("market");
        long claimant = house(state, market, "Claimant");
        long incumbent = house(state, market, "Incumbent");
        long chain = state.addAutonomousChain(claimant, incumbent, market, -1,
                HouseRank.TIER_3.toByte(), ChainArchetype.CIVIL_WAR,
                (short) 180, (byte) 128, 1);
        int chainRow = state.chainIndex(chain);
        state.chainProgress[chainRow] = 70;
        long contract = state.addContract(claimant, incumbent, chain,
                ContractType.CADRE, ContractState.COMPLETED, 20, 40, -1,
                (byte) 0, -1, market, 2, 1_000, 100,
                (byte) 25, (byte) 25, (byte) 100);
        int contractRow = state.contractIndex(contract);
        state.contractCivilWarBand[contractRow] = CivilWarBand.MOBILIZATION.toByte();
        CivilWarParticipationSystem system = new CivilWarParticipationSystem();

        system.tick(state, 41);
        system.tick(state, 42);

        assertEquals(100, state.chainProgress[chainRow]);
        assertEquals(CivilWarAllegiance.CLAIMANT,
                CivilWarAllegiance.fromByte(state.chainPlayerAllegiance[chainRow]));
        assertEquals(30, state.chainPlayerContribution[chainRow]);
        assertEquals(41, state.contractCivilWarContributionAppliedTick[contractRow]);
    }

    private static long house(CampaignState state, int market, String name) {
        return state.addHouse(market, 1, HouseFlavor.FEUDAL, HouseRank.TIER_3,
                HouseStatus.ACTIVE, PatronArchetype.NEWCOMER, name);
    }
}
