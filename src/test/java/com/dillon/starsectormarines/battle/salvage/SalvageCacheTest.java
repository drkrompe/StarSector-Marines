package com.dillon.starsectormarines.battle.salvage;

import org.junit.jupiter.api.Test;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.Direction;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class SalvageCacheTest {
    @Test void nearbyInfantryCannotCollectThroughAnImpassableSharedEdge() {
        var grid = new NavigationGrid(10, 10);
        for (int y = 0; y < 10; y++) for (int x = 0; x < 10; x++) grid.setWalkableFloor(x, y);
        assertTrue(FieldSalvage.canReach(grid, 2, 4, 4, 4));
        grid.blockSharedEdge(3, 4, Direction.E);
        assertFalse(FieldSalvage.canReach(grid, 2, 4, 4, 4));
        grid.openSharedEdge(3, 4, Direction.E);
        assertTrue(FieldSalvage.canReach(grid, 2, 4, 4, 4));
        assertFalse(FieldSalvage.canReach(grid, 0, 4, 4, 4));
    }
    @Test void spottingDoesNotRecoverAndInterruptedWorkRestarts() {
        var c = cache();
        c.advance(20, false, true, false);
        assertFalse(c.discovered());
        c.advance(20, true, false, false);
        assertTrue(c.discovered()); assertFalse(c.secured());
        c.advance(4, true, true, false);
        assertEquals(.8f, c.fraction(), .001);
        c.advance(1, true, true, true);
        assertTrue(c.contested()); assertEquals(0, c.fraction());
        c.advance(4, true, true, false);
        assertFalse(c.secured());
        c.advance(1, true, true, false);
        assertTrue(c.secured());
        c.advance(20, false, false, true);
        assertTrue(c.secured()); assertFalse(c.contested());
    }
    @Test void ledgerFreezesOnlySecuredFindsAndRejectsDuplicateIds() {
        var c = cache(); var ledger = new FieldSalvage(List.of(c));
        var before = ledger.recovered();
        c.advance(5, true, true, false);
        assertTrue(before.isEmpty());
        assertEquals(List.of(c.recovered()), ledger.recovered());
        assertThrows(IllegalArgumentException.class, () -> new FieldSalvage(List.of(c, cache())));
    }
    private SalvageCache cache() { return new SalvageCache("one", 4, 4, SalvageResource.METALS, 40); }
}
