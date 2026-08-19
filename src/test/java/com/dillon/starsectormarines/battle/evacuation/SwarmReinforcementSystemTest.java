package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.PointOfInterest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SwarmReinforcementSystemTest {

    @Test
    void depletedPopulationReceivesBoundedSafePerimeterWave() {
        Fixture fixture = fixture();
        long marine = fixture.sim.spawn(new EntitySpec("marine", Faction.MARINE,
                UnitType.MARINE, 32, 24));
        spawnRunners(fixture.sim, 8);
        SwarmReinforcementSystem system = new SwarmReinforcementSystem(
                fixture.sim.getCivilianEvacuationTracker());
        assertTrue(system.configure(fixture.payload.placement, 12, 919L));

        system.tick(SwarmReinforcementSystem.WAVE_INTERVAL_SECONDS, fixture.sim);

        assertEquals(12, runnerCount(fixture.sim));
        int perimeterRunners = 0;
        for (int i = 0, n = fixture.sim.liveUnitCount(); i < n; i++) {
            long unit = fixture.sim.liveUnitAt(i);
            if (fixture.sim.identity().type(unit) != UnitType.SWARM_RUNNER) continue;
            int x = fixture.sim.world().cellX(unit);
            int y = fixture.sim.world().cellY(unit);
            int edgeDistance = Math.min(Math.min(x, 63 - x), Math.min(y, 47 - y));
            if (edgeDistance > SwarmReinforcementSystem.PERIMETER_BAND_CELLS) continue;
            perimeterRunners++;
            assertTrue(distance(x, y, fixture.sim.world().cellX(marine),
                    fixture.sim.world().cellY(marine))
                    >= SwarmReinforcementSystem.MIN_MARINE_ENTRY_DISTANCE);
            for (int civilian = 0; civilian < fixture.payload.size(); civilian++) {
                long id = fixture.payload.entityId(civilian);
                assertTrue(distance(x, y, fixture.sim.world().cellX(id),
                        fixture.sim.world().cellY(id))
                        >= SwarmReinforcementSystem.MIN_CIVILIAN_ENTRY_DISTANCE);
            }
        }
        assertEquals(4, perimeterRunners);
    }

    @Test
    void populationAtFloorDoesNotReceiveWave() {
        Fixture fixture = fixture();
        spawnRunners(fixture.sim, 9);
        SwarmReinforcementSystem system = new SwarmReinforcementSystem(
                fixture.sim.getCivilianEvacuationTracker());
        assertTrue(system.configure(fixture.payload.placement, 12, 920L));

        system.tick(SwarmReinforcementSystem.WAVE_INTERVAL_SECONDS, fixture.sim);

        assertEquals(9, runnerCount(fixture.sim));
    }

    @Test
    void completedCohortCannotCallAnotherWave() {
        Fixture fixture = fixture();
        spawnRunners(fixture.sim, 4);
        for (int i = 0; i < fixture.payload.size(); i++) {
            fixture.sim.getCivilianEvacuationTracker()
                    .markEvacuated(fixture.payload.entityId(i));
        }
        SwarmReinforcementSystem system = new SwarmReinforcementSystem(
                fixture.sim.getCivilianEvacuationTracker());
        assertTrue(system.configure(fixture.payload.placement, 12, 921L));

        system.tick(SwarmReinforcementSystem.WAVE_INTERVAL_SECONDS, fixture.sim);

        assertEquals(4, runnerCount(fixture.sim));
    }

    private static Fixture fixture() {
        NavigationGrid grid = new NavigationGrid(64, 48);
        for (int y = 0; y < grid.getHeight(); y++) {
            for (int x = 0; x < grid.getWidth(); x++) grid.setWalkableFloor(x, y);
        }
        BattleSimulation sim = new BattleSimulation(grid, new CellTopology(64, 48));
        CivilianEvacuationPayload payload = CivilianEvacuationPayload.install(
                sim, List.of(new PointOfInterest(PointOfInterest.Kind.RESIDENTIAL,
                        30, 22, 34, 26, 32, 24, 32, 24)), 77L);
        assertNotNull(payload);
        return new Fixture(sim, payload);
    }

    private static void spawnRunners(BattleSimulation sim, int count) {
        for (int i = 0; i < count; i++) {
            sim.spawn(new EntitySpec("runner " + i, Faction.DEFENDER,
                    UnitType.SWARM_RUNNER, 25 + i, 18)
                    .role(UnitRole.SWARM_PRESSURE));
        }
    }

    private static int runnerCount(BattleSimulation sim) {
        int count = 0;
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            if (sim.identity().type(sim.liveUnitAt(i)) == UnitType.SWARM_RUNNER) count++;
        }
        return count;
    }

    private static float distance(int x0, int y0, int x1, int y1) {
        int dx = x1 - x0;
        int dy = y1 - y0;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    private static final class Fixture {
        final BattleSimulation sim;
        final CivilianEvacuationPayload payload;

        Fixture(BattleSimulation sim, CivilianEvacuationPayload payload) {
            this.sim = sim;
            this.payload = payload;
        }
    }
}
