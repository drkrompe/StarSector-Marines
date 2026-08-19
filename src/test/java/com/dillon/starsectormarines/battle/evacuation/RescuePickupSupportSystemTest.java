package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.air.ShuttleMission;
import com.dillon.starsectormarines.battle.air.MechSupportPayload;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.PointOfInterest;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RescuePickupSupportSystemTest {

    @Test
    void casualtiesDispatchCappedMilitiaShuttleWave() {
        BattleSimulation sim = simulation();
        CivilianEvacuationPayload payload = CivilianEvacuationPayload.install(
                sim, List.of(residential()), 901L);
        assertNotNull(payload);
        RescuePickupSupportSystem support = new RescuePickupSupportSystem(
                sim.getCivilianEvacuationTracker());
        assertTrue(support.configure(payload.placement,
                10.5f, 10.5f, -6f, 10.5f, -10f, 10.5f, 901L, sim));
        assertEquals(0, support.liveGuardCount(sim));
        assertInitialSorties(sim, MechVariant.SIROCCO);
        advanceSeconds(sim,
                RescuePickupSupportSystem.INITIAL_ARRIVAL_DELAY_SECONDS - 1f);
        assertEquals(0, support.liveGuardCount(sim),
                "the pickup line stays off-map through the opening grace period");
        assertEquals(0, countPickupMechs(sim));
        advanceUntilInitialSupport(sim, support);
        assertEquals(RescuePickupSupportSystem.TARGET_GUARDS,
                support.liveGuardCount(sim));

        List<Long> guards = new ArrayList<>();
        for (int i = 0; i < sim.liveUnitCount(); i++) {
            long unit = sim.liveUnitAt(i);
            if (sim.identity().faction(unit) == Faction.MARINE
                    && sim.identity().type(unit) == UnitType.MILITIA) guards.add(unit);
        }
        for (int i = 0; i < 4; i++) sim.releaseFromRegistry(guards.get(i));

        support.tick(RescuePickupSupportSystem.WAVE_INTERVAL_SECONDS, sim);

        assertEquals(4, support.liveGuardCount(sim));
        int transports = 0;
        for (long id : sim.getAirEntityIds()) {
            ShuttleMission mission = sim.world().mission(id);
            if (!mission.rescueMilitiaTransport
                    || mission.marinesRemaining != 4) continue;
            transports++;
            assertEquals(4, mission.marinesRemaining);
            assertEquals(UnitType.MILITIA, mission.deboardUnitType);
            assertEquals(payload.placement.liftX, mission.rescueGuardX);
            assertEquals(payload.placement.liftY, mission.rescueGuardY);
        }
        assertEquals(1, transports);
    }

    @Test
    void seedSelectsOnePickupMechWithoutChangingMilitiaStrength() {
        BattleSimulation bulwarkSim = configuredSimulation(902L);
        BattleSimulation siroccoSim = configuredSimulation(903L);

        assertEquals(RescuePickupSupportSystem.TARGET_GUARDS,
                countMilitia(bulwarkSim));
        assertEquals(RescuePickupSupportSystem.TARGET_GUARDS,
                countMilitia(siroccoSim));
        assertEquals(MechVariant.BULWARK, pickupMech(bulwarkSim));
        assertEquals(MechVariant.SIROCCO, pickupMech(siroccoSim));
    }

    private static BattleSimulation configuredSimulation(long seed) {
        BattleSimulation sim = simulation();
        CivilianEvacuationPayload payload = CivilianEvacuationPayload.install(
                sim, List.of(residential()), seed);
        assertNotNull(payload);
        RescuePickupSupportSystem support = new RescuePickupSupportSystem(
                sim.getCivilianEvacuationTracker());
        assertTrue(support.configure(payload.placement,
                10.5f, 10.5f, -6f, 10.5f, -10f, 10.5f, seed, sim));
        assertEquals(0, support.liveGuardCount(sim));
        advanceUntilInitialSupport(sim, support);
        assertEquals(RescuePickupSupportSystem.TARGET_GUARDS,
                support.liveGuardCount(sim));
        return sim;
    }

    private static int countMilitia(BattleSimulation sim) {
        int count = 0;
        for (int i = 0; i < sim.liveUnitCount(); i++) {
            long unit = sim.liveUnitAt(i);
            if (sim.identity().faction(unit) == Faction.MARINE
                    && sim.identity().type(unit) == UnitType.MILITIA) count++;
        }
        return count;
    }

    private static MechVariant pickupMech(BattleSimulation sim) {
        MechVariant variant = null;
        int count = 0;
        for (int i = 0; i < sim.liveUnitCount(); i++) {
            long unit = sim.liveUnitAt(i);
            if (sim.identity().faction(unit) != Faction.MARINE
                    || sim.identity().type(unit) != UnitType.HEAVY_MECH) continue;
            count++;
            variant = sim.identity().mechVariant(unit);
            assertTrue(sim.squadOf(unit).rescuePickupGuard);
        }
        assertEquals(RescuePickupSupportSystem.PICKUP_MECHS, count);
        return variant;
    }

    private static BattleSimulation simulation() {
        NavigationGrid grid = new NavigationGrid(26, 22);
        for (int y = 0; y < grid.getHeight(); y++) {
            for (int x = 0; x < grid.getWidth(); x++) grid.setWalkableFloor(x, y);
        }
        BattleSimulation sim = new BattleSimulation(
                grid, new CellTopology(26, 22));
        sim.spawn(new EntitySpec(
                "battle anchor", Faction.DEFENDER,
                UnitType.HEAVY_MECH, 25, 21));
        return sim;
    }

    private static void assertInitialSorties(BattleSimulation sim,
                                               MechVariant expectedVariant) {
        int militiaSorties = 0;
        int mechSorties = 0;
        for (long id : sim.getAirEntityIds()) {
            ShuttleMission mission = sim.world().mission(id);
            if (mission.rescueMilitiaTransport) {
                militiaSorties++;
                assertEquals(4, mission.marinesRemaining);
                assertTrue(mission.pendingDelay
                        >= RescuePickupSupportSystem.INITIAL_ARRIVAL_DELAY_SECONDS);
            }
            if (mission.rescuePickupMechTransport) {
                mechSorties++;
                assertEquals(MechSupportPayload.INSTANCE, mission.payload);
                assertEquals(expectedVariant, mission.mechVariant);
                assertTrue(mission.pendingDelay
                        > RescuePickupSupportSystem.INITIAL_ARRIVAL_DELAY_SECONDS);
            }
        }
        assertEquals(2, militiaSorties);
        assertEquals(1, mechSorties);
    }

    private static void advanceUntilInitialSupport(
            BattleSimulation sim, RescuePickupSupportSystem support) {
        for (int tick = 0; tick < 2_000
                && (support.liveGuardCount(sim)
                < RescuePickupSupportSystem.TARGET_GUARDS
                || countPickupMechs(sim) < RescuePickupSupportSystem.PICKUP_MECHS);
             tick++) {
            sim.advance(BattleSimulation.TICK_DT);
        }
        assertEquals(RescuePickupSupportSystem.TARGET_GUARDS,
                support.liveGuardCount(sim));
        assertEquals(RescuePickupSupportSystem.PICKUP_MECHS,
                countPickupMechs(sim));
    }

    private static void advanceSeconds(BattleSimulation sim, float seconds) {
        int ticks = (int) Math.floor(seconds / BattleSimulation.TICK_DT);
        for (int tick = 0; tick < ticks; tick++) {
            sim.advance(BattleSimulation.TICK_DT);
        }
    }

    private static int countPickupMechs(BattleSimulation sim) {
        int count = 0;
        for (int i = 0; i < sim.liveUnitCount(); i++) {
            long unit = sim.liveUnitAt(i);
            if (sim.identity().faction(unit) == Faction.MARINE
                    && sim.identity().type(unit) == UnitType.HEAVY_MECH
                    && sim.squad().hasSquad(unit)
                    && sim.squadOf(unit).rescuePickupGuard) count++;
        }
        return count;
    }

    private static PointOfInterest residential() {
        return new PointOfInterest(PointOfInterest.Kind.RESIDENTIAL,
                10, 8, 14, 12, 12, 10, 12, 10);
    }
}
