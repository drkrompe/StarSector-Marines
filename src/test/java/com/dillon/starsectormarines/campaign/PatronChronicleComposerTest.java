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
import static org.junit.jupiter.api.Assertions.assertTrue;

class PatronChronicleComposerTest {

    @BeforeAll
    static void injectVoice() {
        PatronChronicleVoice.loadForTest(chroniclePools());
        Map<PatronEngagementOutcome, String[]> direct =
                new EnumMap<>(PatronEngagementOutcome.class);
        Map<PatronEngagementOutcome, String[]> local =
                new EnumMap<>(PatronEngagementOutcome.class);
        for (PatronEngagementOutcome outcome
                : PatronEngagementOutcome.values()) {
            direct.put(outcome, new String[] {
                    "[DIRECT:" + outcome.name() + "] {patron}"
            });
            local.put(outcome, new String[] {
                    "[LOCAL:" + outcome.name() + "] {otherPatron}"
            });
        }
        PatronMemoryVoice.loadForTest(direct);
        PatronMemoryVoice.loadLocalEchoForTest(local);
    }

    @AfterAll
    static void resetVoice() {
        PatronChronicleVoice.loadForTest(null);
        PatronMemoryVoice.loadForTest(null);
        PatronMemoryVoice.loadLocalEchoForTest(null);
    }

    @Test
    void confirmedPatronFactIsDeterministicAndReplacesTokens() {
        Fixture fixture = fixture();
        addChain(fixture, fixture.patronId, fixture.otherId,
                ChainState.FAILED, 20, 30);

        String first = compose(fixture, 100L, 100);
        String replay = compose(fixture, 100L, 100);

        assertEquals(first, replay);
        assertTrue(first.startsWith("[CHRON:CHAIN_ACTOR"));
        assertTrue(first.contains(
                "House Current/House Other/failed/80/80 days ago"));
        assertFalse(first.contains("{"));
    }

    @Test
    void directHistoryWinsThenChronicleWinsOverLocalEcho() {
        Fixture direct = fixture();
        record(direct, direct.otherId, PatronEngagementOutcome.COMPLETED, 20);
        addChain(direct, direct.patronId, direct.otherId,
                ChainState.RESOLVED, 30, 31);
        record(direct, direct.patronId,
                PatronEngagementOutcome.COMPLETED, 40);
        String directLine = compose(direct, 100L, 100);
        assertTrue(directLine.startsWith("[DIRECT:COMPLETED]"));

        Fixture chronicle = fixture();
        record(chronicle, chronicle.otherId,
                PatronEngagementOutcome.COMPLETED, 20);
        addChain(chronicle, chronicle.otherId, chronicle.patronId,
                ChainState.FAILED, 30, 31);
        String chronicleLine = compose(chronicle, 100L, 100);
        assertTrue(chronicleLine.startsWith("[CHRON:CHAIN_TARGET"));
        assertFalse(chronicleLine.contains("[LOCAL:"));
    }

    @Test
    void missingChronicleContentFallsThroughToLocalEcho() {
        Fixture fixture = fixture();
        record(fixture, fixture.otherId,
                PatronEngagementOutcome.WITHDREW, 20);
        addChain(fixture, fixture.patronId, fixture.otherId,
                ChainState.RESOLVED, 30, 31);
        PatronChronicleVoice.loadForTest(
                new EnumMap<>(PatronChronicleReferenceType.class));
        try {
            String line = compose(fixture, 100L, 100);
            assertTrue(line.startsWith("[LOCAL:WITHDREW]"));
        } finally {
            PatronChronicleVoice.loadForTest(chroniclePools());
        }
    }

    @Test
    void saveLoadKeepsReferenceStable() throws Exception {
        Fixture fixture = fixture();
        addTestament(fixture, fixture.otherId, fixture.patronId, 40, 41);
        String before = compose(fixture, 100L, 100);

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
                fixture.patronId, fixture.marketId, 100L, 100,
                "House Current");

