package com.dillon.starsectormarines.campaign.systems;

import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.ContractState;
import com.dillon.starsectormarines.campaign.ContractType;
import com.dillon.starsectormarines.campaign.HouseFlavor;
import com.dillon.starsectormarines.campaign.HouseRank;
import com.dillon.starsectormarines.campaign.HouseStatus;
import com.dillon.starsectormarines.campaign.PatronArchetype;
import com.dillon.starsectormarines.campaign.PatronEngagementOutcome;
import com.dillon.starsectormarines.marine.MarineCaptain;
import com.dillon.starsectormarines.marine.Rank;
import com.dillon.starsectormarines.marine.Status;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExtractionResolutionSystemTest {

    @Test
    void successfulExtractionReturnsPersonnelAndPenalizesEmployerOnce() {
        Fixture fixture = fixture(ContractState.COMPLETED);

        fixture.system.tick(fixture.state, 40);
        fixture.system.tick(fixture.state, 41);

        assertEquals(80, fixture.returnedMarines);
        assertEquals(Status.ACTIVE, fixture.captain.status());
        assertEquals(0, fixture.state.contractMarinesCommitted[0]);
        assertEquals(-1, fixture.state.contractCaptainId[0]);
        assertEquals(-10, fixture.state.repValue[0]);
        assertEquals(40, fixture.state.repLastContractTick[0]);
        assertEquals(1, fixture.state.patronEngagementCount);
        assertEquals(PatronEngagementOutcome.EMPLOYER_BREACHED,
                PatronEngagementOutcome.fromByte(
                        fixture.state.patronEngagementOutcome[0]));
    }

    @Test
    void failedExtractionLosesMarinesAndReturnsCaptainInjured() {
        Fixture fixture = fixture(ContractState.FAILED);

        fixture.system.tick(fixture.state, 40);

        assertEquals(0, fixture.returnedMarines);
        assertEquals(Status.INJURED, fixture.captain.status());
        assertEquals(85f, fixture.captain.injuredUntilDay());
        assertEquals(0, fixture.state.contractMarinesCommitted[0]);
        assertEquals(-10, fixture.state.repValue[0]);
    }

    @Test
    void failedMarineDeliveryRetriesWithoutPartialResolution() {
        Fixture fixture = fixture(ContractState.COMPLETED);
        fixture.deliverySucceeds = false;

        fixture.system.tick(fixture.state, 40);

        assertEquals(Status.GARRISONED, fixture.captain.status());
        assertEquals(80, fixture.state.contractMarinesCommitted[0]);
        assertEquals(0, fixture.state.repCount);
        assertEquals(0, fixture.state.patronEngagementCount);

        fixture.deliverySucceeds = true;
        fixture.system.tick(fixture.state, 41);
        assertEquals(80, fixture.returnedMarines);
        assertEquals(-10, fixture.state.repValue[0]);
        assertEquals(1, fixture.state.patronEngagementCount);
    }

    @Test
    void successfulNamedExtractionReleasesTeamsWithoutCargoOnce() {
        Fixture fixture = fixture(ContractState.COMPLETED);
        fixture.namedAssignment = true;

        fixture.system.tick(fixture.state, 40);
        fixture.system.tick(fixture.state, 41);

        assertEquals(1, fixture.namedSettlements);
        assertTrue(fixture.lastNamedSuccess);
        assertEquals(0, fixture.returnedMarines);
        assertEquals(Status.ACTIVE, fixture.captain.status());
        assertEquals(0, fixture.state.contractMarinesCommitted[0]);
        assertEquals(-1, fixture.state.contractCaptainId[0]);
    }

    @Test
    void failedNamedExtractionSettlesTeamsAsLostWithoutCargo() {
        Fixture fixture = fixture(ContractState.FAILED);
        fixture.namedAssignment = true;

        fixture.system.tick(fixture.state, 40);

        assertEquals(1, fixture.namedSettlements);
        assertFalse(fixture.lastNamedSuccess);
        assertEquals(0, fixture.returnedMarines);
        assertEquals(Status.INJURED, fixture.captain.status());
        assertEquals(0, fixture.state.contractMarinesCommitted[0]);
        assertEquals(-1, fixture.state.contractCaptainId[0]);
    }

    @Test
    void failedNamedSettlementPreservesAuthorityForRetry() {
        Fixture fixture = fixture(ContractState.COMPLETED);
        fixture.namedAssignment = true;
        fixture.namedSettlementSucceeds = false;

        fixture.system.tick(fixture.state, 40);

        assertEquals(Status.GARRISONED, fixture.captain.status());
        assertEquals(80, fixture.state.contractMarinesCommitted[0]);
        assertEquals(0, fixture.state.repCount);
    }

    private static Fixture fixture(ContractState extractionState) {
        CampaignState state = new CampaignState();
        int marketId = state.marketRegistry.intern("jangala");
        long patronId = state.addHouse(marketId, 1, HouseFlavor.CORPORATE,
                HouseRank.TIER_2, HouseStatus.ACTIVE,
                PatronArchetype.ESTABLISHED, "House Cavor");
        MarineCaptain captain = new MarineCaptain("Stranded", null, Rank.SERGEANT, 0f);
        captain.setStatus(Status.GARRISONED);
        int captainSlot = state.captainRegistry.intern(captain.id());
        long parentId = state.addContract(patronId, -1L, -1L, ContractType.GARRISON,
                ContractState.DEFAULTED, 1, 100, -1, (byte) 0, captainSlot, marketId, -1,
                0, 2_000, (byte) 25, (byte) 25, (byte) 100);
        state.contractMarinesCommitted[0] = 80;
        long extractionId = state.addContract(patronId, -1L, -1L, ContractType.EXTRACTION,
                extractionState, 2, -1, -1, (byte) 1, -1, marketId, -1,
                2_000, 0, (byte) 25, (byte) 25, (byte) 100);
        state.contractSourceContractId[state.contractIndex(extractionId)] = parentId;
        return new Fixture(state, captain);
    }

    private static final class Fixture implements ExtractionResolutionSystem.PersonnelStore {
        final CampaignState state;
        final MarineCaptain captain;
        final ExtractionResolutionSystem system;
        int returnedMarines;
        boolean deliverySucceeds = true;
        boolean namedAssignment;
        boolean namedSettlementSucceeds = true;
        int namedSettlements;
        boolean lastNamedSuccess;

        Fixture(CampaignState state, MarineCaptain captain) {
            this.state = state;
            this.captain = captain;
            this.system = new ExtractionResolutionSystem(this);
        }

        @Override
        public MarineCaptain captain(String id) {
            return captain.id().equals(id) ? captain : null;
        }

        @Override
        public boolean addMarines(int count) {
            if (!deliverySucceeds) return false;
            returnedMarines += count;
            return true;
        }

        @Override
        public boolean hasNamedAssignment(long contractId) {
            return namedAssignment;
        }

        @Override
        public boolean settleNamedAssignment(long contractId, boolean success) {
            if (!namedSettlementSucceeds) return false;
            namedAssignment = false;
            namedSettlements++;
            lastNamedSuccess = success;
            return true;
        }
    }
}
