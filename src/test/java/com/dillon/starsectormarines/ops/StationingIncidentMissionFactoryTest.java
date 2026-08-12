package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.air.ShuttleAssignment;
import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.ContractState;
import com.dillon.starsectormarines.campaign.ContractType;
import com.dillon.starsectormarines.campaign.StationingIncidentPayload;
import com.dillon.starsectormarines.campaign.StationingIncidentType;
import com.dillon.starsectormarines.ops.detachment.Detachment;
import com.dillon.starsectormarines.ops.detachment.DetachmentResolver;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class StationingIncidentMissionFactoryTest {

    @Test
    void liveFireRaidUsesOnlyLocalCadreDrops() {
        StationingIncidentPayload payload = payload(80, StationingIncidentType.LIVE_FIRE_RAID);

        Mission mission = StationingIncidentMissionFactory.create(payload, "Jangala", "hegemony");
        Detachment detachment = DetachmentResolver.resolveStationed(mission);

        assertEquals(MissionSource.STATIONING, mission.source);
        assertEquals(MissionType.ASSAULT, mission.type);
        assertEquals(RiskLevel.MEDIUM, mission.risk);
        assertEquals(8, mission.requiredDrops);
        assertEquals(8, mission.employerShuttles);
        assertEquals(payload.contractId, mission.contractId);
        assertEquals("Jangala", mission.targetPlanetName);
        assertEquals("hegemony", mission.targetFactionId);
        assertEquals(5, mission.salvageBaseline & 0xFF);
        assertEquals(8, totalCycles(detachment));
    }

    @Test
    void rescueIncidentsMapToExtractionAndRejectMissingPersonnel() {
        Mission accident = StationingIncidentMissionFactory.create(
                payload(20, StationingIncidentType.FACTORY_ACCIDENT), "Asharu", null);
        Mission defector = StationingIncidentMissionFactory.create(
                payload(20, StationingIncidentType.DEFECTOR_LEAD), "Asharu", null);

        assertEquals(MissionType.EXTRACTION, accident.type);
        assertEquals(RiskLevel.LOW, accident.risk);
        assertEquals(MissionType.EXTRACTION, defector.type);
        assertEquals(RiskLevel.MEDIUM, defector.risk);
        assertNull(StationingIncidentMissionFactory.create(null, "Asharu", null));
        assertNotNull(accident.id);
    }

    private static int totalCycles(Detachment detachment) {
        int total = 0;
        for (ShuttleAssignment assignment : detachment.shuttleManifest) {
            total += assignment.cycles;
        }
        return total;
    }

    private static StationingIncidentPayload payload(int marines,
                                                     StationingIncidentType type) {
        CampaignState state = new CampaignState();
        int captain = state.captainRegistry.intern("captain-1");
        long id = state.addContract(1L, -1L, -1L, ContractType.CADRE,
                ContractState.ACTIVE, 10, 100, -1, (byte) 0,
                captain, 1, -1, 0, 1_000,
                (byte) 5, (byte) 5, (byte) 100);
        state.contractMarinesCommitted[0] = marines;
        state.contractNextIncidentTick[0] = 42;
        state.contractIncidentPending[0] = 1;
        state.contractIncidentType[0] = type.toByte();
        return StationingIncidentPayload.from(state, id);
    }
}
