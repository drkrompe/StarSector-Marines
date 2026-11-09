package com.dillon.starsectormarines.battle.world.gen.nature;

import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Which formations a world grows, and on which ground: {@link FormationBrush#pool} and its ground rule. */
class FormationPoolTest {
    @Test void irradiatedWorldsGrowHusksAndOnlyOnHardpan() {
        for (var shape : FormationBrush.pool(SurfacePalette.IRRADIATED))
            assertEquals(MineralFormation.Material.HUSK, shape.material());
        assertEquals(2, FormationBrush.pool(SurfacePalette.IRRADIATED).length);
        assertTrue(FormationBrush.standsOn(SurfacePalette.IRRADIATED, GroundKind.HARDPAN));
        assertFalse(FormationBrush.standsOn(SurfacePalette.IRRADIATED, GroundKind.STONE));
        assertFalse(FormationBrush.standsOn(SurfacePalette.ROCK, GroundKind.HARDPAN));
    }

    @Test void eachWorldKeepsItsOwnMaterial() {
        for (var shape : FormationBrush.pool(SurfacePalette.ROCK)) assertEquals(MineralFormation.Material.ROCK, shape.material());
        for (var shape : FormationBrush.pool(SurfacePalette.FROZEN)) assertEquals(MineralFormation.Material.ICE, shape.material());
        assertTrue(FormationBrush.standsOn(SurfacePalette.FROZEN, GroundKind.SNOW));
        assertTrue(FormationBrush.standsOn(SurfacePalette.ROCK, GroundKind.DIRT));
    }

    @Test void livingAndAridWorldsGrowNone() {
        assertEquals(0, FormationBrush.pool(SurfacePalette.VERDANT).length);
        assertEquals(0, FormationBrush.pool(SurfacePalette.ARID).length);
        assertEquals(0, FormationBrush.pool(null).length);
    }
}
