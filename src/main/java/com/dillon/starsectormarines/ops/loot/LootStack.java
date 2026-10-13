package com.dillon.starsectormarines.ops.loot;

/** Immutable recovery stack shown by the salvage picker. */
public final class LootStack {

    public final LootKind kind;
    public final String itemId;
    public final String displayName;
    public final String iconPath;
    public final int quantity;
    public final int unitValue;
    public final float cargoPerUnit;
    /** Secured on the battlefield; claims no negotiated salvage budget. */
    public final boolean fieldRecovered;

    public LootStack(LootCandidate candidate, int quantity) {
        this(candidate, quantity, false);
    }

    public LootStack(LootCandidate candidate, int quantity, boolean fieldRecovered) {
        if (candidate == null) throw new IllegalArgumentException("candidate");
        if (quantity <= 0) throw new IllegalArgumentException("quantity");
        this.fieldRecovered = fieldRecovered;
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

    public int claimCost() { return fieldRecovered ? 0 : totalValue(); }

    public float totalCargo() {
        return cargoPerUnit * quantity;
    }
}
