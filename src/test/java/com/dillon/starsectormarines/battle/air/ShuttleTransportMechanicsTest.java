package com.dillon.starsectormarines.battle.air;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.setup.BattleForceScore;
import com.dillon.starsectormarines.battle.command.objective.Objective;
import com.dillon.starsectormarines.battle.infantry.MarineLoadout;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.turret.TurretRole;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.marine.BoatFitting;
import com.dillon.starsectormarines.ops.detachment.CampaignMarineDeployment;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ShuttleTransportMechanicsTest {

    @Test
    void pairedCraftDeboardOneTwelveMarineThreeFireTeamSquad() {
        try (BattleSimulation sim = openSimulation()) {
            sim.addObjective(new WaitingObjective(Faction.MARINE));
            sim.addObjective(new WaitingObjective(Faction.DEFENDER));
            pairedLandedShuttle(sim, 8.5f, 11);
            pairedLandedShuttle(sim, 12.5f, 11);

            for (int i = 0; i < 300; i++) sim.advance(BattleSimulation.TICK_DT);

            List<Squad> marineSquads = sim.getSquads().stream()
                    .filter(squad -> squad.faction == Faction.MARINE)
                    .toList();
            assertEquals(1, marineSquads.size());
            Squad squad = marineSquads.get(0);
            assertEquals(12, squad.originalSize);
            assertEquals(12, sim.squadMemberCount(squad.id));
            Set<Integer> fireTeams = new java.util.HashSet<>();
            for (int i = 0; i < sim.squadMemberCount(squad.id); i++) {
                fireTeams.add(sim.squad().fireTeamIndex(
                        sim.squadMemberAt(squad.id, i)));
            }
            assertEquals(Set.of(0, 1, 2), fireTeams);
        }
    }

    @Test
    void assignmentDefaultsToPhysicalCapacityAndAcceptsAPartialLoad() {
        ShuttleAssignment full = new ShuttleAssignment(ShuttleType.VALKYRIE, 2);
        ShuttleAssignment half = new ShuttleAssignment(ShuttleType.VALKYRIE, 2, 6);
        ShuttleAssignment partialFinal = new ShuttleAssignment(
                ShuttleType.VALKYRIE, 2, 12, 13);

        assertEquals(ShuttleType.VALKYRIE.capacity, full.seatsPerSortie);
        assertEquals(6, half.seatsPerSortie);
        assertEquals(12, partialFinal.seatsForCycle(0));
        assertEquals(1, partialFinal.seatsForCycle(1));
        assertNotEquals(full, half);
    }

    @Test
    void assignmentRejectsAnEmptyOrOverCapacityLoad() {
        assertThrows(IllegalArgumentException.class,
                () -> new ShuttleAssignment(ShuttleType.VALKYRIE, 1, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new ShuttleAssignment(ShuttleType.VALKYRIE, 1, 13));
        assertThrows(IllegalArgumentException.class,
                () -> new ShuttleAssignment(ShuttleType.KITE, 1, 6));
    }

    /**
     * An assignment always has an airframe, so nothing downstream has to guess
     * whether one was supplied. A craft nobody fitted flies as its own pattern.
     */
    @Test
    void anAssignmentWithNoBoatBehindItFliesAsItsOwnPattern() {
        ShuttleAssignment plain = new ShuttleAssignment(ShuttleType.VALKYRIE, 2);

        assertSame(ShuttleType.VALKYRIE, plain.airframe);
    }

    /**
     * Seats and cycles stay facts about the pattern — a yard's plating does not
     * add a seat — while the frame is what the sim measures the craft by.
     */
    @Test
    void anAssignmentBuiltFromABoatKeepsThePatternAndCarriesTheFit() {
        FittedBoat boat = new FittedBoat(ShuttleType.VALKYRIE,
                BoatFitting.REINFORCED_PLATING, BoatFitting.STANDARD_DRIVE);

        ShuttleAssignment assignment = new ShuttleAssignment(boat, 2, 6);

        assertEquals(ShuttleType.VALKYRIE, assignment.type);
        assertSame(boat, assignment.airframe);
        assertEquals(6, assignment.seatsPerSortie);
        assertNotEquals(new ShuttleAssignment(ShuttleType.VALKYRIE, 2, 6), assignment,
                "a fitted boat and a bare pattern are not the same commitment");
    }

    /**
     * The whole point of the seam: what an anti-air gun has to get through is
     * the plating the player paid for, not the pattern's factory hull.
     */
    @Test
    void aSortieSpawnedFromAFittedBoatIsSeededWithThatBoatsHull() {
        try (BattleSimulation sim = openSimulation()) {
            FittedBoat armoured = new FittedBoat(ShuttleType.VALKYRIE,
                    BoatFitting.ARMOURED_PLATING, BoatFitting.TUNED_DRIVE);

            long shuttle = sim.spawnShuttle(ShuttleType.VALKYRIE, armoured, Faction.MARINE,
                    10.5f, 10.5f, -2f, 10.5f, 22f, 10.5f, 0f, 6);

            assertEquals(ShuttleType.VALKYRIE.maxHp() * 1.75f,
                    sim.world().maxHp(shuttle), 1e-3f);
            assertSame(armoured, sim.world().airframe(shuttle),
                    "the steering tick reads handling off the identity, so the fit "
                            + "has to be what is stored there");
        }
    }

    /**
     * The ledger is the only record of a shoot-down that survives the tick that
     * caused it, so what it says has to be right at the moment it is written and
     * still be there afterwards.
     */
    @Test
    void aBoatShotDownBeforeItDeboardsNamesEverybodyStillAboard() {
        try (BattleSimulation sim = openSimulation()) {
            FittedBoat boat = new FittedBoat(ShuttleType.VALKYRIE,
                    BoatFitting.STANDARD_PLATING, BoatFitting.STANDARD_DRIVE, "boat_03");
            long shuttle = sim.spawnShuttle(ShuttleType.VALKYRIE, boat, Faction.MARINE,
                    10.5f, 10.5f, -2f, 10.5f, 22f, 10.5f, 0f, 4);
            ShuttleMission mission = sim.world().mission(shuttle);
            mission.marineLoadout = passengers("soldier-a", "soldier-b", "soldier-c", "soldier-d");
            mission.marinesRemaining = 4;

            sim.getRoster().airTargets().destroy(shuttle);

            assertEquals(1, sim.getAirLosses().size());
            AirLoss loss = sim.getAirLosses().get(0);
            assertSame(boat, loss.frame());
            assertEquals(Faction.MARINE, loss.faction());
            assertEquals(4, loss.passengersAboard());
            assertEquals(List.of("soldier-a", "soldier-b", "soldier-c", "soldier-d"),
                    loss.passengerSoldierIds());

            sim.advance(BattleSimulation.TICK_DT);

            assertEquals(1, sim.getAirLosses().size(),
                    "the entity is reaped at end of tick and the loss has to outlive it");
        }
    }

    /**
     * A marine who walked off the ramp is on the ground, and whatever happens to
     * the boat afterwards is not what killed them.
     */
    @Test
    void aBoatShotDownPartwayThroughUnloadingNamesOnlyTheSeatsStillFull() {
        try (BattleSimulation sim = openSimulation()) {
            FittedBoat boat = new FittedBoat(ShuttleType.VALKYRIE,
                    BoatFitting.STANDARD_PLATING, BoatFitting.STANDARD_DRIVE, "boat_03");
            long shuttle = sim.spawnShuttle(ShuttleType.VALKYRIE, boat, Faction.MARINE,
                    10.5f, 10.5f, -2f, 10.5f, 22f, 10.5f, 0f, 4);
            ShuttleMission mission = sim.world().mission(shuttle);
            mission.marineLoadout = passengers("soldier-a", "soldier-b", "soldier-c", "soldier-d");
            mission.deboardedThisSortie = 3;
            mission.marinesRemaining = 1;
            // The seats of a later cycle never left the ship, so they are
            // neither survivors nor casualties whatever happens to this sortie.
            mission.totalCycles = 2;
            mission.cycleLoadouts = new MarineLoadout[][]{
                    mission.marineLoadout, passengers("soldier-e", "soldier-f")};

            sim.getRoster().airTargets().destroy(shuttle);

            AirLoss loss = sim.getAirLosses().get(0);
            assertEquals(1, loss.passengersAboard());
            assertEquals(List.of("soldier-d"), loss.passengerSoldierIds());
        }
    }

    /**
     * A craft the campaign does not track people aboard — an employer's lander,
     * a padding sortie — still counts its dead without naming them.
     */
    @Test
    void anUnnamedPassengerIsStillCountedAsHavingGoneDown() {
        try (BattleSimulation sim = openSimulation()) {
            long shuttle = sim.spawnShuttle(ShuttleType.VALKYRIE, Faction.MARINE,
                    10.5f, 10.5f, -2f, 10.5f, 22f, 10.5f, 0f, 4);
            ShuttleMission mission = sim.world().mission(shuttle);
            mission.marineLoadout = new MarineLoadout[]{
                    MarineLoadout.COMBATANT, MarineLoadout.COMBATANT};
            mission.marinesRemaining = 2;

            sim.getRoster().airTargets().destroy(shuttle);

            AirLoss loss = sim.getAirLosses().get(0);
            assertSame(ShuttleType.VALKYRIE, loss.frame());
            assertEquals(2, loss.passengersAboard());
            assertEquals(List.of(), loss.passengerSoldierIds());
        }
    }

    private static MarineLoadout[] passengers(String... campaignSoldierIds) {
        MarineLoadout[] seats = new MarineLoadout[campaignSoldierIds.length];
        for (int seat = 0; seat < seats.length; seat++) {
            seats[seat] = new MarineLoadout(UnitRole.COMBATANT, null,
                    MarineLoadout.DEFAULT_PRIMARY_ID, null, null, null, 0,
                    campaignSoldierIds[seat], null);
        }
        return seats;
    }

    @Test
    void accountingUsesEmbarkedSeatsRatherThanPhysicalCapacity() {
        List<ShuttleAssignment> manifest = List.of(
                new ShuttleAssignment(ShuttleType.VALKYRIE, 2, 6),
                new ShuttleAssignment(ShuttleType.VALKYRIE, 2, 6));

        assertEquals(24, CampaignMarineDeployment.requiredSeats(manifest, 0));
        assertEquals(36f, BattleForceScore.attackers(manifest));
    }

    @Test
    void cyclingRestoresTheAuthoredPartialLoad() {
        try (BattleSimulation sim = openSimulation()) {
            long shuttle = sim.spawnShuttle(ShuttleType.VALKYRIE, Faction.MARINE,
                    10.5f, 10.5f, -2f, 10.5f, 22f, 10.5f, 0f, 6);
            ShuttleMission mission = sim.world().mission(shuttle);
            mission.totalCycles = 2;
            mission.cycleLoadouts = new MarineLoadout[][]{
                    new MarineLoadout[6], new MarineLoadout[1]};
            mission.marineLoadout = mission.cycleLoadouts[0];
            mission.currentCycle = 0;
            mission.marinesRemaining = 0;
            mission.state = ShuttleState.DEPARTING;
            sim.world().kinematics(shuttle).teleport(
                    mission.exitX, mission.exitY, 0f);

            sim.advance(BattleSimulation.TICK_DT);

            assertEquals(ShuttleState.PENDING, mission.state);
            assertEquals(6, mission.seatsPerSortie);
            assertEquals(1, mission.marinesRemaining);
            assertEquals(mission.rearmDelay, mission.pendingDelay);
        }
    }

    @Test
    void anUnloadedTransportDepartsEvenWhenItIsArmed() {
        try (BattleSimulation sim = openSimulation()) {
            long shuttle = landedArmedShuttle(sim, 8.5f);
            ShuttleMission mission = sim.world().mission(shuttle);

            sim.advance(BattleSimulation.TICK_DT);

            assertEquals(ShuttleState.DEPARTING, mission.state);
        }
    }

    @Test
    void arrivalMetadataDefaultsToLegacyNeutralValues() {
        try (BattleSimulation sim = openSimulation()) {
            long shuttle = sim.spawnShuttle(ShuttleType.KITE, Faction.MARINE,
                    10.5f, 10.5f, -2f, 10.5f, 22f, 10.5f, 0f);
            ShuttleMission mission = sim.world().mission(shuttle);

            assertEquals(-1, mission.manifestOrdinal);
            assertEquals(-1, mission.landingAreaId);
            assertEquals(-1, mission.arrivalGroupId);
            assertEquals(0, mission.expectedArrivalStrength);
        }
    }

    private static long landedArmedShuttle(BattleSimulation sim, float y) {
        long shuttle = sim.spawnShuttle(ShuttleType.VALKYRIE, Faction.MARINE,
                10.5f, y, -2f, y, 22f, y, 0f);
        ShuttleMission mission = sim.world().mission(shuttle);
        mission.state = ShuttleState.LANDED;
        mission.marinesRemaining = 0;
        mission.assignedRole = TurretRole.A2G;
        sim.world().attachAirTurrets(shuttle, new AirTurrets(new MountedTurret[0]));
        return shuttle;
    }

    private static long pairedLandedShuttle(BattleSimulation sim, float y,
                                             int arrivalGroupId) {
        long shuttle = sim.spawnShuttle(ShuttleType.VALKYRIE, Faction.MARINE,
                10.5f, y, -2f, y, 22f, y, 0f, 6);
        ShuttleMission mission = sim.world().mission(shuttle);
        mission.state = ShuttleState.LANDED;
        mission.arrivalGroupId = arrivalGroupId;
        mission.expectedArrivalStrength = 12;
        sim.world().kinematics(shuttle).teleport(10.5f, y, 0f);
        return shuttle;
    }

    private static BattleSimulation openSimulation() {
        NavigationGrid grid = new NavigationGrid(20, 20);
        for (int y = 0; y < 20; y++) {
            for (int x = 0; x < 20; x++) grid.setWalkableFloor(x, y);
        }
        return new BattleSimulation(grid, new CellTopology(20, 20));
    }

    private record WaitingObjective(Faction owningFaction) implements Objective {
        @Override public void tick(BattleView sim) {}
        @Override public boolean isComplete() { return false; }
        @Override public boolean isFailed() { return false; }
        @Override public String displayName() { return "Waiting"; }
    }
}
