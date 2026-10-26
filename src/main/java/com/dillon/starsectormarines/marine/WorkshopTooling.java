package com.dillon.starsectormarines.marine;

/**
 * Installed tools waste less: a workshop job costs {@link #DISCOUNT_PERCENT} percent less, rounded
 * up to the credit, without changing any price list. It stands in for the fifth of the supplies
 * and machinery the tools saved when jobs were paid in materials.
 */
public final class WorkshopTooling {
    public static final int DISCOUNT_PERCENT = 15;

    private WorkshopTooling() { }

    public static FabricationCost quote(FabricationCost base, boolean equipped) {
        if (base == null || !equipped) return base;
        return base.scaled((100 - DISCOUNT_PERCENT) / 100f);
    }
}
