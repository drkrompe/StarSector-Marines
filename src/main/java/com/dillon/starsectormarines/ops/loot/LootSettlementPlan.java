package com.dillon.starsectormarines.ops.loot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable confirmation preview produced before campaign cargo is mutated. */
public final class LootSettlementPlan {

    public final List<LootSettlementLine> lines;
    public final int keptValue;
    public final int fencedBaseValue;
    public final int fencedCredits;
    public final int keptUnits;
    public final int fencedUnits;

    public LootSettlementPlan(List<LootSettlementLine> lines) {
        List<LootSettlementLine> copy = lines != null
                ? new ArrayList<>(lines)
                : Collections.emptyList();
        this.lines = Collections.unmodifiableList(copy);
        long keptValue = 0L;
        long fencedBaseValue = 0L;
        long fencedCredits = 0L;
        long keptUnits = 0L;
        long fencedUnits = 0L;
        for (LootSettlementLine line : copy) {
            keptValue += line.keptValue;
            fencedBaseValue += line.fencedBaseValue;
            fencedCredits += line.fencedCredits;
            keptUnits += line.keptQuantity;
            fencedUnits += line.fencedQuantity;
        }
        this.keptValue = bounded(keptValue);
        this.fencedBaseValue = bounded(fencedBaseValue);
        this.fencedCredits = bounded(fencedCredits);
        this.keptUnits = bounded(keptUnits);
        this.fencedUnits = bounded(fencedUnits);
    }

    /** Hold space the kept lines take in one bucket. */
    public float keptSpace(LootCapacityBucket bucket) {
        float total = 0f;
        for (LootSettlementLine line : lines) {
            if (line.bucket == bucket) total += line.keptSpace;
        }
        return total;
    }

    public boolean isEmpty() {
        return lines.isEmpty();
    }

    private static int bounded(long value) {
        return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) Math.max(0L, value);
    }
}
