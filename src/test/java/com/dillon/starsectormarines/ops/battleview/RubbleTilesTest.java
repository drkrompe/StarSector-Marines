package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import org.junit.jupiter.api.Test;
import org.json.JSONObject;

import javax.imageio.ImageIO;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class RubbleTilesTest {
    @Test void everyMaterialRetainsAllSixteenExposedFaceCombinations() throws Exception {
        TileRegistry registry = TileRegistry.installed();
        var image = ImageIO.read(Path.of("mod/graphics/tilesets/colony-rubble.png").toFile());
        for (String family : new String[]{"masonry", "ceramic", "olive"}) {
            for (int mask = 0; mask < 16; mask++) {
                var remnant = new CellTopology.WallRemnant("colony." + family + "-wall",
                        CellTopology.GroundKind.INDOOR, null, mask);
                var skin = RubbleTiles.skin(registry, remnant, "urban.wall");
                assertEquals("colony." + family + "-rubble", skin.id);
                int[] cell = RubbleTiles.cell(skin, remnant);
                int slot = (mask & 1) | ((mask & 4) >> 1) | ((mask & 2) << 1) | (mask & 8);
                assertArrayEquals(new int[]{skin.originCol + slot % 4, skin.originRow + slot / 4}, cell);
                int empty = 0, solid = 0;
                for (int y = 0; y < 64; y++) for (int x = 0; x < 64; x++) {
                    int alpha = image.getRGB(cell[0] * 64 + x, cell[1] * 64 + y) >>> 24;
                    if (alpha <= 4) empty++;
                    if (alpha > 128) solid++;
                }
                // A floor opening is a spatial promise, not just an alpha total.
                for (int y = 30; y < 34; y++) for (int x = 30; x < 34; x++) {
                    int alpha = image.getRGB(cell[0] * 64 + x, cell[1] * 64 + y) >>> 24;
                    assertTrue(alpha <= 4, "the central breach must stay open");
                }
                assertTrue(empty > 900, "rubble must expose floor between fragments");
                assertTrue(solid > 350, "each rubble frame must contain substantial debris");
            }
        }
    }

    @Test void rubblePairsMustResolveAfterAllCatalogSheetsLoad() throws Exception {
        TileRegistry registry = new TileRegistry();
        registry.ingestSheet(new JSONObject("{sheet:'wall.png',cellPx:64,blocks:["
                + "{id:'wall',layout:'wall-faces-4x4',origin:[0,0],rubbleBlock:'broken'}]}"));
        assertThrows(IllegalStateException.class, registry::validateReferences);
        registry.ingestSheet(new JSONObject("{sheet:'debris.png',cellPx:64,blocks:["
                + "{id:'broken',layout:'wall-faces-4x4',origin:[0,0]}]}"));
        assertDoesNotThrow(registry::validateReferences);
    }

    @Test void unpairedWallsAndNonWallRubbleKeepTheExistingFallback() {
        TileRegistry registry = TileRegistry.installed();
        assertNull(RubbleTiles.skin(registry, null, "urban.wall"));
        assertNull(RubbleTiles.skin(registry, new CellTopology.WallRemnant("urban.wall",
                CellTopology.GroundKind.INDOOR, null, 3), "urban.wall"));
    }
}
