package com.dillon.starsectormarines.campaign;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PatronRememberedTargetComposerTest {

    @BeforeAll
    static void injectVoice() {
        Map<PatronEngagementOutcome, String[]> direct =
                new EnumMap<>(PatronEngagementOutcome.class);
        Map<PatronEngagementOutcome, String[]> local =
                new EnumMap<>(PatronEngagementOutcome.class);
        for (PatronEngagementOutcome outcome
                : PatronEngagementOutcome.values()) {
            direct.put(outcome, new String[] {
                    "[DIRECT:" + outcome.name() + "] {target}"
            });
            local.put(outcome, new String[] {
                    "[LOCAL:" + outcome.name() + "] {otherTarget}"
            });
        }
        Map<PatronRelationshipPattern, String[]> continuity =
                new EnumMap<>(PatronRelationshipPattern.class);
        for (PatronRelationshipPattern pattern
                : PatronRelationshipPattern.values()) {
            continuity.put(pattern, new String[] {
                    "[CONT:" + pattern.name() + "] {previousTarget}/"
                            + "{latestTarget}"
            });
        }
        PatronMemoryVoice.loadForTest(direct);
        PatronMemoryVoice.loadContinuityForTest(continuity);
        PatronMemoryVoice.loadLocalEchoForTest(local);
    }

    @AfterAll
    static void resetVoice() {
        PatronMemoryVoice.loadForTest(null);
        PatronMemoryVoice.loadContinuityForTest(null);
        PatronMemoryVoice.loadLocalEchoForTest(null);
    }

    @Test
    void directMemoryUsesPlayerFacingFrozenTargetName() {
        Fixture fixture = fixture();
        Target target = target(fixture, "kazeron", "Kazeron");
        record(fixture, fixture.patronId, target.houseId,
                ContractType.STRIKE, PatronEngagementOutcome.COMPLETED, 20);

        String first = compose(fixture, 99L);
        String replay = compose(fixture, 99L);

        assertEquals(first, replay);
        assertEquals("[DIRECT:COMPLETED] Kazeron", first);
        assertFalse(first.contains("kazeron"));
    }

    @Test
    void continuityNamesBothFrozenTargetsInSequence() {
        Fixture fixture = fixture();
        Target previousTarget = target(fixture, "kazeron", "Kazeron");
        Target latestTarget = target(fixture, "chicomoztoc", "Chicomoztoc");
        record(fixture, fixture.patronId, previousTarget.houseId,
                ContractType.STRIKE, PatronEngagementOutcome.COMPLETED, 10);
        record(fixture, fixture.patronId, latestTarget.houseId,
                ContractType.ESCORT, PatronEngagementOutcome.FAILED, 20);

        String line = compose(fixture, 99L);

        assertEquals("[CONT:PLAYER_SETBACK] Kazeron/Chicomoztoc", line);
    }

    @Test
    void localEchoNamesOtherPatronsFrozenTarget() {
        Fixture fixture = fixture();
        Target target = target(fixture, "sindria", "Sindria");
        record(fixture, fixture.otherPatronId, target.houseId,
                ContractType.ESCORT, PatronEngagementOutcome.WITHDREW, 20);

        String line = compose(fixture, 99L);

        assertEquals("[LOCAL:WITHDREW] Sindria", line);
    }

    @Test
    void unknownOrFailingResolverUsesNeutralLocation() {
        Fixture fixture = fixture();
        Target target = target(fixture, "kazeron", "Kazeron");
        record(fixture, fixture.patronId, target.houseId,
                ContractType.STRIKE, PatronEngagementOutcome.FAILED, 20);

        String missing = PatronBriefingContextComposer.compose(fixture.state,
                fixture.patronId, fixture.originMarketId, 99L, 100,
                "House Current", marketId -> null);
        String failure = PatronBriefingContextComposer.compose(fixture.state,
                fixture.patronId, fixture.originMarketId, 99L, 100,
                "House Current", marketId -> {
                    throw new IllegalStateException("economy unavailable");
                });

        assertEquals("[DIRECT:FAILED] the last operation site", missing);
        assertEquals(missing, failure);
    }

    @Test
    void compactionAndSaveLoadKeepTargetReferenceStable() throws Exception {
        Fixture fixture = fixture();
        Target target = target(fixture, "kazeron", "Kazeron");
        record(fixture, fixture.patronId, target.houseId,
                ContractType.STRIKE, PatronEngagementOutcome.COMPLETED, 20);
        String before = compose(fixture, 99L);
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
                fixture.patronId, fixture.originMarketId, 99L, 100,
                "House Current", fixture.names::get);

        assertEquals(before, after);
        assertTrue(after.endsWith("Kazeron"));
    }

    private static String compose(Fixture fixture, long contractId) {
        return PatronBriefingContextComposer.compose(fixture.state,
                fixture.patronId, fixture.originMarketId, contractId, 100,
                "House Current", fixture.names::get);
    }

    private static Target target(Fixture fixture, String marketId,
                                 String displayName) {
        int marketSlot = fixture.state.marketRegistry.intern(marketId);
        fixture.names.put(marketSlot, displayName);
        long houseId = fixture.state.addHouse(marketSlot, 1,
                HouseFlavor.UNDERWORLD, HouseRank.TIER_2,
                HouseStatus.ACTIVE, PatronArchetype.SUSPICIOUS,
                "House at " + displayName);
        return new Target(houseId);
    }

    private static void record(Fixture fixture, long patronId,
                               long targetHouseId, ContractType type,
                               PatronEngagementOutcome outcome, int day) {
        ContractState terminal;
        switch (outcome) {
            case COMPLETED: terminal = ContractState.COMPLETED; break;
            case FAILED: terminal = ContractState.FAILED; break;
            case WITHDREW: terminal = ContractState.ABANDONED; break;
            case EMPLOYER_BREACHED: terminal = ContractState.DEFAULTED; break;
            default: throw new IllegalArgumentException(outcome.name());
        }
        long contractId = fixture.state.addContract(patronId, targetHouseId,
                -1L, type, terminal, day, -1, -1, (byte) 1, -1,
                fixture.originMarketId, -1, 1_000, 0,
                (byte) 25, (byte) 25, (byte) 100);
        PatronEngagementMemory.record(fixture.state, contractId, outcome, day);
    }

    private static Fixture fixture() {
        CampaignState state = new CampaignState();
        int origin = state.marketRegistry.intern("jangala");
        long current = state.addHouse(origin, 1, HouseFlavor.CORPORATE,
                HouseRank.TIER_2, HouseStatus.ACTIVE,
                PatronArchetype.ESTABLISHED, "House Current");
        long other = state.addHouse(origin, 1, HouseFlavor.FEUDAL,
                HouseRank.TIER_2, HouseStatus.ACTIVE,
                PatronArchetype.FALLEN_NOBLE, "House Other");
        return new Fixture(state, origin, current, other);
    }

    private static final class Target {
        final long houseId;

        Target(long houseId) {
            this.houseId = houseId;
        }
    }

    private static final class Fixture {
        final CampaignState state;
        final int originMarketId;
        final long patronId;
        final long otherPatronId;
        final Map<Integer, String> names = new HashMap<>();

        Fixture(CampaignState state, int originMarketId, long patronId,
                long otherPatronId) {
            this.state = state;
            this.originMarketId = originMarketId;
            this.patronId = patronId;
            this.otherPatronId = otherPatronId;
        }
    }
}
