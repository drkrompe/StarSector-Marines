package com.dillon.starsectormarines.campaign;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PatronChronicleMemoryTest {

    @Test
    void filtersUnrelatedRumorFutureStaleSelfAndMalformedRows() {
        Fixture fixture = fixture();
        fixture.state.addChronicleChainRumor(10L, ChronicleBand.INTIMATE,
                fixture.patronId, fixture.otherId, fixture.marketId, -1,
                90, 91);
        fixture.state.addChronicleHouseDormancy(ChronicleBand.INTIMATE,
                fixture.patronId, fixture.marketId, 92, 93);
        addChain(fixture, fixture.otherId, fixture.thirdId,
                ChainState.RESOLVED, 94, 95);
        addChain(fixture, fixture.patronId, fixture.patronId,
                ChainState.RESOLVED, 96, 97);
        addChain(fixture, fixture.patronId, fixture.otherId,
                ChainState.RESOLVED, 498, 501);
        addChain(fixture, fixture.patronId, fixture.otherId,
                ChainState.RESOLVED, 0, 1);
        long malformedId = addChain(fixture, fixture.patronId,
                fixture.otherId, ChainState.RESOLVED, 499, 500);
        int malformedRow = chronicleRow(fixture.state, malformedId);
        fixture.state.chronicleEventType[malformedRow] = (byte) 127;

        assertNull(PatronChronicleMemory.latestForPatron(fixture.state,
                fixture.patronId, 500, 365));
    }

    @Test
    void classifiesEverySupportedEventAndPatronRole() {
        assertType(PatronChronicleReferenceType.CHAIN_ACTOR,
                (fixture) -> addChain(fixture, fixture.patronId,
                        fixture.otherId, ChainState.RESOLVED, 99, 100));
        assertType(PatronChronicleReferenceType.CHAIN_TARGET,
                (fixture) -> addChain(fixture, fixture.otherId,
                        fixture.patronId, ChainState.FAILED, 99, 100));
        assertType(PatronChronicleReferenceType.THRONE_CLAIMANT,
                (fixture) -> addThrone(fixture, fixture.patronId,
                        fixture.otherId, 99, 100));
        assertType(PatronChronicleReferenceType.THRONE_DISPLACED,
                (fixture) -> addThrone(fixture, fixture.otherId,
                        fixture.patronId, 99, 100));
        assertType(PatronChronicleReferenceType.TESTAMENT_CLAIMANT,
                (fixture) -> addTestament(fixture, fixture.patronId,
                        fixture.otherId, 99, 100));
        assertType(PatronChronicleReferenceType.TESTAMENT_DEPOSED,
                (fixture) -> addTestament(fixture, fixture.otherId,
                        fixture.patronId, 99, 100));
    }

    @Test
    void exactAgeBoundaryIsInclusive() {
        Fixture fixture = fixture();
        long boundaryId = addChain(fixture, fixture.patronId,
                fixture.otherId, ChainState.RESOLVED, 135, 136);
        addChain(fixture, fixture.patronId, fixture.thirdId,
                ChainState.FAILED, 134, 499);

        PatronChronicleMemory.Snapshot result =
                PatronChronicleMemory.latestForPatron(fixture.state,
                        fixture.patronId, 500, 365);

        assertEquals(boundaryId, result.id);
    }

    @Test
    void newestLearnedThenHappenedThenImmutableIdWins() {
        Fixture fixture = fixture();
        addChain(fixture, fixture.patronId, fixture.otherId,
                ChainState.RESOLVED, 190, 191);
        long newerKnowledge = addChain(fixture, fixture.patronId,
                fixture.thirdId, ChainState.FAILED, 180, 195);
        long tieWinner = addChain(fixture, fixture.patronId,
                fixture.otherId, ChainState.RESOLVED, 180, 195);

        PatronChronicleMemory.Snapshot result =
                PatronChronicleMemory.latestForPatron(fixture.state,
                        fixture.patronId, 200, 365);

        assertEquals(tieWinner, result.id);
        assertEquals(ChainState.RESOLVED, result.chainOutcome);
        assertEquals(180, result.happenedTick);
        assertEquals(195, result.learnedTick);
        assertEquals(newerKnowledge + 1L, tieWinner);
    }

    @Test
    void malformedNewestRowDoesNotHideOlderValidFact() {
        Fixture fixture = fixture();
        long validId = addChain(fixture, fixture.otherId,
                fixture.patronId, ChainState.FAILED, 80, 81);
        long invalidId = addChain(fixture, fixture.patronId,
                fixture.thirdId, ChainState.RESOLVED, 90, 91);
        fixture.state.houseDisplayName[
                fixture.state.houseIndex(fixture.thirdId)] = " ";

        PatronChronicleMemory.Snapshot result =
                PatronChronicleMemory.latestForPatron(fixture.state,
                        fixture.patronId, 100, 365);

        assertEquals(validId, result.id);
        assertEquals(PatronChronicleReferenceType.CHAIN_TARGET,
                result.referenceType);
        assertEquals(invalidId - 1L, validId);
    }

    private static void assertType(PatronChronicleReferenceType expected,
                                   ChronicleAdder adder) {
        Fixture fixture = fixture();
        long id = adder.add(fixture);
        PatronChronicleMemory.Snapshot result =
                PatronChronicleMemory.latestForPatron(fixture.state,
                        fixture.patronId, 100, 365);
        assertEquals(id, result.id);
        assertEquals(expected, result.referenceType);
    }

    private static long addChain(Fixture fixture, long actor, long target,
                                 ChainState outcome, int happened,
                                 int learned) {
        return fixture.state.addChronicleChainOutcome(
                100L + fixture.state.chronicleCount, outcome,
                ChronicleBand.INTIMATE, actor, target, fixture.marketId, -1,
                happened, learned);
    }

    private static long addThrone(Fixture fixture, long actor, long target,
                                  int happened, int learned) {
        return fixture.state.addChronicleThroneClaimApplied(
                200L + fixture.state.chronicleCount, ChronicleBand.EPIC,
                actor, target, fixture.sourceFactionId,
                fixture.resultFactionId, fixture.marketId, happened, learned);
    }

    private static long addTestament(Fixture fixture, long actor, long target,
                                     int happened, int learned) {
        return fixture.state.addChronicleKingmakerTestament(
                300L + fixture.state.chronicleCount,
                400L + fixture.state.chronicleCount, actor, target,
                fixture.sourceFactionId, fixture.resultFactionId,
                fixture.marketId, happened, learned);
    }

    private static int chronicleRow(CampaignState state, long id) {
        for (int row = 0; row < state.chronicleCount; row++) {
            if (state.chronicleId[row] == id) return row;
        }
        return -1;
    }

    private static Fixture fixture() {
        CampaignState state = new CampaignState();
        int marketId = state.marketRegistry.intern("jangala");
        int sourceFaction = state.factionRegistry.intern("hegemony");
        int resultFaction = state.factionRegistry.intern("independent");
        long patron = state.addHouse(marketId, sourceFaction,
                HouseFlavor.CORPORATE, HouseRank.TIER_3,
                HouseStatus.ACTIVE, PatronArchetype.ESTABLISHED,
                "House Current");
        long other = state.addHouse(marketId, sourceFaction,
                HouseFlavor.FEUDAL, HouseRank.TIER_3,
                HouseStatus.ACTIVE, PatronArchetype.FALLEN_NOBLE,
                "House Other");
        long third = state.addHouse(marketId, resultFaction,
                HouseFlavor.UNDERWORLD, HouseRank.TIER_2,
                HouseStatus.ACTIVE, PatronArchetype.SUSPICIOUS,
                "House Third");
        return new Fixture(state, marketId, sourceFaction, resultFaction,
                patron, other, third);
    }

    private interface ChronicleAdder {
        long add(Fixture fixture);
    }

    private static final class Fixture {
        final CampaignState state;
        final int marketId;
        final int sourceFactionId;
        final int resultFactionId;
        final long patronId;
        final long otherId;
        final long thirdId;

        Fixture(CampaignState state, int marketId, int sourceFactionId,
                int resultFactionId, long patronId, long otherId,
                long thirdId) {
            this.state = state;
            this.marketId = marketId;
            this.sourceFactionId = sourceFactionId;
            this.resultFactionId = resultFactionId;
            this.patronId = patronId;
            this.otherId = otherId;
            this.thirdId = thirdId;
        }
    }
}
