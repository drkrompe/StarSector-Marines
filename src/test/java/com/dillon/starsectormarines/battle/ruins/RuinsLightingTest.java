package com.dillon.starsectormarines.battle.ruins;

import com.dillon.starsectormarines.battle.derelict.FacilityWake;
import com.dillon.starsectormarines.battle.derelict.SecurityNetwork;
import com.dillon.starsectormarines.battle.derelict.SecurityZone;
import com.dillon.starsectormarines.battle.ruins.PowerGraph.Branch;
import com.dillon.starsectormarines.battle.ruins.PowerGraph.Consumer;
import com.dillon.starsectormarines.battle.world.gen.ruins.RuinsDelve;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.NightLighting;
import com.dillon.starsectormarines.battle.world.model.NightLighting.Lamp;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuinsLightingTest {

    private static PowerGraph graph(int capacity) {
        return new PowerGraph(capacity, List.of(Branch.ONWARD, Branch.ONWARD), List.of(
                new Consumer("store-1", 0, Branch.STORE),
                new Consumer("gate-1", 0, Branch.ONWARD),
                new Consumer(RuinsWorks.VAULT_SEAL, 1, Branch.ONWARD)));
    }

    @Test
    void aColdHallLeavesOnlyTheEmergencyStrips() {
        assertEquals(Set.of(RuinsLighting.EMERGENCY), RuinsLighting.circuits(graph(3)));
    }

    @Test
    void aRestartLightsTheHallAndEachJunctionsSide() {
        PowerGraph graph = graph(3);
        graph.restart();
        assertEquals(Set.of(RuinsLighting.EMERGENCY, RuinsLighting.HALL, RuinsLighting.band(0),
                RuinsLighting.band(1), RuinsLighting.VAULT), RuinsLighting.circuits(graph));

        graph.throwJunction(1, Branch.STORE);
        Set<String> lit = RuinsLighting.circuits(graph);
        assertTrue(lit.contains(RuinsLighting.store(1)));
        assertFalse(lit.contains(RuinsLighting.band(1)), "the way on is dark once its junction feeds the store");
        assertFalse(lit.contains(RuinsLighting.VAULT), "the vault's seal is off its junction's branch");
    }

    @Test
    void anOverloadedHallBrownsOutAllButItself() {
        PowerGraph graph = graph(1);
        graph.restart();
        assertTrue(graph.overloaded());
        assertEquals(Set.of(RuinsLighting.EMERGENCY, RuinsLighting.HALL), RuinsLighting.circuits(graph));
    }

    @Test
    void stripsLightEveryRoofedRoomAndNothingUnderOpenSky() {
        // Two roofed rooms, 1 (x 0..11) and 2 (x 13..15), a wall between them at x 12, open sky from x 16.
        CellTopology ground = new CellTopology(20, 3);
        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 20; x++) ground.setGroundKind(x, y, CellTopology.GroundKind.INDOOR);
            for (int x = 0; x < 16; x++) ground.setBuildingId(x, y, x < 13 ? 1 : 2);
            ground.setWall(12, y, true);
        }
        List<Lamp> lamps = RuinsLighting.strips(ground, new RuinsDelve.Area(0, 0, 19, 2), (x, y) -> "c");
        assertTrue(lamps.stream().anyMatch(l -> ground.getBuildingId((int) l.x(), (int) l.y()) == 2),
                "a small room takes a lamp of its own: its neighbour's light stops at its walls");
        for (Lamp lamp : lamps) {
            assertTrue(lamp.x() < 16, "no lamp hangs under open sky");
            assertFalse(ground.isWall((int) lamp.x(), (int) lamp.y()));
        }
        long inRoom1 = lamps.stream().filter(l -> l.x() < 12).count();
        assertEquals(3, inRoom1, "a twelve-cell room takes a lamp every " + RuinsLighting.SPACING + " cells");
    }

    @Test
    void anEmergencyStripOnAWallStepsOntoTheNearestFloor() {
        CellTopology ground = new CellTopology(5, 1);
        for (int x = 0; x < 5; x++) {
            ground.setGroundKind(x, 0, CellTopology.GroundKind.INDOOR);
            ground.setBuildingId(x, 0, 1);
        }
        ground.setWall(2, 0, true);
        ground.setWall(3, 0, true);
        assertArrayEquals(new int[] {1, 0}, RuinsLighting.nearestFloor(ground, 2, 0, 2));
    }

    @Test
    void theWatchsLightsFollowItsZonesAndItsWake() {
        SecurityNetwork network = new SecurityNetwork()
                .add(new SecurityZone(1, 10, 10, 5))
                .add(new SecurityZone(2, 30, 10, 5));
        assertEquals(Set.of(), RuinsLighting.watchCircuits(network, FacilityWake.Stage.QUIET), "a sleeping watch is dark");

        network.stir(1, 0);
        assertEquals(Set.of(RuinsLighting.watch(1)), RuinsLighting.watchCircuits(network, FacilityWake.Stage.QUIET));

        assertEquals(Set.of(RuinsLighting.watch(1), RuinsLighting.vents(1), RuinsLighting.vents(2),
                RuinsLighting.bay(1), RuinsLighting.bay(2)),
                RuinsLighting.watchCircuits(network, FacilityWake.Stage.ROUSED), "vents and bays come up by stage");

        network.zone(2).powerDown();
        assertFalse(RuinsLighting.watchCircuits(network, FacilityWake.Stage.CONTINGENCY).contains(RuinsLighting.bay(2)),
                "a zone cut off at its junction is dark");
        assertTrue(RuinsLighting.watchCircuits(network, FacilityWake.Stage.CONTINGENCY)
                .contains(RuinsLighting.watch(1)), "the contingency lights every online zone");
    }

    @Test
    void applyingUnchangedPowerKeepsTheSameLightingSoNothingRebakes() {
        CellTopology ground = new CellTopology(2, 2);
        PowerGraph graph = graph(3);
        NightLighting night = new NightLighting(0f, RuinsLighting.ROOFED_DARKNESS, List.of(),
                RuinsLighting.circuits(graph));
        ground.setNightLighting(night);
        RuinsLighting.apply(ground, graph, 1f);
        assertSame(night, ground.nightLighting());
        graph.restart();
        RuinsLighting.apply(ground, graph, 2f);
        assertNotSame(night, ground.nightLighting());
        assertEquals(RuinsLighting.ROOFED_DARKNESS, ground.nightLighting().roofedDarkness());
    }

    @Test
    void aCircuitRemembersWhenItCameOnSoItsLampsWarmFromThen() {
        CellTopology ground = new CellTopology(2, 2);
        PowerGraph graph = graph(3);
        Lamp hall = new Lamp(1, 1, 3, 1, 1, 1, RuinsLighting.HALL);
        Lamp emergency = new Lamp(1, 1, 3, 1, 1, 1, RuinsLighting.EMERGENCY);
        ground.setNightLighting(new NightLighting(0f, RuinsLighting.ROOFED_DARKNESS, List.of(hall, emergency),
                RuinsLighting.circuits(graph)));
        graph.restart();
        RuinsLighting.apply(ground, graph, 12f);
        assertEquals(12f, ground.nightLighting().poweredAt(hall));
        assertTrue(Float.isNaN(ground.nightLighting().poweredAt(emergency)), "lit since landing: lit as laid");

        graph.throwJunction(0, Branch.STORE);
        RuinsLighting.apply(ground, graph, 20f);
        assertEquals(12f, ground.nightLighting().poweredAt(hall), "a circuit still on keeps its time");
    }
}
