package com.dillon.starsectormarines.tools.tilesetauthoring;

import org.junit.jupiter.api.Test;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The click rules, which a person builds a habit around.
 *
 * <p>Asserted directly because they are invisible everywhere else: a selection
 * that clears when it should have extended looks like a slip of the hand rather
 * than a bug, and the cost lands on whoever has just lost a careful pick.
 */
class SheetSelectionTest {

    private static final boolean PLAIN = false;
    private static final boolean TOGGLE = true;
    private static final boolean EXTEND = true;

    /** A row of four cells, each 10 wide, so band arithmetic is readable. */
    private static List<TilesetExport.Entry> row() {
        List<TilesetExport.Entry> entries = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            entries.add(new TilesetExport.Entry(
                    new SheetSlicer.Piece(i * 10, 0, 10, 10), "piece-" + i));
        }
        return entries;
    }

    @Test
    void aPlainClickReplacesWhateverWasPicked() {
        SheetSelection selection = new SheetSelection();
        selection.click(1, PLAIN, PLAIN);
        selection.click(3, PLAIN, PLAIN);
        assertArrayEquals(new int[]{3}, selection.toArray());
    }

    @Test
    void toggleAddsThenRemovesTheSameCell() {
        SheetSelection selection = new SheetSelection();
        selection.click(1, TOGGLE, PLAIN);
        selection.click(2, TOGGLE, PLAIN);
        assertArrayEquals(new int[]{1, 2}, selection.toArray());
        selection.click(1, TOGGLE, PLAIN);
        assertArrayEquals(new int[]{2}, selection.toArray());
    }

    @Test
    void extendRunsFromTheLastCellClickedRatherThanTheLowestPicked() {
        SheetSelection selection = new SheetSelection();
        selection.click(3, PLAIN, PLAIN);
        selection.click(1, PLAIN, EXTEND);
        assertArrayEquals(new int[]{1, 2, 3}, selection.toArray());
    }

    @Test
    void extendWithNothingToMeasureFromPicksJustThatCell() {
        SheetSelection selection = new SheetSelection();
        selection.click(2, PLAIN, EXTEND);
        assertArrayEquals(new int[]{2}, selection.toArray());
    }

    @Test
    void clickingTheBackdropClearsButAModifiedMissDoesNot() {
        SheetSelection selection = new SheetSelection();
        selection.click(1, PLAIN, PLAIN);
        selection.click(-1, TOGGLE, PLAIN);
        assertArrayEquals(new int[]{1}, selection.toArray(), "a modified miss kept the pick");
        selection.click(-1, PLAIN, PLAIN);
        assertTrue(selection.isEmpty());
    }

    @Test
    void aBandTakesEveryCellItTouches() {
        SheetSelection selection = new SheetSelection();
        // Straddles the boundary between the second and third cells; both are
        // touched, and taking only cells swallowed whole would surprise anyone
        // who dragged across a row.
        selection.band(row(), new Rectangle(15, 2, 10, 4), false);
        assertArrayEquals(new int[]{1, 2}, selection.toArray());
    }

    @Test
    void aBandReplacesUnlessItIsAdditive() {
        SheetSelection selection = new SheetSelection();
        selection.click(3, PLAIN, PLAIN);
        selection.band(row(), new Rectangle(0, 0, 5, 5), false);
        assertArrayEquals(new int[]{0}, selection.toArray());

        selection.band(row(), new Rectangle(20, 0, 5, 5), true);
        assertArrayEquals(new int[]{0, 2}, selection.toArray());
    }

    @Test
    void aBandThatTouchesNothingClearsRatherThanKeepingAStalePick() {
        SheetSelection selection = new SheetSelection();
        selection.click(2, PLAIN, PLAIN);
        selection.band(row(), new Rectangle(0, 50, 5, 5), false);
        assertTrue(selection.isEmpty());
    }

    @Test
    void aSelectionSetFromTheTableDoesNotMoveTheAnchor() {
        SheetSelection selection = new SheetSelection();
        selection.click(3, PLAIN, PLAIN);
        // The table pushing rows in says nothing about where a later shift-click
        // should measure from; the anchor stays where the person last clicked.
        selection.set(new int[]{0});
        selection.click(1, PLAIN, EXTEND);
        assertArrayEquals(new int[]{0, 1, 2, 3}, selection.toArray());
    }

    @Test
    void containsAnswersForTheCanvasWithoutCopyingTheSelection() {
        SheetSelection selection = new SheetSelection();
        selection.click(2, PLAIN, PLAIN);
        assertTrue(selection.contains(2));
        assertFalse(selection.contains(1));
    }
}
