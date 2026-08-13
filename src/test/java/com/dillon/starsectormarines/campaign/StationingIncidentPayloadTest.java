package com.dillon.starsectormarines.campaign;

import com.dillon.starsectormarines.marine.MarineCaptain;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.MarineSoldierStatus;
import com.dillon.starsectormarines.marine.MarineSquad;
import com.dillon.starsectormarines.marine.Rank;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StationingIncidentPayloadTest {

    @Test
    void pendingPayloadBindsOnlyTheStationedDetachment() {
        CampaignState state = new CampaignState();
        int captainSlot = state.captainRegistry.intern("captain-7");
        long id = state.addContract(1L, -1L, -1L, ContractType.CADRE,
                ContractState.ACTIVE, 10, 100, -1, (byte) 0,
                captainSlot, 12, -1, 0, 1_000,
                (byte) 5, (byte) 5, (byte) 100);
        state.contractMarinesCommitted[0] = 80;
        state.contractNextIncidentTick[0] = 42;
        state.contractIncidentPending[0] = 1;
        state.contractIncidentType[0] = StationingIncidentType.FACTORY_ACCIDENT.toByte();

        StationingIncidentPayload payload = StationingIncidentPayload.from(state, id);

        assertEquals(id, payload.contractId);
        assertEquals(StationingIncidentType.FACTORY_ACCIDENT, payload.type);
        assertEquals(42, payload.dueDay);
        assertEquals(12, payload.marketId);
        assertEquals("captain-7", payload.captainId);
        assertEquals(80, payload.committedMarines);
    }

    @Test
    void nonPendingAndTerminalRowsHaveNoPayload() {
        CampaignState state = new CampaignState();
        long id = state.addContract(1L, -1L, -1L, ContractType.CADRE,
                ContractState.ACTIVE, 10, 100, -1, (byte) 0,
                -1, 12, -1, 0, 1_000,
                (byte) 5, (byte) 5, (byte) 100);

        assertNull(StationingIncidentPayload.from(state, id));

        state.contractIncidentPending[0] = 1;
        state.contractIncidentType[0] = StationingIncidentType.LIVE_FIRE_RAID.toByte();
        state.contractState[0] = ContractState.COMPLETED.toByte();
        assertNull(StationingIncidentPayload.from(state, id));
    }

    @Test
    void namedPayloadFreezesBoundTeamsAndOnlyActiveSeats() {
        CampaignState state = new CampaignState();
        MarineRoster roster = new MarineRoster();
        MarineCaptain captain = new MarineCaptain("Cadre Lead", null, Rank.PRIVATE, 0f);
        roster.add(captain);
        roster.ensureActiveSoldiers(6);
        MarineSquad squad = roster.squads().get(0);
        MarineSoldierStatus wounded = MarineSoldierStatus.WIA;
        roster.applySoldierOutcome(Collections.singletonMap(
                roster.squadMembers(squad).get(0).id(), wounded), 0, 10f, 7f);
        int captainSlot = state.captainRegistry.intern(captain.id());
        long id = state.addContract(1L, -1L, -1L, ContractType.CADRE,
                ContractState.ACTIVE, 10, 100, -1, (byte) 0,
                captainSlot, 12, -1, 0, 1_000,
                (byte) 5, (byte) 5, (byte) 100);
        state.contractMarinesCommitted[0] = 6;
        state.contractNextIncidentTick[0] = 42;
        state.contractIncidentPending[0] = 1;
        state.contractIncidentType[0] = StationingIncidentType.LIVE_FIRE_RAID.toByte();
        roster.bindStationing(id, captain.id(), List.of(squad.id()));

        StationingIncidentPayload payload = StationingIncidentPayload.from(state, id, roster);

        assertEquals(List.of(squad.id()), payload.fireteamIds);
        assertEquals(5, payload.activeSeats);
        assertEquals(6, payload.committedMarines);
        assertThrows(UnsupportedOperationException.class,
                () -> payload.fireteamIds.add("late-team"));
    }
}
