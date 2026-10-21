package com.dillon.starsectormarines.battle.tender;

import com.dillon.starsectormarines.battle.nav.NavigationGrid.CellTag;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.power.TenderCallDown;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TenderLandingTest {

    private static NavigationGrid openGrid() {
        NavigationGrid grid = new NavigationGrid(80, 80);
        for (int x = 0; x < 80; x++) for (int y = 0; y < 80; y++) grid.setWalkableFloor(x, y);
        return grid;
    }

    @Test
    void aHullFacingEastTakesItsLengthAlongXAndOpensTheBayAheadOfTheNose() {
        TenderLanding.Footprint f = TenderLanding.footprint(50.5f, 40.5f, -90f, 12f, 6f);
        assertEquals(6f, f.halfX(), 1e-4f);
        assertEquals(3f, f.halfY(), 1e-4f);
        assertEquals(50.5f + 6f + TenderLanding.BAY_GAP_CELLS, f.bayX(), 1e-4f);
        assertEquals(40.5f, f.bayY(), 1e-4f);
    }

    @Test
    void turningTheHullAQuarterSwapsTheExtents() {
        TenderLanding.Footprint f = TenderLanding.footprint(50.5f, 40.5f, 0f, 12f, 6f);
        assertEquals(3f, f.halfX(), 1e-4f);
        assertEquals(6f, f.halfY(), 1e-4f);
        assertTrue(f.bayY() > 40.5f + 6f);
    }

    @Test
    void openGroundIsClearAndAnyBlockedCellUnderTheHullOrBayIsNot() {
        NavigationGrid grid = openGrid();
        TenderLanding.Footprint f = TenderLanding.footprint(40.5f, 40.5f, -90f, 12f, 6f);
        assertTrue(TenderLanding.clear(f, grid));
        grid.setTag(40, 40, CellTag.WALKABLE, false);
        assertFalse(TenderLanding.clear(f, grid), "a wall under the hull");
        grid.setTag(40, 40, CellTag.WALKABLE, true);
        grid.setTag((int) f.bayX(), (int) f.bayY(), CellTag.WALKABLE, false);
        assertFalse(TenderLanding.clear(f, grid), "the bay's mouth is shut");
    }

    @Test
    void aSiteTooCloseToTheMapEdgeDoesNotFit() {
        NavigationGrid grid = openGrid();
        assertFalse(TenderLanding.clear(TenderLanding.footprint(3.5f, 40.5f, -90f, 12f, 6f), grid));
    }

    @Test
    void theCallNeedsATenderSource() {
        assertThrows(IllegalArgumentException.class, () -> new TenderCallDown(List.of()));
    }
}
