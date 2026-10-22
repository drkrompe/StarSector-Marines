package com.dillon.starsectormarines.battle.evacuation;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The tier-keyed strain table: which strains a surge can hold at each tier. */
class BoreStrainTableTest {

    private static Set<BoreStrainTable.Slot> drawn(BoreTier tier) {
        Set<BoreStrainTable.Slot> slots = EnumSet.noneOf(BoreStrainTable.Slot.class);
        for (long roll = -50; roll < 50; roll++) slots.add(BoreStrainTable.pick(tier, roll));
        return slots;
    }

    @Test
    void aFissureSpawnsRunnersOnly() {
        assertEquals(EnumSet.of(BoreStrainTable.Slot.RUNNER), drawn(BoreTier.FISSURE));
    }

    @Test
    void bruteAndSpitterJoinAtBoreTier() {
        Set<BoreStrainTable.Slot> expected = EnumSet.of(BoreStrainTable.Slot.RUNNER,
                BoreStrainTable.Slot.BRUTE, BoreStrainTable.Slot.SPITTER);
        assertEquals(expected, drawn(BoreTier.BORE));
        assertEquals(expected, drawn(BoreTier.MAW));
    }

    @Test
    void theMatriarchIsNeverDrawn() {
        for (BoreTier tier : BoreTier.values()) {
            assertEquals(0, BoreStrainTable.weight(tier, BoreStrainTable.Slot.MATRIARCH));
        }
    }

    @Test
    void onlyAMotherAtMawOwesItsMatriarchAndOnlyOnce() {
        assertTrue(BoreStrainTable.owesMatriarch(true, BoreTier.MAW, false));
        assertFalse(BoreStrainTable.owesMatriarch(true, BoreTier.MAW, true));
        assertFalse(BoreStrainTable.owesMatriarch(true, BoreTier.BORE, false));
        assertFalse(BoreStrainTable.owesMatriarch(false, BoreTier.MAW, false));
        for (long roll = 0; roll < 20; roll++) {
            assertEquals(BoreStrainTable.Slot.MATRIARCH, BoreStrainTable.pick(BoreTier.MAW, roll, true));
            assertEquals(BoreStrainTable.pick(BoreTier.MAW, roll), BoreStrainTable.pick(BoreTier.MAW, roll, false));
        }
    }

    @Test
    void theSameRollPicksTheSameStrain() {
        for (long roll = 0; roll < 20; roll++) {
            assertEquals(BoreStrainTable.pick(BoreTier.MAW, roll), BoreStrainTable.pick(BoreTier.MAW, roll + 10));
        }
    }
}
