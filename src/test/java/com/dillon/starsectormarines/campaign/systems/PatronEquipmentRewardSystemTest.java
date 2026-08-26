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
import com.dillon.starsectormarines.marine.EquipmentTemplateCard;
import com.dillon.starsectormarines.marine.FactionEquipmentCatalog;
import com.dillon.starsectormarines.marine.FactionEquipmentSource;
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
        assertTrue(access.granted.stream().allMatch(templateId ->
                FactionEquipmentCatalog.resolve("hegemony").offers(
                        templateId, FactionEquipmentSource.PATRON)));
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
        Set<String> entirePool = new HashSet<>(FactionEquipmentCatalog.offers(
                "tritachyon", FactionEquipmentSource.PATRON).stream()
                .map(offer -> offer.templateId()).toList());
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
        assertTrue(FactionEquipmentCatalog.resolve("independent").offers(
                access.granted.get(0), FactionEquipmentSource.PATRON));
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
    void rewardSeedIsStableAndSensitiveToLedgerIdentity() {
        long seed = PatronEquipmentRewardSystem.rewardSeed(2L, 4L, 8L);
        assertEquals(seed, PatronEquipmentRewardSystem.rewardSeed(2L, 4L, 8L));
        assertTrue(seed != PatronEquipmentRewardSystem.rewardSeed(3L, 4L, 8L));
        assertTrue(seed != PatronEquipmentRewardSystem.rewardSeed(2L, 5L, 8L));
    }

    private static Fixture fixture(String factionId) {
        CampaignState state = new CampaignState();
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
        boolean cargoAvailable = true;
        boolean acceptGrants = true;
        boolean failAnnouncements;

        @Override
        public Set<String> unavailableTemplateIds() {
            return cargoAvailable ? Set.copyOf(unavailable) : null;
        }

        @Override
        public boolean grant(String templateId) {
            if (!acceptGrants) return false;
            granted.add(templateId);
            unavailable.add(templateId);
            return true;
        }

        @Override
        public void announce(String patronName, EquipmentTemplateCard template) {
            if (failAnnouncements) throw new IllegalStateException("test presentation failure");
            patrons.add(patronName);
        }
    }

    private record Fixture(CampaignState state, long patron, int market) {}
}
