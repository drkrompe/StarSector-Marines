package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.air.ShuttleAssignment;
import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.ContractState;
import com.dillon.starsectormarines.campaign.ContractType;
import com.dillon.starsectormarines.campaign.GarrisonDefenseMissionKey;
import com.dillon.starsectormarines.campaign.GarrisonDefensePayload;
import com.dillon.starsectormarines.campaign.GarrisonDefenseTriggerType;
import com.dillon.starsectormarines.marine.MarineCaptain;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.MarineSquad;
import com.dillon.starsectormarines.marine.Rank;
import com.dillon.starsectormarines.ops.detachment.Detachment;
import com.dillon.starsectormarines.ops.detachment.DetachmentResolver;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;

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

    @Test
    void namedDefenseSizesLiftsFromFrozenActiveSeats() {
        CampaignState state = new CampaignState();
        MarineRoster roster = new MarineRoster();
        MarineCaptain captain = new MarineCaptain("Garrison Lead", null, Rank.LIEUTENANT, 0f);
        roster.add(captain);
        roster.ensureActiveSoldiers(6);
        MarineSquad squad = roster.squads().get(0);
        int captainSlot = state.captainRegistry.intern(captain.id());
        long id = state.addContract(1L, -1L, -1L, ContractType.GARRISON,
                ContractState.IN_PROGRESS, 10, 100, -1, (byte) 0,
                captainSlot, 7, -1, 0, 1_000,
                (byte) 25, (byte) 15, (byte) 105);
        state.contractMarinesCommitted[0] = 6;
        state.contractDefenseEventKey[0] = 77L;
        state.contractDefenseTriggeredTick[0] = 42;
        state.contractDefenseTriggerType[0] = GarrisonDefenseTriggerType.VANILLA_RAID.toByte();
        roster.bindStationing(id, captain.id(), List.of(squad.id()));
        GarrisonDefensePayload payload = GarrisonDefensePayload.from(state, id, roster);

        Mission mission = GarrisonDefenseMissionFactory.create(payload, "Jangala", "hegemony");

        assertEquals(List.of(squad.id()), payload.fireteamIds);
        assertEquals(6, payload.activeSeats);
        assertEquals(2, mission.requiredDrops);
    }

    /**
     * Without the override the raiders wear the defended market's own roster: pirates
     * landing on a Hegemony world arrive in Hegemony kit.
     */
    @Test
    void raidersWearTheAttackersOwnKit() {
        CampaignState state = new CampaignState();
        int captain = state.captainRegistry.intern("captain-1");
        long id = state.addContract(1L, -1L, -1L, ContractType.GARRISON,
                ContractState.IN_PROGRESS, 10, 100, -1, (byte) 0,
                captain, 7, -1, 0, 1_000,
                (byte) 25, (byte) 15, (byte) 105);
        state.contractMarinesCommitted[0] = 20;
        state.contractDefenseEventKey[0] = 77L;
        state.contractDefenseTriggerType[0] = GarrisonDefenseTriggerType.VANILLA_RAID.toByte();
        state.contractDefenseAttackerFactionId[0] = state.factionRegistry.intern("pirates");

        Mission raid = GarrisonDefenseMissionFactory.create(
                GarrisonDefensePayload.from(state, id), "Jangala", "hegemony");

        assertEquals("pirates", raid.defenderFactionOverride);
        assertNull(GarrisonDefenseMissionFactory.create(payload(20), "Jangala", "hegemony")
                .defenderFactionOverride);
    }

    /**
     * The patron whose market is being defended has troops of its own, and they
     * fight beside the company rather than against it — so the allied garrison
     * names the defended market, never the raider the override names.
     */
    @Test
    void theDefendedMarketsOwnGarrisonStandsWithTheCompany() {
        CampaignState state = new CampaignState();
        int captain = state.captainRegistry.intern("captain-1");
        long id = state.addContract(1L, -1L, -1L, ContractType.GARRISON,
                ContractState.IN_PROGRESS, 10, 100, -1, (byte) 0,
                captain, 7, -1, 0, 1_000,
                (byte) 25, (byte) 15, (byte) 105);
        state.contractMarinesCommitted[0] = 20;
        state.contractDefenseEventKey[0] = 77L;
        state.contractDefenseTriggerType[0] = GarrisonDefenseTriggerType.VANILLA_RAID.toByte();
        state.contractDefenseAttackerFactionId[0] = state.factionRegistry.intern("pirates");

        Mission mission = GarrisonDefenseMissionFactory.create(
                GarrisonDefensePayload.from(state, id), "Jangala", "hegemony");

        assertEquals("hegemony", mission.alliedGarrisonFactionId);
        assertEquals("pirates", mission.defenderFactionOverride);
    }

    /**
     * A vanilla raid states how many it is landing, so the operation is sized off that
     * rather than off the risk label: 200 raid strength outgrows Reinforced's 175.
     */
    @Test
    void aVanillaRaidSizesTheOperationFromItsOwnLandingStrength() {
        Mission mission = GarrisonDefenseMissionFactory.create(
                strengthPayload(GarrisonDefenseTriggerType.VANILLA_RAID, 200f),
                "Jangala", "hegemony");

        assertEquals(OperationTier.FULL_STRENGTH, mission.tier);
    }

    /** Only the raid reader estimates a landing; every other trigger keeps the default. */
    @Test
    void aRivalStrikeKeepsTheDefaultTier() {
        Mission rival = GarrisonDefenseMissionFactory.create(
                strengthPayload(GarrisonDefenseTriggerType.RIVAL_STRIKE, 200f),
                "Jangala", "hegemony");
        Mission unestimatedRaid = GarrisonDefenseMissionFactory.create(
                strengthPayload(GarrisonDefenseTriggerType.VANILLA_RAID, 0f),
                "Jangala", "hegemony");

        assertEquals(OperationTier.VETERAN, rival.tier);
        assertEquals(OperationTier.VETERAN, unestimatedRaid.tier);
    }

    private static GarrisonDefensePayload strengthPayload(GarrisonDefenseTriggerType type,
                                                          float strength) {
        CampaignState state = new CampaignState();
        int captain = state.captainRegistry.intern("captain-1");
        long id = state.addContract(1L, -1L, -1L, ContractType.GARRISON,
                ContractState.IN_PROGRESS, 10, 100, -1, (byte) 0,
                captain, 7, -1, 0, 1_000,
                (byte) 25, (byte) 15, (byte) 105);
        state.contractMarinesCommitted[0] = 20;
        state.contractDefenseEventKey[0] = 77L;
        state.contractDefenseTriggerType[0] = type.toByte();
        state.contractDefenseAttackerStrength[0] = strength;
        return GarrisonDefensePayload.from(state, id);
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
