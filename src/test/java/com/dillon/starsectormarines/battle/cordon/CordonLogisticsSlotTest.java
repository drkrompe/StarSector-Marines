package com.dillon.starsectormarines.battle.cordon;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The apron's staging slots stand apart, and off the threshold every hull drives out to. */
class CordonLogisticsSlotTest {

    @Test
    void twoSlotsWantedOnBadGroundDoNotSnapToOneCell() {
        // Only the row y = 6 takes a carrier; two slots are wanted above it, at (10, 1) and (10, 6).
        CordonLogistics.CellTest ground = (x, y) -> y == 6 && x >= 0 && x <= 30;
        List<int[]> keepOff = new ArrayList<>();
        keepOff.add(new int[]{0, 6});
        int[] first = CordonLogistics.slotCell(ground, 10, 6, 24, keepOff, 4);
        assertArrayEquals(new int[]{10, 6}, first);
        keepOff.add(first);
        int[] second = CordonLogistics.slotCell(ground, 10, 1, 24, keepOff, 4);
        assertTrue(Math.abs(second[0] - 10) >= 4, "the second slot stands apart from the first");
    }

    @Test
    void aSlotKeepsOffTheThreshold() {
        CordonLogistics.CellTest ground = (x, y) -> true;
        int[] slot = CordonLogistics.slotCell(ground, 5, 5, 24, List.of(new int[]{5, 5}), 4);
        assertTrue(Math.max(Math.abs(slot[0] - 5), Math.abs(slot[1] - 5)) >= 4);
        assertNull(CordonLogistics.slotCell((x, y) -> false, 5, 5, 3, List.of(), 4));
    }
}
