package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.air.ShuttleAssignment;
import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.ContractState;
import com.dillon.starsectormarines.campaign.ContractType;
import com.dillon.starsectormarines.campaign.GarrisonDefenseMissionKey;
import com.dillon.starsectormarines.campaign.GarrisonDefensePayload;
import com.dillon.starsectormarines.campaign.GarrisonDefenseTriggerType;
import com.dillon.starsectormarines.ops.detachment.Detachment;
import com.dillon.starsectormarines.ops.detachment.DetachmentResolver;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class GarrisonDefenseMissionFactoryTest {

    @Test
    void defenseUsesOnlyLocalGarrisonDropsAndNegotiatedSalvage() {
        GarrisonDefensePayload payload = payload(80);

        Mission mission = GarrisonDefenseMissionFactory.create(payload, "Jangala", "hegemony");
        Detachment detachment = DetachmentResolver.resolveStationed(mission);

        assertEquals(MissionSource.STATIONING, mission.source);
        assertEquals(MissionType.ASSAULT, mission.type);
        assertEquals(RiskLevel.HIGH, mission.risk);
        assertEquals(8, mission.requiredDrops);
        assertEquals(8, mission.employerShuttles);
        assertEquals(payload.contractId, mission.contractId);
        assertEquals("Jangala", mission.targetPlanetName);
        assertEquals("hegemony", mission.targetFactionId);
        assertEquals(25, mission.salvageBaseline & 0xFF);
        assertEquals(15, mission.salvageNegotiated & 0xFF);
        assertEquals(25, mission.contractSalvageBaseline & 0xFF);
        assertEquals(15, mission.contractSalvageNegotiated & 0xFF);
        assertEquals(8, totalCycles(detachment));
        assertEquals(payload.eventKey,
                GarrisonDefenseMissionKey.parse(mission.id).eventKey);
    }

    @Test
    void rejectsMissingOnSiteDetachment() {
        assertNull(GarrisonDefenseMissionFactory.create(null, "Asharu", null));
        assertNotNull(GarrisonDefenseMissionFactory.create(payload(20), "Asharu", null));
    }

    private static int totalCycles(Detachment detachment) {
        int total = 0;
        for (ShuttleAssignment assignment : detachment.shuttleManifest) {
            total += assignment.cycles;
        }
        return total;
    }

    private static GarrisonDefensePayload payload(int marines) {
        CampaignState state = new CampaignState();
        int captain = state.captainRegistry.intern("captain-1");
        long id = state.addContract(1L, -1L, -1L, ContractType.GARRISON,
                ContractState.IN_PROGRESS, 10, 100, -1, (byte) 0,
                captain, 7, -1, 0, 1_000,
                (byte) 25, (byte) 15, (byte) 105);
        state.contractMarinesCommitted[0] = marines;
        state.contractDefenseEventKey[0] = 77L;
        state.contractDefenseTriggeredTick[0] = 42;
        state.contractDefenseTriggerType[0] = GarrisonDefenseTriggerType.VANILLA_RAID.toByte();
        return GarrisonDefensePayload.from(state, id);
    }
}
