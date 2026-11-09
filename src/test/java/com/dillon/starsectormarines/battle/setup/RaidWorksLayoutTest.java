package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.ops.OperationTier;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** How a disrupting raid's works chooses its machines ({@code raid-mission.md}, Break). */
class RaidWorksLayoutTest {

    @Test
    void machinesAreTakenInCandidateOrderUpToTheCount() {
        List<int[]> picked = RaidWorksLayout.pick(List.of(new int[]{0, 0}, new int[]{20, 0}, new int[]{40, 0},
                new int[]{60, 0}), 3, 7, cell -> true, List.of());
        assertEquals(3, picked.size());
        assertArrayEquals(new int[]{40, 0}, picked.get(2));
    }

    @Test
    void twoMachinesNeverShareAPocket() {
        List<int[]> picked = RaidWorksLayout.pick(List.of(new int[]{0, 0}, new int[]{3, 3}, new int[]{10, 0}),
                3, 7, cell -> true, List.of());
        assertEquals(2, picked.size());
        assertArrayEquals(new int[]{10, 0}, picked.get(1));
    }

    @Test
    void anUnusableCellIsSkipped() {
        List<int[]> picked = RaidWorksLayout.pick(List.of(new int[]{0, 0}, new int[]{20, 0}), 2, 7,
                cell -> cell[0] != 0, List.of());
        assertEquals(1, picked.size());
        assertArrayEquals(new int[]{20, 0}, picked.get(0));
    }

    @Test
    void aFallbackKeepsClearOfMachinesAlreadyChosen() {
        List<int[]> chosen = List.of(new int[]{0, 0});
        List<int[]> picked = RaidWorksLayout.pick(List.of(new int[]{2, 0}, new int[]{30, 0}), 3, 7,
                cell -> true, chosen);
        assertEquals(2, picked.size());
        assertArrayEquals(new int[]{30, 0}, picked.get(1));
    }

    @Test
    void aBiggerRaidHasMoreMachines() {
        assertTrue(RaidWorksLayout.machinesFor(OperationTier.FIRST_CONTRACT)
                < RaidWorksLayout.machinesFor(OperationTier.ESTABLISHED));
        assertTrue(RaidWorksLayout.machinesFor(OperationTier.VETERAN)
                < RaidWorksLayout.machinesFor(OperationTier.FULL_STRENGTH));
    }
}
