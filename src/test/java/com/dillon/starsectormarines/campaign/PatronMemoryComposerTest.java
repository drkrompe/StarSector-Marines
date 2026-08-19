package com.dillon.starsectormarines.campaign;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PatronMemoryComposerTest {

    @BeforeAll
    static void injectVoice() {
        Map<PatronEngagementOutcome, String[]> pools =
                new EnumMap<>(PatronEngagementOutcome.class);
        for (PatronEngagementOutcome outcome
                : PatronEngagementOutcome.values()) {
            pools.put(outcome, new String[] {
                    "[" + outcome.name() + "] {patron}/{contract}/{priorCount}",
                    "[" + outcome.name() + ":ALT] {patron}/{contract}/{priorCount}"
            });
        }
        PatronMemoryVoice.loadForTest(pools);
        PatronMemoryVoice.loadContinuityForTest(continuityPools());
    }

    @AfterAll
    static void resetVoice() {
        PatronMemoryVoice.loadForTest(null);
        PatronMemoryVoice.loadContinuityForTest(null);
    }

    @Test
    void firstTimePatronAndCurrentSourceStaySilent() {
        Fixture fixture = fixture();
        assertNull(PatronMemoryComposer.compose(fixture.state,
                fixture.patronId, 2L, "House Cavor"));

        long source = contract(fixture, ContractState.COMPLETED);
        PatronEngagementMemory.record(fixture.state, source,
                PatronEngagementOutcome.COMPLETED, 10);
        assertNull(PatronMemoryComposer.compose(fixture.state,
                fixture.patronId, source, "House Cavor"));
    }

    @Test
    void returningPatronGetsDeterministicFactualCallback() {
        Fixture fixture = fixture();
        long source = contract(fixture, ContractState.FAILED);
        PatronEngagementMemory.record(fixture.state, source,
                PatronEngagementOutcome.FAILED, 10);

        String first = PatronMemoryComposer.compose(fixture.state,
                fixture.patronId, source + 1L, "House Cavor");
        String replay = PatronMemoryComposer.compose(fixture.state,
                fixture.patronId, source + 1L, "House Cavor");

        assertEquals(first, replay);
        assertTrue(first.startsWith("[FAILED"));
        assertTrue(first.contains("House Cavor/strike/1"));
        assertFalse(first.contains("{"));
    }

    @Test
    void twoEngagementsUseDeterministicPatternCallback() {
        Fixture fixture = fixture();
        long previous = contract(fixture, ContractType.STRIKE,
                ContractState.COMPLETED);
        PatronEngagementMemory.record(fixture.state, previous,
                PatronEngagementOutcome.COMPLETED, 10);
        long latest = contract(fixture, ContractType.ESCORT,
                ContractState.COMPLETED);
        PatronEngagementMemory.record(fixture.state, latest,
                PatronEngagementOutcome.COMPLETED, 20);

        String first = PatronMemoryComposer.compose(fixture.state,
                fixture.patronId, 99L, "House Cavor");
        String replay = PatronMemoryComposer.compose(fixture.state,
                fixture.patronId, 99L, "House Cavor");

        assertEquals(first, replay);
        assertTrue(first.startsWith("[SUCCESS_STREAK"));
        assertTrue(first.contains(
                "House Cavor/strike/completion/escort/completion/2/3"));
        assertFalse(first.contains("{"));
    }

    @Test
    void missingContinuityContentFallsBackToLatestS1Outcome() {
        Fixture fixture = fixture();
        long previous = contract(fixture, ContractType.STRIKE,
                ContractState.COMPLETED);
        PatronEngagementMemory.record(fixture.state, previous,
                PatronEngagementOutcome.COMPLETED, 10);
        long latest = contract(fixture, ContractType.ESCORT,
                ContractState.FAILED);
        PatronEngagementMemory.record(fixture.state, latest,
                PatronEngagementOutcome.FAILED, 20);
        PatronMemoryVoice.loadContinuityForTest(
                new EnumMap<>(PatronRelationshipPattern.class));
        try {
            String line = PatronMemoryComposer.compose(fixture.state,
                    fixture.patronId, 99L, "House Cavor");
            assertTrue(line.startsWith("[FAILED"));
        } finally {
            PatronMemoryVoice.loadContinuityForTest(continuityPools());
        }
    }

    @Test
    void allOrderedOutcomePairsHaveOneLockedPattern() {
        assertPattern(PatronRelationshipPattern.SUCCESS_STREAK,
                PatronEngagementOutcome.COMPLETED,
                PatronEngagementOutcome.COMPLETED);
        assertPattern(PatronRelationshipPattern.PLAYER_SETBACK,
                PatronEngagementOutcome.COMPLETED,
                PatronEngagementOutcome.FAILED);
        assertPattern(PatronRelationshipPattern.PLAYER_SETBACK,
                PatronEngagementOutcome.COMPLETED,
                PatronEngagementOutcome.WITHDREW);
        assertPattern(PatronRelationshipPattern.BREACH_AFTER_SUCCESS,
                PatronEngagementOutcome.COMPLETED,
                PatronEngagementOutcome.EMPLOYER_BREACHED);
        assertRecoveryFrom(PatronEngagementOutcome.FAILED);
        assertRecoveryFrom(PatronEngagementOutcome.WITHDREW);
        assertRecoveryFrom(PatronEngagementOutcome.EMPLOYER_BREACHED);
        assertPlayerTrouble(PatronEngagementOutcome.FAILED,
                PatronEngagementOutcome.FAILED);
        assertPlayerTrouble(PatronEngagementOutcome.FAILED,
                PatronEngagementOutcome.WITHDREW);
        assertPlayerTrouble(PatronEngagementOutcome.WITHDREW,
                PatronEngagementOutcome.FAILED);
        assertPlayerTrouble(PatronEngagementOutcome.WITHDREW,
                PatronEngagementOutcome.WITHDREW);
        assertPattern(PatronRelationshipPattern.MUTUAL_TROUBLE,
                PatronEngagementOutcome.FAILED,
                PatronEngagementOutcome.EMPLOYER_BREACHED);
        assertPattern(PatronRelationshipPattern.MUTUAL_TROUBLE,
                PatronEngagementOutcome.WITHDREW,
                PatronEngagementOutcome.EMPLOYER_BREACHED);
        assertPattern(PatronRelationshipPattern.MUTUAL_TROUBLE,
                PatronEngagementOutcome.EMPLOYER_BREACHED,
                PatronEngagementOutcome.FAILED);
        assertPattern(PatronRelationshipPattern.MUTUAL_TROUBLE,
                PatronEngagementOutcome.EMPLOYER_BREACHED,
                PatronEngagementOutcome.WITHDREW);
        assertPattern(PatronRelationshipPattern.REPEATED_PATRON_BREACH,
                PatronEngagementOutcome.EMPLOYER_BREACHED,
                PatronEngagementOutcome.EMPLOYER_BREACHED);
    }

    private static void assertRecoveryFrom(
            PatronEngagementOutcome previous) {
        assertPattern(PatronRelationshipPattern.RECOVERY, previous,
                PatronEngagementOutcome.COMPLETED);
    }

    private static void assertPlayerTrouble(
            PatronEngagementOutcome previous,
            PatronEngagementOutcome latest) {
        assertPattern(PatronRelationshipPattern.REPEATED_PLAYER_TROUBLE,
                previous, latest);
    }

    private static void assertPattern(PatronRelationshipPattern expected,
                                      PatronEngagementOutcome previous,
                                      PatronEngagementOutcome latest) {
        assertEquals(expected, PatronMemoryComposer.classify(previous, latest));
    }

    private static Map<PatronRelationshipPattern, String[]>
            continuityPools() {
        Map<PatronRelationshipPattern, String[]> pools =
                new EnumMap<>(PatronRelationshipPattern.class);
        for (PatronRelationshipPattern pattern
                : PatronRelationshipPattern.values()) {
            pools.put(pattern, new String[] {
                    "[" + pattern.name() + "] {patron}/{previousContract}/"
                            + "{previousOutcome}/{latestContract}/"
                            + "{latestOutcome}/{priorCount}/"
                            + "{engagementNumber}",
                    "[" + pattern.name() + ":ALT] {patron}/"
                            + "{previousContract}/{previousOutcome}/"
                            + "{latestContract}/{latestOutcome}/"
                            + "{priorCount}/{engagementNumber}"
            });
        }
        return pools;
    }

    private static Fixture fixture() {
        CampaignState state = new CampaignState();
        int marketId = state.marketRegistry.intern("jangala");
        long patronId = state.addHouse(marketId, 1, HouseFlavor.CORPORATE,
                HouseRank.TIER_2, HouseStatus.ACTIVE,
                PatronArchetype.ESTABLISHED, "House Cavor");
        return new Fixture(state, patronId, marketId);
    }

    private static long contract(Fixture fixture, ContractState state) {
        return contract(fixture, ContractType.STRIKE, state);
    }

    private static long contract(Fixture fixture, ContractType type,
                                 ContractState state) {
        return fixture.state.addContract(fixture.patronId, -1L, -1L,
                type, state, 10, -1, -1, (byte) 1, -1,
                fixture.marketId, -1, 1_000, 0, (byte) 25, (byte) 25,
                (byte) 100);
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
