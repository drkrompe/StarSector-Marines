package com.dillon.starsectormarines.battle.drone;

import com.dillon.starsectormarines.battle.air.AirBody;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A drone squad whose anchor says it is an escort loiters in formation, obeys the carrier's orders and keeps off its wingmates. */
class EscortAnchorTest {

    private static final class Carrier implements DroneAnchor {
        float x = 60f;
        float y = 60f;
        float[] goal;
        long[] ids = NO_IDS;
        float[] at = NO_POSITIONS;

        @Override public float anchorX() {
            return x;
        }

        @Override public float anchorY() {
            return y;
        }

        @Override public boolean anchorAlive() {
            return true;
        }

        @Override public boolean escort() {
            return true;
        }

        @Override public float leashCells() {
            return 20f;
        }

        @Override public long[] escortIds() {
            return ids;
        }

        @Override public float[] escortPositions() {
            return at;
        }

        @Override public float[] sortieGoal(long member) {
            return goal;
        }
    }

    private static BattleSimulation arena() {
        NavigationGrid grid = new NavigationGrid(140, 140);
        CellTopology topology = new CellTopology(140, 140);
        for (int y = 0; y < 140; y++) {
            for (int x = 0; x < 140; x++) grid.setWalkableFloor(x, y);
        }
        return new BattleSimulation(grid, topology);
    }

    private static long drone(BattleSimulation sim, Squad squad, int squadId, String name, int cx, int cy) {
        squad.aliveMembers++;
        return sim.spawn(Drone.create(name, Faction.MARINE, cx, cy, 0L).squad(squadId));
    }

    @Test
    void anEscortAtEaseSettlesOnTheCarriersRingRatherThanRollingRandomWaypoints() {
        BattleSimulation sim = arena();
        Carrier carrier = new Carrier();
        int squadId = sim.mintSquad(Faction.MARINE, UnitType.DRONE);
        Squad squad = sim.getSquad(squadId);
        squad.droneAnchor = carrier;
        long id = drone(sim, squad, squadId, "d0", 90, 90);
        carrier.ids = new long[]{id};

        for (int i = 0; i < 900; i++) DroneSwarmAction.INSTANCE.execute(id, squad, sim);

        AirBody body = sim.world().kinematics(id);
        assertTrue(body.distanceTo(carrier.x, carrier.y) < EscortFormation.EASE_RADIUS + EscortFormation.BREATH
                + EscortFormation.LEAD_CELLS + 2.5f, "ended " + body.distanceTo(carrier.x, carrier.y) + " out");
        assertTrue(Float.isNaN(sim.droneState().patrolGoalX(id)), "no random waypoint was rolled");
    }

    @Test
    void aSortieGoalOverridesTheFormationUntilTheCarrierWithdrawsIt() {
        BattleSimulation sim = arena();
        Carrier carrier = new Carrier();
        int squadId = sim.mintSquad(Faction.MARINE, UnitType.DRONE);
        Squad squad = sim.getSquad(squadId);
        squad.droneAnchor = carrier;
        long id = drone(sim, squad, squadId, "d0", 60, 60);
        carrier.ids = new long[]{id};
        carrier.goal = new float[]{100f, 60f};

        for (int i = 0; i < 450; i++) DroneSwarmAction.INSTANCE.execute(id, squad, sim);

        AirBody body = sim.world().kinematics(id);
        assertTrue(body.x > 95f, "flew out to the goal, x=" + body.x);
        assertEquals(60f, body.y, 3f);
    }

    @Test
    void anEscortSeparatesFromAWingmateOnTheSamePoint() {
        BattleSimulation sim = arena();
        Carrier carrier = new Carrier();
        int squadId = sim.mintSquad(Faction.MARINE, UnitType.DRONE);
        Squad squad = sim.getSquad(squadId);
        squad.droneAnchor = carrier;
        long a = drone(sim, squad, squadId, "a", 60, 60);
        long b = drone(sim, squad, squadId, "b", 60, 60);
        carrier.ids = new long[]{a, b};
        for (int i = 0; i < 600; i++) {
            carrier.at = new float[]{sim.world().x(a), sim.world().y(a), sim.world().x(b), sim.world().y(b)};
            DroneSwarmAction.INSTANCE.execute(a, squad, sim);
            DroneSwarmAction.INSTANCE.execute(b, squad, sim);
        }
        double gap = Math.hypot(sim.world().x(a) - sim.world().x(b), sim.world().y(a) - sim.world().y(b));
        assertTrue(gap > 1.5, "the two ended " + gap + " apart");
    }

    @Test
    void aPlainAnchorStillRollsPatrolWaypointsAsAHubSquadDoes() {
        BattleSimulation sim = arena();
        DroneAnchor plain = new DroneAnchor() {
            @Override public float anchorX() {
                return 60f;
            }

            @Override public float anchorY() {
                return 60f;
            }

            @Override public boolean anchorAlive() {
                return true;
            }
        };
        int squadId = sim.mintSquad(Faction.MARINE, UnitType.DRONE);
        Squad squad = sim.getSquad(squadId);
        squad.droneAnchor = plain;
        long id = drone(sim, squad, squadId, "d0", 60, 60);

        DroneSwarmAction.INSTANCE.execute(id, squad, sim);

        assertTrue(!Float.isNaN(sim.droneState().patrolGoalX(id)));
        assertEquals(Drone.ENGAGE_LEASH_RADIUS_CELLS, plain.leashCells(), 1e-6f);
    }
}
