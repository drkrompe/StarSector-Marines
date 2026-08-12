package com.dillon.starsectormarines.ops.loot;

/** One selected stack split into carried and fenced quantities. */
public final class LootSettlementLine {

    public final int stackIndex;
    public final LootStack stack;
    public final LootCapacityBucket bucket;
    public final int keptQuantity;
    public final int fencedQuantity;
    public final int keptValue;
    public final int fencedBaseValue;
    public final int fencedCredits;

    public LootSettlementLine(int stackIndex, LootStack stack, LootCapacityBucket bucket,
                              int keptQuantity, int fencedQuantity, int fencedCredits) {
        this.stackIndex = stackIndex;
        this.stack = stack;
        this.bucket = bucket;
        this.keptQuantity = keptQuantity;
        this.fencedQuantity = fencedQuantity;
        this.keptValue = multiply(stack.unitValue, keptQuantity);
        this.fencedBaseValue = multiply(stack.unitValue, fencedQuantity);
        this.fencedCredits = Math.max(0, fencedCredits);
    }

    private static int multiply(int left, int right) {
        long value = (long) left * right;
        return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) value;
    }
}
