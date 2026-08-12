package com.dillon.starsectormarines.ops.loot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Frozen recovery roll and the value budget the player may claim from it. */
public final class LootManifest {

    public static final LootManifest EMPTY = new LootManifest(0L, 0, 0,
            Collections.emptyList());

    public final long seed;
    public final int entitlement;
    public final int selectionBudget;
    public final List<LootStack> stacks;
    public final int totalValue;

    public LootManifest(long seed, int entitlement, int selectionBudget,
                        List<LootStack> stacks) {
        this.seed = seed;
        this.entitlement = Math.max(0, entitlement);
        this.selectionBudget = Math.max(0, selectionBudget);
        List<LootStack> copy = stacks != null
                ? new ArrayList<>(stacks)
                : Collections.emptyList();
        this.stacks = Collections.unmodifiableList(copy);
        long total = 0L;
        for (LootStack stack : copy) total += stack.totalValue();
        this.totalValue = total > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) total;
    }

    public boolean isEmpty() {
        return stacks.isEmpty();
    }
}
