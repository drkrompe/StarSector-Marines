package com.dillon.starsectormarines.campaign;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PatronLocalEchoComposerTest {

    @BeforeAll
    static void injectVoice() {
        PatronMemoryVoice.loadLocalEchoForTest(localPools());
        Map<PatronEngagementOutcome, String[]> direct =
                new EnumMap<>(PatronEngagementOutcome.class);
        for (PatronEngagementOutcome outcome
                : PatronEngagementOutcome.values()) {
            direct.put(outcome, new String[] {
                    "[DIRECT:" + outcome.name() + "] {patron}"
            });
        }
        PatronMemoryVoice.loadForTest(direct);
    }

    @AfterAll
    static void resetVoice() {
        PatronMemoryVoice.loadLocalEchoForTest(null);
        PatronMemoryVoice.loadForTest(null);
    }

    @Test
    void firstTimePatronGetsDeterministicRecentLocalEcho() {
        Fixture fixture = fixture();
        record(fixture, fixture.otherPatronId, ContractType.ESCORT,
                PatronEngagementOutcome.FAILED, 20);

        String first = PatronBriefingContextComposer.compose(fixture.state,
                fixture.currentPatronId, fixture.marketId, 99L, 100,
                "House Current");
        String replay = PatronBriefingContextComposer.compose(fixture.state,
                fixture.currentPatronId, fixture.marketId, 99L, 100,
                "House Current");

        assertEquals(first, replay);
        assertTrue(first.startsWith("[LOCAL:FAILED"));
        assertTrue(first.contains(
                "House Other/escort/failure/80/80 days ago"));
        assertFalse(first.contains("{"));
    }

    @Test
    void directPatronHistorySuppressesLocalEcho() {
        Fixture fixture = fixture();
        record(fixture, fixture.otherPatronId, ContractType.ESCORT,
                PatronEngagementOutcome.COMPLETED, 20);
        record(fixture, fixture.currentPatronId, ContractType.STRIKE,
                PatronEngagementOutcome.COMPLETED, 30);

        assertNull(PatronLocalEchoComposer.compose(fixture.state,
                fixture.currentPatronId, fixture.marketId, 99L, 100));
        String chosen = PatronBriefingContextComposer.compose(fixture.state,
                fixture.currentPatronId, fixture.marketId, 99L, 100,
                "House Current");
        assertTrue(chosen.startsWith("[DIRECT:COMPLETED]"));
        assertFalse(chosen.contains("[LOCAL:"));
    }

    @Test
    void staleAndMissingContentLeaveFirstTimeBriefingUnchanged() {
        Fixture fixture = fixture();
        record(fixture, fixture.otherPatronId, ContractType.GARRISON,
                PatronEngagementOutcome.WITHDREW, 19);
        assertNull(PatronLocalEchoComposer.compose(fixture.state,
                fixture.currentPatronId, fixture.marketId, 99L, 200));

        Fixture recent = fixture();
        record(recent, recent.otherPatronId, ContractType.GARRISON,
                PatronEngagementOutcome.WITHDREW, 20);
        PatronMemoryVoice.loadLocalEchoForTest(
                new EnumMap<>(PatronEngagementOutcome.class));
        try {
            assertNull(PatronLocalEchoComposer.compose(recent.state,
                    recent.currentPatronId, recent.marketId, 99L, 200));
        } finally {
            PatronMemoryVoice.loadLocalEchoForTest(localPools());
        }
    }

    @Test
    void saveLoadAndSourceCompactionKeepEchoStable() throws Exception {
        Fixture fixture = fixture();
        record(fixture, fixture.otherPatronId, ContractType.CADRE,
                PatronEngagementOutcome.EMPLOYER_BREACHED, 40);
        String before = PatronBriefingContextComposer.compose(fixture.state,
                fixture.currentPatronId, fixture.marketId, 99L, 100,
                "House Current");
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
        String after = PatronBriefingContextComposer.compose(restored,
                fixture.currentPatronId, fixture.marketId, 99L, 100,
                "House Current");

        assertEquals(before, after);
        assertTrue(after.startsWith("[LOCAL:EMPLOYER_BREACHED"));
    }

    @Test
    void everyMeasuredOutcomeUsesItsOwnLocalPool() {
        for (PatronEngagementOutcome outcome
                : PatronEngagementOutcome.values()) {
            Fixture fixture = fixture();
            record(fixture, fixture.otherPatronId, ContractType.STRIKE,
                    outcome, 99);
            String line = PatronBriefingContextComposer.compose(fixture.state,
                    fixture.currentPatronId, fixture.marketId, 100L, 100,
                    "House Current");
            assertTrue(line.startsWith("[LOCAL:" + outcome.name()));
            assertTrue(line.contains("/1/1 day ago"));
        }
    }

    private static Map<PatronEngagementOutcome, String[]> localPools() {
        Map<PatronEngagementOutcome, String[]> pools =
                new EnumMap<>(PatronEngagementOutcome.class);
        for (PatronEngagementOutcome outcome
                : PatronEngagementOutcome.values()) {
            pools.put(outcome, new String[] {
                    "[LOCAL:" + outcome.name() + "] {otherPatron}/"
                            + "{otherContract}/{otherOutcome}/{daysAgo}/{age}",
                    "[LOCAL:" + outcome.name() + ":ALT] {otherPatron}/"
                            + "{otherContract}/{otherOutcome}/{daysAgo}/{age}"
            });
        }
        return pools;
    }

    private static Fixture fixture() {
        CampaignState state = new CampaignState();
        int marketId = state.marketRegistry.intern("jangala");
        long currentPatronId = state.addHouse(marketId, 1,
                HouseFlavor.CORPORATE, HouseRank.TIER_2,
                HouseStatus.ACTIVE, PatronArchetype.ESTABLISHED,
                "House Current");
        long otherPatronId = state.addHouse(marketId, 1,
                HouseFlavor.FEUDAL, HouseRank.TIER_2,
                HouseStatus.ACTIVE, PatronArchetype.FALLEN_NOBLE,
                "House Other");
        return new Fixture(state, marketId, currentPatronId, otherPatronId);
    }

    private static void record(Fixture fixture, long patronId,
                               ContractType type,
                               PatronEngagementOutcome outcome, int day) {
        ContractState terminal = terminal(outcome);
        long contractId = fixture.state.addContract(patronId, -1L, -1L,
                type, terminal, day, -1, -1, (byte) 1, -1,
                fixture.marketId, -1, 1_000, 0,
                (byte) 25, (byte) 25, (byte) 100);
        PatronEngagementMemory.record(
                fixture.state, contractId, outcome, day);
    }

    private static ContractState terminal(PatronEngagementOutcome outcome) {
        switch (outcome) {
            case COMPLETED: return ContractState.COMPLETED;
            case FAILED: return ContractState.FAILED;
            case WITHDREW: return ContractState.ABANDONED;
            case EMPLOYER_BREACHED: return ContractState.DEFAULTED;
            default: throw new IllegalArgumentException(outcome.name());
        }
    }

    private static final class Fixture {
        final CampaignState state;
        final int marketId;
        final long currentPatronId;
        final long otherPatronId;

        Fixture(CampaignState state, int marketId, long currentPatronId,
                long otherPatronId) {
            this.state = state;
            this.marketId = marketId;
            this.currentPatronId = currentPatronId;
            this.otherPatronId = otherPatronId;
        }
    }
}
