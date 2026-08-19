package com.dillon.starsectormarines.campaign;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class PatronEngagementMemoryTest {

    @Test
    void recordsFrozenTerminalContractFactExactlyOnce() {
        Fixture fixture = fixture();
        long contractId = contract(fixture, ContractType.GARRISON,
                ContractState.ABANDONED);

        long id = PatronEngagementMemory.record(fixture.state, contractId,
                PatronEngagementOutcome.WITHDREW, 40);
        long replay = PatronEngagementMemory.record(fixture.state, contractId,
                PatronEngagementOutcome.WITHDREW, 99);

        assertEquals(id, replay);
        assertEquals(1, fixture.state.patronEngagementCount);
        PatronEngagementMemory.Snapshot memory =
                PatronEngagementMemory.latest(fixture.state, fixture.patronId);
        assertNotNull(memory);
        assertEquals(contractId, memory.sourceContractId);
        assertEquals(fixture.patronId, memory.houseId);
        assertEquals(ContractType.GARRISON, memory.contractType);
        assertEquals(fixture.marketId, memory.marketId);
        assertEquals(PatronEngagementOutcome.WITHDREW, memory.outcome);
        assertEquals(40, memory.happenedTick);
        assertEquals(1, memory.priorEngagementCount);
    }

    @Test
    void rejectsNonTerminalAndOutcomeMismatch() {
        Fixture fixture = fixture();
        long active = contract(fixture, ContractType.STRIKE,
                ContractState.ACTIVE);
        long failed = contract(fixture, ContractType.ESCORT,
                ContractState.FAILED);

        assertEquals(-1L, PatronEngagementMemory.record(fixture.state, active,
                PatronEngagementOutcome.COMPLETED, 10));
        assertEquals(-1L, PatronEngagementMemory.record(fixture.state, failed,
                PatronEngagementOutcome.COMPLETED, 10));
        assertEquals(-1L, PatronEngagementMemory.record(fixture.state, failed,
                PatronEngagementOutcome.FAILED, -1));
        assertEquals(0, fixture.state.patronEngagementCount);
    }

    @Test
    void survivesContractCompactionAndReplay() {
        Fixture fixture = fixture();
        long contractId = contract(fixture, ContractType.ESCORT,
                ContractState.COMPLETED);
        long id = PatronEngagementMemory.record(fixture.state, contractId,
                PatronEngagementOutcome.COMPLETED, 20);

        assertEquals(1, ContractTableCompactor.removeTerminal(fixture.state));
        assertEquals(-1, fixture.state.contractIndex(contractId));
        assertEquals(id, PatronEngagementMemory.record(fixture.state,
                contractId, PatronEngagementOutcome.COMPLETED, 21));
        assertEquals(contractId, PatronEngagementMemory.latest(
                fixture.state, fixture.patronId).sourceContractId);
    }

    @Test
    void latestUsesDayThenIdAndIgnoresMalformedRows() {
        Fixture fixture = fixture();
        long first = contract(fixture, ContractType.STRIKE,
                ContractState.FAILED);
        long second = contract(fixture, ContractType.ESCORT,
                ContractState.COMPLETED);
        PatronEngagementMemory.record(fixture.state, first,
                PatronEngagementOutcome.FAILED, 30);
        PatronEngagementMemory.record(fixture.state, second,
                PatronEngagementOutcome.COMPLETED, 30);

        PatronEngagementMemory.Snapshot latest =
                PatronEngagementMemory.latest(fixture.state, fixture.patronId);
        assertEquals(second, latest.sourceContractId);
        assertEquals(2, latest.priorEngagementCount);

        fixture.state.patronEngagementOutcome[1] = (byte) 127;
        latest = PatronEngagementMemory.latest(fixture.state, fixture.patronId);
        assertEquals(first, latest.sourceContractId);
        assertEquals(1, latest.priorEngagementCount);
    }

    @Test
    void roundTripRetainsMemoryAfterSourceContractIsGone() throws Exception {
        Fixture fixture = fixture();
        long contractId = contract(fixture, ContractType.EXTRACTION,
                ContractState.DEFAULTED);
        PatronEngagementMemory.record(fixture.state, contractId,
                PatronEngagementOutcome.EMPLOYER_BREACHED, 55);
        ContractTableCompactor.removeTerminal(fixture.state);

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(fixture.state);
        }
        CampaignState restored;
        try (ObjectInputStream input = new ObjectInputStream(
                new ByteArrayInputStream(bytes.toByteArray()))) {
            restored = (CampaignState) input.readObject();
        }

        PatronEngagementMemory.Snapshot memory =
                PatronEngagementMemory.latest(restored, fixture.patronId);
        assertNotNull(memory);
        assertEquals(PatronEngagementOutcome.EMPLOYER_BREACHED,
                memory.outcome);
        assertEquals(1, restored.patronEngagementCount);
        assertEquals(memory.id, PatronEngagementMemory.record(restored,
                contractId, PatronEngagementOutcome.EMPLOYER_BREACHED, 60));
    }

    @Test
    void growthAndLegacyBackfillInitializeSentinels() throws Exception {
        Fixture fixture = fixture();
        for (int i = 0; i < 20; i++) {
            long contractId = contract(fixture, ContractType.STRIKE,
                    ContractState.COMPLETED);
            PatronEngagementMemory.record(fixture.state, contractId,
                    PatronEngagementOutcome.COMPLETED, i);
        }
        assertEquals(-1L, fixture.state.patronEngagementSourceContractId[20]);
        assertEquals(-1L, fixture.state.patronEngagementHouseId[20]);
        assertEquals(-1, fixture.state.patronEngagementMarketId[20]);
        assertEquals(-1, fixture.state.patronEngagementHappenedTick[20]);

        CampaignState legacy = new CampaignState();
        legacy.patronEngagementId = null;
        legacy.patronEngagementSourceContractId = null;
        legacy.patronEngagementHouseId = null;
        legacy.patronEngagementContractType = null;
        legacy.patronEngagementMarketId = null;
        legacy.patronEngagementOutcome = null;
        legacy.patronEngagementHappenedTick = null;
        Method readResolve = CampaignState.class.getDeclaredMethod("readResolve");
        readResolve.setAccessible(true);
        readResolve.invoke(legacy);

        assertNotNull(legacy.patronEngagementId);
        assertEquals(-1L, legacy.patronEngagementSourceContractId[0]);
        assertEquals(-1L, legacy.patronEngagementHouseId[0]);
        assertEquals(-1, legacy.patronEngagementMarketId[0]);
        assertEquals(-1, legacy.patronEngagementHappenedTick[0]);
        assertNull(PatronEngagementMemory.latest(legacy, 1L));
    }

    private static Fixture fixture() {
        CampaignState state = new CampaignState();
        int marketId = state.marketRegistry.intern("jangala");
        long patronId = state.addHouse(marketId, 1, HouseFlavor.CORPORATE,
                HouseRank.TIER_2, HouseStatus.ACTIVE,
                PatronArchetype.ESTABLISHED, "House Cavor");
        return new Fixture(state, patronId, marketId);
    }

    private static long contract(Fixture fixture, ContractType type,
                                 ContractState state) {
        return fixture.state.addContract(fixture.patronId, -1L, -1L, type,
                state, 10, -1, -1, (byte) 1, -1, fixture.marketId, -1,
                1_000, 0, (byte) 25, (byte) 25, (byte) 100);
    }

    private static final class Fixture {
        final CampaignState state;
        final long patronId;
        final int marketId;

        Fixture(CampaignState state, long patronId, int marketId) {
            this.state = state;
            this.patronId = patronId;
            this.marketId = marketId;
        }
    }
}
