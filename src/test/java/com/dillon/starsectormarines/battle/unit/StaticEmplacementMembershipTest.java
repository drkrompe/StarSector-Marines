package com.dillon.starsectormarines.battle.unit;

import com.dillon.starsectormarines.battle.combat.HitResponseService;
import com.dillon.starsectormarines.battle.drone.DroneHubUnit;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.turret.MapTurret;
import com.dillon.starsectormarines.battle.turret.TurretKind;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

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
 * ({@link HitResponseService#rollFallbackOnHit}, called whenever any unit takes
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
        Entity marine = new Entity("m", Faction.MARINE, UnitType.MARINE, 2, 2);
        MapTurret turret = new MapTurret("t", Faction.DEFENDER, TurretKind.VULCAN, 21, 21);
        DroneHubUnit hub = new DroneHubUnit("h", Faction.DEFENDER, 21, 2);
        sim.addUnit(marine);
        sim.addUnit(turret);
        sim.addUnit(hub);

        assertTrue(sim.world().hasMovement(marine.entityId));
        assertTrue(sim.world().hasAiState(marine.entityId));
        assertFalse(sim.world().hasMovement(turret.entityId));
        assertFalse(sim.world().hasAiState(turret.entityId));
        assertFalse(sim.world().hasMovement(hub.entityId));
        assertFalse(sim.world().hasAiState(hub.entityId));
    }

    @Test
    public void fullTickWithStaticEmplacementsPresentDoesNotFailLoud() {
        BattleSimulation sim = openSim();
        Entity marine = new Entity("m", Faction.MARINE, UnitType.MARINE, 2, 2);
        int sid = sim.mintSquad(Faction.MARINE, marine);
        marine.squadId = sid;
        MapTurret turret = new MapTurret("t", Faction.DEFENDER, TurretKind.VULCAN, 21, 21);
        DroneHubUnit hub = new DroneHubUnit("h", Faction.DEFENDER, 21, 2);
        sim.addUnit(marine);
        sim.addUnit(turret);
        sim.addUnit(hub);

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
    public void rollFallbackOnHitSkipsStaticEmplacements() {
        BattleSimulation sim = openSim();
        HitResponseService hitResponse = sim.getHitResponseService();
        MapTurret turret = new MapTurret("t", Faction.DEFENDER, TurretKind.VULCAN, 8, 8);
        DroneHubUnit hub = new DroneHubUnit("h", Faction.DEFENDER, 9, 9);
        sim.addUnit(turret);
        sim.addUnit(hub);
        sim.addUnit(new Entity("opp", Faction.MARINE, UnitType.MARINE, 2, 2));

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
