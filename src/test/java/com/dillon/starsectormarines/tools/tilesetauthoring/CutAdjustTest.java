package com.dillon.starsectormarines.tools.tilesetauthoring;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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

        adjuster.show(sheet, List.of(entry));
        assertEquals(entry.piece, adjuster.proposedPatch().bounds(),
                "it opens on the cut as it stands");
        assertTrue(!adjuster.isChanged(), "and reports nothing changed until something does");
    }

    /** A selection no grid describes leaves the controls dead and says why. */
    @Test
    void anAdjusterShownPiecesThatAreNotOneGridSaysSo() {
        BufferedImage sheet = new BufferedImage(800, 64, BufferedImage.TYPE_INT_ARGB);
        CutAdjusterView adjuster = new CutAdjusterView(() -> { }, () -> { });

        adjuster.show(sheet, threePieces().subList(0, 2));
        assertNotNull(adjuster.patch(), "two cells side by side are a 2x1 grid");

        List<TilesetExport.Entry> apart = new ArrayList<>(threePieces().subList(0, 1));
        apart.add(new TilesetExport.Entry(new SheetSlicer.Piece(700, 0, 32, 32), "piece-far"));
        adjuster.show(sheet, apart);

        assertNull(adjuster.patch(), "two props 700px apart were never one plate");
        assertNull(adjuster.proposedPatch());
        assertTrue(!adjuster.isChanged());
    }
}
