package com.dillon.starsectormarines.battle.drone;

import com.dillon.starsectormarines.battle.air.AirBody;
import com.dillon.starsectormarines.battle.air.components.CrashingComponent;
import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.Entity;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.engine.ecs.ArchetypeTable;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end coverage for the drone crash modelled as a component: a dead
 * {@link Drone} gets a {@code CrashingComponent} component attached off its DeathEvent,
 * the crash system processes the component-set (not a units-list scan) to spin
 * + count down the fall, and on impact drops a smoking wreck and detaches the
 * component.
 *
 * <p>A live MARINE and a live (inert) DEFENDER hub are parked far apart so the
 * battle stays in progress across the ~0.7s crash window — otherwise the
 * win-check would complete once the target drone (the only other DEFENDER unit)
 * dies and {@code advance()} would no-op before the crash settles.
 */
public class DroneCrashSystemTest {

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

    /** A drone homed to a hub kept alive far away — so killing the drone doesn't end the battle. */
    private static Drone parkArenaWithDrone(BattleSimulation sim) {
        sim.addUnit(new Entity("m0", Faction.MARINE, UnitType.MARINE, 1, 1));
        DroneHubUnit keepAlive = new DroneHubUnit("hub", Faction.DEFENDER, 38, 38);
        sim.addUnit(keepAlive);
        Drone drone = new Drone("d0", Faction.DEFENDER, 20, 20, keepAlive);
        sim.addUnit(drone);
        return drone;
    }

    /** Whether {@code id} carries the world's CRASHING component (the post-fold presence check). */
    private static boolean isCrashing(BattleSimulation sim, long id) {
        return sim.getEntityWorld().has(id, sim.getBattleComponents().CRASHING);
    }

    /** Whether {@code id} carries the world's KINEMATICS component (the drone's flight body). */
    private static boolean hasKinematics(BattleSimulation sim, long id) {
        return sim.getEntityWorld().has(id, sim.getBattleComponents().KINEMATICS);
    }

    /** Total rows across the CRASHING query — the world equivalent of the old store's size. */
    private static int crashingCount(BattleSimulation sim) {
        int n = 0;
        for (ArchetypeTable t : sim.getEntityWorld().matched(sim.getBattleComponents().crashing)) {
            n += t.rowCount();
        }
        return n;
    }

    @Test
    public void shotDownDroneGetsACrashingComponentThenSettlesIntoAWreck() {
        BattleSimulation sim = openArena(40, 40);
        Drone drone = parkArenaWithDrone(sim);
        int wrecksBefore = sim.getSmokingWrecks().size();

        sim.applyDamage(drone, 100_000f, 20f, 20f);
        assertFalse(sim.world().isAlive(drone.entityId), "lethal hit kills the drone");
        // Buffered: no component until the death mailbox drains in the tick.
        assertFalse(isCrashing(sim, drone.entityId),
                "the CrashingComponent component attaches on the death drain, not inline");

        sim.advance(BattleSimulation.TICK_DT);
        assertTrue(isCrashing(sim, drone.entityId),
                "drain → the dead drone gets a CrashingComponent component (the crash started)");
        assertEquals(wrecksBefore, sim.getSmokingWrecks().size(),
                "no wreck yet — the drone is still falling");

        // Run past the crash window: the fall settles into a wreck and the
        // component is detached.
        for (int i = 0; i < 30; i++) {
            sim.advance(BattleSimulation.TICK_DT);
        }
        assertFalse(isCrashing(sim, drone.entityId),
                "on impact the CrashingComponent component is detached (crash done)");
        assertTrue(sim.getSmokingWrecks().size() > wrecksBefore,
                "a smoking wreck is dropped at the impact site");
    }

    @Test
    public void liveDroneCarriesKinematicsAndTheCrashDetachesIt() {
        BattleSimulation sim = openArena(40, 40);
        Drone drone = parkArenaWithDrone(sim);

        // Alive: the drone flies, so it carries a KINEMATICS body — the SAME
        // instance its ctor positioned at the spawn cell center (20.5, 20.5),
        // now world-resident and read by id.
        assertTrue(hasKinematics(sim, drone.entityId), "a live drone has KINEMATICS");
        AirBody body = sim.world().kinematics(drone.entityId);
        assertNotNull(body, "KINEMATICS round-trips the seeded AirBody");
        assertEquals(20.5f, body.x, 1e-4f, "body seeded at the spawn cell center x");
        assertEquals(20.5f, body.y, 1e-4f, "body seeded at the spawn cell center y");

        // Kill it. On the death drain the crash handler reads the body off
        // KINEMATICS (it rode the corpse transmute, off the corpseRemove mask) to
        // seed the CrashingComponent, then DETACHES KINEMATICS — the body's
        // lifecycle has moved to the crash component (a corpse doesn't fly).
        sim.applyDamage(drone, 100_000f, 20f, 20f);
        sim.advance(BattleSimulation.TICK_DT);
        assertTrue(isCrashing(sim, drone.entityId), "the dead drone is crashing");
        assertFalse(hasKinematics(sim, drone.entityId),
                "KINEMATICS detached on crash — the CrashingComponent owns the body now");

        // Read-then-detach identity: the crash took over the EXACT body instance,
        // not a copy — so the fall animates from where the drone actually was.
        CrashingComponent crash = (CrashingComponent) sim.getEntityWorld().getObject(
                drone.entityId, sim.getBattleComponents().CRASHING, BattleComponents.CRASHING_STATE);
        assertSame(body, crash.body, "the crash reuses the drone's body instance, not a copy");
    }

    @Test
    public void liveDroneNeverGetsACrashingComponent() {
        BattleSimulation sim = openArena(40, 40);
        Drone drone = parkArenaWithDrone(sim);

        for (int i = 0; i < 5; i++) {
            sim.advance(BattleSimulation.TICK_DT);
        }

        assertTrue(sim.world().isAlive(drone.entityId), "no damage → still flying");
        assertFalse(isCrashing(sim, drone.entityId), "a live drone is never crashing");
        assertEquals(0, crashingCount(sim), "no crash components for an undamaged arena");
    }
}
