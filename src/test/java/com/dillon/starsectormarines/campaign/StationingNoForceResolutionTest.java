package com.dillon.starsectormarines.campaign;

import com.dillon.starsectormarines.marine.MarineCaptain;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.MarineSoldier;
import com.dillon.starsectormarines.marine.MarineSoldierStatus;
import com.dillon.starsectormarines.marine.MarineSquad;
import com.dillon.starsectormarines.marine.Rank;
import com.dillon.starsectormarines.marine.Status;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StationingNoForceResolutionTest {

    @Test
    void wiaOnlyCadreFailsExplicitlyWithoutOpeningAnEmptyBattle() {
        CampaignState state = new CampaignState();
        MarineRoster roster = new MarineRoster();
        MarineCaptain captain = new MarineCaptain("Cadre Lead", null, Rank.PRIVATE, 0f);
        roster.add(captain);
        roster.ensureActiveSoldiers(6);
        MarineSquad squad = roster.squads().get(0);
        int captainSlot = state.captainRegistry.intern(captain.id());
        long contractId = state.addContract(1L, -1L, -1L, ContractType.CADRE,
                ContractState.ACTIVE, 10, 100, -1, (byte) 0,
                captainSlot, 12, -1, 0, 1_000,
                (byte) 5, (byte) 5, (byte) 100);
        state.contractMarinesCommitted[0] = 6;
        state.contractNextIncidentTick[0] = 42;
        state.contractIncidentPending[0] = 1;
        state.contractIncidentType[0] = StationingIncidentType.LIVE_FIRE_RAID.toByte();
        assertTrue(roster.bindStationing(contractId, captain.id(), List.of(squad.id())));
        captain.setStatus(Status.GARRISONED);
        Map<String, MarineSoldierStatus> wounded = new HashMap<>();
        for (MarineSoldier soldier : roster.squadMembers(squad)) {
            wounded.put(soldier.id(), MarineSoldierStatus.WIA);
        }
        roster.applySoldierOutcome(wounded, 0, 40f, 12f);
        StationingIncidentPayload payload = StationingIncidentPayload.from(
                state, contractId, roster);
        assertNotNull(payload);
        assertEquals(0, payload.activeSeats);

        StationingNoForceResolution.Result result = StationingNoForceResolution.apply(
                state, payload, roster, 40);

        assertEquals(StationingNoForceResolution.Result.INCIDENT_FAILED, result);
        assertEquals(ContractState.FAILED, ContractState.fromByte(state.contractState[0]));
        assertEquals(0, state.contractMarinesCommitted[0]);
        assertEquals(-1, state.contractCaptainId[0]);
        assertEquals(Status.ACTIVE, captain.status());
        assertFalse(squad.stationed());
        for (MarineSoldier soldier : roster.squadMembers(squad)) {
            assertEquals(MarineSoldierStatus.WIA, soldier.status());
        }
    }
}
