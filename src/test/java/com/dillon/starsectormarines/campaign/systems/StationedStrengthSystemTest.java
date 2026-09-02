package com.dillon.starsectormarines.campaign.systems;

import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.ContractState;
import com.dillon.starsectormarines.campaign.ContractType;
import com.dillon.starsectormarines.campaign.StationedStrength;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Coverage for the daily ground-defence write ({@code contracts-nouns.md}, law 11). */
class StationedStrengthSystemTest {

    private static final String MARKET = "jangala";

    /** Records every write instead of touching a market that only exists in the game. */
    private static final class RecordingSink
            implements StationedStrengthSystem.GroundDefenceSink {

        final List<String> calls = new ArrayList<>();
        boolean succeed = true;

        @Override
        public boolean apply(String marketId, String modifierId, float value,
                            String description) {
            calls.add("apply " + marketId + " " + modifierId + " " + value + " " + description);
            return succeed;
        }

        @Override
        public boolean remove(String marketId, String modifierId) {
            calls.add("remove " + marketId + " " + modifierId);
            return succeed;
        }
    }

    @Test
    void anActiveGarrisonContributesItsCommittedSeatsUnderItsOwnId() {
        CampaignState state = garrison(ContractState.ACTIVE, 12);
        RecordingSink sink = new RecordingSink();

        new StationedStrengthSystem(sink, () -> null).tick(state, 10);

        assertEquals(List.of("apply " + MARKET + " "
                        + StationedStrengthSystem.modifierId(state.contractId[0]) + " "
                        + 12 * StationedStrength.GREEN_SEAT_WORTH + " "
                        + StationedStrengthSystem.MODIFIER_DESCRIPTION),
                sink.calls);
        assertEquals(1, state.stationedStrengthCount);
        assertEquals(state.contractId[0], state.stationedStrengthContractId[0]);
        assertEquals(state.contractMarketId[0], state.stationedStrengthMarketId[0]);
    }

    @Test
    void theModifierIsRewrittenEveryDayRatherThanAccumulating() {
        CampaignState state = garrison(ContractState.IN_PROGRESS, 12);
        RecordingSink sink = new RecordingSink();
        StationedStrengthSystem system = new StationedStrengthSystem(sink, () -> null);

        system.tick(state, 10);
        system.tick(state, 11);
        system.tick(state, 12);

        assertEquals(3, sink.calls.size());
        assertTrue(sink.calls.stream().allMatch(call -> call.startsWith("apply ")),
                "the same id is overwritten; nothing is removed while the term stands");
        assertEquals(1, state.stationedStrengthCount, "one pair, not one per day");
    }

    @Test
    void everyStateThatEndsTheTermTakesTheModifierWithIt() {
        for (ContractState ended : List.of(ContractState.FAILED, ContractState.COMPLETED,
                ContractState.DEFAULTED, ContractState.ABANDONED, ContractState.EXPIRED)) {
            CampaignState state = garrison(ContractState.ACTIVE, 12);
            RecordingSink sink = new RecordingSink();
            StationedStrengthSystem system = new StationedStrengthSystem(sink, () -> null);
            system.tick(state, 10);

            state.contractState[0] = ended.toByte();
            sink.calls.clear();
            system.tick(state, 11);

            assertEquals(List.of("remove " + MARKET + " "
                            + StationedStrengthSystem.modifierId(state.contractId[0])),
                    sink.calls, "state " + ended);
            assertEquals(0, state.stationedStrengthCount, "state " + ended);
        }
    }

    @Test
    void aContractRowThatVanishedIsStillSweptOffItsMarket() {
        CampaignState state = garrison(ContractState.ACTIVE, 12);
        long contractId = state.contractId[0];
        int marketSlot = state.contractMarketId[0];
        RecordingSink sink = new RecordingSink();
        new StationedStrengthSystem(sink, () -> null).tick(state, 10);

        // A fresh load whose contract row was compacted away, carrying the applied set.
        CampaignState loaded = new CampaignState();
        loaded.marketRegistry.intern(MARKET);
        loaded.recordStationedStrength(contractId, marketSlot);
        sink.calls.clear();

        new StationedStrengthSystem(sink, () -> null).tick(loaded, 11);

        assertEquals(List.of("remove " + MARKET + " "
                        + StationedStrengthSystem.modifierId(contractId)),
                sink.calls, "the sweep does not trust that vanilla dropped it");
        assertEquals(0, loaded.stationedStrengthCount);
    }

