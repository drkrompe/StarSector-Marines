package com.dillon.starsectormarines.tools.tilesetauthoring;

import com.dillon.starsectormarines.battle.world.model.CoverPropDamage;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import java.nio.file.Path;
import java.util.ArrayDeque;
import static org.junit.jupiter.api.Assertions.*;

class CrateDamageArtTest {
    @Test void damageKeepsCoverAndRubbleHasSeparateFragmentsWithTransparentMargins() throws Exception {
        var registry = TileRegistry.installed();
        var atlas = ImageIO.read(Path.of("mod/graphics/doodads/crate-damage.png").toFile());
        for (String id : CoverPropDamage.CRATES) for (String state : new String[]{"damaged", "destroyed"}) {
            var def = registry.doodad(id + "-" + state);
            assertNotNull(def);
            assertEquals(1, def.footprintCellsX); assertEquals(1, def.footprintCellsY);
            boolean destroyed = state.equals("destroyed");
            assertEquals(destroyed ? 0 : registry.doodad(id).cover.level(), def.cover.level());
            assertEquals(destroyed ? 0 : registry.doodad(id).ballisticHalfHeight, def.ballisticHalfHeight, .0001f);
            boolean[] solid = new boolean[64 * 64];
            for (int y = 0; y < 64; y++) for (int x = 0; x < 64; x++) {
                int alpha = atlas.getRGB(def.col * 64 + x, def.row * 64 + y) >>> 24;
                solid[y * 64 + x] = alpha > 40;
                if (x == 0 || y == 0 || x == 63 || y == 63)
                    assertTrue(alpha <= 4, id + " clips its full frame");
            }
            if (destroyed) {
                int fragments = 0;
                for (int start = 0; start < solid.length; start++) {
                    if (!solid[start]) continue;
                    var pending = new ArrayDeque<Integer>(); pending.add(start); solid[start] = false;
                    int area = 0;
                    while (!pending.isEmpty()) {
                        int pixel = pending.remove(); area++;
                        for (int offset : new int[]{-64, 64, -1, 1}) {
                            int next = pixel + offset;
                            if (next < 0 || next >= solid.length || !solid[next]) continue;
                            if (Math.abs(next % 64 - pixel % 64) > 1) continue;
                            solid[next] = false; pending.add(next);
                        }
                    }
                    if (area >= 4) fragments++;
                    assertTrue(area < 250, id + " debris has become a solid slab");
                }
                assertTrue(fragments >= 5, id + " needs multiple visibly separated fragments");
            }
        }
    }
}
