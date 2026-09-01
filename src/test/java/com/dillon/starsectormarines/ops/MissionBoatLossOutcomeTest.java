package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.air.FittedBoat;
import com.dillon.starsectormarines.battle.air.ShuttleMission;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.infantry.MarineLoadout;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.marine.BoatFitting;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A passenger is neither a live unit nor a corpse, so the resolver's two walks
 * cannot see them and the air ledger is the only thing that can.
 *
 * <p>Resolved against a real simulation because the ledger is the seam under
 * test and a hand-made one would be a test of the fixture. Nothing is played:
 * the shuttle is spawned, destroyed, and the outcome computed.
 */
class MissionBoatLossOutcomeTest {

    @Test
    void aBoatShotDownWithMarinesAboardIsNamedAndItsPassengersAreFallen() {
        try (BattleSimulation sim = simulation()) {
            destroyBoatCarrying(sim, "boat_03",
                    "soldier-a", "soldier-b", "soldier-c", "soldier-d");

            MissionOutcome outcome = MissionResolver.compute(sim, mission(), null);

            assertEquals(1, outcome.boatsLost.size());
            MissionOutcome.BoatLoss loss = outcome.boatsLost.get(0);
            assertEquals("boat_03", loss.boatId());
            assertEquals(ShuttleType.VALKYRIE, loss.pattern());
            assertEquals(4, loss.passengersLost());
            assertEquals("boat_03", loss.displayName(),
                    "with no campaign deck to ask, the id is the only name there is");

            assertEquals(4, outcome.marinesLostAboard);
            assertEquals(4, outcome.marinesLost, "they are casualties like any other");
            assertEquals(4, outcome.marinesEngaged,
                    "and they were committed, so they are part of what went in");
            assertTrue(outcome.fallenSoldierIds.containsAll(
                    List.of("soldier-a", "soldier-b", "soldier-c", "soldier-d")));
            assertTrue(outcome.survivingSoldierIds.isEmpty());
        }
    }

    /** An employer's lander is not the company's, so its loss reaches nothing. */
    @Test
    void aCraftNoCampaignBoatWasFrozenFromIsNotAnOutcome() {
        try (BattleSimulation sim = simulation()) {
            long shuttle = sim.spawnShuttle(ShuttleType.VALKYRIE, Faction.MARINE,
                    10.5f, 10.5f, -2f, 10.5f, 22f, 10.5f, 0f, 4);
            sim.world().mission(shuttle).marinesRemaining = 4;
            sim.getRoster().airTargets().destroy(shuttle);

            MissionOutcome outcome = MissionResolver.compute(sim, mission(), null);

            assertEquals(1, sim.getAirLosses().size(), "the battle still lost a craft");
            assertEquals(List.of(), outcome.boatsLost);
            assertEquals(0, outcome.marinesLostAboard);
            assertEquals(0, outcome.marinesLost);
        }
    }

    /** The outcome is frozen, so a later read cannot be handed a different battle. */
    @Test
    void theLostBoatsAreFrozenOnTheOutcome() {
        MissionOutcome outcome = MissionOutcome.builder()
                .missionId("boat-loss")
                .boatsLost(List.of(new MissionOutcome.BoatLoss(
                        "boat_03", "Aeroshuttle 03", ShuttleType.AEROSHUTTLE, 4)))
                .build();

        assertEquals(1, outcome.boatsLost.size());
        assertThrows(UnsupportedOperationException.class,
                () -> outcome.boatsLost.add(null));
    }

    private static void destroyBoatCarrying(BattleSimulation sim, String boatId,
                                            String... campaignSoldierIds) {
        FittedBoat boat = new FittedBoat(ShuttleType.VALKYRIE,
                BoatFitting.STANDARD_PLATING, BoatFitting.STANDARD_DRIVE, boatId);
        long shuttle = sim.spawnShuttle(ShuttleType.VALKYRIE, boat, Faction.MARINE,
                10.5f, 10.5f, -2f, 10.5f, 22f, 10.5f, 0f, campaignSoldierIds.length);
        ShuttleMission mission = sim.world().mission(shuttle);
        MarineLoadout[] seats = new MarineLoadout[campaignSoldierIds.length];
        for (int seat = 0; seat < seats.length; seat++) {
            seats[seat] = new MarineLoadout(UnitRole.COMBATANT, null,
                    MarineLoadout.DEFAULT_PRIMARY_ID, null, null, null, 0,
                    campaignSoldierIds[seat], null);
        }
        mission.marineLoadout = seats;
        mission.marinesRemaining = seats.length;
        sim.getRoster().airTargets().destroy(shuttle);
    }

    private static BattleSimulation simulation() {
        NavigationGrid grid = new NavigationGrid(20, 20);
        for (int y = 0; y < 20; y++) {
            for (int x = 0; x < 20; x++) grid.setWalkableFloor(x, y);
        }
        return new BattleSimulation(grid, new CellTopology(20, 20));
    }

    private static Mission mission() {
        return Mission.builder()
                .id("boat-loss:1")
                .name("Boat Loss")
                .type(MissionType.ASSAULT)
                .source(MissionSource.GENERATED)
                .risk(RiskLevel.MEDIUM)
                .requirements("Committed assault")
                .mapPosition(0.5f, 0.5f)
                .requiredDrops(1)
                .build();
    }
}
