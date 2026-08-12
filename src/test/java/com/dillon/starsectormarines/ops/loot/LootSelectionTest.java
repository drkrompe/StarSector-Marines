package com.dillon.starsectormarines.ops.loot;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LootSelectionTest {

    @Test
    void enforcesBudgetAndReleasesValueWhenDeselected() {
        LootSelection selection = new LootSelection(manifest(900));

        assertTrue(selection.toggle(0));
        assertEquals(600, selection.selectedValue());
        assertFalse(selection.canSelect(1));
        assertFalse(selection.toggle(1));
        assertTrue(selection.canSelect(2));
        assertTrue(selection.toggle(2));
        assertEquals(900, selection.selectedValue());
        assertEquals(Arrays.asList(0, 2), selection.selectedIndices());

        assertTrue(selection.toggle(0));
        assertEquals(300, selection.selectedValue());
        assertEquals(600, selection.remainingBudget());
        assertTrue(selection.toggle(1));
        assertEquals(800, selection.selectedValue());
    }

    @Test
    void invalidIndicesAreSafeNoOps() {
        LootSelection selection = new LootSelection(manifest(900));

        assertFalse(selection.toggle(-1));
        assertFalse(selection.toggle(3));
        assertFalse(selection.isSelected(3));
        assertFalse(selection.canSelect(3));
    }

    private static LootManifest manifest(int budget) {
        LootCandidate first = candidate("first", 600);
        LootCandidate second = candidate("second", 500);
        LootCandidate third = candidate("third", 300);
        return new LootManifest(42L, 60, budget, Arrays.asList(
                new LootStack(first, 1), new LootStack(second, 1), new LootStack(third, 1)));
    }

    private static LootCandidate candidate(String id, int value) {
        return new LootCandidate(LootKind.WEAPON, id, id, null,
                value, 5f, 1f, 1, 1);
    }
}
