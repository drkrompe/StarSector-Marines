package com.dillon.starsectormarines.battle.world.gen.precinct;

import com.dillon.starsectormarines.battle.world.gen.bsp.GrownTrunkPlan;
import com.dillon.starsectormarines.battle.world.gen.elevation.RidgePlanner;
import com.dillon.starsectormarines.battle.world.gen.fortress.FortressProgram;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LandformTest {

    @Test
    void aDesignedDropIsReadBackFromTheGroundItHolds() {
        assertNull(Landform.Drop.of(Landscape.STOCK, 120, 80), "a map whose design places none has none");
        Landform.Drop design = new Landform.Drop(false, .6f);
        Landscape held = Landscape.STOCK.withHeld(List.of(design.band(120, 80)));
        Landform.Drop read = Landform.Drop.of(held, 120, 80);
        assertFalse(read.alongX());
        assertEquals(.6f, read.across(), .01f);
        assertTrue(CrevasseTown.wallsOf(held).isEmpty(), "a drop is no sheltering wall");
    }

    @Test
    void aWalledPlaceOnTheDropsLineIsMovedOffItsBandAndATownStays() {
        // A drop south to north at x = 71 of 120.
        Landform.Drop design = new Landform.Drop(false, .6f);
        Precinct garrison = new Precinct("garrison", 68, 40, GrownTrunkPlan.Profile.of(.5f),
                FortressProgram.garrison(), Precinct.Boundary.WALLED);
        Precinct town = new Precinct("town", 70, 20, GrownTrunkPlan.Profile.of(.5f), null,
                Precinct.Boundary.OPEN);
        Precinct far = new Precinct("far", 20, 40, GrownTrunkPlan.Profile.of(.5f),
                FortressProgram.garrison(), Precinct.Boundary.WALLED);
        List<Precinct> cleared = design.clear(List.of(garrison, town, far), 120, 80);
        boolean[][] held = RidgePlanner.heldGround(List.of(design.band(120, 80)), 120, 80,
                Landform.Drop.HELD_MARGIN);
        Precinct moved = cleared.get(0);
        assertTrue(moved.seedX() < 68 && !held[moved.seedX()][40] && !held[moved.seedX() + 1][40],
                "off the band's held ground, on the side it stood: " + moved.seedX());
        assertTrue(held[moved.seedX() + Landform.Drop.SEED_ROOM + 1][40], "and no farther than it has to be");
        assertEquals(40, moved.seedY());
        assertEquals(town, cleared.get(1), "a town's roads cross the drop as bridges");
        assertEquals(far, cleared.get(2), "a place clear of the band stays");
    }
}
