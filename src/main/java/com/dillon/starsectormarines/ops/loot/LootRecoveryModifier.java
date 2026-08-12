package com.dillon.starsectormarines.ops.loot;

/** Frozen Layer-3 salvage modifier applied to one recovery roll. */
public final class LootRecoveryModifier {

    public static final LootRecoveryModifier NONE = new LootRecoveryModifier(0, 0);

    public final int recoveryBonusPct;
    public final int highValueChancePct;

    public LootRecoveryModifier(int recoveryBonusPct, int highValueChancePct) {
        this.recoveryBonusPct = Math.max(0, recoveryBonusPct);
        this.highValueChancePct = Math.max(0, Math.min(100, highValueChancePct));
    }
}
