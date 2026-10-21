package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.world.gen.fit.RoomPose;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomShape;
import com.dillon.starsectormarines.battle.world.gen.ship.DeckGraph;
import com.dillon.starsectormarines.battle.world.gen.ship.DeckSide;
import com.dillon.starsectormarines.battle.world.gen.ship.DeckZone;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import com.dillon.starsectormarines.battle.world.tiles.DoodadCover;
import com.dillon.starsectormarines.battle.world.tiles.DoodadDef;
import java.util.List;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ShipDeckLightingTest {
    @Test void repairRestoresTheSameFixturesWithoutFaults() {
        var topology = new CellTopology(20, 12);
        for (int y = 0; y < 12; y++) for (int x = 0; x < 20; x++) topology.setGroundKind(x, y, GroundKind.INDOOR);
        var room = new DeckGraph.Compartment(1, RoomShape.rectangle(10, 6), 3, 3,
                RoomPose.CANONICAL, DeckSide.PORT, DeckZone.MIDSHIPS, RoomPurpose.HANGAR, List.of());
        var intact = new DeckGraph(List.of(room), List.of());
        var damaged = new DeckGraph(List.of(room), List.of(), List.of(), intact.boats(), Set.of(1));
        var before = new ShipDeckLighting(topology, damaged, 42);
        var after = new ShipDeckLighting(topology, intact, 42);
        assertTrue(before.lamps.stream().anyMatch(ShipDeckLighting.Lamp::faulty));
        assertTrue(after.lamps.stream().noneMatch(ShipDeckLighting.Lamp::emergency));
        assertEquals(before.lamps.size(), after.lamps.size());
        for (int i = 0; i < before.lamps.size(); i++) {
            assertEquals(before.lamps.get(i).x(), after.lamps.get(i).x());
            assertEquals(before.lamps.get(i).y(), after.lamps.get(i).y());
            for (float t = 0; t < 12; t += .13f) assertEquals(1f, ShipDeckLighting.gain(after.lamps.get(i), t));
        }
        assertTrue(before.cells[5 * 20 + 5].emergency());
        assertFalse(after.cells[5 * 20 + 5].emergency());
    }

    @Test void restoringPowerRelightsRoomsWithFaultyCircuitsButNotDamagedOnes() {
        var topology = new CellTopology(30, 12);
        for (int y = 0; y < 12; y++) for (int x = 0; x < 30; x++) topology.setGroundKind(x, y, GroundKind.INDOOR);
        var faulty = new DeckGraph.Compartment(1, RoomShape.rectangle(10, 6), 2, 3,
                RoomPose.CANONICAL, DeckSide.PORT, DeckZone.MIDSHIPS, RoomPurpose.BARRACKS, List.of());
        var wrecked = new DeckGraph.Compartment(2, RoomShape.rectangle(10, 6), 16, 3,
                RoomPose.CANONICAL, DeckSide.PORT, DeckZone.MIDSHIPS, RoomPurpose.BARRACKS, List.of());
        var plain = new DeckGraph(List.of(faulty, wrecked), List.of());
        var graph = new DeckGraph(List.of(faulty, wrecked), List.of(), List.of(), plain.boats(), Set.of(2), Set.of(1));
        var dark = new ShipDeckLighting(topology, graph, 42, false);
        var lit = new ShipDeckLighting(topology, graph, 42, true);
        assertTrue(dark.cells[5 * 30 + 5].emergency());
        assertFalse(lit.cells[5 * 30 + 5].emergency());
        assertTrue(dark.cells[5 * 30 + 20].emergency());
        assertTrue(lit.cells[5 * 30 + 20].emergency());
        // The same fixtures either way, so a wave can blend one lamp into its own other state.
        assertEquals(dark.lamps.size(), lit.lamps.size());
        for (int i = 0; i < dark.lamps.size(); i++) {
            assertEquals(dark.lamps.get(i).x(), lit.lamps.get(i).x());
            assertEquals(dark.lamps.get(i).y(), lit.lamps.get(i).y());
        }
    }

    @Test void emergencyPowerLeavesDistinctDarkGapsAndRepairRestoresCoverage() {
        var topology = new CellTopology(20, 16);
        for (int y = 0; y < 16; y++) for (int x = 0; x < 20; x++) topology.setGroundKind(x, y, GroundKind.INDOOR);
        var room = new DeckGraph.Compartment(1, RoomShape.rectangle(14, 10), 3, 3,
                RoomPose.CANONICAL, DeckSide.PORT, DeckZone.MIDSHIPS, RoomPurpose.PARTS_CAGE, List.of());
        var intact = new DeckGraph(List.of(room), List.of());
        var damaged = new DeckGraph(List.of(room), List.of(), List.of(), intact.boats(), Set.of(1));
        var before = new ShipDeckLighting(topology, damaged, 42);
        var after = new ShipDeckLighting(topology, intact, 42);
        float[] steadyGains = new float[before.lamps.size()];
        Arrays.fill(steadyGains, 1f);
        float near = ShipDeckLighting.illumination(before.cells[3 * 20 + 4], 5, steadyGains);
        float gap = ShipDeckLighting.illumination(before.cells[7 * 20 + 7], 5, steadyGains);
        float repairedGap = ShipDeckLighting.illumination(after.cells[7 * 20 + 7], 5, steadyGains);
        assertTrue(ShipDeckLighting.shade(true, gap) - ShipDeckLighting.shade(true, near) > .5f,
                "Emergency pools must separate visibly from the dark room interior");
        assertTrue(ShipDeckLighting.shade(false, repairedGap) < ShipDeckLighting.shade(true, gap) - .2f,
                "Repair restores usable light between fixtures");
        assertTrue(ShipDeckLighting.shade(true, gap) < .95f, "Keep some texture in the darkest gaps");
    }

    @Test void faultIsLocalizedBoundedAndDrivenBySceneTime() {
        var fault = new ShipDeckLighting.Lamp(0, 0, false, true, true, 0);
        var steady = new ShipDeckLighting.Lamp(0, 0, false, true, false, 0);
        assertTrue(ShipDeckLighting.gain(fault, 0) < ShipDeckLighting.gain(fault, 3));
        assertEquals(ShipDeckLighting.gain(fault, .25f), ShipDeckLighting.gain(fault, 11.25f));
        for (float t = 0; t < 22; t += .03f) {
            float gain = ShipDeckLighting.gain(fault, t);
            assertTrue(gain >= .2f && gain <= 1f);
            assertEquals(1f, ShipDeckLighting.gain(steady, t));
        }
    }

    @Test void demolishingTheMountRemovesTheFixtureAndItsPoolWithoutRebuildingTheScene() {
        var topology = new CellTopology(10, 10);
        for (int y = 0; y < 10; y++) for (int x = 0; x < 10; x++) topology.setGroundKind(x, y, GroundKind.INDOOR);
        for (int x = 0; x < 10; x++) topology.setWall(x, 5, true); // A straight passage wall; lamps on x % 4 == 0.
        var lights = new ShipDeckLighting(topology, null, 42, true, List.of(), true, id -> null);
        int lamp = -1;
        for (int i = 0; i < lights.lamps.size(); i++)
            if (lights.lamps.get(i).x() == 4.5f && lights.lamps.get(i).y() < 5f) lamp = i;
        assertTrue(lamp >= 0); assertTrue(lights.present(lamp));
        assertEquals(ShipDeckLighting.Style.STRUNG, lights.lamps.get(lamp).style());
        var floor = lights.cells[4 * 10 + 4];
        float[] gains = new float[lights.lamps.size()];
        for (int i = 0; i < gains.length; i++) gains[i] = lights.gain(i, 0);
        assertTrue(ShipDeckLighting.illumination(floor, 3 * ShipDeckLighting.SAMPLES + 4, gains) > .3f);
        topology.setWall(4, 5, false);
        assertFalse(lights.present(lamp)); assertEquals(0f, lights.gain(lamp, 0));
        // Ordinary scarring does not destroy a mount; restoring actual structure restores light.
        topology.setWall(4, 5, true); topology.setTag(4, 4, CellTopology.Tag.GROUND_DAMAGED, true);
        assertTrue(lights.present(lamp)); assertEquals(1f, lights.gain(lamp, 0));
    }

    @Test void aStrungLampLightsInboardAndNeverThroughItsOwnWall() {
        var lamp = new ShipDeckLighting.Lamp(4.5f, 4.88f, false, false, false, 0f,
                ShipDeckLighting.Style.STRUNG, 0f, -1f, 1f, .64f, .3f, 2.4f);
        float near = ShipDeckLighting.weight(lamp, 4.5f, 4f), across = ShipDeckLighting.weight(lamp, 4.5f, 2.5f);
        float aside = ShipDeckLighting.weight(lamp, 6.5f, 4f), behind = ShipDeckLighting.weight(lamp, 4.5f, 5.1f);
        assertTrue(near > .8f, "brightest just inboard of the lamp: " + near);
        assertTrue(across > aside, "the pool runs out from the wall more than along it");
        assertEquals(0f, behind);
    }

    @Test void passagesWithoutPowerLoseTwoLampsInThreeAndSlackTheirCable() {
        var topology = new CellTopology(40, 6);
        for (int y = 0; y < 6; y++) for (int x = 0; x < 40; x++) topology.setGroundKind(x, y, GroundKind.INDOOR);
        for (int x = 0; x < 40; x++) { topology.setWall(x, 0, true); topology.setWall(x, 5, true); }
        var def = new DoodadDef("x", "s.png", 0, 0,
                DoodadCover.NONE);
        var lit = new ShipDeckLighting(topology, null, 7, true, List.of(), true, id -> def);
        var dark = new ShipDeckLighting(topology, null, 7, false, List.of(), true, id -> def);
        assertEquals(lit.lamps.size(), dark.lamps.size());
        long dead = IntStream.range(0, dark.lamps.size()).filter(dark::dead).count();
        assertEquals(dark.lamps.size() - (dark.lamps.size() + 2) / 3, dead);
        assertTrue(IntStream.range(0, lit.lamps.size()).noneMatch(lit::dead));
        assertEquals(lit.cables.size(), dark.cables.size());
        assertEquals(lit.fixtures.size(), lit.lamps.size());
    }

    @Test void aCollapsedCeilingRemovesOnlyTheFixtureItSupported() {
        var topology = new CellTopology(20, 12);
        for (int y = 0; y < 12; y++) for (int x = 0; x < 20; x++) topology.setGroundKind(x, y, GroundKind.INDOOR);
        var room = new DeckGraph.Compartment(1, RoomShape.rectangle(10, 6), 3, 3,
                RoomPose.CANONICAL, DeckSide.PORT, DeckZone.MIDSHIPS, RoomPurpose.HANGAR, List.of());
        var lights = new ShipDeckLighting(topology, new DeckGraph(List.of(room), List.of()), 42);
        var lost = lights.lamps.get(0);
        topology.setRoofDestroyed((int)lost.x(), (int)lost.y(), true);
        assertFalse(lights.present(0)); assertEquals(0f, lights.gain(0, 0));
        for (int i = 1; i < lights.lamps.size(); i++) assertTrue(lights.present(i));
    }

    @Test void wallsAndVacuumNeverReceiveFloorShading() {
        var topology = new CellTopology(4, 4);
        topology.setGroundKind(2, 1, GroundKind.INDOOR);
        topology.setWall(1, 1, true);
        topology.setGroundKind(2, 2, GroundKind.VOID);
        var lights = new ShipDeckLighting(topology, null, 42);
        assertNull(lights.cells[5]);
        assertNull(lights.cells[10]);
        assertNotNull(lights.cells[6]);
        assertTrue(topology.isWall(1, 1));
        assertEquals(GroundKind.VOID, topology.getGroundKind(2, 2));
    }
}
