package com.dillon.starsectormarines.battle.world.gen;

import com.dillon.starsectormarines.battle.world.gen.bsp.fill.StreetDressing;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.CellTopology.Tag;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.tiles.DoodadCover;
import com.dillon.starsectormarines.battle.world.tiles.DoodadDef;
import com.dillon.starsectormarines.battle.world.model.NightLighting;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StreetLightsTest {

    /**
     * Every lamp bracket on a wall pools warm light on the pavement it hangs over, on a circuit that is powered once
     * the night is lit with it; an awning gives none.
     */
    @Test
    void aWallLampLightsThePavementUnderIt() {
        Doodad lamp = new Doodad(12, 7, def(StreetDressing.WALL_LAMP), 0);
        Doodad awning = new Doodad(20, 7, def("doodad.street-awning-striped"), 0);
        List<NightLighting.Lamp> lamps = StreetLights.wallLamps(List.of(lamp, awning));
        assertEquals(1, lamps.size());
        assertEquals(12.5f, lamps.get(0).x());
        assertEquals(7.5f, lamps.get(0).y());
        NightLighting night = StreetLights.lit(new NightLighting(.78f, List.of(), Set.of()), lamps);
        assertTrue(night.powered(night.lamps().get(0)), "the street circuit is on");
    }

    /**
     * A building's wall facing a street lights the pavement in front of it every sixth cell by its place on the map,
     * never in front of a window or a wall with no room behind it, and never beside a bracket's own lamp.
     */
    @Test
    void aStreetWallIsLitAlongItsLength() {
        CellTopology town = new CellTopology(12, 3);
        for (int x = 0; x < 12; x++) {
            town.setTag(x, 1, Tag.WALL, true);
            town.setGroundKind(x, 0, GroundKind.INDOOR);
            town.setGroundKind(x, 2, GroundKind.STREET);
        }
        town.setTag(9, 1, Tag.WINDOW, true);
        town.setGroundKind(9, 0, GroundKind.GRASS);
        List<NightLighting.Lamp> lamps = StreetLights.townLamps(town, List.of());
        assertEquals(1, lamps.size(), "x=3 is lit; x=9 is a window");
        assertEquals(3.5f, lamps.get(0).x());
        assertEquals(3f, lamps.get(0).y(), 1e-5f);

        NightLighting.Lamp bracket = StreetLights.wallLamps(List.of(new Doodad(4, 2, def(StreetDressing.WALL_LAMP), 0)))
                .get(0);
        assertEquals(List.of(bracket), StreetLights.townLamps(town, List.of(bracket)));
    }

    /**
     * A pavement is lit at its kerb every seventh cell by its place on the map, and on its corner where a side street
     * meets it, whatever the spacing says.
     */
    @Test
    void aKerbIsLitAlongItsLengthAndAtItsCorner() {
        CellTopology town = new CellTopology(15, 4);
        for (int x = 0; x < 15; x++) {
            town.setGroundKind(x, 0, GroundKind.INDOOR);
            town.setGroundKind(x, 1, x == 14 ? GroundKind.STREET : GroundKind.SIDEWALK);
            town.setGroundKind(x, 2, GroundKind.STREET);
            town.setGroundKind(x, 3, GroundKind.STREET);
        }
        List<NightLighting.Lamp> lamps = StreetLights.kerbLamps(town, List.of());
        assertEquals(3, lamps.size(), "x=3, x=10, and the corner at x=13: " + lamps);
        assertEquals(3.5f, lamps.get(0).x());
        assertEquals(1.85f, lamps.get(0).y(), 1e-5f, "at the kerb, toward the carriageway");
        assertEquals(13.85f, lamps.get(2).x(), 1e-5f);
    }

    /** A landing pad is washed white over its middle and marked amber at each of its four corners. */
    @Test
    void aPadIsLitOverItsMiddleAndAtItsCorners() {
        List<NightLighting.Lamp> lamps = StreetLights.padLamps(List.of(LandingPad.civilian(20, 20,
                LandingPad.Approach.NORTH)));
        assertEquals(5, lamps.size());
        assertEquals(20.5f, lamps.get(0).x());
    }

    private static DoodadDef def(String id) {
        return new DoodadDef(id, "graphics/doodads/street-lamps.png", 0, 0, DoodadCover.NONE, 0.3f, 1, 1);
    }
}
