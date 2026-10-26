package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The rocket truck works the corners: the hidden stand nearest the armor, held unless another is clearly nearer. */
class DustLedgerTechnicalTest {
    private static final float[] XS = {10f, 20f, 30f}, YS = {0f, 0f, 0f};

    @Test void itMakesForTheHiddenStandNearestTheArmor() {
        assertEquals(1, DustLedgerTechnical.choose(XS, YS, new boolean[] {true, true, false}, 32f, 0f, 0),
                "the nearest stand is in the armor's sight, so the next one back");
        assertEquals(2, DustLedgerTechnical.choose(XS, YS, new boolean[] {true, true, true}, 32f, 0f, -1));
    }

    @Test void itHoldsAHiddenStandUnlessAnotherIsClearlyNearer() {
        boolean[] all = {true, true, true};
        assertEquals(1, DustLedgerTechnical.choose(XS, YS, all, 26f, 0f, 1), "four cells nearer is not worth the drive");
        assertEquals(2, DustLedgerTechnical.choose(XS, YS, all, 35f, 0f, 1));
    }

    @Test void caughtInTheOpenWithNowhereHiddenItStays() {
        assertEquals(0, DustLedgerTechnical.choose(XS, YS, new boolean[3], 32f, 0f, 0));
    }

    @Test void aWallHidesAStand() {
        NavigationGrid grid = new NavigationGrid(20, 10);
        for (int y = 0; y < 10; y++) for (int x = 0; x < 20; x++) grid.setWalkableFloor(x, y);
        assertFalse(DustLedgerTechnical.hidden(grid, 2.5f, 5.5f, 17.5f, 5.5f));
        for (int y = 0; y < 10; y++) grid.setWalkable(10, y, false);
        assertTrue(DustLedgerTechnical.hidden(grid, 2.5f, 5.5f, 17.5f, 5.5f));
    }
}
