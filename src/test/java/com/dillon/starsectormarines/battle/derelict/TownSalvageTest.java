package com.dillon.starsectormarines.battle.derelict;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TownSalvageTest {

    /**
     * A 40x12 ground: the hull at x 0..5, its own building cells inside it; building 1 (3x3) at x 12..14, building 2
     * (4x4) at x 30..33, beyond the hull zone's reach of 10.
     */
    private static TownSalvage.Ground ground() {
        return new TownSalvage.Ground() {
            @Override public int width() { return 40; }

            @Override public int height() { return 12; }

            @Override public int buildingAt(int x, int y) {
                if (x <= 5) return 9;
                if (x >= 12 && x <= 14 && y >= 4 && y <= 6) return 1;
                if (x >= 30 && x <= 33 && y >= 4 && y <= 7) return 2;
                return 0;
            }

            @Override public boolean open(int x, int y) { return buildingAt(x, y) == 0; }
        };
    }

    private static FallenHullSite site() {
        FallenHullSite.Rect hull = new FallenHullSite.Rect(0, 0, 5, 11);
        return new FallenHullSite(hull, 2, 5, 7, 5, 10, List.of(), 8, List.of(), List.of(), List.of(), List.of(),
                List.of(), 0f, List.of(hull));
    }

    @Test
    void aBuildingTheHullZoneKeepsGivesOnePileBesideItAndTheHullGivesNone() {
        List<FallenHullSite.Pile> piles = TownSalvage.piles(ground(), site(), 5);
        assertEquals(1, piles.size(), "building 2 lies beyond the zone's reach; the hull's own cells give none");
        FallenHullSite.Pile pile = piles.get(0);
        assertTrue(ground().open(pile.x(), pile.y()), "on ground a stripper stands on");
        assertEquals(10, pile.x(), "beside building 1, on its side toward the hull");
        assertEquals(TownSalvage.MIN_METAL, pile.metal(), "a small building's floor of metal");
    }
}
