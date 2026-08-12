package com.dillon.starsectormarines.campaign.systems;

import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.ChainArchetype;
import com.dillon.starsectormarines.campaign.ChainState;
import com.dillon.starsectormarines.campaign.ContractState;
import com.dillon.starsectormarines.campaign.ContractType;
import com.dillon.starsectormarines.campaign.HouseFlavor;
import com.dillon.starsectormarines.campaign.HouseRank;
import com.dillon.starsectormarines.campaign.HouseStatus;
import com.dillon.starsectormarines.campaign.PatronArchetype;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HouseConsolidationSystemTest {

    @Test
    void activeHouseWithOnlyTombstonedStakeHistoryBecomesDormant() {
        CampaignState state = new CampaignState();
        long house = house(state, HouseStatus.ACTIVE);
        state.addStake(house, 1, 7, (short) 0);
        state.addStake(house, 2, 9, (short) 0);

        new HouseConsolidationSystem().tick(state, 20);

        assertEquals(HouseStatus.DORMANT,
                HouseStatus.fromByte(state.houseStatus[state.houseIndex(house)]));
    }

    @Test
    void anyPositiveStakeAnywhereKeepsHouseActive() {
        CampaignState state = new CampaignState();
        long house = house(state, HouseStatus.ACTIVE);
        state.addStake(house, 1, 7, (short) 0);
        state.addStake(house, 2, 9, (short) 1);

        new HouseConsolidationSystem().tick(state, 20);

        assertEquals(HouseStatus.ACTIVE,
                HouseStatus.fromByte(state.houseStatus[state.houseIndex(house)]));
    }

    @Test
    void neverSeededHouseAndNonActiveStatusesRemainUntouched() {
        CampaignState state = new CampaignState();
        long neverSeeded = house(state, HouseStatus.ACTIVE);
        long deposed = house(state, HouseStatus.DEPOSED);
        state.addStake(deposed, 1, 7, (short) 0);

        new HouseConsolidationSystem().tick(state, 20);

        assertEquals(HouseStatus.ACTIVE,
                HouseStatus.fromByte(state.houseStatus[state.houseIndex(neverSeeded)]));
        assertEquals(HouseStatus.DEPOSED,
                HouseStatus.fromByte(state.houseStatus[state.houseIndex(deposed)]));
    }

    @Test
    void repeatedTicksDoNotChangeDormantIdentity() {
        CampaignState state = new CampaignState();
        long house = house(state, HouseStatus.ACTIVE);
        state.addStake(house, 1, 7, (short) 0);
        HouseConsolidationSystem system = new HouseConsolidationSystem();

        system.tick(state, 20);
        system.tick(state, 21);

        assertEquals(1, state.houseCount);
        assertEquals(house, state.houseId[0]);
        assertEquals(0, state.houseIndex(house));
        assertEquals(HouseStatus.DORMANT, HouseStatus.fromByte(state.houseStatus[0]));
    }

    @Test
    void dormancyFailsActiveChainsInvolvingHouseWithoutOverwritingTerminalRows() {
        CampaignState state = new CampaignState();
        long empty = house(state, HouseStatus.ACTIVE);
        long rival = house(state, HouseStatus.ACTIVE);
        state.addStake(empty, 1, 7, (short) 0);
        long owned = state.addAutonomousChain(empty, rival, 1, 7, (byte) 0,
                ChainArchetype.CONSOLIDATE_STAKE, (short) 45, (byte) 32, 1);
        long targeting = state.addAutonomousChain(rival, empty, 1, 7, (byte) 0,
                ChainArchetype.CONSOLIDATE_STAKE, (short) 45, (byte) 32, 1);
        long terminal = state.addAutonomousChain(empty, rival, 1, 7, (byte) 0,
                ChainArchetype.CONSOLIDATE_STAKE, (short) 45, (byte) 32, 1);
        int terminalRow = state.chainIndex(terminal);
        state.chainState[terminalRow] = ChainState.RESOLVED.toByte();
        state.chainResolvedTick[terminalRow] = 18;

        new HouseConsolidationSystem().tick(state, 20);

        assertEquals(ChainState.FAILED,
                ChainState.fromByte(state.chainState[state.chainIndex(owned)]));
        assertEquals(20, state.chainResolvedTick[state.chainIndex(owned)]);
        assertEquals(ChainState.FAILED,
                ChainState.fromByte(state.chainState[state.chainIndex(targeting)]));
        assertEquals(20, state.chainResolvedTick[state.chainIndex(targeting)]);
        assertEquals(ChainState.RESOLVED, ChainState.fromByte(state.chainState[terminalRow]));
        assertEquals(18, state.chainResolvedTick[terminalRow]);
    }

    @Test
    void dormancyExpiresOffersDefaultsAcceptedWorkAndPreservesExtraction() {
        CampaignState state = new CampaignState();
        long empty = house(state, HouseStatus.ACTIVE);
        state.addStake(empty, 1, 7, (short) 0);
        addContract(state, empty, ContractType.STRIKE, ContractState.OFFERED);
        addContract(state, empty, ContractType.STRIKE, ContractState.ACTIVE);
        addContract(state, empty, ContractType.STRIKE, ContractState.IN_PROGRESS);
        addContract(state, empty, ContractType.EXTRACTION, ContractState.OFFERED);
        addContract(state, empty, ContractType.STRIKE, ContractState.COMPLETED);

        new HouseConsolidationSystem().tick(state, 20);

        assertEquals(ContractState.EXPIRED, ContractState.fromByte(state.contractState[0]));
        assertEquals(ContractState.DEFAULTED, ContractState.fromByte(state.contractState[1]));
        assertEquals(ContractState.DEFAULTED, ContractState.fromByte(state.contractState[2]));
        assertEquals(ContractState.OFFERED, ContractState.fromByte(state.contractState[3]));
        assertEquals(ContractState.COMPLETED, ContractState.fromByte(state.contractState[4]));
    }

    private static void addContract(CampaignState state, long patron,
                                    ContractType type, ContractState contractState) {
        state.addContract(patron, -1L, -1L, type, contractState,
                1, -1, 10, (byte) 1, -1, 1, -1,
                1_000, 0, (byte) 10, (byte) 10, (byte) 100);
    }

    private static long house(CampaignState state, HouseStatus status) {
        return state.addHouse(1, 1, HouseFlavor.FEUDAL, HouseRank.TIER_1,
                status, PatronArchetype.NEWCOMER, "House");
    }
}
