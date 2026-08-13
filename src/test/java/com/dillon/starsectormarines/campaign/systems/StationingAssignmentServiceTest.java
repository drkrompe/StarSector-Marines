package com.dillon.starsectormarines.campaign.systems;

import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.ChainArchetype;
import com.dillon.starsectormarines.campaign.CivilWarBand;
import com.dillon.starsectormarines.campaign.ContractEligibility;
import com.dillon.starsectormarines.campaign.ContractState;
import com.dillon.starsectormarines.campaign.ContractType;
import com.dillon.starsectormarines.campaign.HouseFlavor;
import com.dillon.starsectormarines.campaign.HouseRank;
import com.dillon.starsectormarines.campaign.HouseStatus;
import com.dillon.starsectormarines.campaign.PatronArchetype;
import com.dillon.starsectormarines.campaign.StationingIncidentType;
import com.dillon.starsectormarines.campaign.GarrisonDefenseTriggerType;
import com.dillon.starsectormarines.marine.MarineCaptain;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.MarineSoldierStatus;
import com.dillon.starsectormarines.marine.MarineSquad;
import com.dillon.starsectormarines.marine.Rank;
import com.dillon.starsectormarines.marine.Status;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StationingAssignmentServiceTest {

    @Test
    void acceptsAndFreezesPersonnelAndTerms() {
        Fixture fixture = fixture(ContractType.GARRISON, HouseRank.TIER_2, 150);

        boolean accepted = StationingAssignmentService.acceptLegacy(
                fixture.state, fixture.contractId,
                fixture.captain, 100, 5, 40, fixture.store);

        int row = fixture.state.contractIndex(fixture.contractId);
        assertTrue(accepted);
        assertEquals(50, fixture.store.available);
        assertEquals(Status.GARRISONED, fixture.captain.status());
        assertEquals(ContractState.ACTIVE,
                ContractState.fromByte(fixture.state.contractState[row]));
        assertEquals(40, fixture.state.contractAcceptedTick[row]);
        assertEquals(130, fixture.state.contractExpiresTick[row]);
        assertEquals(-1, fixture.state.contractOfferExpiresTick[row]);
        assertEquals(3_300, fixture.state.contractRetainerPerMonth[row]);
        assertEquals(100, fixture.state.contractMarinesCommitted[row]);
        assertEquals(40, fixture.state.contractLastRetainerTick[row]);
        assertEquals(-1, fixture.state.contractNextIncidentTick[row]);
        assertEquals(0, fixture.state.contractIncidentPending[row]);
        assertEquals(StationingIncidentType.NONE,
                StationingIncidentType.fromByte(fixture.state.contractIncidentType[row]));
        assertEquals(0L, fixture.state.contractDefenseEventKey[row]);
        assertEquals(-1, fixture.state.contractDefenseTriggeredTick[row]);
        assertEquals(GarrisonDefenseTriggerType.NONE,
                GarrisonDefenseTriggerType.fromByte(
                        fixture.state.contractDefenseTriggerType[row]));
        assertEquals(25, fixture.state.contractSalvageBaseline[row] & 0xFF);
        assertEquals(fixture.captain.id(), fixture.state.captainRegistry.get(
                fixture.state.contractCaptainId[row]));
    }

    @Test
    void duplicateAndInvalidAcceptanceDoNotRemoveMarines() {
        Fixture fixture = fixture(ContractType.CADRE, HouseRank.TIER_2, 50);
        assertFalse(StationingAssignmentService.acceptLegacy(
                fixture.state, fixture.contractId,
                fixture.captain, 60, 1, 10, fixture.store));
        assertEquals(50, fixture.store.available);

        assertTrue(StationingAssignmentService.acceptLegacy(
                fixture.state, fixture.contractId,
                fixture.captain, 40, 1, 10, fixture.store));
        int row = fixture.state.contractIndex(fixture.contractId);
        assertTrue(fixture.state.contractNextIncidentTick[row] > 10);
        assertFalse(StationingAssignmentService.acceptLegacy(
                fixture.state, fixture.contractId,
                fixture.captain, 10, 1, 10, fixture.store));
        assertEquals(10, fixture.store.available);
    }

    @Test
    void namedAcceptanceBindsFireteamsAndDerivesLivingStrengthWithoutCargo() {
        Fixture fixture = fixture(ContractType.GARRISON, HouseRank.TIER_2, 0);
        MarineRoster roster = new MarineRoster();
        roster.add(fixture.captain);
        roster.ensureActiveSoldiers(7);
        MarineSquad first = roster.squads().get(0);
        MarineSquad second = roster.squads().get(1);
        roster.applySoldierOutcome(Map.of(
                roster.squadMembers(first).get(0).id(), MarineSoldierStatus.WIA,
                roster.squadMembers(first).get(1).id(), MarineSoldierStatus.KIA),
                0, 10f, 7f);

        assertTrue(StationingAssignmentService.acceptNamed(
                fixture.state, fixture.contractId, roster, fixture.captain,
                List.of(first.id(), second.id()), 2, 40));

        int row = fixture.state.contractIndex(fixture.contractId);
        assertEquals(List.of(first, second), roster.squadsStationedOn(fixture.contractId));
        assertEquals(6, roster.stationedLivingCount(fixture.contractId));
        assertEquals(6, fixture.state.contractMarinesCommitted[row]);
        assertEquals(100, fixture.state.contractExpiresTick[row]);
        assertEquals(198, fixture.state.contractRetainerPerMonth[row]);
        assertEquals(Status.GARRISONED, fixture.captain.status());
        assertEquals(0, fixture.store.available);
    }

    @Test
    void invalidNamedSelectionLeavesContractAndRosterUntouched() {
        Fixture fixture = fixture(ContractType.CADRE, HouseRank.TIER_2, 0);
        MarineRoster roster = new MarineRoster();
        roster.add(fixture.captain);
        roster.ensureActiveSoldiers(6);
        MarineSquad squad = roster.squads().get(0);

        assertFalse(StationingAssignmentService.acceptNamed(
                fixture.state, fixture.contractId, roster, fixture.captain,
                List.of(squad.id(), squad.id()), 1, 20));

        int row = fixture.state.contractIndex(fixture.contractId);
        assertFalse(squad.stationed());
        assertEquals(ContractState.OFFERED,
                ContractState.fromByte(fixture.state.contractState[row]));
        assertEquals(Status.ACTIVE, fixture.captain.status());
        assertEquals(0, fixture.state.contractMarinesCommitted[row]);
    }

    @Test
    void civilWarStationingChoiceWithdrawsOpposingOffer() {
        CampaignState state = new CampaignState();
        state.playerMrbRep = ContractEligibility.TIER_3_MRB_REQUIRED;
        int market = state.marketRegistry.intern("market");
        long claimant = state.addHouse(market, 0, HouseFlavor.FEUDAL,
                HouseRank.TIER_3, HouseStatus.ACTIVE,
                PatronArchetype.NEWCOMER, "Claimant");
        long incumbent = state.addHouse(market, 0, HouseFlavor.FEUDAL,
                HouseRank.TIER_3, HouseStatus.ACTIVE,
                PatronArchetype.NEWCOMER, "Incumbent");
        long chainId = state.addAutonomousChain(claimant, incumbent, market,
                -1, HouseRank.TIER_3.toByte(), ChainArchetype.CIVIL_WAR,
                (short) 180, (byte) 128, 1);
        int chainRow = state.chainIndex(chainId);
        state.chainProgress[chainRow] = 70;
        long claimantOffer = civilWarOffer(state, claimant, incumbent,
                chainId, -1L, ContractType.CADRE, market);
        long incumbentOffer = civilWarOffer(state, incumbent, claimant,
                -1L, chainId, ContractType.GARRISON, market);
        MarineCaptain captain = new MarineCaptain(
                "Captain", null, Rank.SERGEANT, 0f);
        TestMarineStore store = new TestMarineStore(100);

        assertTrue(StationingAssignmentService.acceptLegacy(state, claimantOffer,
                captain, 40, 1, 40, store));

        assertEquals(ContractState.ACTIVE, ContractState.fromByte(
                state.contractState[state.contractIndex(claimantOffer)]));
        assertEquals(ContractState.EXPIRED, ContractState.fromByte(
                state.contractState[state.contractIndex(incumbentOffer)]));
    }

    private static long civilWarOffer(CampaignState state, long patron,
                                      long target, long parent, long opposed,
                                      ContractType type, int market) {
        long id = state.addContract(patron, target, parent, type,
                ContractState.OFFERED, 20, -1, 27, (byte) 0, -1,
                market, -1, 0, 0, (byte) 0, (byte) 0, (byte) 100);
        int row = state.contractIndex(id);
        state.contractOpposedChainId[row] = opposed;
        state.contractCivilWarBand[row] = CivilWarBand.MOBILIZATION.toByte();
        return id;
    }

    private static Fixture fixture(ContractType type, HouseRank rank, int marines) {
        CampaignState state = new CampaignState();
        state.playerMrbRep = ContractEligibility.TIER_3_MRB_REQUIRED;
        long patronId = state.addHouse(0, 0, HouseFlavor.CORPORATE, rank,
                HouseStatus.ACTIVE, PatronArchetype.ESTABLISHED, "Patron");
        long contractId = state.addContract(patronId, -1L, -1L, type,
                ContractState.OFFERED, 0, -1, 20, (byte) 0, -1, 0, -1,
                0, 0, (byte) 0, (byte) 0, (byte) 100);
        MarineCaptain captain = new MarineCaptain("Captain", null, Rank.SERGEANT, 0f);
        return new Fixture(state, contractId, captain, new TestMarineStore(marines));
    }

    private static final class Fixture {
        final CampaignState state;
        final long contractId;
        final MarineCaptain captain;
        final TestMarineStore store;

        Fixture(CampaignState state, long contractId, MarineCaptain captain,
                TestMarineStore store) {
            this.state = state;
            this.contractId = contractId;
            this.captain = captain;
            this.store = store;
        }
    }

    private static final class TestMarineStore implements StationingAssignmentService.MarineStore {
        int available;

        TestMarineStore(int available) {
            this.available = available;
        }

        @Override
        public int available() {
            return available;
        }

        @Override
        public boolean remove(int count) {
            if (count > available) return false;
            available -= count;
            return true;
        }
    }
}
