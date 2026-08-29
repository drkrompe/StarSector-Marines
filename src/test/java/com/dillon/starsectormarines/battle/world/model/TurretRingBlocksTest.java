package com.dillon.starsectormarines.battle.world.model;

import com.dillon.starsectormarines.battle.world.tiles.GridBlockDef;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The rings a defense post is built from, and the split that separated one of
 * them from a ground surface.
 *
 * <p>{@code road.courtyard} was two things at once: the courtyard surface the
 * generator paves yards and runways with, and — through the same neighbour mask
 * — the outward-bowed cover ringing a WEDGE or TRAPEZOID turret. Redrawing a
 * courtyard as flatter paving would have quietly changed what a turret hides
 * behind, and nothing would have said so.
 *
 * <p>They are two blocks now, sharing their art. The check that matters is that
 * the split changed nothing: every cell the ring draws is the same pixels it
 * drew before. What it buys is that they are free to differ later, and this
 * test will not mind when they do — it compares what the ring resolves to
 * against the block the ring is declared from, not against a frozen picture.
 */
public class TurretRingBlocksTest {

    /** The eight cells around a turret; the centre is the turret itself. */
    private static List<int[]> ringOffsets() {
        List<int[]> offsets = new ArrayList<>();
        for (int relY = 1; relY >= -1; relY--) {
            for (int relX = -1; relX <= 1; relX++) {
                if (relX == 0 && relY == 0) continue;
                offsets.add(new int[]{relX, relY});
            }
        }
        return offsets;
    }

    @Test
    void theBowOutRingComesFromItsOwnBlock() {
        GridBlockDef revetment = TileRegistry.installed().block("road.revetment");
        assertNotNull(revetment, "road.revetment should be a block of its own");

        for (int[] offset : ringOffsets()) {
            TileManifest.TileFrame frame = TileManifest.turretBowOut(offset[0], offset[1]);
            assertTrue(within(revetment, frame),
                    "the ring cell for " + offset[0] + "," + offset[1] + " should lie in "
                            + "road.revetment's patch, not somewhere else on the sheet");
        }
    }

    /** And the embankment ring still comes from its own, unchanged by the split. */
    @Test
    void theEmbankmentRingStillComesFromItsOwnBlock() {
        GridBlockDef embankment = TileRegistry.installed().block("road.embankment");
        assertNotNull(embankment);
        for (int[] offset : ringOffsets()) {
            assertTrue(within(embankment, TileManifest.turretEmbankment(offset[0], offset[1])),
                    "the embankment ring should lie in road.embankment's patch");
        }
    }

    /**
     * The split is invisible: the revetment draws the courtyard's pixels.
     *
     * <p>Compared as pixels rather than as coordinates, because the two blocks
     * are packed at different origins by design — sameness of art is the claim,
     * not sameness of address.
     */
    @Test
    void theSplitChangedNothingThatIsDrawn() throws Exception {
        TileRegistry registry = TileRegistry.installed();
        GridBlockDef revetment = registry.block("road.revetment");
        GridBlockDef courtyard = registry.block("road.courtyard");
        assertNotNull(revetment);
        assertNotNull(courtyard);
        assertEquals(courtyard.sheetPath, revetment.sheetPath, "both are cut from one sheet");

        BufferedImage atlas = atlas(revetment.sheetPath);
        int cell = revetment.cellPx;
        for (int[] offset : ringOffsets()) {
            TileManifest.TileFrame drawn = TileManifest.turretBowOut(offset[0], offset[1]);
            int[] wasDrawn = courtyard.resolve(offset[1] > 0, offset[1] < 0,
                    offset[0] > 0, offset[0] < 0);
            assertNotNull(wasDrawn, "the courtyard resolved every ring cell before the split");
            assertArrayEquals(
                    pixels(atlas, wasDrawn[0], wasDrawn[1], cell),
                    pixels(atlas, drawn.col, drawn.row, cell),
                    "ring cell " + offset[0] + "," + offset[1] + " draws different art than it "
                            + "did before road.revetment was split out of road.courtyard");
        }
    }

    /** The courtyard keeps the fill that is most of what it looks like. */
    @Test
    void theCourtyardKeepsItsFill() {
        GridBlockDef courtyard = TileRegistry.installed().block("road.courtyard");
        assertNotNull(courtyard);
        assertNotNull(courtyard.fillRgb,
                "perimeter-3x3 resolves to nothing for the open interior, and the fill is what "
                        + "the renderer paints there — without it a courtyard has no surface");
    }

    private static boolean within(GridBlockDef block, TileManifest.TileFrame frame) {
        int span = block.layout == null ? 1 : block.layout.span();
        return frame.col >= block.originCol && frame.col < block.originCol + span
                && frame.row >= block.originRow && frame.row < block.originRow + span;
    }

    private static int[] pixels(BufferedImage atlas, int col, int row, int cell) {
        return atlas.getRGB(col * cell, row * cell, cell, cell, null, 0, cell);
    }

    private static BufferedImage atlas(String sheetPath) throws Exception {
        Path file = Paths.get("mod");
        for (String part : sheetPath.split("/")) file = file.resolve(part);
        assertTrue(Files.isRegularFile(file), "no atlas at " + file);
        return ImageIO.read(file.toFile());
    }
}
