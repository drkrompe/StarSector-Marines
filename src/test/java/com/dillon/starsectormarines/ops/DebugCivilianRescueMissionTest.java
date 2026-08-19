package com.dillon.starsectormarines.ops;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DebugCivilianRescueMissionTest {

    @Test
    void debugCatalogAddsCanonicalRescueThenOneStressTestPerRiskTier() {
        List<Mission> missions = MissionGenerator.debugCivilianRescueMissions(
                "Test Colony", new Random(71L), 15);

        assertEquals(RiskLevel.values().length + 1, missions.size());
        Mission canonical = missions.get(0);
        assertEquals("SWARM RESCUE — CANONICAL", canonical.name);
        assertEquals(MissionSource.DEBUG_CANONICAL_CIVILIAN_RESCUE,
                canonical.source);
        assertEquals(RiskLevel.HIGH, canonical.risk);
        assertEquals(4, canonical.requiredDrops);
        assertEquals(0, canonical.employerShuttles);
        assertTrue(canonical.source.isDebug());
        assertTrue(MissionLaunch.isCivilianRescueBattle(canonical));

        for (int i = 1; i < missions.size(); i++) {
            Mission mission = missions.get(i);
            assertEquals(RiskLevel.values()[i - 1], mission.risk);
            assertEquals("SWARM RESCUE STRESS — " + mission.risk.name(),
                    mission.name);
            assertEquals(MissionType.EXTRACTION, mission.type);
            assertEquals(MissionSource.DEBUG_CIVILIAN_RESCUE,
                    mission.source);
            assertTrue(mission.source.isDebug());
            assertEquals(0, mission.payout);
            assertEquals(8, mission.civiliansAtRisk);
            assertEquals(-1L, mission.campaignEventId);
            assertTrue(mission.clientFighterSupport.isEmpty());
            assertTrue(mission.enemyFighterSupport.isEmpty());
            assertEquals("Test Colony", mission.targetPlanetName);
            assertNull(mission.targetIndustryId);
            assertTrue(MissionLaunch.isCivilianRescueBattle(mission));
        }
    }

    @Test
    void ordinaryExtractionDoesNotSelectTheRescueFactory() {
        Mission mission = new Mission("ordinary", "Extraction",
                MissionType.EXTRACTION, MissionSource.GENERATED,
                1_000, RiskLevel.LOW, "", "", 0.5f, 0.5f,
                null, null, 1, 0, "Test Colony", null);

        assertFalse(MissionLaunch.isCivilianRescueBattle(mission));
        assertFalse(mission.source.isDebug());
    }
}
