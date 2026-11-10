package com.dillon.starsectormarines.battle.world.gen.precinct;

import com.dillon.starsectormarines.battle.world.gen.elevation.RidgePlanner;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrevasseTownTest {

    @Test
    void theWallsRunAlongTheEdgesTheyAreAskedFor() {
        List<RidgePlanner.Band> northSouth = CrevasseTown.walls(120, 80, true);
        assertEquals(2, northSouth.size());
        assertTrue(CrevasseTown.alongX(northSouth));
        for (RidgePlanner.Band band : northSouth) assertEquals(RidgePlanner.Relief.ICE_WALL, band.relief());
        assertFalse(CrevasseTown.alongX(CrevasseTown.walls(120, 80, false)));
    }

    @Test
    void aLandscapeWithoutWallsIsNoCrevasseTown() {
        assertTrue(CrevasseTown.wallsOf(Landscape.STOCK).isEmpty());
        Landscape held = Landscape.STOCK.withHeld(CrevasseTown.walls(120, 80, true));
        assertEquals(2, CrevasseTown.wallsOf(held).size());
    }
}
