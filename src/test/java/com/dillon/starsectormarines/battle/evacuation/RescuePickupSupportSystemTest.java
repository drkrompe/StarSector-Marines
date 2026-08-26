package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.air.ShuttleMission;
import com.dillon.starsectormarines.battle.air.MechSupportPayload;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.infantry.MarineLoadout;
import com.dillon.starsectormarines.battle.weapon.WeaponDef;
import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.PointOfInterest;
import com.dillon.starsectormarines.ops.RiskLevel;
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
        holdEvacuationOpen(sim, payload);
        RescuePickupSupportSystem support = new RescuePickupSupportSystem(
                sim.getCivilianEvacuationTracker());
        assertTrue(support.configure(payload.placement,
                10.5f, 10.5f, -6f, 10.5f, -10f, 10.5f,
                901L, RiskLevel.LOW, sim));
        assertEquals(0, support.liveGuardCount(sim));
        assertInitialSorties(sim, payload.placement, MechVariant.SIROCCO);
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
        for (int i = 0; i < 5; i++) sim.releaseFromRegistry(guards.get(i));

        support.tick(RescuePickupSupportSystem.WAVE_INTERVAL_SECONDS, sim);

        assertEquals(15, support.liveGuardCount(sim));
        int transports = 0;
        for (long id : sim.getAirEntityIds()) {
            ShuttleMission mission = sim.world().mission(id);
            if (!mission.rescueMilitiaTransport
                    || mission.marinesRemaining != 4) continue;
            transports++;
            assertEquals(4, mission.marinesRemaining);
            assertEquals(UnitType.MILITIA, mission.deboardUnitType);
            assertTrue(isFormationPoint(payload.placement,
                    mission.rescueGuardX, mission.rescueGuardY));
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

    @Test
    void destroyedPickupMechReceivesAReplacementSortie() {
        BattleSimulation sim = simulation();
        CivilianEvacuationPayload payload = CivilianEvacuationPayload.install(
                sim, List.of(residential()), 904L);
        assertNotNull(payload);
        holdEvacuationOpen(sim, payload);
        RescuePickupSupportSystem support = new RescuePickupSupportSystem(
                sim.getCivilianEvacuationTracker());
        assertTrue(support.configure(payload.placement,
                10.5f, 10.5f, -6f, 10.5f, -10f, 10.5f,
                904L, RiskLevel.LOW, sim));
        advanceUntilInitialSupport(sim, support);

        long mech = pickupMechId(sim);
        sim.releaseFromRegistry(mech);
        support.tick(RescuePickupSupportSystem.WAVE_INTERVAL_SECONDS, sim);

        assertEquals(0, support.livePickupMechCount(sim));
        int inbound = 0;
        for (long id : sim.getAirEntityIds()) {
            ShuttleMission mission = sim.world().mission(id);
            if (mission.rescuePickupMechTransport
                    && mission.marinesRemaining == 1) {
                inbound++;
                assertEquals(MechVariant.BULWARK, mission.mechVariant);
                assertEquals(payload.placement.formationPointCount() * 2,
                        mission.rescuePatrolCells.length);
            }
        }
        assertEquals(1, inbound);
    }

    private static BattleSimulation configuredSimulation(long seed) {
        BattleSimulation sim = simulation();
        CivilianEvacuationPayload payload = CivilianEvacuationPayload.install(
                sim, List.of(residential()), seed);
        assertNotNull(payload);
        holdEvacuationOpen(sim, payload);
        RescuePickupSupportSystem support = new RescuePickupSupportSystem(
                sim.getCivilianEvacuationTracker());
        assertTrue(support.configure(payload.placement,
                10.5f, 10.5f, -6f, 10.5f, -10f, 10.5f,
                seed, RiskLevel.LOW, sim));
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
        return new BattleSimulation(grid, new CellTopology(26, 22));
    }

    private static void assertInitialSorties(BattleSimulation sim,
                                               CivilianEvacuationPlacement placement,
                                               MechVariant expectedVariant) {
        int militiaSorties = 0;
        int mechSorties = 0;
        int[] pointSorties = new int[placement.formationPointCount()];
        for (long id : sim.getAirEntityIds()) {
            ShuttleMission mission = sim.world().mission(id);
            if (mission.rescueMilitiaTransport) {
                militiaSorties++;
                assertEquals(4, mission.marinesRemaining);
                assertEquals(4, mission.marineLoadout.length);
                for (MarineLoadout loadout : mission.marineLoadout) {
                    assertNotNull(loadout.primaryDef());
                    assertTrue(loadout.primaryDef() == WeaponRegistry.require(WeaponRegistry.PULSE_RIFLE_ID)
                            || loadout.primaryDef() == WeaponRegistry.require(WeaponRegistry.SMG_ID)
                            || loadout.primaryDef() == WeaponRegistry.require(WeaponRegistry.DMR_ID));
                }
                int point = formationPointIndex(placement,
                        mission.rescueGuardX, mission.rescueGuardY);
                assertTrue(point >= 0);
                pointSorties[point]++;
                assertTrue(mission.pendingDelay
                        >= RescuePickupSupportSystem.INITIAL_ARRIVAL_DELAY_SECONDS);
            }
            if (mission.rescuePickupMechTransport) {
                mechSorties++;
                assertEquals(MechSupportPayload.INSTANCE, mission.payload);
                assertEquals(expectedVariant, mission.mechVariant);
                assertEquals(placement.formationPointCount() * 2,
                        mission.rescuePatrolCells.length);
                assertTrue(mission.pendingDelay
                        > RescuePickupSupportSystem.INITIAL_ARRIVAL_DELAY_SECONDS);
            }
        }
        assertEquals(RescuePickupSupportSystem.TARGET_GUARD_SQUADS,
                militiaSorties);
        for (int sorties : pointSorties) assertEquals(1, sorties);
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

    private static long pickupMechId(BattleSimulation sim) {
        for (int i = 0; i < sim.liveUnitCount(); i++) {
            long unit = sim.liveUnitAt(i);
            if (sim.identity().faction(unit) == Faction.MARINE
                    && sim.identity().type(unit) == UnitType.HEAVY_MECH
                    && sim.squad().hasSquad(unit)
                    && sim.squadOf(unit).rescuePickupMech) return unit;
        }
        throw new AssertionError("pickup mech not found");
    }

    private static boolean isFormationPoint(
            CivilianEvacuationPlacement placement, int x, int y) {
        return formationPointIndex(placement, x, y) >= 0;
    }

    private static int formationPointIndex(
            CivilianEvacuationPlacement placement, int x, int y) {
        for (int point = 0; point < placement.formationPointCount(); point++) {
            if (placement.formationX(point) == x
                    && placement.formationY(point) == y) return point;
        }
        return -1;
    }

    private static void holdEvacuationOpen(BattleSimulation sim,
                                            CivilianEvacuationPayload payload) {
        float x = payload.placement.liftX + 0.5f;
        float y = payload.placement.liftY + 0.5f;
        long shuttle = sim.spawnShuttle(ShuttleType.VALKYRIE,
                Faction.CIVILIAN, x, y, -8f, y, -12f, y, 10_000f);
        ShuttleMission mission = sim.world().mission(shuttle);
        mission.marinesRemaining = 0;
        mission.awaitingEvacuees = true;
        mission.evacueeCapacity = payload.size();
        assertTrue(sim.attachCivilianPickupShuttle(shuttle));
    }

    private static PointOfInterest residential() {
        return new PointOfInterest(PointOfInterest.Kind.RESIDENTIAL,
                10, 8, 14, 12, 12, 10, 12, 10);
    }
}
