package com.dillon.starsectormarines.tools.tilesetauthoring;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Moving a whole plate's cut as one grid.
 *
 * <p>The thing being pinned is that the grid <em>describes</em> cells that were
 * already regular before it replaces them: a patch read off a plate and written
 * straight back must not move a pixel, or every visit to this screen would nudge
 * the sheet. Everything else here is about what happens when it is deliberately
 * moved.
 */
public class GridPatchTest {

    /** A cols x rows plate of touching cells, the shape a wall block is cut in. */
    private static List<TilesetExport.Entry> plate(int originX, int originY,
                                                   int cols, int rows, int cell) {
        List<TilesetExport.Entry> entries = new ArrayList<>();
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                entries.add(new TilesetExport.Entry(
                        new SheetSlicer.Piece(originX + col * cell, originY + row * cell,
                                cell, cell),
                        "doodad.road.c" + col + "r" + row));
            }
        }
        return entries;
    }

    private static GridPatch patchOf(List<TilesetExport.Entry> entries) {
        GridPatch.Derived derived = GridPatch.of(entries);
        assertNull(derived.refusal(), "expected these to form a grid");
        return derived.patch();
    }

    @Test
    void aBlocksNineCellsAreReadBackAsTheGridTheyWereCutOn() {
        GridPatch patch = patchOf(plate(96, 0, 3, 3, 32));

        assertEquals(3, patch.cut().cols());
        assertEquals(3, patch.cut().rows());
        assertEquals(96.0, patch.cut().originX(), 1e-9);
        assertEquals(32.0, patch.cut().pitchX(), 1e-9);
        assertEquals(new SheetSlicer.Piece(96, 0, 96, 96), patch.bounds());
        assertEquals(0, patch.drift(),
                "reading a regular plate and writing it back must not move anything");
    }

    /** One piece is the 1x1 case, so a single correction is the same operation. */
    @Test
    void onePieceIsAOneByOnePatch() {
        GridPatch patch = patchOf(List.of(new TilesetExport.Entry(
                new SheetSlicer.Piece(41, 2, 30, 29), "piece-0")));

        assertEquals(1, patch.cut().cols());
        assertEquals(1, patch.cut().rows());
        assertEquals(new SheetSlicer.Piece(41, 2, 30, 29), patch.bounds());
        assertEquals(0, patch.drift());
    }

    @Test
    void movingTheOriginMovesEveryCellAndResizesNone() throws IOException {
        List<TilesetExport.Entry> entries = plate(96, 0, 3, 3, 32);
        GridPatch patch = patchOf(entries);

        GridPatch.Applied applied = patch
                .withCut(patch.cut().withColumnAxis(94.0, 32.0))
                .applyTo(512, 192);

        assertEquals(9, applied.moved());
        assertEquals(9, applied.shifted());
        assertEquals(2, applied.maxShift());
        for (TilesetExport.Entry entry : entries) {
            assertEquals(32, entry.piece.width(), entry.id + " must keep its size");
            assertEquals(32, entry.piece.height(), entry.id + " must keep its size");
        }
        assertEquals(new SheetSlicer.Piece(94, 0, 32, 32), entries.get(0).piece);
        assertEquals(new SheetSlicer.Piece(158, 64, 32, 32), entries.get(8).piece);
    }

    /** Resizing the cell moves the seams with it, so the cells stay touching. */
    @Test
    void changingTheCellSizeKeepsTheCellsTouching() throws IOException {
        List<TilesetExport.Entry> entries = plate(0, 0, 3, 1, 32);
        GridPatch patch = patchOf(entries);

        patch.withCut(patch.cut().withColumnAxis(0.0, 30.0)).applyTo(512, 192);

        assertEquals(new SheetSlicer.Piece(0, 0, 30, 32), entries.get(0).piece);
        assertEquals(new SheetSlicer.Piece(30, 0, 30, 32), entries.get(1).piece);
        assertEquals(new SheetSlicer.Piece(60, 0, 30, 32), entries.get(2).piece);
    }

    /**
     * A plate whose pitch is not a whole number reads back as the cells it has.
     *
     * <p>This is the fault the patch exists for: 47.55 rounds to three different
     * widths across three columns, and a grid that could only describe whole
     * pixels would have to move one of them to say so.
     */
    @Test
    void aPitchThatIsNotAWholeNumberSurvivesBeingReadBack() {
        List<TilesetExport.Entry> entries = new ArrayList<>();
        GridCut source = new GridCut(3, 1, 0.0, 47.55, 0.0, 32.0);
        for (int col = 0; col < 3; col++) {
            entries.add(new TilesetExport.Entry(source.cell(col, 0), "cell-" + col));
        }
        assertNotEquals(entries.get(0).piece.width(), entries.get(1).piece.width(),
                "this plate's columns are deliberately not all the same width");

        GridPatch patch = patchOf(entries);

        assertNotEquals(Math.rint(patch.cut().pitchX()), patch.cut().pitchX(),
                "a fractional pitch must not be rounded away");
        assertEquals(0, patch.drift(), "and reading it back must reproduce the cells exactly");
    }

    /**
     * A cell somebody already nudged is still in its column.
     *
     * <p>Otherwise the one screen that fixes a cut would refuse every plate that
     * had ever been fixed on it.
     */
    @Test
    void aCellNudgedByAPixelStaysInItsColumn() {
        List<TilesetExport.Entry> entries = plate(0, 0, 3, 3, 32);
        entries.get(4).piece = new SheetSlicer.Piece(33, 32, 32, 32);

        GridPatch patch = patchOf(entries);

        assertEquals(3, patch.cut().cols());
        assertEquals(3, patch.cut().rows());
        assertEquals(1, patch.drift(),
                "and adopting the grid says how far it would move it back");
    }

    /**
     * A variant pool's cells are a cross, and that is one grid.
     *
     * <p>{@code floors.brick} really is five cells on {@code Floors_Tiles}: one,
     * then three, then one, on the sheet's own 47.55px pitch. Requiring a filled
     * rectangle refused it for a reason that had nothing to do with the art —
     * they were cut on one grid and moving that grid is the whole point.
     */
    @Test
    void aVariantPoolsCrossOfCellsIsTheGridItLiesOn() {
        GridCut sheet = new GridCut(19, 4, 0.0, 47.55, 0.0, 49.346153846);
        int[][] cross = {{17, 1}, {16, 2}, {17, 2}, {18, 2}, {17, 3}};
        List<TilesetExport.Entry> entries = new ArrayList<>();
        for (int[] at : cross) {
            TilesetExport.Entry entry = new TilesetExport.Entry(
                    sheet.cell(at[0], at[1]), "doodad.floors.c" + at[0] + "r" + at[1]);
            entry.blockId = "floors.brick";
            entries.add(entry);
        }

        GridPatch patch = patchOf(entries);

        assertEquals(3, patch.cut().cols());
        assertEquals(3, patch.cut().rows());
        assertTrue(patch.isSparse(), "five cells do not fill a 3x3");
        assertEquals(0, patch.drift(),
                "the pitch they were cut on must be read back exactly");
        assertEquals(47.55, patch.cut().pitchX(), 0.2);
    }

    /** A sparse patch moves its own cells and invents nothing for the empty ones. */
    @Test
    void movingASparsePatchMovesOnlyTheCellsInIt() throws IOException {
        List<TilesetExport.Entry> entries = new ArrayList<>(plate(0, 0, 3, 3, 32));
        entries.removeIf(entry -> !entry.id.endsWith("c1r1") && !entry.id.endsWith("c2r2"));
        GridPatch patch = patchOf(entries);

        GridPatch.Applied applied = patch
                .withCut(patch.cut().withColumnAxis(2.0, 32.0))
                .applyTo(512, 192);

        assertEquals(2, applied.moved(), "only the selected cells are the patch");
        assertEquals(new SheetSlicer.Piece(2, 32, 32, 32), entries.get(0).piece);
        assertEquals(new SheetSlicer.Piece(34, 64, 32, 32), entries.get(1).piece);
    }

    /** Pieces no single origin and pitch can describe are not a patch at all. */
    @Test
    void piecesThatAreNotOnOneLatticeAreRefused() {
        List<TilesetExport.Entry> entries = List.of(
                new TilesetExport.Entry(new SheetSlicer.Piece(0, 0, 32, 32), "prop-a"),
                new TilesetExport.Entry(new SheetSlicer.Piece(500, 0, 32, 32), "prop-b"));

        GridPatch.Derived derived = GridPatch.of(entries);

        assertNull(derived.patch());
        assertTrue(derived.refusal().contains("evenly"), derived.refusal());
        assertTrue(derived.refusal().contains("Pick one cell"), derived.refusal());
    }

    /** A refusal names the block when the selection is one, since that is what was picked. */
    @Test
    void aRefusalNamesTheBlockItWasAskedAbout() {
        List<TilesetExport.Entry> entries = new ArrayList<>();
        for (int[] at : new int[][]{{0, 0}, {500, 0}}) {
            TilesetExport.Entry entry = new TilesetExport.Entry(
                    new SheetSlicer.Piece(at[0], at[1], 32, 32), "cell-" + at[0]);
            entry.blockId = "floors.brick";
            entries.add(entry);
        }

        assertTrue(GridPatch.of(entries).refusal().startsWith("floors.brick's 2 cells"),
                GridPatch.of(entries).refusal());
    }

    /**
     * A selection that skips a column still lands on the right addresses.
     *
     * <p>Numbering the lines it does have 0, 1, 2 would put the fourth cell where
     * the third belongs, and the grid would come out a quarter narrower than the
     * plate it describes.
     */
    @Test
    void aSkippedColumnKeepsItsPlaceInTheLattice() {
        List<TilesetExport.Entry> entries = new ArrayList<>(plate(0, 0, 4, 1, 32));
        entries.remove(1);

        GridPatch patch = patchOf(entries);

        assertEquals(4, patch.cut().cols(), "three cells spanning four columns");
        assertEquals(32.0, patch.cut().pitchX(), 0.5);
        assertEquals(0, patch.drift());
    }

    /**
     * A plate cut with gutters is counted on its step, not on its cell size.
     *
     * <p>Three cells 40 apart are three columns. Estimating the lattice from how
     * wide a cell is reads the third one as column 3 of four, which is a plate a
     * third too wide with a hole in it.
     */
    @Test
    void aPlateWithGuttersIsCountedOnItsStep() {
        List<TilesetExport.Entry> entries = new ArrayList<>();
        for (int col = 0; col < 3; col++) {
            entries.add(new TilesetExport.Entry(
                    new SheetSlicer.Piece(col * 40, 0, 32, 32), "cell-" + col));
        }

        assertEquals(3, patchOf(entries).cut().cols());
    }

    @Test
    void nothingIsSelectedIsSaidPlainlyRatherThanRefused() {
        GridPatch.Derived derived = GridPatch.of(List.of());

        assertNull(derived.patch());
        assertTrue(derived.refusal().contains("Pick a piece"), derived.refusal());
    }

    /** A patch half-applied is worse than one not applied. */
    @Test
    void aGridThatWouldLeaveTheSheetMovesNothing() {
        List<TilesetExport.Entry> entries = plate(96, 0, 3, 3, 32);
        GridPatch patch = patchOf(entries);

        IOException refused = assertThrows(IOException.class, () -> patch
                .withCut(patch.cut().withColumnAxis(440.0, 32.0))
                .applyTo(512, 192));

        assertTrue(refused.getMessage().contains("runs off"), refused.getMessage());
        for (TilesetExport.Entry entry : entries) {
            assertTrue(entry.piece.x() >= 96 && entry.piece.x() <= 160,
                    entry.id + " moved despite the refusal: " + entry.piece);
        }
    }
}
