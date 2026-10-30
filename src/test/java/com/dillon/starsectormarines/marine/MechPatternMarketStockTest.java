package com.dillon.starsectormarines.marine;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MechPatternMarketStockTest {

    private static final EquipmentAcquisitionEligibility.Progress VETERAN =
            new EquipmentAcquisitionEligibility.Progress(15, 20);

    @Test
    void onlyLargeOrMilitaryMarketsCarryTheSlot() {
        assertTrue(slot("independent", 4, false, 3L, false, Set.of()).isEmpty());
        assertEquals(1, slot("independent", 4, true, 3L, false, Set.of()).size());
        assertEquals(1, slot("independent", 5, false, 3L, false, Set.of()).size());
        assertEquals(1, slot("hegemony", 8, true, 3L, true, Set.of()).size());
    }

    @Test
    void slotIsStableWithinAMonthAndRotatesAcrossMonths() {
        assertEquals(slot("independent", 6, false, 12L, false, Set.of()),
                slot("independent", 6, false, 12L, false, Set.of()));
        Set<List<String>> rotations = new HashSet<>();
        for (long month = 0; month < 24; month++) {
            rotations.add(slot("independent", 6, false, month, false, Set.of()));
        }
        assertTrue(rotations.size() > 1);
    }

    @Test
    void knownOrCarriedPatternsAreNotStocked() {
        Set<String> everything = new HashSet<>();
        for (MechPattern pattern : MechPattern.offerable()) everything.add(pattern.id());
        assertTrue(slot("independent", 7, true, 5L, true, everything).isEmpty());
    }

    private static List<String> slot(String factionId, int size, boolean military, long rotation,
                                     boolean licensed, Set<String> unavailable) {
        return FactionEquipmentMarketStockPlanner.mechPattern(factionId, "test_market", size,
                military, rotation, licensed, unavailable, VETERAN);
    }
}
