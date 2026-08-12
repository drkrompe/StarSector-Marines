package com.dillon.starsectormarines.campaign;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChainInterventionTest {

    @Test
    void completedMatchingInterventionFailsHostileChainExactlyOnce() {
        Fixture fixture = new Fixture();
        fixture.state.contractState[fixture.contractRow] = ContractState.COMPLETED.toByte();

        assertTrue(ChainIntervention.stopOpposedChain(
                fixture.state, fixture.contractRow, 20));
        assertEquals(ChainState.FAILED,
                ChainState.fromByte(fixture.state.chainState[fixture.chainRow]));
        assertEquals(20, fixture.state.chainResolvedTick[fixture.chainRow]);

        assertFalse(ChainIntervention.stopOpposedChain(
                fixture.state, fixture.contractRow, 21));
        assertEquals(20, fixture.state.chainResolvedTick[fixture.chainRow]);
    }

    @Test
    void nonCompletedContractLeavesPlotRunning() {
        Fixture fixture = new Fixture();

        assertFalse(ChainIntervention.stopOpposedChain(
                fixture.state, fixture.contractRow, 20));
        assertEquals(ChainState.ACTIVE,
                ChainState.fromByte(fixture.state.chainState[fixture.chainRow]));
        assertEquals(-1, fixture.state.chainResolvedTick[fixture.chainRow]);
    }

    @Test
    void mismatchedPartiesCannotStopUnrelatedPlot() {
        Fixture wrongPatron = new Fixture();
        wrongPatron.state.contractState[wrongPatron.contractRow] =
                ContractState.COMPLETED.toByte();
        wrongPatron.state.contractPatronHouseId[wrongPatron.contractRow] = 999L;
        Fixture wrongTarget = new Fixture();
        wrongTarget.state.contractState[wrongTarget.contractRow] =
                ContractState.COMPLETED.toByte();
        wrongTarget.state.contractTargetHouseId[wrongTarget.contractRow] = 999L;

        assertFalse(ChainIntervention.stopOpposedChain(
                wrongPatron.state, wrongPatron.contractRow, 20));
        assertFalse(ChainIntervention.stopOpposedChain(
                wrongTarget.state, wrongTarget.contractRow, 20));
        assertEquals(ChainState.ACTIVE,
                ChainState.fromByte(wrongPatron.state.chainState[wrongPatron.chainRow]));
        assertEquals(ChainState.ACTIVE,
                ChainState.fromByte(wrongTarget.state.chainState[wrongTarget.chainRow]));
    }

    @Test
    void playerBackedOrAlreadyTerminalChainIsNeverOverwritten() {
        Fixture playerBacked = new Fixture();
        playerBacked.state.contractState[playerBacked.contractRow] =
                ContractState.COMPLETED.toByte();
        playerBacked.state.chainPatron[playerBacked.chainRow] = playerBacked.actor;
        Fixture terminal = new Fixture();
        terminal.state.contractState[terminal.contractRow] = ContractState.COMPLETED.toByte();
        terminal.state.chainState[terminal.chainRow] = ChainState.RESOLVED.toByte();
        terminal.state.chainResolvedTick[terminal.chainRow] = 18;

        assertFalse(ChainIntervention.stopOpposedChain(
                playerBacked.state, playerBacked.contractRow, 20));
        assertFalse(ChainIntervention.stopOpposedChain(
                terminal.state, terminal.contractRow, 20));
        assertEquals(ChainState.RESOLVED,
                ChainState.fromByte(terminal.state.chainState[terminal.chainRow]));
        assertEquals(18, terminal.state.chainResolvedTick[terminal.chainRow]);
    }

    @Test
    void bandedCivilWarContractUsesParticipationResolutionInstead() {
        Fixture fixture = new Fixture();
        fixture.state.contractState[fixture.contractRow] = ContractState.COMPLETED.toByte();
        fixture.state.contractCivilWarBand[fixture.contractRow] =
                CivilWarBand.COALITION_BUILDING.toByte();

        assertFalse(ChainIntervention.stopOpposedChain(
                fixture.state, fixture.contractRow, 20));
        assertEquals(ChainState.ACTIVE,
                ChainState.fromByte(fixture.state.chainState[fixture.chainRow]));
    }

    private static final class Fixture {
        final CampaignState state = new CampaignState();
        final long actor = house(state, "Actor");
        final long target = house(state, "Target");
        final int chainRow;
        final int contractRow;

        Fixture() {
            long chainId = state.addAutonomousChain(actor, target, 1, 7, (byte) 0,
                    ChainArchetype.CONSOLIDATE_STAKE, (short) 45, (byte) 32, 1);
            chainRow = state.chainIndex(chainId);
            long contractId = state.addContract(target, actor, -1L,
                    ContractType.STRIKE, ContractState.ACTIVE,
                    10, -1, -1, (byte) 1, -1, 1, 7,
                    25_000, 0, (byte) 60, (byte) 60, (byte) 100);
            contractRow = state.contractIndex(contractId);
            state.contractOpposedChainId[contractRow] = chainId;
        }

        private static long house(CampaignState state, String name) {
            return state.addHouse(1, 1, HouseFlavor.FEUDAL, HouseRank.TIER_1,
                    HouseStatus.ACTIVE, PatronArchetype.NEWCOMER, name);
        }
    }
}
