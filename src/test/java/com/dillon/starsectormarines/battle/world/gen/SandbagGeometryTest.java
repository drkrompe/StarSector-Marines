package com.dillon.starsectormarines.battle.world.gen;

import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import com.dillon.starsectormarines.testsupport.DiskRegistries;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SandbagGeometryTest {
    @Test void shippedPiecesKeepTheirEdgeAndConnectAcrossCells() throws Exception {
        DiskRegistries.installMapGeneration(Path.of(""));
        BufferedImage atlas = ImageIO.read(Path.of("mod/graphics/doodads/doodads.png").toFile());
        String[] straight = {"n", "e", "s", "w"};
        String[] corner = {"nw", "ne", "se", "sw"};
        for (int turn = 0; turn < 4; turn++) {
            BufferedImage run = frame(atlas, "straight-" + straight[turn]);
            BufferedImage elbow = frame(atlas, "corner-" + corner[turn]);
            // Sample in the north/NW frame, rotating the coordinates with the art.
            for (int y = 16; y < 32; y++) for (int x = 16; x < 32; x++) {
                assertTrue(alpha(run, x, y, turn) <= 2, "run gained an opaque apron");
                assertTrue(alpha(elbow, x, y, turn) <= 2, "elbow interior must stay empty");
            }
            int connected = 0;
            for (int y = 0; y < 10; y++) {
                if (alpha(elbow, 31, y, turn) > 100 && alpha(run, 0, y, turn) > 100
                        && alpha(run, 31, y, turn) > 100) connected++;
            }
            assertTrue(connected >= 5, "corner and straight must share a substantial connector");
            int vertical = 0;
            for (int x = 0; x < 10; x++) if (alpha(elbow, x, 31, turn) > 100) vertical++;
            assertTrue(vertical >= 5, "elbow must connect to its second arm");
        }
    }

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

    private static BufferedImage frame(BufferedImage atlas, String suffix) {
        var def = TileRegistry.installed().doodad("doodad.sandbag-" + suffix);
        return atlas.getSubimage(def.col * 32, def.row * 32, 32, 32);
    }

    private static int alpha(BufferedImage image, int x, int y, int turns) {
        for (int i = 0; i < turns; i++) { int oldX = x; x = 31 - y; y = oldX; }
        return image.getRGB(x, y) >>> 24;
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
