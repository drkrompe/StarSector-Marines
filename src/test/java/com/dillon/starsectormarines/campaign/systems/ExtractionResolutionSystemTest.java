package com.dillon.starsectormarines.campaign.systems;

import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.ContractState;
import com.dillon.starsectormarines.campaign.ContractType;
import com.dillon.starsectormarines.marine.MarineCaptain;
import com.dillon.starsectormarines.marine.Rank;
import com.dillon.starsectormarines.marine.Status;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

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

        fixture.deliverySucceeds = true;
        fixture.system.tick(fixture.state, 41);
        assertEquals(80, fixture.returnedMarines);
        assertEquals(-10, fixture.state.repValue[0]);
    }

    private static Fixture fixture(ContractState extractionState) {
        CampaignState state = new CampaignState();
        MarineCaptain captain = new MarineCaptain("Stranded", null, Rank.SERGEANT, 0f);
        captain.setStatus(Status.GARRISONED);
        int captainSlot = state.captainRegistry.intern(captain.id());
        long parentId = state.addContract(1L, -1L, -1L, ContractType.GARRISON,
                ContractState.DEFAULTED, 1, 100, -1, (byte) 0, captainSlot, 7, -1,
                0, 2_000, (byte) 25, (byte) 25, (byte) 100);
        state.contractMarinesCommitted[0] = 80;
        long extractionId = state.addContract(1L, -1L, -1L, ContractType.EXTRACTION,
                extractionState, 2, -1, -1, (byte) 1, -1, 7, -1,
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
    }
}
