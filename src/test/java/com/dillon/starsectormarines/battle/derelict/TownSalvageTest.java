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
    void theTownsPlacesAreOnePerSquareNearestTheHullFirst() {
        FallenHullSite wide = site().withPerimeter(40);
        List<int[]> places = TownSalvage.places(ground(), wide, 16);
        assertEquals(2, places.size(), "both buildings, in different squares");
        assertTrue(places.get(0)[0] < places.get(1)[0], "the nearer the hull first");
        assertEquals(1, TownSalvage.places(ground(), wide, 64).size(), "one square holds both");
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

    @Test
    void aTownBuildingBetweenSectionsSpreadThroughTheTownIsTheTownsNotTheHulls() {
        // Sections at x 0..5 and x 30..35; the laid box spans both and the furrow between, where building 1 stands.
        FallenHullSite.Rect box = new FallenHullSite.Rect(0, 0, 35, 11);
        FallenHullSite spread = new FallenHullSite(box, 2, 5, 7, 5, 10, List.of(), 8, List.of(), List.of(), List.of(),
                List.of(), List.of(), 0f,
                List.of(new FallenHullSite.Rect(0, 0, 5, 11), new FallenHullSite.Rect(30, 0, 35, 11)));
        TownSalvage.Ground ground = new TownSalvage.Ground() {
            @Override public int width() { return 40; }

            @Override public int height() { return 12; }

            @Override public int buildingAt(int x, int y) {
                if (x <= 5 || x >= 30 && x <= 35) return 9;
                return x >= 16 && x <= 18 && y >= 4 && y <= 6 ? 1 : 0;
            }

            @Override public boolean open(int x, int y) { return buildingAt(x, y) == 0; }
        };
        List<FallenHullSite.Pile> piles = TownSalvage.piles(ground, spread, 5);
        assertEquals(1, piles.size(), "the building between the sections gives its pile; the sections give none");
        assertTrue(Math.abs(piles.get(0).x() - 17) <= TownSalvage.BESIDE_CELLS + 1);
    }
}