        assertEquals(before, after);
        assertTrue(after.startsWith("[CHRON:TESTAMENT_DEPOSED"));
    }

    @Test
    void everySupportedRoleUsesItsOwnPool() {
        assertPool(PatronChronicleReferenceType.CHAIN_ACTOR,
                (fixture) -> addChain(fixture, fixture.patronId,
                        fixture.otherId, ChainState.RESOLVED, 99, 100));
        assertPool(PatronChronicleReferenceType.CHAIN_TARGET,
                (fixture) -> addChain(fixture, fixture.otherId,
                        fixture.patronId, ChainState.FAILED, 99, 100));
        assertPool(PatronChronicleReferenceType.THRONE_CLAIMANT,
                (fixture) -> addThrone(fixture, fixture.patronId,
                        fixture.otherId, 99, 100));
        assertPool(PatronChronicleReferenceType.THRONE_DISPLACED,
                (fixture) -> addThrone(fixture, fixture.otherId,
                        fixture.patronId, 99, 100));
        assertPool(PatronChronicleReferenceType.TESTAMENT_CLAIMANT,
                (fixture) -> addTestament(fixture, fixture.patronId,
                        fixture.otherId, 99, 100));
        assertPool(PatronChronicleReferenceType.TESTAMENT_DEPOSED,
                (fixture) -> addTestament(fixture, fixture.otherId,
                        fixture.patronId, 99, 100));
    }

    private static void assertPool(PatronChronicleReferenceType expected,
                                   ChronicleAdder adder) {
        Fixture fixture = fixture();
        adder.add(fixture);
        String line = compose(fixture, 100L, 100);
        assertTrue(line.startsWith("[CHRON:" + expected.name()));
        assertTrue(line.contains("/1/1 day ago"));
    }

    private static String compose(Fixture fixture, long contractId,
                                  int offerDay) {
        return PatronBriefingContextComposer.compose(fixture.state,
                fixture.patronId, fixture.marketId, contractId, offerDay,
                "House Current");
    }

    private static Map<PatronChronicleReferenceType, String[]>
            chroniclePools() {
        Map<PatronChronicleReferenceType, String[]> pools =
                new EnumMap<>(PatronChronicleReferenceType.class);
        for (PatronChronicleReferenceType type
                : PatronChronicleReferenceType.values()) {
            pools.put(type, new String[] {
                    "[CHRON:" + type.name() + "] {patron}/{otherHouse}/"
                            + "{chronicleOutcome}/{daysAgo}/{age}",
                    "[CHRON:" + type.name() + ":ALT] {patron}/{otherHouse}/"
                            + "{chronicleOutcome}/{daysAgo}/{age}"
            });
        }
        return pools;
    }

    private static long addChain(Fixture fixture, long actor, long target,
                                 ChainState outcome, int happened,
                                 int learned) {
        return fixture.state.addChronicleChainOutcome(100L, outcome,
                ChronicleBand.INTIMATE, actor, target, fixture.marketId, -1,
                happened, learned);
    }

    private static long addThrone(Fixture fixture, long actor, long target,
                                  int happened, int learned) {
        return fixture.state.addChronicleThroneClaimApplied(200L,
                ChronicleBand.EPIC, actor, target, fixture.sourceFactionId,
                fixture.resultFactionId, fixture.marketId, happened, learned);
    }

    private static long addTestament(Fixture fixture, long actor, long target,
                                     int happened, int learned) {
        return fixture.state.addChronicleKingmakerTestament(300L, 400L,
                actor, target, fixture.sourceFactionId,
                fixture.resultFactionId, fixture.marketId, happened, learned);
    }

    private static void record(Fixture fixture, long patronId,
                               PatronEngagementOutcome outcome, int day) {
        ContractState state = outcome == PatronEngagementOutcome.COMPLETED
                ? ContractState.COMPLETED
                : outcome == PatronEngagementOutcome.WITHDREW
                    ? ContractState.ABANDONED : ContractState.FAILED;
        long contractId = fixture.state.addContract(patronId, -1L, -1L,
                ContractType.STRIKE, state, day, -1, -1, (byte) 1, -1,
                fixture.marketId, -1, 1_000, 0,
                (byte) 25, (byte) 25, (byte) 100);
        PatronEngagementMemory.record(fixture.state, contractId, outcome, day);
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
        return new Fixture(state, marketId, sourceFaction, resultFaction,
                patron, other);
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

        Fixture(CampaignState state, int marketId, int sourceFactionId,
                int resultFactionId, long patronId, long otherId) {
            this.state = state;
            this.marketId = marketId;
            this.sourceFactionId = sourceFactionId;
            this.resultFactionId = resultFactionId;
            this.patronId = patronId;
            this.otherId = otherId;
        }
    }
}
