package com.dillon.starsectormarines.battle.vision;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The band of ground shown under a large solid prop: how deep it reaches and what it leaves alone. */
final class PropGroundRevealTest {

    private static final int W = 40;
    private static final int H = 40;
    /** A 12 x 10 opaque footprint with its lower-left corner here. */
    private static final int FX = 10, FY = 10, FW = 12, FH = 10;

    private final NavigationGrid grid = solidFootprint();
    private final boolean[] revealed = new boolean[W * H];
    private final boolean[] shown = new boolean[W * H];
    private final RevealChangeLog log = new RevealChangeLog();
    private final PropGroundReveal band = new PropGroundReveal();

    PropGroundRevealTest() {
        log.init(W, H);
        band.register(FX, FY, FW, FH);
    }

    @Test
    void groundIsShownADepthInFromARevealedFaceAndNoFurther() {
        revealFace();
        refresh();

        for (int d = 0; d <= PropGroundReveal.DEPTH; d++)
            assertTrue(shown[at(FX + d, 14)], "cell " + d + " in from the seen face is shown");
        assertFalse(shown[at(FX + PropGroundReveal.DEPTH + 1, 14)], "one past the band is still fogged");
        assertFalse(shown[at(FX + FW - 1, 14)], "the far side of the prop is still fogged");
    }

    @Test
    void nothingOutsideTheFootprintIsTouchedAndTheRealRevealIsNotWritten() {
        revealFace();
        boolean[] before = revealed.clone();
        refresh();

        assertFalse(shown[at(FX - 1, 14)], "ground beside the prop stays as fog left it");
        assertFalse(shown[at(FX + FW, 14)]);
        assertFalse(shown[at(FX + 3, FY - 1)]);
        assertEquals(Arrays.toString(before), Arrays.toString(revealed),
                "sight reads the real bitmap, which this pass must never write");
    }

    @Test
    void anUnseenPropShowsNothingAndALostFaceTakesItsBandWithIt() {
        refresh();
        for (boolean cell : shown) assertFalse(cell, "no face seen, no ground shown");

        revealFace();
        refresh();
        assertTrue(shown[at(FX + 2, 14)]);

        for (int y = FY; y < FY + FH; y++) revealed[at(FX, y)] = false;
        refresh();
        assertFalse(shown[at(FX + 2, 14)], "the band follows the face that earned it");
        assertFalse(shown[at(FX, 14)]);
    }

    @Test
    void aSeeThroughCellInsideTheFootprintIsNotPulledIntoTheBand() {
        grid.setSeeThrough(FX + 2, 14, true);
        revealFace();
        refresh();

        assertFalse(shown[at(FX + 2, 14)], "ordinary reveal governs a cell that is not solid");
        assertTrue(shown[at(FX + 1, 14)]);
    }

    @Test
    void everyFlipIsRecordedForThePictureThatKeepsACopy() {
        revealFace();
        refresh();
        log.seal();

        int[] rect = new int[4];
        assertTrue(log.unionSince(0, rect));
        assertTrue(rect[2] >= FX + PropGroundReveal.DEPTH && rect[0] <= FX,
                "the log covers the band, not only the cells the shadowcast revealed");
    }

    @Test
    void onlyLargePropsQualify() {
        assertFalse(PropGroundReveal.qualifies(3, 20), "a narrow prop is covered by its own art");
        assertTrue(PropGroundReveal.qualifies(4, 4));
        assertTrue(PropGroundReveal.qualifies(24, 20));
    }

    private void refresh() {
        band.refresh(grid, revealed, shown, log);
        // The service sets a cell's own bit when it is revealed; mirror that so the test is the whole rule.
        for (int i = 0; i < revealed.length; i++) if (revealed[i]) shown[i] = true;
    }

    private void revealFace() {
        for (int y = FY; y < FY + FH; y++) revealed[at(FX, y)] = true;
    }

    private static int at(int x, int y) {
        return y * W + x;
    }

    private static NavigationGrid solidFootprint() {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) grid.setWalkableFloor(x, y);
        for (int y = FY; y < FY + FH; y++) for (int x = FX; x < FX + FW; x++) {
            grid.setWalkable(x, y, false);
            grid.setSeeThrough(x, y, false);
        }
        return grid;
    }
}
