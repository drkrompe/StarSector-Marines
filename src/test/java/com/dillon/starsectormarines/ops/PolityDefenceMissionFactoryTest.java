package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.campaign.PolityDefenceMissionKey;
import com.dillon.starsectormarines.campaign.systems.VanillaRaidGarrisonSystem.RaidThreat;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PolityDefenceMissionFactoryTest {

    @Test
    void aColonyDefenceIsAnAssaultWithNoContractBehindIt() {
        Mission mission = PolityDefenceMissionFactory.create(
                new RaidThreat(88L, 3, 5, 60f), 3, "pirates", "Jangala", "player", 1f);

        assertEquals(MissionSource.POLITY_DEFENCE, mission.source);
        assertEquals(MissionType.ASSAULT, mission.type);
        assertEquals(RiskLevel.HIGH, mission.risk);
        assertEquals("Colony Defence — Jangala", mission.name);
        assertEquals("Jangala", mission.targetPlanetName);
        assertEquals("player", mission.targetFactionId);
        assertEquals(-1L, mission.contractId);
        assertEquals(0, mission.salvageBaseline & 0xFF);
        assertEquals(0, mission.salvageNegotiated & 0xFF);
        assertEquals(mission.tier.drops, mission.requiredDrops);
        assertEquals(mission.tier.drops, mission.employerShuttles);
    }

    /** Without the override the raiders wear the defended colony's own roster. */
    @Test
    void raidersWearTheAttackersOwnKit() {
        Mission mission = PolityDefenceMissionFactory.create(
                new RaidThreat(88L, 3, 5, 60f), 3, "pirates", "Jangala", "player", 1f);

        assertEquals("pirates", mission.defenderFactionOverride);
    }

    /** The colony's own troops meet the landing beside the company. */
    @Test
    void theColonysOwnGarrisonStandsWithTheCompany() {
        Mission mission = PolityDefenceMissionFactory.create(
                new RaidThreat(88L, 3, 5, 60f), 3, "pirates", "Jangala", "player", 1f);

        assertEquals("player", mission.alliedGarrisonFactionId);
        assertEquals("pirates", mission.defenderFactionOverride);
    }

    /**
     * The polity's numbers doctrine is carried on the mission, since this is the
     * one factory that has a doctrine to carry.
     */
    @Test
    void thePolitysNumbersDoctrineRidesTheMission() {
        Mission neutral = PolityDefenceMissionFactory.create(
                new RaidThreat(88L, 3, 5, 60f), 3, "pirates", "Jangala", "player", 1f);
        Mission doubled = PolityDefenceMissionFactory.create(
                new RaidThreat(88L, 3, 5, 60f), 3, "pirates", "Jangala", "player", 2f);

        assertEquals(1f, neutral.alliedGarrisonStrengthMult);
        assertEquals(2f, doubled.alliedGarrisonStrengthMult);
    }

    @Test
    void tierRisesWithTheRaidsGroundStrength() {
        Mission small = PolityDefenceMissionFactory.create(
                new RaidThreat(1L, 3, 5, 10f), 3, "pirates", "Jangala", "player", 1f);
        Mission middling = PolityDefenceMissionFactory.create(
                new RaidThreat(2L, 3, 5, 100f), 3, "pirates", "Jangala", "player", 1f);
        Mission large = PolityDefenceMissionFactory.create(
                new RaidThreat(3L, 3, 5, 400f), 3, "pirates", "Jangala", "player", 1f);

        assertEquals(OperationTier.FIRST_CONTRACT, small.tier);
        assertEquals(OperationTier.VETERAN, middling.tier);
        assertEquals(OperationTier.FULL_STRENGTH, large.tier);
        assertTrue(small.tier.ordinal() < middling.tier.ordinal());
        assertTrue(middling.tier.ordinal() < large.tier.ordinal());
    }

    @Test
    void theIdCarriesTheMarketAndTheRaidItSettles() {
        Mission mission = PolityDefenceMissionFactory.create(
                new RaidThreat(0xBEEFL, 3, 5, 60f), 12, "pirates", "Jangala", "player", 1f);

        PolityDefenceMissionKey key = PolityDefenceMissionKey.parse(mission.id);
        assertNotNull(key);
        assertEquals(12, key.marketSlot);
        assertEquals(0xBEEFL, key.eventKey);
    }

    @Test
    void rejectsAMissingThreatOrColony() {
        assertNull(PolityDefenceMissionFactory.create(null, 3, "pirates", "Jangala", "player", 1f));
        assertNull(PolityDefenceMissionFactory.create(
                new RaidThreat(88L, 3, 5, 60f), 3, "pirates", null, "player", 1f));
    }
}
