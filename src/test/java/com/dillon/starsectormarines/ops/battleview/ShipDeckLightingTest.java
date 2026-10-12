package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.world.gen.fit.RoomPose;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomShape;
import com.dillon.starsectormarines.battle.world.gen.ship.DeckGraph;
import com.dillon.starsectormarines.battle.world.gen.ship.DeckSide;
import com.dillon.starsectormarines.battle.world.gen.ship.DeckZone;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import java.util.List;
import java.util.Set;
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
