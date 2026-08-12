package com.dillon.starsectormarines.ops.loot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Mutable picker state kept separate from the immutable recovery manifest. */
public final class LootSelection {

    private final LootManifest manifest;
    private final boolean[] selected;
    private int selectedValue;

    public LootSelection(LootManifest manifest) {
        this.manifest = manifest != null ? manifest : LootManifest.EMPTY;
        this.selected = new boolean[this.manifest.stacks.size()];
    }

    public LootManifest manifest() {
        return manifest;
    }

    public int selectedValue() {
        return selectedValue;
    }

    public int remainingBudget() {
        return Math.max(0, manifest.selectionBudget - selectedValue);
    }

    public boolean isSelected(int index) {
        return valid(index) && selected[index];
    }

    public boolean canSelect(int index) {
        if (!valid(index)) return false;
        return selected[index]
                || selectedValue + (long) manifest.stacks.get(index).totalValue()
                <= manifest.selectionBudget;
    }

    /** Toggles a stack if the resulting selection fits. Returns true on change. */
    public boolean toggle(int index) {
        if (!valid(index)) return false;
        LootStack stack = manifest.stacks.get(index);
        if (selected[index]) {
            selected[index] = false;
            selectedValue -= stack.totalValue();
            return true;
        }
        if (!canSelect(index)) return false;
        selected[index] = true;
        selectedValue += stack.totalValue();
        return true;
    }

    public List<Integer> selectedIndices() {
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < selected.length; i++) {
            if (selected[i]) out.add(i);
        }
        return Collections.unmodifiableList(out);
    }

    private boolean valid(int index) {
        return index >= 0 && index < selected.length;
    }
}
