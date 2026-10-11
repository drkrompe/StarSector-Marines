package com.dillon.starsectormarines.tools.tilesetauthoring;

import com.dillon.starsectormarines.battle.world.model.BarrelDamage;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import java.nio.file.Path;
import java.util.ArrayDeque;
import static org.junit.jupiter.api.Assertions.*;

class BarrelDamageArtTest {
    @Test void fullFramesRetainTheirProfilesAndDebrisHasOpenShellsAndSeparatedScraps() throws Exception {
        var registry = TileRegistry.installed();
        var atlas = ImageIO.read(Path.of("mod/graphics/doodads/barrel-damage.png").toFile());
        for (String id : BarrelDamage.IDS) for (String state : new String[]{"damaged", "destroyed"}) {
            var original = registry.doodad(id);
            var def = registry.doodad(id + "-" + state);
            assertNotNull(def);
            assertEquals(original.footprintCellsX, def.footprintCellsX);
            assertEquals(original.footprintCellsY, def.footprintCellsY);
            boolean destroyed = state.equals("destroyed");
            assertEquals(destroyed ? 0 : original.cover.level(), def.cover.level());
            assertEquals(destroyed ? 0 : original.ballisticHalfHeight, def.ballisticHalfHeight, .0001f);
            int width = def.footprintCellsX * def.sourceCellPx, height = def.footprintCellsY * def.sourceCellPx;
            int ox = def.col * def.sourceCellPx, oy = def.row * def.sourceCellPx;
            boolean[] solid = new boolean[width * height];
            for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
                int alpha = atlas.getRGB(ox + x, oy + y) >>> 24;
                solid[y * width + x] = alpha > 40;
                if (x == 0 || y == 0 || x == width - 1 || y == height - 1)
                    assertTrue(alpha <= 4, id + " clips its full frame");
            }
            if (!destroyed) continue;
            // The first C-shaped shell's center must expose the ground, not become a solid lid.
            for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++)
                assertTrue((atlas.getRGB(ox + (int)(width * .28) + dx, oy + (int)(height * .26) + dy) >>> 24) <= 4,
                        id + " fills its open shell");
            int fragments = 0;
            for (int start = 0; start < solid.length; start++) {
                if (!solid[start]) continue;
                var pending = new ArrayDeque<Integer>(); pending.add(start); solid[start] = false;
                int area = 0;
                while (!pending.isEmpty()) {
                    int pixel = pending.remove(); area++;
                    for (int offset : new int[]{-width, width, -1, 1}) {
                        int next = pixel + offset;
                        if (next < 0 || next >= solid.length || !solid[next]) continue;
                        if (Math.abs(next % width - pixel % width) > 1) continue;
                        solid[next] = false; pending.add(next);
                    }
                }
                if (area >= 4) fragments++;
                assertTrue(area < width * height / 16, id + " debris has become a solid slab");
            }
            assertTrue(fragments >= 5, id + " needs visibly separated scraps");
        }
    }
}
