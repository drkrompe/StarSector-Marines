package com.dillon.starsectormarines.campaign.systems;

import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.ContractState;
import com.dillon.starsectormarines.campaign.ContractType;
import com.dillon.starsectormarines.marine.MarineCaptain;
import com.dillon.starsectormarines.marine.Rank;
import com.dillon.starsectormarines.marine.Status;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StationingWithdrawalServiceTest {

    @Test
    void withdrawalReturnsPersonnelAndAppliesPenaltiesOnce() {
        Fixture fixture = fixture(ContractState.ACTIVE);

        assertTrue(StationingWithdrawalService.withdraw(
                fixture.state, fixture.contractId, 40, fixture.store));
        assertFalse(StationingWithdrawalService.withdraw(
                fixture.state, fixture.contractId, 41, fixture.store));

        assertEquals(80, fixture.store.returnedMarines);
        assertEquals(Status.ACTIVE, fixture.captain.status());
        assertEquals(ContractState.ABANDONED,
                ContractState.fromByte(fixture.state.contractState[0]));
        assertEquals(0, fixture.state.contractMarinesCommitted[0]);
        assertEquals(-1, fixture.state.contractCaptainId[0]);
        assertEquals(-15, fixture.state.repValue[0]);
        assertEquals(1, fixture.state.repContractsFailed[0] & 0xFFFF);
        assertEquals(40, fixture.state.repLastContractTick[0]);
        assertEquals(-10, fixture.state.playerMrbRep);
    }

    @Test
    void failedDeliveryLeavesAssignmentAndReputationUntouched() {
        Fixture fixture = fixture(ContractState.ACTIVE);
        fixture.store.deliverySucceeds = false;

        assertFalse(StationingWithdrawalService.withdraw(
                fixture.state, fixture.contractId, 40, fixture.store));

        assertEquals(ContractState.ACTIVE,
                ContractState.fromByte(fixture.state.contractState[0]));
        assertEquals(80, fixture.state.contractMarinesCommitted[0]);
        assertEquals(Status.GARRISONED, fixture.captain.status());
        assertEquals(0, fixture.state.repCount);
        assertEquals(0, fixture.state.playerMrbRep);
    }

    @Test
    void namedWithdrawalReleasesTeamsWithoutCreatingCargo() {
        Fixture fixture = fixture(ContractState.ACTIVE);
        fixture.store.namedAssignment = true;

        assertTrue(StationingWithdrawalService.withdraw(
                fixture.state, fixture.contractId, 40, fixture.store));

        assertEquals(1, fixture.store.namedReleases);
        assertEquals(0, fixture.store.returnedMarines);
        assertEquals(0, fixture.state.contractMarinesCommitted[0]);
        assertEquals(-1, fixture.state.contractCaptainId[0]);
        assertEquals(Status.ACTIVE, fixture.captain.status());
    }

    @Test
    void failedNamedReleaseLeavesAssignmentUntouched() {
        Fixture fixture = fixture(ContractState.ACTIVE);
        fixture.store.namedAssignment = true;
        fixture.store.namedReleaseSucceeds = false;

        assertFalse(StationingWithdrawalService.withdraw(
                fixture.state, fixture.contractId, 40, fixture.store));

        assertEquals(0, fixture.store.returnedMarines);
        assertEquals(80, fixture.state.contractMarinesCommitted[0]);
        assertEquals(Status.GARRISONED, fixture.captain.status());
        assertEquals(ContractState.ACTIVE,
                ContractState.fromByte(fixture.state.contractState[0]));
        assertEquals(0, fixture.state.repCount);
    }

    @Test
    void terminalAndInProgressAssignmentsCannotWithdraw() {
        Fixture defaulted = fixture(ContractState.DEFAULTED);
        Fixture inProgress = fixture(ContractState.IN_PROGRESS);

        assertFalse(StationingWithdrawalService.withdraw(
                defaulted.state, defaulted.contractId, 40, defaulted.store));
        assertFalse(StationingWithdrawalService.withdraw(
                inProgress.state, inProgress.contractId, 40, inProgress.store));
    }

    private static Fixture fixture(ContractState stateValue) {
        CampaignState state = new CampaignState();
        MarineCaptain captain = new MarineCaptain("Captain", null, Rank.SERGEANT, 0f);
        captain.setStatus(Status.GARRISONED);
        int captainSlot = state.captainRegistry.intern(captain.id());
        long contractId = state.addContract(1L, -1L, -1L, ContractType.GARRISON,
                stateValue, 10, 100, -1, (byte) 0, captainSlot, 7, -1,
                0, 2_000, (byte) 25, (byte) 25, (byte) 100);
        state.contractMarinesCommitted[0] = 80;
        return new Fixture(state, contractId, captain);
    }

    private static final class Fixture {
        final CampaignState state;
        final long contractId;
        final MarineCaptain captain;
        final TestPersonnelStore store;

        Fixture(CampaignState state, long contractId, MarineCaptain captain) {
            this.state = state;
            this.contractId = contractId;
            this.captain = captain;
            this.store = new TestPersonnelStore(captain);
        }
    }

    private static final class TestPersonnelStore
            implements StationingWithdrawalService.PersonnelStore {
        final MarineCaptain captain;
        int returnedMarines;
        boolean deliverySucceeds = true;
        boolean namedAssignment;
        boolean namedReleaseSucceeds = true;
        int namedReleases;

        TestPersonnelStore(MarineCaptain captain) {
            this.captain = captain;
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
        public boolean releaseNamedAssignment(long contractId) {
            if (!namedReleaseSucceeds) return false;
            namedReleases++;
            namedAssignment = false;
            return true;
        }
    }
}
