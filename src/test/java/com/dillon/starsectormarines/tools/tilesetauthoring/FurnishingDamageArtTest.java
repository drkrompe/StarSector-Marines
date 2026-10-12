package com.dillon.starsectormarines.tools.tilesetauthoring;

import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import java.nio.file.Path;
import java.util.ArrayDeque;
import static org.junit.jupiter.api.Assertions.*;

class FurnishingDamageArtTest {
    @Test void pairedFramesKeepTheOriginalFootprintAndDestroyedPiecesExposeGround() throws Exception {
        var registry = TileRegistry.installed();
        for (String id : FurnishingDamageTemplate.IDS) for (String state : new String[]{"damaged", "destroyed"}) {
            var original = registry.doodad(id);
            var def = registry.doodad(id + "-" + state);
            assertNotNull(def, id);
            assertEquals(original.footprintCellsX, def.footprintCellsX);
            assertEquals(original.footprintCellsY, def.footprintCellsY);
            boolean wreck = state.equals("destroyed");
            assertEquals(wreck ? 0 : original.cover.level(), def.cover.level());
            assertEquals(wreck ? 0 : original.ballisticHalfHeight, def.ballisticHalfHeight);
            var atlas = ImageIO.read(Path.of("mod").resolve(def.sheetPath).toFile());
            int size = def.sourceCellPx, ox = def.col * size, oy = def.row * size;
            boolean[] solid = new boolean[size * size];
            for (int y = 0; y < size; y++) for (int x = 0; x < size; x++) {
                int alpha = atlas.getRGB(ox + x, oy + y) >>> 24;
                solid[y * size + x] = alpha > 40;
                if (wreck && (x == 0 || y == 0 || x == size - 1 || y == size - 1))
                    assertTrue(alpha <= 4, id + " touches frame edge");
            }
            if (!wreck) continue;
            // The reel's loose cable crosses the center; inspect the open ground beside it.
            int gapX = id.endsWith("cable-reel") ? size * 39 / 100 : size / 2;
            int gapY = id.endsWith("cable-reel") ? size * 42 / 100 : size / 2;
            assertTrue((atlas.getRGB(ox + gapX, oy + gapY) >>> 24) <= 4,
                    id + " closes the central ground gap");
            int fragments = 0;
            for (int start = 0; start < solid.length; start++) {
                if (!solid[start]) continue;
                var queue = new ArrayDeque<Integer>(); queue.add(start); solid[start] = false;
                int area = 0;
                while (!queue.isEmpty()) {
                    int pixel = queue.remove(); area++;
                    for (int offset : new int[]{-size, size, -1, 1}) {
                        int next = pixel + offset;
                        if (next < 0 || next >= solid.length || !solid[next]
                                || Math.abs(next % size - pixel % size) > 1) continue;
                        solid[next] = false; queue.add(next);
                    }
                }
                if (area >= 8) fragments++;
            }
            assertTrue(fragments >= 3, id + " wreck fragments have fused");
        }
    }
}