    @Test
    void theSweepClearsEveryStaleRecordInOnePass() {
        CampaignState state = new CampaignState();
        int marketSlot = state.marketRegistry.intern(MARKET);
        for (long contractId = 1L; contractId <= 4L; contractId++) {
            state.recordStationedStrength(contractId, marketSlot);
        }
        RecordingSink sink = new RecordingSink();

        new StationedStrengthSystem(sink, () -> null).tick(state, 10);

        assertEquals(0, state.stationedStrengthCount, "swap-and-pop must not skip an entry");
        assertEquals(4, sink.calls.size());
    }

    @Test
    void cadreAndMissionContractsDefendNothing() {
        for (ContractType type : List.of(ContractType.CADRE, ContractType.STRIKE,
                ContractType.PLANETARY_ASSAULT, ContractType.EXTRACTION)) {
            CampaignState state = new CampaignState();
            state.addContract(1L, -1L, -1L, type, ContractState.ACTIVE, 10, 500, -1,
                    (byte) 0, -1, state.marketRegistry.intern(MARKET), -1, 0, 1_000,
                    (byte) 25, (byte) 25, (byte) 100);
            state.contractMarinesCommitted[0] = 12;
            RecordingSink sink = new RecordingSink();

            new StationedStrengthSystem(sink, () -> null).tick(state, 10);

            assertTrue(sink.calls.isEmpty(), "type " + type);
            assertEquals(0, state.stationedStrengthCount, "type " + type);
        }
    }

    @Test
    void aGarrisonWithNobodyLeftOnItRemovesItsModifier() {
        CampaignState state = garrison(ContractState.ACTIVE, 0);
        RecordingSink sink = new RecordingSink();

        new StationedStrengthSystem(sink, () -> null).tick(state, 10);

        assertEquals(List.of("remove " + MARKET + " "
                        + StationedStrengthSystem.modifierId(state.contractId[0])),
                sink.calls);
        assertEquals(0, state.stationedStrengthCount);
    }

    @Test
    void aWriteThatDidNotLandIsNotRecordedAsApplied() {
        CampaignState state = garrison(ContractState.ACTIVE, 12);
        RecordingSink sink = new RecordingSink();
        sink.succeed = false;

        new StationedStrengthSystem(sink, () -> null).tick(state, 10);

        assertEquals(1, sink.calls.size());
        assertEquals(0, state.stationedStrengthCount,
                "a pair recorded for a modifier that never landed cannot be swept honestly");
    }

    @Test
    void aRemovalThatDidNotLandKeepsThePairForTheNextSweep() {
        CampaignState state = garrison(ContractState.ACTIVE, 12);
        RecordingSink sink = new RecordingSink();
        StationedStrengthSystem system = new StationedStrengthSystem(sink, () -> null);
        system.tick(state, 10);

        state.contractState[0] = ContractState.FAILED.toByte();
        sink.succeed = false;
        system.tick(state, 11);
        assertEquals(1, state.stationedStrengthCount, "the market was not there; try tomorrow");

        sink.succeed = true;
        system.tick(state, 12);
        assertEquals(0, state.stationedStrengthCount);
    }

    @Test
    void anUnresolvableMarketIsSkippedRatherThanWrittenToUnderANullId() {
        CampaignState state = garrison(ContractState.ACTIVE, 12);
        state.contractMarketId[0] = -1;
        RecordingSink sink = new RecordingSink();

        new StationedStrengthSystem(sink, () -> null).tick(state, 10);

        assertTrue(sink.calls.isEmpty());
        assertEquals(0, state.stationedStrengthCount);
    }

    private static CampaignState garrison(ContractState contractState, int committed) {
        CampaignState state = new CampaignState();
        state.addContract(1L, -1L, -1L, ContractType.GARRISON, contractState, 10, 500, -1,
                (byte) 0, -1, state.marketRegistry.intern(MARKET), -1, 0, 1_000,
                (byte) 25, (byte) 25, (byte) 100);
        state.contractMarinesCommitted[0] = committed;
        return state;
    }
}
