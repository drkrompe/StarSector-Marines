package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.setup.DustLedgerSequence.Area;
import com.dillon.starsectormarines.battle.setup.DustLedgerSequence.Cell;
import com.dillon.starsectormarines.battle.setup.DustLedgerSequence.Crate;
import com.dillon.starsectormarines.battle.setup.DustLedgerSequence.Sites;
import com.dillon.starsectormarines.battle.setup.DustLedgerSequence.Watcher;
import com.dillon.starsectormarines.battle.unit.Faction;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Dust Ledger rules the adapter decides itself, on small synthetic grids. */
class DustLedgerSequenceTest {
    /** Compound grounds at x 20..24, y 4..8; the ring grows them by the margin. */
    private static final Area GROUNDS = Area.rect(20, 4, 24, 8);
    private static final Area RING = Sites.ring(GROUNDS);

    @Test void theQuietPhaseHidesSableWakeAndTheCompanyOutsideTheRingOnly() {
        assertTrue(DustLedgerSequence.covered(RING, Faction.DEFENDER, 22.5f, 6.5f), "the company holds its fire");
        assertTrue(DustLedgerSequence.covered(RING, Faction.MARINE, 5.5f, 6.5f));
        assertTrue(DustLedgerSequence.covered(RING, Faction.ALLY, 5.5f, 6.5f));
        float edge = 20 - DustLedgerSequence.RING_MARGIN_CELLS;
        assertFalse(DustLedgerSequence.covered(RING, Faction.MARINE, edge + .5f, 6.5f), "inside the ring is fair game");
        assertTrue(DustLedgerSequence.covered(RING, Faction.MARINE, edge - .5f, 6.5f));
        assertFalse(DustLedgerSequence.covered(RING, Faction.CIVILIAN, 22.5f, 6.5f));
    }

    @Test void aSentrySightingCountsOnlyInsideTheRingInRangeAndInView() {
        NavigationGrid grid = open(40, 14);
        Watcher sentry = new Watcher(22, 6, 10f);
        Cell inRing = new Cell(17, 6), outside = new Cell(14, 6);
        assertTrue(DustLedgerSequence.sighted(grid, RING, List.of(sentry), List.of(inRing)));
        assertFalse(DustLedgerSequence.sighted(grid, RING, List.of(sentry), List.of(outside)),
                "seen outside the ring is still freight crew");
        assertFalse(DustLedgerSequence.sighted(grid, RING, List.of(new Watcher(22, 6, 3f)), List.of(inRing)),
                "presence beyond the sentry's sight is not a sighting");
        for (int y = 0; y < 14; y++) grid.setWalkable(19, y, false);
        assertFalse(DustLedgerSequence.sighted(grid, RING, List.of(sentry), List.of(inRing)), "a wall hides the company");
        assertFalse(DustLedgerSequence.sighted(grid, RING, List.of(), List.of(inRing)));
    }

    @Test void onlyShotsFromArmedSidesBreakTheQuiet() {
        assertTrue(DustLedgerSequence.breaksQuiet(Faction.MARINE));
        assertTrue(DustLedgerSequence.breaksQuiet(Faction.DEFENDER));
        assertTrue(DustLedgerSequence.breaksQuiet(Faction.ALLY));
        assertFalse(DustLedgerSequence.breaksQuiet(Faction.CIVILIAN));
        assertFalse(DustLedgerSequence.breaksQuiet(null));
    }

    @Test void theCrateIsCarriedDroppedWhereItsCarrierFallsAndDeliveredInTheYard() {
        Area yard = Area.rect(10, 0, 12, 2);
        Crate crate = new Crate(new Cell(1, 1));
        crate.step(false, 0, 0, 0L, yard);
        assertEquals(0L, crate.carrier);
        crate.step(false, 0, 0, 7L, yard);
        assertEquals(7L, crate.carrier);
        crate.step(true, 5, 1, 9L, yard);
        assertEquals(7L, crate.carrier, "a carried crate is not taken by another unit");
        assertEquals(5, crate.x);
        crate.step(false, 6, 1, 0L, yard);
        assertEquals(0L, crate.carrier);
        assertEquals(5, crate.x, "dropped where the carrier last stood");
        crate.step(false, 0, 0, 9L, yard);
        crate.step(true, 11, 1, 0L, yard);
        assertTrue(crate.delivered);
        assertEquals(0L, crate.carrier);
        crate.step(false, 0, 0, 3L, yard);
        assertEquals(0L, crate.carrier, "a delivered crate stays in the yard");
    }

    @Test void sitesPutTheOfficeAndBarrelInsideTheRingAndTheSideJobsOutside() {
        assertEquals(sites(new Cell(22, 6), Area.rect(0, 0, 2, 2)).exclusionRing(), RING);
        assertThrows(IllegalArgumentException.class, () -> sites(new Cell(2, 6), Area.rect(0, 0, 2, 2)),
                "an office outside the ring");
        assertThrows(IllegalArgumentException.class, () -> sites(new Cell(22, 6), Area.rect(15, 5, 17, 7)),
                "a well house inside the ring");
    }

    @Test void anAreaGrowsByKingsMovesAndOutlinesItsEdge() {
        Area one = Area.of(new Cell(5, 5));
        assertEquals(9, one.grown(1).cells().size());
        assertEquals(new Cell(5, 5), one.grown(2).centre());
        assertEquals(8, Area.rect(0, 0, 2, 2).outline().size());
        assertEquals(3, Area.rect(0, 0, 2, 2).reach(new Cell(0, 0)));
    }

    private static Sites sites(Cell office, Area wellHouse) {
        return new Sites(Area.rect(0, 10, 3, 13), new Cell(1, 11), Area.rect(21, 5, 23, 7), new Cell(22, 6), office,
                new Cell(30, 12), RING, GROUNDS, List.of(new Cell(10, 6)), Area.rect(6, 0, 8, 2),
                List.of(new Cell(7, 1)), wellHouse, Area.rect(10, 10, 12, 12), List.of(new Cell(30, 6)), new Cell(25, 6));
    }

    private static NavigationGrid open(int width, int height) {
        NavigationGrid grid = new NavigationGrid(width, height);
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) grid.setWalkableFloor(x, y);
        return grid;
    }
}
