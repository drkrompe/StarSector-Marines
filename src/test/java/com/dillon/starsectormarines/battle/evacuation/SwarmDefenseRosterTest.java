package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.PointOfInterest;
import com.dillon.starsectormarines.ops.RiskLevel;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SwarmDefenseRosterTest {

    @Test
    void riskScalesCompleteRunnerOnlyRostersOutsideProtectedZones() {
        for (RiskLevel risk : RiskLevel.values()) {
            Fixture fixture = openFixture();
            SwarmDefenseRoster roster = SwarmDefenseRoster.install(
                    fixture.sim, fixture.payload.placement, risk, 713L);

            assertNotNull(roster);
            assertEquals(SwarmDefenseRoster.countFor(risk), roster.size());
            for (int i = 0; i < roster.size(); i++) {
                long entity = roster.entityId(i);
                assertEquals(Faction.DEFENDER,
                        fixture.sim.identity().faction(entity));
                assertEquals(UnitType.SWARM_RUNNER,
                        fixture.sim.identity().type(entity));
                assertEquals(UnitRole.SWARM_PRESSURE,
                        fixture.sim.role().role(entity));
                int x = fixture.sim.world().cellX(entity);
                int y = fixture.sim.world().cellY(entity);
                assertTrue(Math.abs(x - fixture.payload.placement.shelterX)
                        + Math.abs(y - fixture.payload.placement.shelterY)
                        > CivilianEvacuationPlacement.SHELTER_ZONE_RADIUS);
                assertTrue(Math.abs(x - fixture.payload.placement.liftX)
                                > CivilianEvacuationPlacement.LIFT_ZONE_RADIUS
                        || Math.abs(y - fixture.payload.placement.liftY)
                                > CivilianEvacuationPlacement.LIFT_ZONE_RADIUS);
            }
        }
    }

    @Test
    void identicalSeedProducesIdenticalCells() {
        Fixture a = openFixture();
        Fixture b = openFixture();
        SwarmDefenseRoster first = SwarmDefenseRoster.install(
                a.sim, a.payload.placement, RiskLevel.MEDIUM, 991L);
        SwarmDefenseRoster second = SwarmDefenseRoster.install(
                b.sim, b.payload.placement, RiskLevel.MEDIUM, 991L);

        assertNotNull(first);
        assertNotNull(second);
        for (int i = 0; i < first.size(); i++) {
            assertEquals(a.sim.world().cellX(first.entityId(i)),
                    b.sim.world().cellX(second.entityId(i)));
            assertEquals(a.sim.world().cellY(first.entityId(i)),
                    b.sim.world().cellY(second.entityId(i)));
        }
    }

    @Test
    void debugCountsScaleAgainstLandingStrengthWithoutReducingProductionFloor() {
        assertEquals(150, SwarmDefenseRoster.debugCountFor(
                RiskLevel.LOW, 75));
        assertEquals(225, SwarmDefenseRoster.debugCountFor(
                RiskLevel.MEDIUM, 75));
        assertEquals(300, SwarmDefenseRoster.debugCountFor(
                RiskLevel.HIGH, 75));
        assertEquals(SwarmDefenseRoster.HIGH_COUNT,
                SwarmDefenseRoster.debugCountFor(RiskLevel.HIGH, 1));
    }

    @Test
    void explicitDebugRosterSizeIsInstalledCompletely() {
        Fixture fixture = openFixture();

        SwarmDefenseRoster roster = SwarmDefenseRoster.install(
                fixture.sim, fixture.payload.placement, 180, 713L);

        assertNotNull(roster);
        assertEquals(180, roster.size());
        for (int i = 0; i < roster.size(); i++) {
            long entity = roster.entityId(i);
            int distance = Math.abs(fixture.sim.world().cellX(entity)
                    - fixture.payload.placement.shelterX)
                    + Math.abs(fixture.sim.world().cellY(entity)
                    - fixture.payload.placement.shelterY);
            assertTrue(distance
                    >= SwarmDefenseRoster.DEBUG_SHELTER_APPROACH_DISTANCE);
        }
    }

    @Test
    void incompletePlacementLeavesSimulationUntouched() {
        NavigationGrid grid = new NavigationGrid(20, 20);
        for (int y = 5; y <= 15; y++) {
            for (int x = 5; x <= 15; x++) {
                if (Math.abs(x - 10) + Math.abs(y - 10) <= 5) {
                    grid.setWalkableFloor(x, y);
                }
            }
        }
        for (int x = 0; x <= 10; x++) grid.setWalkableFloor(x, 10);
        BattleSimulation sim = new BattleSimulation(
                grid, new CellTopology(20, 20));
        CivilianEvacuationPayload payload = CivilianEvacuationPayload.install(
                sim, List.of(homeAt(10, 10)), 5L);
        assertNotNull(payload);
        int before = sim.liveUnitCount();

        assertNull(SwarmDefenseRoster.install(
                sim, payload.placement, RiskLevel.HIGH, 5L));
        assertEquals(before, sim.liveUnitCount());
    }

    private static Fixture openFixture() {
        NavigationGrid grid = new NavigationGrid(64, 48);
        for (int y = 0; y < grid.getHeight(); y++) {
            for (int x = 0; x < grid.getWidth(); x++) {
                grid.setWalkableFloor(x, y);
            }
        }
        BattleSimulation sim = new BattleSimulation(
                grid, new CellTopology(64, 48));
        CivilianEvacuationPayload payload = CivilianEvacuationPayload.install(
                sim, List.of(homeAt(32, 24)), 77L);
        assertNotNull(payload);
        return new Fixture(sim, payload);
    }

    private static PointOfInterest homeAt(int x, int y) {
        return new PointOfInterest(PointOfInterest.Kind.RESIDENTIAL,
                x - 2, y - 2, x + 2, y + 2, x, y, x, y);
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
