package com.dillon.starsectormarines.battle.air;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.setup.BattleForceScore;
import com.dillon.starsectormarines.battle.command.objective.Objective;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.turret.TurretRole;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.ops.detachment.CampaignMarineDeployment;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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

        assertEquals(ShuttleType.VALKYRIE.capacity, full.seatsPerSortie);
        assertEquals(6, half.seatsPerSortie);
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
            mission.currentCycle = 0;
            mission.marinesRemaining = 0;
            mission.state = ShuttleState.DEPARTING;
            sim.world().kinematics(shuttle).teleport(
                    mission.exitX, mission.exitY, 0f);

            sim.advance(BattleSimulation.TICK_DT);

            assertEquals(ShuttleState.PENDING, mission.state);
            assertEquals(6, mission.seatsPerSortie);
            assertEquals(6, mission.marinesRemaining);
            assertEquals(mission.rearmDelay, mission.pendingDelay);
        }
    }

    @Test
    void explicitDepartSkipsLegacyArmedLoiter() {
        try (BattleSimulation sim = openSimulation()) {
            long departing = landedArmedShuttle(sim, 8.5f);
            ShuttleMission departingMission = sim.world().mission(departing);
            departingMission.postDeliveryDisposition = PostDeliveryDisposition.DEPART;

            long loitering = landedArmedShuttle(sim, 12.5f);
            ShuttleMission loiteringMission = sim.world().mission(loitering);

            sim.advance(BattleSimulation.TICK_DT);

            assertEquals(ShuttleState.DEPARTING, departingMission.state);
            assertEquals(ShuttleState.HOVER_STATION, loiteringMission.state);
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
        mission.postDeliveryDisposition = PostDeliveryDisposition.DEPART;
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
