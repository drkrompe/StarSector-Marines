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
    }

    @AfterAll
    static void resetVoice() {
        PatronMemoryVoice.loadForTest(null);
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

    private static Fixture fixture() {
        CampaignState state = new CampaignState();
        int marketId = state.marketRegistry.intern("jangala");
        long patronId = state.addHouse(marketId, 1, HouseFlavor.CORPORATE,
                HouseRank.TIER_2, HouseStatus.ACTIVE,
                PatronArchetype.ESTABLISHED, "House Cavor");
        return new Fixture(state, patronId, marketId);
    }

    private static long contract(Fixture fixture, ContractState state) {
        return fixture.state.addContract(fixture.patronId, -1L, -1L,
                ContractType.STRIKE, state, 10, -1, -1, (byte) 1, -1,
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
