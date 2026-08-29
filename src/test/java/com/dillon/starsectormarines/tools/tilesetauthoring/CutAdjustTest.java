package com.dillon.starsectormarines.tools.tilesetauthoring;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Moving one piece's cut.
 *
 * <p>The whole point is that it is <em>one</em> piece: re-slicing or re-fitting
 * to correct a two-pixel boundary moves every other piece on the sheet, which
 * is what made a small correction expensive enough to avoid. So the check that
 * matters is that nothing else moved.
 */
public class CutAdjustTest {

    private static final Path EVIDENCE = Paths.get("build", "tileset-authoring");

    private static List<TilesetExport.Entry> threePieces() {
        List<TilesetExport.Entry> entries = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            entries.add(new TilesetExport.Entry(
                    new SheetSlicer.Piece(i * 40, 0, 32, 32), "piece-" + i));
        }
        return entries;
    }

    @Test
    void movingOneCutLeavesEveryOtherPieceWhereItWas() throws Exception {
        List<TilesetExport.Entry> entries = threePieces();
        SheetSlicer.Piece was = TilesetOperations.setCut(
                entries, "piece-1", 41, 2, 30, 29, 200, 64);

        assertEquals(new SheetSlicer.Piece(40, 0, 32, 32), was, "it returns what it replaced");
        assertEquals(new SheetSlicer.Piece(41, 2, 30, 29), entries.get(1).piece);
        assertEquals(new SheetSlicer.Piece(0, 0, 32, 32), entries.get(0).piece,
                "the piece before it must not move");
        assertEquals(new SheetSlicer.Piece(80, 0, 32, 32), entries.get(2).piece,
                "nor the piece after it");
    }

    /** The piece is still the same piece: only where its picture comes from changed. */
    @Test
    void thePieceKeepsEverythingElseAboutIt() throws Exception {
        List<TilesetExport.Entry> entries = threePieces();
        TilesetExport.Entry entry = entries.get(0);
        entry.blockId = "urban.wall";
        entry.slot = "nw";
        entry.footprintX = 2;
        entry.note = "the north-west corner";

        TilesetOperations.setCut(entries, "piece-0", 1, 1, 30, 30, 200, 64);

        assertEquals("piece-0", entry.id);
        assertEquals("urban.wall", entry.blockId);
        assertEquals("nw", entry.slot);
        assertEquals(2, entry.footprintX);
        assertEquals("the north-west corner", entry.note);
    }

    /** A rectangle that leaves the sheet or holds no pixels is refused. */
    @Test
    void aCutThatIsNotOnTheSheetIsRefused() {
        List<TilesetExport.Entry> entries = threePieces();

        IOException offSheet = assertThrows(IOException.class, () ->
                TilesetOperations.setCut(entries, "piece-0", 180, 0, 32, 32, 200, 64));
        assertTrue(offSheet.getMessage().contains("runs off"), offSheet.getMessage());

        IOException empty = assertThrows(IOException.class, () ->
                TilesetOperations.setCut(entries, "piece-0", 0, 0, 0, 32, 200, 64));
        assertTrue(empty.getMessage().contains("no picture"), empty.getMessage());

        assertThrows(IOException.class, () ->
                TilesetOperations.setCut(entries, "no-such-piece", 0, 0, 4, 4, 200, 64));

        assertEquals(new SheetSlicer.Piece(0, 0, 32, 32), entries.get(0).piece,
                "a refused cut must leave the piece alone");
    }

    /** The adjuster proposes what the controls say, and knows when that differs. */
    @Test
    void theAdjusterReportsWhatItWouldChange() {
        BufferedImage sheet = new BufferedImage(200, 64, BufferedImage.TYPE_INT_ARGB);
        TilesetExport.Entry entry = threePieces().get(0);
        CutAdjusterView adjuster = new CutAdjusterView(() -> { }, () -> { });

        adjuster.show(sheet, entry);
        assertEquals(entry.piece, adjuster.proposed(), "it opens on the cut as it stands");
        assertTrue(!adjuster.isChanged(), "and reports nothing changed until something does");
    }

    /**
     * The magnified view, painted. What it has to show is a boundary one pixel
     * out, so a picture is the only evidence that means anything.
     */
    @Test
    void theMagnifiedCutPaints() throws Exception {
        Path atlas = Paths.get("mod", "graphics", "tilesets", "urban-tileset-2.png");
        assertTrue(Files.isRegularFile(atlas), "no sheet at " + atlas);
        BufferedImage sheet = ImageIO.read(atlas.toFile());

        TilesetExport.Entry entry = new TilesetExport.Entry(
                new SheetSlicer.Piece(32, 0, 32, 32), "doodad.road.c1r0");
        CutAdjusterView adjuster = new CutAdjusterView(() -> { }, () -> { });
        adjuster.show(sheet, entry);
        adjuster.setSize(720, 480);
        layOut(adjuster);

        BufferedImage painted = new BufferedImage(720, 480, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = painted.createGraphics();
        try {
            adjuster.paint(graphics);
        } finally {
            graphics.dispose();
        }
        Files.createDirectories(EVIDENCE);
        Path out = EVIDENCE.resolve("cut-adjuster.png");
        ImageIO.write(painted, "PNG", out.toFile());

        assertTrue(distinctColours(painted) > 8,
                "the magnified cut painted nothing legible — see " + out.toAbsolutePath());
        assertTrue(hasCutOutline(painted),
                "the cut rectangle should be drawn over the plate — see " + out.toAbsolutePath());
    }

    /** The cut is drawn in a colour nothing on these sheets uses. */
    private static boolean hasCutOutline(BufferedImage image) {
        for (int y = 0; y < image.getHeight(); y += 2) {
            for (int x = 0; x < image.getWidth(); x += 2) {
                Color pixel = new Color(image.getRGB(x, y));
                if (pixel.getBlue() > 200 && pixel.getGreen() > 150 && pixel.getRed() < 110) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void layOut(Component component) {
        component.doLayout();
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) layOut(child);
        }
    }

    private static int distinctColours(BufferedImage image) {
        Set<Integer> seen = new HashSet<>();
        for (int y = 0; y < image.getHeight(); y += 2) {
            for (int x = 0; x < image.getWidth(); x += 2) {
                seen.add(image.getRGB(x, y));
                if (seen.size() > 64) return seen.size();
            }
        }
        return seen.size();
    }
}
