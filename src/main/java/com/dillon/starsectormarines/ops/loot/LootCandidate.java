package com.dillon.starsectormarines.ops.loot;

/**
 * One catalog entry eligible for a recovery roll. This is deliberately free of
 * live Starsector API objects so the roll can be tested and replayed exactly.
 */
public final class LootCandidate {

    public final LootKind kind;
    public final String itemId;
    public final String displayName;
    public final String iconPath;
    public final int unitValue;
    public final float cargoPerUnit;
    public final float weight;
    public final int minQuantity;
    public final int maxQuantity;

    public LootCandidate(LootKind kind, String itemId, String displayName, String iconPath,
                         int unitValue, float cargoPerUnit, float weight,
                         int minQuantity, int maxQuantity) {
        if (kind == null) throw new IllegalArgumentException("kind");
        if (itemId == null || itemId.isEmpty()) throw new IllegalArgumentException("itemId");
        if (unitValue <= 0) throw new IllegalArgumentException("unitValue");
        if (!(weight > 0f)) throw new IllegalArgumentException("weight");
        if (minQuantity <= 0 || maxQuantity < minQuantity) {
            throw new IllegalArgumentException("quantity range");
        }
        this.kind = kind;
        this.itemId = itemId;
        this.displayName = displayName != null ? displayName : itemId;
        this.iconPath = iconPath;
        this.unitValue = unitValue;
        this.cargoPerUnit = Math.max(0f, cargoPerUnit);
        this.weight = weight;
        this.minQuantity = minQuantity;
        this.maxQuantity = maxQuantity;
    }
}
