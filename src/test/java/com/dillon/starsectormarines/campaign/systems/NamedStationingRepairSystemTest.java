package com.dillon.starsectormarines.campaign.systems;

import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.ContractState;
import com.dillon.starsectormarines.campaign.ContractType;
import com.dillon.starsectormarines.marine.MarineCaptain;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.MarineSquad;
import com.dillon.starsectormarines.marine.Rank;
import com.dillon.starsectormarines.marine.Status;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NamedStationingRepairSystemTest {

    @Test
    void validNamedBindingRepairsDerivedStrengthAndCaptainStatus() {
        Fixture fixture = fixture(ContractState.ACTIVE);
        fixture.state.contractMarinesCommitted[0] = 99;

        int repairs = NamedStationingRepairSystem.repair(fixture.state, fixture.roster);

        assertEquals(2, repairs);
        assertEquals(6, fixture.state.contractMarinesCommitted[0]);
        assertEquals(Status.GARRISONED, fixture.captain.status());
        assertTrue(fixture.squad.stationed());
    }

    @Test
    void anonymousLegacyRowIsNeverAutoBoundOrRecounted() {
        CampaignState state = new CampaignState();
        state.addContract(1L, -1L, -1L, ContractType.GARRISON,
                ContractState.ACTIVE, 1, 30, -1, (byte) 0, -1, 5, -1,
                0, 1_000, (byte) 25, (byte) 25, (byte) 100);
        state.contractMarinesCommitted[0] = 80;
        MarineRoster roster = new MarineRoster();
        roster.ensureActiveSoldiers(6);

        assertEquals(0, NamedStationingRepairSystem.repair(state, roster));
        assertEquals(80, state.contractMarinesCommitted[0]);
        assertFalse(roster.squads().get(0).stationed());
    }

    @Test
    void failedAndDanglingBindingsReleaseWithoutCreatingLegacyAuthority() {
        Fixture failed = fixture(ContractState.FAILED);
        failed.captain.setStatus(Status.GARRISONED);
        assertTrue(NamedStationingRepairSystem.repair(failed.state, failed.roster) > 0);
        assertFalse(failed.squad.stationed());
        assertEquals(Status.ACTIVE, failed.captain.status());
        assertEquals(0, failed.state.contractMarinesCommitted[0]);
        assertEquals(-1, failed.state.contractCaptainId[0]);

        Fixture dangling = fixture(ContractState.ACTIVE);
        long removedId = dangling.state.contractId[0];
        dangling.state.contractIndexById.clear();
        dangling.state.contractCount = 0;
        assertEquals(1, dangling.roster.squadsStationedOn(removedId).size());
        assertEquals(1, NamedStationingRepairSystem.repair(dangling.state, dangling.roster));
        assertFalse(dangling.squad.stationed());
    }

    private static Fixture fixture(ContractState contractState) {
        CampaignState state = new CampaignState();
        MarineRoster roster = new MarineRoster();
        MarineCaptain captain = new MarineCaptain("Station Lead", null, Rank.PRIVATE, 0f);
        roster.add(captain);
        roster.ensureActiveSoldiers(6);
        int captainSlot = state.captainRegistry.intern(captain.id());
        long contractId = state.addContract(1L, -1L, -1L, ContractType.CADRE,
                contractState, 1, 30, -1, (byte) 0, captainSlot, 5, -1,
                0, 1_000, (byte) 5, (byte) 5, (byte) 100);
        MarineSquad squad = roster.squads().get(0);
        assertTrue(roster.bindStationing(contractId, captain.id(), List.of(squad.id())));
        state.contractMarinesCommitted[0] = 6;
        return new Fixture(state, roster, captain, squad);
    }

    private static final class Fixture {
        final CampaignState state;
        final MarineRoster roster;
        final MarineCaptain captain;
        final MarineSquad squad;

        Fixture(CampaignState state, MarineRoster roster,
                MarineCaptain captain, MarineSquad squad) {
            this.state = state;
            this.roster = roster;
            this.captain = captain;
            this.squad = squad;
        }
    }
}
