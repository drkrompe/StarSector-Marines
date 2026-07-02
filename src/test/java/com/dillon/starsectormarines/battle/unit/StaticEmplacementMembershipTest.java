package com.dillon.starsectormarines.battle.unit;

import com.dillon.starsectormarines.battle.combat.HitResponseSystem;
import com.dillon.starsectormarines.battle.drone.Drone;
import com.dillon.starsectormarines.battle.drone.DroneHub;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.turret.MapTurret;
import com.dillon.starsectormarines.battle.turret.TurretKind;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runtime coverage for the MOVEMENT / AI_STATE membership narrowing: static
 * emplacements (turrets, drone hubs) carry neither component, so the
 * cross-cutting all-unit readers that run every tick must tolerate their
 * absence rather than fail-loud on the now-missing component.
 *
 * <p>A turret + hub are exercised two ways: present through a full
 * {@link BattleSimulation#advance} loop (which drives {@code rebuildOccupancyMap},
 * the destination-index rebuild, and the parallel per-unit dispatch — every one
 * an all-live-units walk), and through the per-hit fall-back roll
 * ({@link HitResponseSystem#rollFallbackOnHit}, called whenever any unit takes
 * damage). Each of those read a MOVEMENT/AI_STATE field unconditionally before
 * the narrowing.
 */
public class StaticEmplacementMembershipTest {

    private static final int W = 24;
    private static final int H = 24;

    private static BattleSimulation openSim() {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) grid.setWalkableFloor(x, y);
        }
        return new BattleSimulation(grid, new CellTopology(W, H));
    }

    @Test
    public void mobileUnitsAreMoversAndThinkersStaticEmplacementsAreNeither() {
        BattleSimulation sim = openSim();
        Entity marine = sim.spawn(new EntitySpec("m", Faction.MARINE, UnitType.MARINE, 2, 2));
        Entity turret = sim.spawn(MapTurret.create("t", Faction.DEFENDER, TurretKind.VULCAN, 21, 21));
        Entity hub = sim.spawn(DroneHub.create("h", Faction.DEFENDER, 21, 2));

        assertTrue(sim.world().hasMovement(marine.entityId));
        assertTrue(sim.world().hasAiState(marine.entityId));
        assertFalse(sim.world().hasMovement(turret.entityId));
        assertFalse(sim.world().hasAiState(turret.entityId));
        assertFalse(sim.world().hasMovement(hub.entityId));
        assertFalse(sim.world().hasAiState(hub.entityId));

        // The drone hub carries HUB_STATE (presence IS "is a live drone hub");
        // the turret and marine — neither a drone hub — don't.
        assertTrue(sim.hubState().isHub(hub.entityId));
        assertFalse(sim.hubState().isHub(turret.entityId));
        assertFalse(sim.hubState().isHub(marine.entityId));
        assertEquals(DroneHub.INITIAL_SPAWN_DELAY_SEC, sim.hubState().spawnCooldown(hub.entityId), 1e-4f);
        assertEquals(-1, sim.hubState().droneSquadId(hub.entityId));

        // The turret carries TURRET_STATE (presence IS "is a live turret"),
        // seeded with its TurretKind and a recoilTimer past the renderer's
        // recoil window; the hub and marine — neither a turret — don't.
        assertTrue(sim.turretState().isTurret(turret.entityId));
        assertFalse(sim.turretState().isTurret(hub.entityId));
        assertFalse(sim.turretState().isTurret(marine.entityId));
        assertEquals(TurretKind.VULCAN, sim.turretState().kind(turret.entityId));
        assertEquals(1f, sim.turretState().recoilTimer(turret.entityId), 1e-4f);
    }

    @Test
    public void fullTickWithStaticEmplacementsPresentDoesNotFailLoud() {
        BattleSimulation sim = openSim();
        Entity marine = new Entity("m", Faction.MARINE, UnitType.MARINE, 2, 2);
        int sid = sim.mintSquad(Faction.MARINE, marine);
        marine.seedSquadId = sid;
        sim.addUnit(marine);
        Entity turret = sim.spawn(MapTurret.create("t", Faction.DEFENDER, TurretKind.VULCAN, 21, 21));
        Entity hub = sim.spawn(DroneHub.create("h", Faction.DEFENDER, 21, 2));

        // Each advance() drives rebuildOccupancyMap + the destination-index
        // rebuild and the parallel per-unit dispatch — all walks over every live
        // unit, the component-less turret and hub included. Spaced far apart so
        // nothing dies over the loop, keeping the post-tick assertions meaningful.
        for (int i = 0; i < 15; i++) sim.advance(BattleSimulation.TICK_DT);

        // The emplacements were never accidentally granted the mover/thinker
        // components, and the marine kept its.
        assertTrue(sim.world().hasMovement(marine.entityId));
        assertTrue(sim.world().hasAiState(marine.entityId));
        assertFalse(sim.world().hasMovement(turret.entityId));
        assertFalse(sim.world().hasAiState(hub.entityId));
    }

    @Test
    public void droneCarriesDroneStateSeededToNaNGoalsAndItsHomeHubId() {
        BattleSimulation sim = openSim();
        Entity hub = sim.spawn(DroneHub.create("h", Faction.DEFENDER, 5, 5));
        Entity drone = sim.spawn(Drone.create("d", Faction.DEFENDER, 10, 10, hub.entityId));
        Entity marine = sim.spawn(new EntitySpec("m", Faction.MARINE, UnitType.MARINE, 2, 2));
        Entity turret = sim.spawn(MapTurret.create("t", Faction.DEFENDER, TurretKind.VULCAN, 21, 21));

        // The drone carries DRONE_STATE (presence IS "is a live drone"); the hub,
        // marine, and turret — none a drone — don't.
        assertTrue(sim.droneState().isDrone(drone.entityId));
        assertFalse(sim.droneState().isDrone(hub.entityId));
        assertFalse(sim.droneState().isDrone(marine.entityId));
        assertFalse(sim.droneState().isDrone(turret.entityId));

        // Patrol/pursuit goals seed to NaN ("no waypoint yet" — DroneSwarmAction's
        // ensureSectorWaypoint gates on this); pursuitTimer rides the zero-init
        // (0f = latch expired); homeHubId seeds from the id passed to create().
        assertTrue(Float.isNaN(sim.droneState().patrolGoalX(drone.entityId)), "patrolGoalX seeds to NaN");
        assertTrue(Float.isNaN(sim.droneState().patrolGoalY(drone.entityId)), "patrolGoalY seeds to NaN");
        assertTrue(Float.isNaN(sim.droneState().pursuitGoalX(drone.entityId)), "pursuitGoalX seeds to NaN");
        assertTrue(Float.isNaN(sim.droneState().pursuitGoalY(drone.entityId)), "pursuitGoalY seeds to NaN");
        assertEquals(0f, sim.droneState().pursuitTimer(drone.entityId), 1e-4f, "pursuitTimer seeds to 0 (latch expired)");
        assertEquals(hub.entityId, sim.droneState().homeHubId(drone.entityId), "homeHubId seeds from the passed hub id");
    }

    @Test
    public void rollFallbackOnHitSkipsStaticEmplacements() {
        BattleSimulation sim = openSim();
        HitResponseSystem hitResponse = sim.getHitResponseSystem();
        Entity turret = sim.spawn(MapTurret.create("t", Faction.DEFENDER, TurretKind.VULCAN, 8, 8));
        Entity hub = sim.spawn(DroneHub.create("h", Faction.DEFENDER, 9, 9));
        sim.spawn(new EntitySpec("opp", Faction.MARINE, UnitType.MARINE, 2, 2));

        // A static emplacement takes damage like anything else, but has no
        // AI_STATE — the fall-back roll must skip it before the fail-loud
        // fallbackTimer read. (Pre-narrowing a hub could roll a fall-back it had
        // no behavior to execute; now neither emplacement can.)
        for (int i = 0; i < 100; i++) {
            hitResponse.rollFallbackOnHit(turret);
            hitResponse.rollFallbackOnHit(hub);
        }

        assertFalse(sim.world().hasAiState(turret.entityId));
        assertFalse(sim.world().hasAiState(hub.entityId));
    }
}
