package com.dillon.starsectormarines.campaign.systems;

import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.ContractState;
import com.dillon.starsectormarines.campaign.ContractType;
import com.dillon.starsectormarines.campaign.HouseFlavor;
import com.dillon.starsectormarines.campaign.HouseRank;
import com.dillon.starsectormarines.campaign.HouseStatus;
import com.dillon.starsectormarines.campaign.PatronArchetype;
import com.dillon.starsectormarines.campaign.PatronEngagementMemory;
import com.dillon.starsectormarines.campaign.PatronEngagementOutcome;
import com.dillon.starsectormarines.marine.LoadoutCard;
import com.dillon.starsectormarines.marine.LoadoutCards;
import com.dillon.starsectormarines.marine.MechPattern;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PatronEquipmentRewardSystemTest {

    @Test
    void completedContractsGrantDistinctFactionCardsExactlyOnce() {
        Fixture fixture = fixture("hegemony");
        record(fixture, ContractType.STRIKE, PatronEngagementOutcome.COMPLETED, 10);
        record(fixture, ContractType.GARRISON, PatronEngagementOutcome.COMPLETED, 40);
        FakeAccess access = new FakeAccess();
        PatronEquipmentRewardSystem system = new PatronEquipmentRewardSystem(access);

        system.tick(fixture.state, 40);

        assertEquals(2, access.granted.size());
        assertEquals(2, new HashSet<>(access.granted).size());
        assertTrue(access.granted.stream().allMatch(cardId -> LoadoutCard.parse(cardId) != null));
        assertEquals(List.of("House Test", "House Test"), access.patrons);
        assertEquals(2, fixture.state.patronEquipmentRewardCursor);

        system.tick(fixture.state, 41);
        assertEquals(2, access.granted.size());
    }

    @Test
    void nonCompletionsAndSystemGeneratedExtractionDoNotReward() {
        Fixture fixture = fixture("independent");
        record(fixture, ContractType.STRIKE, PatronEngagementOutcome.FAILED, 10);
        record(fixture, ContractType.ESCORT, PatronEngagementOutcome.WITHDREW, 20);
        record(fixture, ContractType.EXTRACTION, PatronEngagementOutcome.COMPLETED, 30);
        FakeAccess access = new FakeAccess();

        new PatronEquipmentRewardSystem(access).tick(fixture.state, 30);

        assertTrue(access.granted.isEmpty());
        assertEquals(3, fixture.state.patronEquipmentRewardCursor);
    }

    @Test
    void ownedOrCargoHeldCardsAreNeverDuplicated() {
        Fixture fixture = fixture("tritachyon");
        record(fixture, ContractType.STRIKE, PatronEngagementOutcome.COMPLETED, 10);
        Set<String> entirePool = new HashSet<>(LoadoutCards.all().stream()
                .map(LoadoutCard::id).toList());
        FakeAccess access = new FakeAccess();
        access.unavailable.addAll(entirePool);

        new PatronEquipmentRewardSystem(access).tick(fixture.state, 10);

        assertTrue(access.granted.isEmpty());
        assertEquals(1, fixture.state.patronEquipmentRewardCursor);
    }

    @Test
    void unknownSubmodFactionUsesTheIndependentFallbackPool() {
        Fixture fixture = fixture("example_oc_without_a_pool");
        record(fixture, ContractType.STRIKE, PatronEngagementOutcome.COMPLETED, 10);
        FakeAccess access = new FakeAccess();

        new PatronEquipmentRewardSystem(access).tick(fixture.state, 10);

        assertEquals(1, access.granted.size());
        assertTrue(LoadoutCard.parse(access.granted.get(0)) != null);
    }

    @Test
    void unavailableCargoRetriesWithoutConsumingTheLedgerRow() {
        Fixture fixture = fixture("hegemony");
        record(fixture, ContractType.STRIKE, PatronEngagementOutcome.COMPLETED, 10);
        FakeAccess access = new FakeAccess();
        access.cargoAvailable = false;
        PatronEquipmentRewardSystem system = new PatronEquipmentRewardSystem(access);

        system.tick(fixture.state, 10);
        assertEquals(0, fixture.state.patronEquipmentRewardCursor);

        access.cargoAvailable = true;
        system.tick(fixture.state, 11);
        assertEquals(1, fixture.state.patronEquipmentRewardCursor);
        assertEquals(1, access.granted.size());
    }

    @Test
    void failedCargoMutationRetriesTheSameDeterministicReward() {
        Fixture fixture = fixture("hegemony");
        record(fixture, ContractType.STRIKE, PatronEngagementOutcome.COMPLETED, 10);
        FakeAccess access = new FakeAccess();
        access.acceptGrants = false;
        PatronEquipmentRewardSystem system = new PatronEquipmentRewardSystem(access);

        system.tick(fixture.state, 10);
        assertEquals(0, fixture.state.patronEquipmentRewardCursor);

        access.acceptGrants = true;
        system.tick(fixture.state, 11);
        assertEquals(1, fixture.state.patronEquipmentRewardCursor);
        assertEquals(1, access.granted.size());
    }

    @Test
    void presentationFailureCannotReplayAnAlreadyGrantedCard() {
        Fixture fixture = fixture("hegemony");
        record(fixture, ContractType.STRIKE, PatronEngagementOutcome.COMPLETED, 10);
        FakeAccess access = new FakeAccess();
        access.failAnnouncements = true;
        PatronEquipmentRewardSystem system = new PatronEquipmentRewardSystem(access);

        system.tick(fixture.state, 10);
        system.tick(fixture.state, 11);

        assertEquals(1, access.granted.size());
        assertEquals(1, fixture.state.patronEquipmentRewardCursor);
    }

    @Test
    void someCompletedContractsPayMechPatternsExactlyOnce() {
        Fixture fixture = fixture("hegemony");
        for (int day = 0; day < 16; day++) {
            record(fixture, ContractType.STRIKE, PatronEngagementOutcome.COMPLETED, day);
        }
        FakeAccess access = new FakeAccess();
        access.patternsEnabled = true;
        access.fieldsMechs = true;
        PatronEquipmentRewardSystem system = new PatronEquipmentRewardSystem(access);

        system.tick(fixture.state, 16);
        int paid = access.granted.size() + access.grantedPatterns.size();
        system.tick(fixture.state, 17);

        assertTrue(!access.grantedPatterns.isEmpty(), "some contract should pay a pattern");
        assertTrue(!access.granted.isEmpty(), "contracts still pay loadout cards");
        assertEquals(16, fixture.state.patronEquipmentRewardCursor);
        assertEquals(paid, access.granted.size() + access.grantedPatterns.size(),
                "a consumed ledger row never pays twice");
        assertEquals(access.grantedPatterns.size(), new HashSet<>(access.grantedPatterns).size());
        assertTrue(access.grantedPatterns.stream().allMatch(id -> MechPattern.parse(id) != null));
    }

    @Test
    void aCompanyWithoutMechsIsPaidOnlyWholeMachinePatterns() {
        Fixture fixture = fixture("hegemony");
        for (int day = 0; day < 24; day++) {
            record(fixture, ContractType.STRIKE, PatronEngagementOutcome.COMPLETED, day);
        }
        FakeAccess access = new FakeAccess();
        access.patternsEnabled = true;

        new PatronEquipmentRewardSystem(access).tick(fixture.state, 24);

        assertTrue(access.grantedPatterns.stream().allMatch(
                id -> MechPattern.parse(id).kind() != MechPattern.Kind.WEAPON));
        assertEquals(24, fixture.state.patronEquipmentRewardCursor);
    }

    @Test
    void rewardSeedIsStableAndSensitiveToLedgerIdentity() {
        long seed = PatronEquipmentRewardSystem.rewardSeed(2L, 4L, 8L);
        assertEquals(seed, PatronEquipmentRewardSystem.rewardSeed(2L, 4L, 8L));
        assertTrue(seed != PatronEquipmentRewardSystem.rewardSeed(3L, 4L, 8L));
        assertTrue(seed != PatronEquipmentRewardSystem.rewardSeed(2L, 5L, 8L));
    }

    private static Fixture fixture(String factionId) {
        CampaignState state = new CampaignState();
        state.playerMrbRep = 20;
        int market = state.marketRegistry.intern("test_market");
        int faction = state.factionRegistry.intern(factionId);
        long patron = state.addHouse(market, faction, HouseFlavor.CORPORATE,
                HouseRank.TIER_2, HouseStatus.ACTIVE,
                PatronArchetype.ESTABLISHED, "House Test");
        return new Fixture(state, patron, market);
    }

    private static void record(Fixture fixture, ContractType type,
                               PatronEngagementOutcome outcome, int day) {
        ContractState terminal = switch (outcome) {
            case COMPLETED -> ContractState.COMPLETED;
            case FAILED -> ContractState.FAILED;
            case WITHDREW -> ContractState.ABANDONED;
            case EMPLOYER_BREACHED -> ContractState.DEFAULTED;
        };
        long contract = fixture.state.addContract(fixture.patron, -1L, -1L,
                type, terminal, day, -1, -1, (byte) 1, -1,
                fixture.market, -1, 1_000, 0,
                (byte) 25, (byte) 25, (byte) 100);
        PatronEngagementMemory.record(fixture.state, contract, outcome, day);
    }

    private static final class FakeAccess
            implements PatronEquipmentRewardSystem.RewardAccess {
        final Set<String> unavailable = new HashSet<>();
        final List<String> granted = new ArrayList<>();
        final List<String> patrons = new ArrayList<>();
        final Set<String> knownPatterns = new HashSet<>();
        final List<String> grantedPatterns = new ArrayList<>();
        boolean patternsEnabled;
        boolean fieldsMechs;
        boolean cargoAvailable = true;
        boolean acceptGrants = true;
        boolean failAnnouncements;

        @Override
        public Set<String> unavailableTemplateIds() {
            return cargoAvailable ? Set.copyOf(unavailable) : null;
        }

        @Override
        public int victories() {
            return 15;
        }

        @Override
        public boolean grant(String templateId) {
            if (!acceptGrants) return false;
            granted.add(templateId);
            unavailable.add(templateId);
            return true;
        }

        @Override
        public Set<String> unavailablePatternIds() {
            return patternsEnabled ? Set.copyOf(knownPatterns) : null;
        }

        @Override
        public boolean fieldsMechs() {
            return fieldsMechs;
        }

        @Override
        public boolean grantPattern(String patternId) {
            if (!acceptGrants) return false;
            grantedPatterns.add(patternId);
            knownPatterns.add(patternId);
            return true;
        }

        @Override
        public void announce(String patronName, LoadoutCard card) {
            if (failAnnouncements) throw new IllegalStateException("test presentation failure");
            patrons.add(patronName);
        }
    }

    private record Fixture(CampaignState state, long patron, int market) {}
}
