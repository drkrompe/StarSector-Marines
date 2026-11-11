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

    private static DoodadDef def(String id) {
        return new DoodadDef(id, "graphics/doodads/street-lamps.png", 0, 0, DoodadCover.NONE, 0.3f, 1, 1);
    }
}
