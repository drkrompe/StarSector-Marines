package com.dillon.starsectormarines.battle.world.tiles;

import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.gen.forest.TreeCanopy;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class DoodadFogSurfaceTest {
    @Test void anyPhysicalPropDefaultsToASurfaceAndFlatDecorationsDoNot() {
        var solid = new DoodadDef("custom.machine", "custom.png", 0, 0, DoodadCover.MED);
        var floor = new DoodadDef("custom.floor", "custom.png", 0, 0, DoodadCover.NONE);
        assertEquals(List.of("#"), solid.fogSurface);
        assertTrue(floor.fogSurface.isEmpty());
        var optOut = new DoodadDef("custom.flat", "custom.png", 0, 0, DoodadCover.MED, .5f,
                1, 1, null, 64, List.of());
        assertFalse(new Doodad(0, 0, optOut).hasFogSurface());
        assertThrows(IllegalArgumentException.class, () -> new DoodadDef("bad", "custom.png", 0, 0,
                DoodadCover.NONE, 0, 2, 1, null, 64, List.of("#")));
    }

    @Test void arbitraryIrregularSurfaceMatchesSpriteRotationAndMirroring() {
        var def = new DoodadDef("custom.arch", "custom.png", 0, 0, DoodadCover.NONE, 0,
                3, 2, null, 64, List.of("#..", ".#."));
        int[][] expected = {{0,0,1,1}, {1,0,0,1}, {2,1,1,0}, {0,2,1,1},
                {2,0,1,1}, {1,2,0,1}, {0,1,1,0}, {0,0,1,1}};
        for (int i = 0; i < 8; i++) {
            var prop = new Doodad(0, 0, def, i % 4, i >= 4);
            int[] points = expected[i];
            for (int y = 0; y < prop.footprintCellsY; y++) for (int x = 0; x < prop.footprintCellsX; x++)
                assertEquals((x == points[0] && y == points[1]) || (x == points[2] && y == points[3]),
                        prop.hasFogSurfaceAt(x, y));
        }
    }

    @Test void authoredForestMasksMatchGeometryAndDamageStates() {
        for (TreeCanopy canopy : TreeCanopy.values()) for (String suffix : new String[]{"", "-damaged"})
            for (int turns = 0; turns < 4; turns++) {
                var prop = new Doodad(0, 0, TileRegistry.installed().doodad(canopy.id + suffix), turns);
                for (int y = 0; y < prop.footprintCellsY; y++) for (int x = 0; x < prop.footprintCellsX; x++)
                    assertEquals(canopy.blocks(x, y, turns), prop.hasFogSurfaceAt(x, y));
                assertTrue(TileRegistry.installed().doodad(canopy.id + "-destroyed").fogSurface.isEmpty());
            }
    }
}
