package com.dillon.starsectormarines.battle.world.gen;

import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import com.dillon.starsectormarines.testsupport.DiskRegistries;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SandbagGeometryTest {
    @Tag("generated-space-evidence")
    @Test void thunderRunHorseshoesJoinAtBothCornersAndFaceTheRoad() throws Exception {
        DiskRegistries.installMapGeneration(Path.of(""));
        MapResult map = ThunderRunCompound.generate(9301, TargetProfile.NEUTRAL).map();
        for (var gun : ThunderRunCompound.EXIT_GUNS) {
            assertPiece(map, gun.x() - 2, gun.y() + 2, "corner-nw");
            assertPiece(map, gun.x() - 2, gun.y() - 2, "corner-sw");
            for (int x = gun.x() - 1; x <= gun.x() + 2; x++) {
                assertPiece(map, x, gun.y() + 2, "straight-n");
                assertPiece(map, x, gun.y() - 2, "straight-s");
            }
        }
        assertPiece(map, 290, 61, "straight-n");
        assertPiece(map, 290, 89, "straight-s");
    }

    private static void assertPiece(MapResult map, int x, int y, String suffix) {
        Doodad prop = map.doodads.stream().filter(d -> d.cellX == x && d.cellY == y)
                .findFirst().orElseThrow();
        var def = TileRegistry.installed().doodad("doodad.sandbag-" + suffix);
        assertEquals(def.col, prop.tile.col);
        assertEquals(def.row, prop.tile.row);
        assertEquals(Doodad.COVER_HEAVY, prop.cover);
        assertEquals(0.3f, prop.ballisticHalfHeight);
        assertTrue(map.topology.isFixture(x, y));
        assertTrue(map.grid.isSeeThrough(x, y));
    }
}
