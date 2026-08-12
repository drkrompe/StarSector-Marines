package com.dillon.starsectormarines.ops.loot;

/** Immutable stack shown by the future salvage picker. */
public final class LootStack {

    public final LootKind kind;
    public final String itemId;
    public final String displayName;
    public final String iconPath;
    public final int quantity;
    public final int unitValue;
    public final float cargoPerUnit;

    public LootStack(LootCandidate candidate, int quantity) {
        if (candidate == null) throw new IllegalArgumentException("candidate");
        if (quantity <= 0) throw new IllegalArgumentException("quantity");
        this.kind = candidate.kind;
        this.itemId = candidate.itemId;
        this.displayName = candidate.displayName;
        this.iconPath = candidate.iconPath;
        this.quantity = quantity;
        this.unitValue = candidate.unitValue;
        this.cargoPerUnit = candidate.cargoPerUnit;
    }

    public int totalValue() {
        long total = (long) unitValue * quantity;
        return total > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) total;
    }

    public float totalCargo() {
        return cargoPerUnit * quantity;
    }
}
