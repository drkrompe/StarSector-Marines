package com.dillon.starsectormarines.tools.tilesetauthoring;

import com.dillon.starsectormarines.battle.world.tiles.GridLayout;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class OpeningTilesetTest {
    @Test
    void cardinalSlotsMatchTheRuntimeFacingAndRoundTripTheirLayout() {
        GridLayout layout = GridLayout.CARDINAL_2X2;
        assertEquals(layout, GridLayout.fromJson(TilesetExport.jsonLayout(layout)));
        for (int facing = 0; facing < 4; facing++) {
            String slot = BlockSlots.of(layout).get(facing);
            assertTrue(BlockSlots.fits(layout, slot));
            assertFalse(BlockSlots.fits(GridLayout.WALL_3X3, slot));
            assertArrayEquals(layout.resolve(0, 0, facing == 0, facing == 2, facing == 1, facing == 3),
                    BlockSlots.offset(slot));
        }
        assertFalse(BlockSlots.fits(layout, "nw"));
    }

    @Test
    void materialPairingsSurviveDocumentEditsAndExport(@TempDir Path dir) throws Exception {
        TilesetDocument doc = TilesetDocument.read(Path.of("art-source/tilesets/colony-walls.tileset-authoring.json"));
        var spec = doc.blocks.get(0);
        TilesetOperations.setBlock(doc.entries, doc.blocks, spec.id, spec.layout, spec.fillRgb, Map.of());
        Path path = dir.resolve("roundtrip.json");
        doc.write(path);
        doc = TilesetDocument.read(path);
        TileRegistry registry = new TileRegistry();
        registry.ingestSheet(TilesetExport.tileset("test.png", doc.cellPx, doc.entries, doc.blocks));
        var block = registry.block(spec.id);
        assertEquals(spec.windowBlockId, block.windowBlockId);
        assertEquals(spec.doorBlockId, block.doorBlockId);
        assertThrows(IllegalStateException.class, registry::validateReferences,
                "a missing paired opening must fail catalog validation");
    }

    @Test
    void shippedOpeningVoidsRemainTransparentInEveryFacing() throws Exception {
        TileRegistry registry = TileRegistry.installed();
        var image = ImageIO.read(Path.of("mod/graphics/tilesets/colony-openings.png").toFile());
        for (String family : new String[]{"masonry", "ceramic", "olive"}) {
            for (String kind : new String[]{"window", "door"}) {
                var block = registry.block("colony." + family + "-" + kind);
                for (int facing = 0; facing < 4; facing++) {
                    int[] cell = block.resolve(facing == 0, facing == 2, facing == 1, facing == 3);
                    // Probe the void as a region, not average alpha (which could hide a plugged gap).
                    for (int y = kind.equals("window") ? 24 : 4; y < 60; y++) {
                        if (kind.equals("door") && y >= 23 && y < 42) continue;
                        for (int x = 16; x < 48; x++) {
                            int px = x, py = y;
                            for (int turn = 0; turn < facing; turn++) {
                                int oldX = px; px = 63 - py; py = oldX;
                            }
                            int alpha = image.getRGB(cell[0] * 64 + px, cell[1] * 64 + py) >>> 24;
                            assertTrue(alpha <= 2, block.id + " facing " + facing + " has paint in its open floor");
                        }
                    }
                }
            }
        }
    }
}
