package com.dillon.starsectormarines.battle.cordon;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CordonTownWarrenTest {

    @Test
    void buildingsStandInTheirRegionWithAnAlleyOfAtLeastTwoCellsBetweenThem() {
        int[] region = {0, 0, 80, 60};
        List<CordonTownWarren.Lot> lots = CordonTownWarren.pack((x, y) -> true, List.of(region), 1);
        assertTrue(lots.size() > 4, "the region is built up");
        for (CordonTownWarren.Lot a : lots) {
            int ax1 = a.x() + CordonTownWarren.width(a) - 1, ay1 = a.y() + CordonTownWarren.height(a) - 1;
            assertTrue(a.x() >= region[0] && a.y() >= region[1] && ax1 <= region[2] && ay1 <= region[3]);
            for (CordonTownWarren.Lot b : lots) {
                if (a == b) continue;
                int bx1 = b.x() + CordonTownWarren.width(b) - 1, by1 = b.y() + CordonTownWarren.height(b) - 1;
                int gapX = Math.max(b.x() - ax1, a.x() - bx1) - 1, gapY = Math.max(b.y() - ay1, a.y() - by1) - 1;
                assertTrue(Math.max(gapX, gapY) >= CordonTownWarren.ALLEY, a + " and " + b);
            }
        }
    }

    @Test
    void streetFrontsStandBothSidesOfAStreetSetBackFromItsCarriageway() {
        List<int[]> street = List.of(new int[]{0, 50}, new int[]{120, 50});
        List<CordonTownWarren.Lot> lots = CordonTownWarren.frontage(List.of(street), (x, y) -> true, new HashSet<>());
        int set = CordonTownWarren.STREET_WIDTH / 2 + CordonTownWarren.ROAD_CLEAR + 1;
        boolean north = false, south = false;
        for (CordonTownWarren.Lot lot : lots) {
            int y1 = lot.y() + CordonTownWarren.height(lot) - 1;
            if (lot.y() == 50 + set) north = true;
            else if (y1 == 50 - set) south = true;
            else throw new AssertionError("off the street front: " + lot);
        }
        assertTrue(north && south, "both sides are built");
    }

    @Test
    void noBuildingStandsOnGroundThatIsNotFree() {
        List<CordonTownWarren.Lot> lots = CordonTownWarren.pack((x, y) -> x < 30, List.of(new int[]{0, 0, 80, 60}), 1);
        for (CordonTownWarren.Lot lot : lots) assertTrue(lot.x() + CordonTownWarren.width(lot) - 1 < 30);
    }
}
