package com.dillon.starsectormarines.tools.tilesetauthoring;

import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;

import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The preview is only worth anything if the two images differ exactly where the
 * candidate art was substituted and nowhere else — a different map, or an
 * identical one, would both make the comparison meaningless.
 */
class TilesetMapPreviewTest {

    private static final Path PROJECT = Path.of(".");
    /** Small and coarse: this asserts the mechanism, not the art. */
    private static final int GRID = 40;
    private static final int CELL_PX = 6;
    private static final long SEED = 1L;

    /** An authored atlas of flat magenta — unmistakable against any shipped art. */
    private static BufferedImage candidateAtlas(int cellPx) {
        BufferedImage atlas = new BufferedImage(cellPx * 3, cellPx * 3, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = atlas.createGraphics();
        g.setColor(new Color(0xFF, 0x00, 0xFF));
        g.fillRect(0, 0, atlas.getWidth(), atlas.getHeight());
        g.dispose();
        return atlas;
    }

    private static long differingPixels(BufferedImage a, BufferedImage b) {
        assertEquals(a.getWidth(), b.getWidth());
        assertEquals(a.getHeight(), b.getHeight());
        long differing = 0;
        for (int y = 0; y < a.getHeight(); y++) {
            for (int x = 0; x < a.getWidth(); x++) {
                if (a.getRGB(x, y) != b.getRGB(x, y)) differing++;
            }
        }
        return differing;
    }

    @Test
    void withNoCandidateBoundBothMapsAreTheBaseline() throws Exception {
        TilesetMapPreview.Result result = TilesetMapPreview.render(
                PROJECT, candidateAtlas(32), 32, List.of(), SEED, GRID, CELL_PX);

        assertNotNull(result.baseline());
        assertEquals(0, differingPixels(result.baseline(), result.substituted()));
        assertTrue(result.notes().stream().anyMatch(note -> note.contains("baseline")),
                "the preview should say why nothing changed: " + result.notes());
    }

    @Test
    void substitutingAShippedDoodadRepaintsTheMapWithoutRegeneratingIt() throws Exception {
        // urban.wall is placed all over a generated city, so its art is visible
        // in any seed; substituting it must change paint and nothing else.
        TilesetMapPreview.Result result = TilesetMapPreview.render(
                PROJECT, candidateAtlas(32), 32,
                List.of(new TilesetMapPreview.Substitution("urban.wall", 0, 0, 3, 3)),
                SEED, GRID, CELL_PX);

        long differing = differingPixels(result.baseline(), result.substituted());
        assertTrue(differing > 0,
                "the candidate art should be visible somewhere: " + result.notes());
        long total = (long) result.baseline().getWidth() * result.baseline().getHeight();
        assertTrue(differing < total,
                "a substitution repaints some cells, not the whole image");
    }

    @Test
    void theSameSeedGivesTheSameMapOnBothSidesOfTheComparison() throws Exception {
        // If substituting changed which content the generator picked, the two
        // images would differ everywhere and the comparison would be worthless.
        TilesetMapPreview.Result plain = TilesetMapPreview.render(
                PROJECT, candidateAtlas(32), 32, List.of(), SEED, GRID, CELL_PX);
        TilesetMapPreview.Result swapped = TilesetMapPreview.render(
                PROJECT, candidateAtlas(32), 32,
                List.of(new TilesetMapPreview.Substitution("urban.wall", 0, 0, 3, 3)),
                SEED, GRID, CELL_PX);

        assertEquals(0, differingPixels(plain.baseline(), swapped.baseline()),
                "the baseline is the same map whether or not a candidate is bound");
    }

    @Test
    void anUnknownIdIsReportedRatherThanSilentlyIgnored() throws Exception {
        TilesetMapPreview.Result result = TilesetMapPreview.render(
                PROJECT, candidateAtlas(32), 32,
                List.of(new TilesetMapPreview.Substitution("nope.not-a-tile", 0, 0, 1, 1)),
                SEED, GRID, CELL_PX);

        assertEquals(0, differingPixels(result.baseline(), result.substituted()));
        assertTrue(result.notes().stream().anyMatch(note -> note.contains("nope.not-a-tile")),
                "the operator should be told which binding did nothing: " + result.notes());
    }

    @Test
    void aShippedIdIsLocatedOnItsOwnSheetAtItsOwnCells() {
        TileRegistry registry = TileRegistry.installed();
        assertNotNull(registry, "the test installer provides the shipped catalog");

        TilesetMapPreview.Target wall = TilesetMapPreview.locate(registry, "urban.wall");
        assertNotNull(wall, "urban.wall is a shipped block");
        assertTrue(wall.sheetPath().endsWith("urban-tileset.png"), wall.sheetPath());
        assertEquals(3, wall.cellsX(), "a 3x3 layout owns its whole patch");
        assertEquals(3, wall.cellsY());
        assertTrue(wall.cellPx() > 0);

        assertNull(TilesetMapPreview.locate(registry, "nope.not-a-tile"));
    }

    @Test
    void aDoodadIsLocatedWithTheFootprintItActuallyCovers() {
        TileRegistry registry = TileRegistry.installed();
        TilesetMapPreview.Target crate = TilesetMapPreview.locate(registry, "doodad.crate");

        assertNotNull(crate, "doodad.crate ships in the urban tileset");
        assertTrue(crate.cellsX() >= 1 && crate.cellsY() >= 1);
        assertFalse(crate.sheetPath().isBlank());
    }
}
