package com.dillon.starsectormarines.campaign.systems;

import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.ContractState;
import com.dillon.starsectormarines.campaign.ContractType;
import com.dillon.starsectormarines.marine.MarineCaptain;
import com.dillon.starsectormarines.marine.Rank;
import com.dillon.starsectormarines.marine.Status;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CadreTrainingSystemTest {

    @Test
    void catchesUpMonthsPromotesAndDoesNotDuplicate() {
        Fixture fixture = fixture(ContractType.CADRE, ContractState.ACTIVE, 10, 100);
        fixture.captain.addXp(100);

        fixture.system.tick(fixture.state, 70);
        fixture.system.tick(fixture.state, 70);

        assertEquals(Rank.CORPORAL, fixture.captain.rank());
        assertEquals(250, fixture.captain.xp());
        assertEquals(70, fixture.state.contractLastTrainingTick[0]);
    }

    @Test
    void capsTrainingAtExpiry() {
        Fixture fixture = fixture(ContractType.CADRE, ContractState.ACTIVE, 0, 60);

        fixture.system.tick(fixture.state, 120);

        assertEquals(Rank.CORPORAL, fixture.captain.rank());
        assertEquals(150, fixture.captain.xp());
        assertEquals(60, fixture.state.contractLastTrainingTick[0]);
    }

    @Test
    void missingCaptainRetriesAndExcludedRowsDoNothing() {
        Fixture missing = fixture(ContractType.CADRE, ContractState.ACTIVE, 0, 60);
        missing.lookupEnabled = false;
        missing.system.tick(missing.state, 30);
        assertEquals(0, missing.state.contractLastTrainingTick[0]);
        assertEquals(0, missing.captain.xp());

        Fixture garrison = fixture(ContractType.GARRISON, ContractState.ACTIVE, 0, 60);
        Fixture complete = fixture(ContractType.CADRE, ContractState.COMPLETED, 0, 60);
        garrison.system.tick(garrison.state, 30);
        complete.system.tick(complete.state, 30);
        assertEquals(0, garrison.captain.xp());
        assertEquals(0, complete.captain.xp());
    }

    private static Fixture fixture(ContractType type, ContractState contractState,
                                   int acceptedDay, int expiresDay) {
        CampaignState state = new CampaignState();
        MarineCaptain captain = new MarineCaptain("Trainer", null, Rank.PRIVATE, 0f);
        captain.setStatus(Status.GARRISONED);
        int captainSlot = state.captainRegistry.intern(captain.id());
        state.addContract(1L, -1L, -1L, type, contractState,
                acceptedDay, expiresDay, -1, (byte) 0, captainSlot, 0, -1,
                0, 500, (byte) 5, (byte) 5, (byte) 100);
        state.contractCaptainId[0] = captainSlot;
        state.contractLastTrainingTick[0] = acceptedDay;
        return new Fixture(state, captain);
    }

    private static final class Fixture implements CadreTrainingSystem.CaptainLookup {
        final CampaignState state;
        final MarineCaptain captain;
        final CadreTrainingSystem system;
        boolean lookupEnabled = true;

        Fixture(CampaignState state, MarineCaptain captain) {
            this.state = state;
            this.captain = captain;
            this.system = new CadreTrainingSystem(this);
        }

        @Override
        public MarineCaptain find(String id) {
            return lookupEnabled && captain.id().equals(id) ? captain : null;
        }
    }
}
