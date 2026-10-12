package com.dillon.starsectormarines.battle.drone;

import com.dillon.starsectormarines.battle.decision.goap.WorldState;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A drone squad anchored to a moving carrier screens that point exactly as a hub squad screens its hub. */
class DroneAnchorTest {

    private static final class Carrier implements DroneAnchor {
        float x = 60f;
        float y = 60f;
        boolean alive = true;

        @Override public float anchorX() {
            return x;
        }

        @Override public float anchorY() {
            return y;
        }

        @Override public boolean anchorAlive() {
            return alive;
        }
    }

    private static BattleSimulation openArena(int w, int h) {
        NavigationGrid grid = new NavigationGrid(w, h);
        CellTopology topology = new CellTopology(w, h);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) grid.setWalkableFloor(x, y);
        }
        return new BattleSimulation(grid, topology);
    }

    @Test
    void anAnchoredSquadIsADroneSquadAndAHubSquadStillIs() {
        Squad squad = new Squad(0, Faction.MARINE);
        assertFalse(squad.isDroneSquad());
        squad.droneAnchor = new Carrier();
        assertTrue(squad.isDroneSquad());
        Squad hub = new Squad(1, Faction.DEFENDER);
        hub.droneHubId = 7L;
        assertTrue(hub.isDroneSquad());
    }

    @Test
    void theScreenGoalIsRelevantOnlyWhileTheAnchorLives() {
        BattleSimulation sim = openArena(80, 80);
        Carrier carrier = new Carrier();
        Squad squad = new Squad(0, Faction.MARINE);
        squad.droneAnchor = carrier;
        assertEquals(1f, DefendHubGoal.INSTANCE.relevance(WorldState.EMPTY, squad, sim), 1e-6f);
        carrier.alive = false;
        assertEquals(0f, DefendHubGoal.INSTANCE.relevance(WorldState.EMPTY, squad, sim), 1e-6f);
    }

    @Test
    void aHublessDroneRollsItsPatrolAroundTheAnchorNotAroundItself() {
        BattleSimulation sim = openArena(80, 80);
        Carrier carrier = new Carrier();
        int squadId = sim.mintSquad(Faction.MARINE, UnitType.DRONE);
        Squad squad = sim.getSquad(squadId);
        squad.droneAnchor = carrier;
        squad.aliveMembers = 1;
        long drone = sim.spawn(Drone.create("d0", Faction.MARINE, 10, 10, 0L).squad(squadId));

        DroneSwarmAction.INSTANCE.execute(drone, squad, sim);

        float gx = sim.droneState().patrolGoalX(drone);
        float gy = sim.droneState().patrolGoalY(drone);
        assertEquals(0f, (float) Math.max(0.0, Math.hypot(gx - carrier.x, gy - carrier.y) - Drone.PATROL_RADIUS_CELLS),
                1e-3f, "the waypoint lies inside the patrol radius of the carrier, not of the drone at (10, 10)");
    }

    @Test
    void theLeashFollowsTheCarrierWhenItMoves() {
        BattleSimulation sim = openArena(120, 80);
        Carrier carrier = new Carrier();
        int squadId = sim.mintSquad(Faction.MARINE, UnitType.DRONE);
        Squad squad = sim.getSquad(squadId);
        squad.droneAnchor = carrier;
        squad.aliveMembers = 1;
        long drone = sim.spawn(Drone.create("d0", Faction.MARINE, 60, 60, 0L).squad(squadId));
        DroneSwarmAction.INSTANCE.execute(drone, squad, sim);
        carrier.x = 100f;
        sim.droneState().setPatrolGoalX(drone, Float.NaN);
        sim.droneState().setPatrolGoalY(drone, Float.NaN);

        DroneSwarmAction.INSTANCE.execute(drone, squad, sim);

        assertTrue(sim.droneState().patrolGoalX(drone) > 100f - Drone.PATROL_RADIUS_CELLS - 1e-3f,
                "after the carrier moves east the next waypoint is rolled around where it now is");
    }
}
