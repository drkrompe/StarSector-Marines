package com.dillon.starsectormarines.battle.world.gen.layout;

import com.dillon.starsectormarines.battle.world.gen.AirbaseLot;
import com.dillon.starsectormarines.battle.world.gen.LandingPad;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MissionLayoutTest {

    private static Site site(String name, int x) {
        return new Site(name, x, 0, x + 3, 3, SitePurpose.OPEN, false);
    }

    @Test
    void aNameIsUsedOnce() {
        assertThrows(IllegalArgumentException.class, () -> new MissionLayout(List.of(site("a", 0), site("a", 9))));
        assertTrue(MissionLayout.NONE.isEmpty());
    }

    @Test
    void anOverrideReplacesThePieceOfThatNameInPlace() {
        MissionLayout layout = new MissionLayout(List.of(site("a", 0), site("b", 9)));
        Site moved = site("b", 20);
        MissionLayout out = layout.withOverride("b", moved);
        assertSame(moved, out.find("b"));
        assertEquals("a", out.pieces().get(0).name());
        assertThrows(IllegalArgumentException.class, () -> layout.withOverride("c", site("c", 1)));
        assertThrows(IllegalArgumentException.class, () -> layout.withOverride("b", site("a", 1)));
    }

    @Test
    void aLaidLayoutAnswersByName() {
        Laid line = new Laid("picket", Laid.Kind.LINE, Laid.Status.PARTIAL, "broken", false,
                List.of(new int[]{1, 1}), List.of(), List.of());
        Laid refused = Laid.refused("site", Laid.Kind.SITE, false, "off the map");
        LaidLayout laid = new LaidLayout(List.of(line, refused));
        assertSame(line, laid.find("picket"));
        assertTrue(laid.has("picket"));
        assertFalse(laid.has("site"));
        assertNull(laid.find("nothing"));
        assertFalse(LaidLayout.EMPTY.has("picket"));
    }

    @Test
    void onlySitesStatedUpFrontHoldGroundBeforeGrowth() {
        Deferred later = new Deferred("later", Phase.WORKS, ground -> List.of(site("x", 0)), false);
        assertNull(new MissionLayout(List.of(later)).keepClear(16, 8));
        boolean[][] mask = new MissionLayout(List.of(later, site("a", 2))).keepClear(16, 8);
        assertTrue(mask[2][0] && mask[5][3]);
        assertFalse(mask[6][0] || mask[2][4]);
    }

    @Test
    void anAirfieldHoldsItsLotAndClearanceBeforeGrowth() {
        Airfield field = new Airfield("airbase", 12, 12, AirbaseLot.Facing.WEST, AirbaseLot.Size.STATION,
                LandingPad.Purpose.ALLIED_AIRFIELD, false);
        Site held = field.reservation();
        assertEquals(12 + AirbaseLot.reservedSpanX(AirbaseLot.Size.STATION, AirbaseLot.Facing.WEST) - 1, held.x1());
        int clear = AirbaseLot.Size.STATION.clearance();
        assertEquals(12 + clear, field.lot()[0]);
        assertEquals(held.y1() - clear, field.lot()[3]);
        boolean[][] mask = new MissionLayout(List.of(field)).keepClear(120, 120);
        assertTrue(mask[12][12] && mask[held.x1()][held.y1()]);
        assertFalse(mask[11][12] || mask[held.x1() + 1][held.y1()]);
    }
}
