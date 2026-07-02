package com.dillon.starsectormarines.battle.drone;

import com.dillon.starsectormarines.battle.decision.goap.ActionStatus;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.Entity;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Coverage for {@link DroneSwarmAction}'s ferry between a drone's
 * {@code DRONE_STATE} component and the per-tick engage/pursue/patrol
 * dispatch — the behavior-relocation half of slice B3 (the {@link Drone}
 * dissolution). No squad plan / slot machinery is stood up (a bare
 * {@link Squad} with no {@code currentPlan} is enough — {@code resolveSlotIndex}/
 * {@code slotMemberCount} both fall back to slot 0 of 1), so these exercise the
 * patrol and pursuit branches directly against an empty arena (no targets, so
 * {@code TurretAim}/the agro scan never latch a fresh target — the branch
 * choice is driven entirely by the seeded/pre-set {@code DRONE_STATE}).
 */
public class DroneSwarmActionTest {

    private static BattleSimulation openArena(int w, int h) {
        NavigationGrid grid = new NavigationGrid(w, h);
        CellTopology topology = new CellTopology(w, h);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                grid.setWalkableFloor(x, y);
            }
        }
        return new BattleSimulation(grid, topology);
    }

    @Test
    public void patrolBranchPicksASectorWaypointWhenNoneIsSetYet() {
        BattleSimulation sim = openArena(60, 60);
        Entity hub = DroneHub.create("h0", Faction.DEFENDER, 30, 30).toEntity();
        sim.addUnit(hub);
        Entity drone = Drone.create("d0", Faction.DEFENDER, 30, 20, hub.entityId).toEntity();
        sim.addUnit(drone);
        Squad squad = new Squad(0, Faction.DEFENDER);
        squad.aliveMembers = 1;
        squad.droneHubId = hub.entityId;

        // Freshly allocated: patrol goal seeds to NaN (no waypoint yet), no
        // target, no pursuit latch — an empty arena means neither TurretAim
        // nor the agro scan latch anything, so execute must fall through to
        // the patrol branch and roll a fresh sector waypoint.
        assertTrue(Float.isNaN(sim.droneState().patrolGoalX(drone.entityId)));

        ActionStatus status = DroneSwarmAction.INSTANCE.execute(drone, squad, sim);

        assertEquals(ActionStatus.RUNNING, status, "the swarm action runs perpetually");
        assertFalse(Float.isNaN(sim.droneState().patrolGoalX(drone.entityId)),
                "patrol branch rolls a fresh waypoint off the NaN sentinel");
        assertFalse(Float.isNaN(sim.droneState().patrolGoalY(drone.entityId)));
        assertEquals(0f, sim.droneState().pursuitTimer(drone.entityId), 1e-4f,
                "no target and no prior latch — pursuit timer stays at 0");
    }

    @Test
    public void nonDroneMemberFailsImmediately() {
        BattleSimulation sim = openArena(20, 20);
        Entity marine = new Entity("m0", Faction.MARINE, UnitType.MARINE, 2, 2);
        sim.addUnit(marine);
        Squad squad = new Squad(0, Faction.MARINE);

        ActionStatus status = DroneSwarmAction.INSTANCE.execute(marine, squad, sim);

        assertEquals(ActionStatus.FAILURE, status, "a non-drone member is rejected via the type-tag gate");
    }

    @Test
    public void pursuitBranchDecaysTheLatchWhenNothingRefreshesItThisTick() {
        BattleSimulation sim = openArena(60, 60);
        Entity hub = DroneHub.create("h0", Faction.DEFENDER, 30, 30).toEntity();
        sim.addUnit(hub);
        Entity drone = Drone.create("d0", Faction.DEFENDER, 30, 20, hub.entityId).toEntity();
        sim.addUnit(drone);
        Squad squad = new Squad(0, Faction.DEFENDER);
        squad.aliveMembers = 1;
        squad.droneHubId = hub.entityId;

        // Manually latch a pursuit goal + timer, as tryAgroScan/TurretAim would
        // on a real contact — an empty arena has no enemies to source a fresh
        // latch, so this exercises the pursue branch's decay in isolation.
        sim.droneState().setPursuitGoalX(drone.entityId, 32f);
        sim.droneState().setPursuitGoalY(drone.entityId, 20f);
        sim.droneState().setPursuitTimer(drone.entityId, Drone.PURSUIT_LATCH_SECONDS);

        DroneSwarmAction.INSTANCE.execute(drone, squad, sim);

        assertEquals(Drone.PURSUIT_LATCH_SECONDS - BattleSimulation.TICK_DT,
                sim.droneState().pursuitTimer(drone.entityId), 1e-4f,
                "no fresh target this tick — the latch just decays by one TICK_DT");
    }
}
