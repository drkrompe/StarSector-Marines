package com.dillon.starsectormarines.tools.tilesetauthoring;

import com.dillon.starsectormarines.battle.world.gen.forest.TreeCanopy;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class TreeCanopyArtTest {
    @Test void damageFramesKeepFootprintsAndClearMarginsWhileDebrisLeavesGroundVisible() throws Exception {
        for (String state : new String[]{"damaged", "destroyed"}) {
            var atlas = ImageIO.read(Path.of("mod/graphics/doodads/forest-" + state + ".png").toFile());
            for (TreeCanopy tree : TreeCanopy.values()) {
                var def = TileRegistry.installed().doodad(tree.id + "-" + state);
                assertEquals(tree.width, def.footprintCellsX); assertEquals(tree.height, def.footprintCellsY);
                int visible = 0;
                for (int y = 0; y < tree.height * 64; y++) for (int x = 0; x < tree.width * 64; x++) {
                    int alpha = atlas.getRGB(def.col * 64 + x, def.row * 64 + y) >>> 24;
                    if (alpha > 32) {
                        visible++;
                        assertTrue(tree.blocks(x / 64, tree.height - 1 - y / 64, 0),
                                tree.id + "-" + state + " overhangs footprint at " + x + "," + y);
                    }
                    if (x < 4 || y < 4 || x >= tree.width * 64 - 4 || y >= tree.height * 64 - 4)
                        assertTrue(alpha <= 4, tree.id + "-" + state + " clips the frame at " + x + "," + y);
                }
                double fraction = visible / (double)(tree.width * tree.height * 64 * 64);
                assertTrue(fraction > .03, "Damage art disappeared");
                if (state.equals("destroyed")) assertTrue(fraction < .4, "Debris became a solid ground carpet");
            }
        }
    }
    @Test void visibleFoliageNeverOverhangsPassableCellsAndFullFramesSurvivePacking() throws Exception {
        var atlas = ImageIO.read(Path.of("mod/graphics/doodads/forest-canopies.png").toFile());
        for (TreeCanopy tree : TreeCanopy.values()) {
            var def = TileRegistry.installed().doodad(tree.id);
            assertEquals(tree.width, def.footprintCellsX); assertEquals(tree.height, def.footprintCellsY);
            int pixels = 0;
            for (int y = 0; y < tree.height * 64; y++) for (int x = 0; x < tree.width * 64; x++) {
                int alpha = atlas.getRGB(def.col * 64 + x, def.row * 64 + y) >>> 24;
                if (alpha > 32) {
                    pixels++;
                    assertTrue(tree.blocks(x / 64, tree.height - 1 - y / 64, 0),
                            tree.id + " has visible canopy outside its blocked cells at " + x + "," + y);
                }
                if (x < 4 || y < 4 || x >= tree.width * 64 - 4 || y >= tree.height * 64 - 4)
                    assertTrue(alpha <= 4, "the full-frame empty perimeter must survive export");
            }
            assertTrue(pixels > tree.width * tree.height * 64 * 64 / 4, tree.id + " has lost its canopy");
        }
    }
}
