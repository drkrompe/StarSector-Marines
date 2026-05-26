package com.dillon.starsectormarines.battle;

import com.dillon.starsectormarines.battle.map.TileManifest;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Regression guard for the fill colors in {@link TileManifest}. Samples the
 * source PNG and asserts the {@code ROAD_FILL_RGB} / {@code COURTYARD_FILL_RGB}
 * constants still match the art. Fails loudly if either the sheet is rebased
 * (autotile blocks moved) or the constants drift from the sampled colors.
 *
 * <p>Fill colors are what the renderer paints when {@link TileManifest#pickRoadTile}
 * / {@link TileManifest#pickCourtyardTile} return null — i.e., the cell has no
 * wall neighbors. Sampling target is the visible interior of each autotile,
 * not the geometric center cell of the 3×3 (those center cells are transparent
 * on this sheet, which is the autotile convention).
 */
public class TileManifestFillColorTest {

    private static final Path SHEET = Paths.get("mod/graphics/tilesets/urban-tileset-2.png");
    private static final int TILE = 32;

    @Test
    void roadFillMatchesSheet() throws Exception {
        BufferedImage img = load();
        // Road autotile center cell (13, 1) is opaque — sample directly.
        int sampled = sampleRgb(img, 13, 1, TILE / 2, TILE / 2);
        assertEquals(TileManifest.ROAD_FILL_RGB, sampled,
                () -> String.format("ROAD_FILL_RGB drifted from sheet — declared 0x%06X, sampled 0x%06X",
                        TileManifest.ROAD_FILL_RGB, sampled));
    }

    @Test
    void courtyardFillMatchesSheet() throws Exception {
        BufferedImage img = load();
        // Courtyard 3×3's geometric center (1, 1) is transparent — autotile
        // convention for the "no walls anywhere" case. Sample the open-floor
        // quadrant of an edge tile instead. Use NW tile (0, 0)'s SE corner —
        // that quadrant is the open-floor art, away from the stone bevel.
        int sampled = sampleRgb(img, 0, 0, 24, 24);
        assertEquals(TileManifest.COURTYARD_FILL_RGB, sampled,
                () -> String.format("COURTYARD_FILL_RGB drifted from sheet — declared 0x%06X, sampled 0x%06X",
                        TileManifest.COURTYARD_FILL_RGB, sampled));
    }

    private static BufferedImage load() throws Exception {
        try (var in = Files.newInputStream(SHEET)) {
            return ImageIO.read(in);
        }
    }

    private static int sampleRgb(BufferedImage img, int col, int row, int dx, int dy) {
        int argb = img.getRGB(col * TILE + dx, row * TILE + dy);
        return argb & 0x00FFFFFF;
    }
}
