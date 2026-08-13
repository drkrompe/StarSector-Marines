package com.dillon.starsectormarines.campaign.systems;

import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.ContractState;
import com.dillon.starsectormarines.campaign.ContractType;
import com.dillon.starsectormarines.marine.MarineCaptain;
import com.dillon.starsectormarines.marine.Rank;
import com.dillon.starsectormarines.marine.Status;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StationingReleaseSystemTest {

    @Test
    void completedContractReturnsPersonnelOnce() {
        Fixture fixture = fixture(ContractState.COMPLETED);

        fixture.system.tick(fixture.state, 61);
        fixture.system.tick(fixture.state, 62);

        assertEquals(75, fixture.returnedMarines);
        assertEquals(Status.ACTIVE, fixture.captain.status());
        assertEquals(0, fixture.state.contractMarinesCommitted[0]);
        assertEquals(-1, fixture.state.contractCaptainId[0]);
    }

    @Test
    void failedDeliveryRetriesWithoutReleasingCaptain() {
        Fixture fixture = fixture(ContractState.COMPLETED);
        fixture.deliverySucceeds = false;

        fixture.system.tick(fixture.state, 61);

        assertEquals(0, fixture.returnedMarines);
        assertEquals(Status.GARRISONED, fixture.captain.status());
        assertEquals(75, fixture.state.contractMarinesCommitted[0]);
    }

    @Test
    void completedNamedContractReleasesTeamsWithoutCargoOnce() {
        Fixture fixture = fixture(ContractState.COMPLETED);
        fixture.namedAssignment = true;

        fixture.system.tick(fixture.state, 61);
        fixture.system.tick(fixture.state, 62);

        assertEquals(1, fixture.namedReleases);
        assertEquals(0, fixture.returnedMarines);
        assertEquals(Status.ACTIVE, fixture.captain.status());
        assertEquals(0, fixture.state.contractMarinesCommitted[0]);
        assertEquals(-1, fixture.state.contractCaptainId[0]);
    }

    @Test
    void failedNamedReleaseRetriesWithoutClearingAuthority() {
        Fixture fixture = fixture(ContractState.COMPLETED);
        fixture.namedAssignment = true;
        fixture.namedReleaseSucceeds = false;

        fixture.system.tick(fixture.state, 61);

        assertEquals(0, fixture.namedReleases);
        assertEquals(0, fixture.returnedMarines);
        assertEquals(Status.GARRISONED, fixture.captain.status());
        assertEquals(75, fixture.state.contractMarinesCommitted[0]);
        assertEquals(0, fixture.state.contractCaptainId[0]);
    }

    @Test
    void defaultedContractKeepsPersonnelForExtractionFlow() {
        Fixture fixture = fixture(ContractState.DEFAULTED);

        fixture.system.tick(fixture.state, 61);

        assertEquals(0, fixture.returnedMarines);
        assertEquals(Status.GARRISONED, fixture.captain.status());
        assertEquals(75, fixture.state.contractMarinesCommitted[0]);
    }

    private static Fixture fixture(ContractState contractState) {
        CampaignState state = new CampaignState();
        MarineCaptain captain = new MarineCaptain("Captain", null, Rank.SERGEANT, 0f);
        captain.setStatus(Status.GARRISONED);
        int captainSlot = state.captainRegistry.intern(captain.id());
        state.addContract(1L, -1L, -1L, ContractType.GARRISON, contractState,
                0, 60, -1, (byte) 0, captainSlot, 0, -1,
                0, 1_000, (byte) 25, (byte) 25, (byte) 100);
        state.contractMarinesCommitted[0] = 75;
        state.contractCaptainId[0] = captainSlot;
        return new Fixture(state, captain);
    }

    private static final class Fixture implements StationingReleaseSystem.PersonnelStore {
        final CampaignState state;
        final MarineCaptain captain;
        final StationingReleaseSystem system;
        int returnedMarines;
        boolean deliverySucceeds = true;
        boolean namedAssignment;
        boolean namedReleaseSucceeds = true;
        int namedReleases;

        Fixture(CampaignState state, MarineCaptain captain) {
            this.state = state;
            this.captain = captain;
            this.system = new StationingReleaseSystem(this);
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
            namedAssignment = false;
            namedReleases++;
            return true;
        }
    }
}
