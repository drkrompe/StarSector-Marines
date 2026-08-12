package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.evacuation.CivilianEvacuationPayload;
import com.dillon.starsectormarines.battle.evacuation.SwarmDefenseRoster;
import com.dillon.starsectormarines.battle.flyby.FlybyRoster;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.PointOfInterest;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class SwarmEvacuationOutcomeBridgeTest {

    @Test
    void swarmFixtureReportsZeroPartialAndFullWithoutReadingVictory() {
        assertOutcome(0, 0);
        assertOutcome(3, 300);
        assertOutcome(8, 800);
    }

    @Test
    void debugRescueReportsRepresentativesWithoutCampaignLineage() {
        Fixture fixture = fixture();
        for (int i = 0; i < 5; i++) {
            fixture.sim.getCivilianEvacuationTracker().markEvacuated(
                    fixture.payload.entityId(i));
        }
        fixture.sim.getCivilianEvacuationTracker().seal();
        Mission debug = MissionGenerator.debugCivilianRescueMissions(
                "Arcadia", new java.util.Random(1L), 15).get(0);

        MissionOutcome outcome = MissionResolver.compute(
                fixture.sim, debug, null);

        assertEquals(-1L, outcome.campaignEventId);
        assertEquals(8, outcome.civiliansAtRisk);
        assertEquals(5, outcome.civiliansRescued);
        assertEquals(8, outcome.evacuationRepresentatives);
        assertEquals(5, outcome.representativesEvacuated);
    }

    private static void assertOutcome(int evacuated, int expectedCampaign) {
        Fixture fixture = fixture();
        for (int i = 0; i < evacuated; i++) {
            fixture.sim.getCivilianEvacuationTracker().markEvacuated(
                    fixture.payload.entityId(i));
        }
        fixture.sim.getCivilianEvacuationTracker().seal();

        MissionOutcome outcome = MissionResolver.compute(
                fixture.sim, eventMission(800), null);

        assertEquals(false, outcome.victory);
        assertEquals(expectedCampaign, outcome.civiliansRescued);
        assertEquals(8, outcome.evacuationRepresentatives);
        assertEquals(evacuated, outcome.representativesEvacuated);
    }

    private static Fixture fixture() {
        NavigationGrid grid = new NavigationGrid(32, 24);
        for (int y = 0; y < grid.getHeight(); y++) {
            for (int x = 0; x < grid.getWidth(); x++) {
                grid.setWalkableFloor(x, y);
            }
        }
        BattleSimulation sim = new BattleSimulation(
                grid, new CellTopology(32, 24));
        PointOfInterest home = new PointOfInterest(
                PointOfInterest.Kind.RESIDENTIAL,
                14, 10, 18, 14, 16, 12, 16, 12);
        CivilianEvacuationPayload payload = CivilianEvacuationPayload.install(
                sim, List.of(home), 77L);
        assertNotNull(payload);
        SwarmDefenseRoster swarm = SwarmDefenseRoster.install(
                sim, payload.placement, RiskLevel.LOW, 77L);
        assertNotNull(swarm);
        assertEquals(SwarmDefenseRoster.LOW_COUNT, swarm.size());
        return new Fixture(sim, payload);
    }

    private static Mission eventMission(int civiliansAtRisk) {
        return new Mission("civilian-rescue:7", "Civilian Evacuation",
                MissionType.EXTRACTION, MissionSource.CAMPAIGN_EVENT,
                0, RiskLevel.HIGH, "Committed relief response", "",
                0.5f, 0.5f, FlybyRoster.EMPTY, FlybyRoster.EMPTY,
                4, 0, "Arcadia", null, "independent",
                -1L, 7L, 3, civiliansAtRisk,
                (byte) 0, (byte) 0, (byte) 100,
                (byte) 0, (byte) 0, Collections.emptyList());
    }

    private static final class Fixture {
        final BattleSimulation sim;
        final CivilianEvacuationPayload payload;

        Fixture(BattleSimulation sim, CivilianEvacuationPayload payload) {
            this.sim = sim;
            this.payload = payload;
        }
    }
}
