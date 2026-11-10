package com.dillon.starsectormarines.battle.world.gen.precinct;

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
}
