package com.dillon.starsectormarines.campaign;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StarsectorThroneClaimWritebackTest {

    @Test
    void alreadyAppliedPostconditionDoesNotReplayTransfer() {
        FakeAccess access = readyAccess("result", true);

        ThroneClaimWriteback.Result result = writeback(access).apply(
                "source", "result", "market");

        assertEquals(ThroneClaimWriteback.Result.ALREADY_APPLIED, result);
        assertEquals(0, access.transfers);
    }

    @Test
    void sourceOwnedMarketTransfersAndVerifies() {
        FakeAccess access = readyAccess("source", false);

        ThroneClaimWriteback.Result result = writeback(access).apply(
                "source", "result", "market");

        assertEquals(ThroneClaimWriteback.Result.APPLIED, result);
        assertEquals(1, access.transfers);
        assertEquals("result", access.marketFaction);
        assertEquals(true, access.entitiesMatch);
    }

    @Test
    void partialPriorApplicationRepairsEntityOwnership() {
        FakeAccess access = readyAccess("result", false);

        ThroneClaimWriteback.Result result = writeback(access).apply(
                "source", "result", "market");

        assertEquals(ThroneClaimWriteback.Result.APPLIED, result);
        assertEquals(1, access.transfers);
    }

    @Test
    void unavailableSectorRetriesAndUnknownIdentityRejects() {
        FakeAccess unavailable = readyAccess("source", true);
        unavailable.available = false;
        FakeAccess unknownFaction = readyAccess("source", true);
        unknownFaction.factions.remove("result");
        FakeAccess unknownMarket = readyAccess("source", true);
        unknownMarket.marketExists = false;

        assertEquals(ThroneClaimWriteback.Result.RETRY,
                writeback(unavailable).apply("source", "result", "market"));
        assertEquals(ThroneClaimWriteback.Result.REJECTED,
                writeback(unknownFaction).apply("source", "result", "market"));
        assertEquals(ThroneClaimWriteback.Result.REJECTED,
                writeback(unknownMarket).apply("source", "result", "market"));
    }

    @Test
    void thirdPartyOwnerRejectsAndFailedPostconditionRetries() {
        FakeAccess thirdParty = readyAccess("other", true);
        FakeAccess failedWrite = readyAccess("source", false);
        failedWrite.writeSucceeds = false;

        assertEquals(ThroneClaimWriteback.Result.REJECTED,
                writeback(thirdParty).apply("source", "result", "market"));
        assertEquals(ThroneClaimWriteback.Result.RETRY,
                writeback(failedWrite).apply("source", "result", "market"));
    }

    private static StarsectorThroneClaimWriteback writeback(FakeAccess access) {
        return new StarsectorThroneClaimWriteback(access);
    }

    private static FakeAccess readyAccess(String marketFaction, boolean entitiesMatch) {
        FakeAccess access = new FakeAccess();
        access.factions.add("source");
        access.factions.add("result");
        access.marketFaction = marketFaction;
        access.entitiesMatch = entitiesMatch;
        return access;
    }

    private static final class FakeAccess
            implements StarsectorThroneClaimWriteback.SectorAccess {
        boolean available = true;
        boolean marketExists = true;
        boolean entitiesMatch;
        boolean writeSucceeds = true;
        String marketFaction;
        int transfers;
        final Set<String> factions = new HashSet<>();

        @Override
        public boolean available() {
            return available;
        }

        @Override
        public boolean factionExists(String factionId) {
            return factions.contains(factionId);
        }

        @Override
        public boolean marketExists(String marketId) {
            return marketExists;
        }

        @Override
        public String marketFactionId(String marketId) {
            return marketFaction;
        }

        @Override
        public boolean marketEntitiesBelongTo(String marketId, String factionId) {
            return entitiesMatch && factionId.equals(marketFaction);
        }

        @Override
        public void transferMarket(String marketId, String factionId) {
            transfers++;
            if (!writeSucceeds) return;
            marketFaction = factionId;
            entitiesMatch = true;
        }
    }
}
