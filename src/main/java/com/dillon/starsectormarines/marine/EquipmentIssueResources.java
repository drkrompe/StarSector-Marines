package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.campaign.CommodityPresentation;

/** Legacy equipment resource adapter, retained for callers and stock presentation. Fitting never spends it. */
public interface EquipmentIssueResources extends CommodityPresentation {

    EquipmentIssueResources UNLIMITED = new EquipmentIssueResources() {
        private final EquipmentTemplateCost available = new EquipmentTemplateCost(
                Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE);

        @Override
        public EquipmentTemplateCost available() {
            return available;
        }

        @Override
        public boolean spend(EquipmentTemplateCost cost) {
            return cost != null;
        }
    };

    EquipmentTemplateCost available();

    /** Rechecks and consumes the complete cost, or consumes nothing. */
    boolean spend(EquipmentTemplateCost cost);

    default boolean canAfford(EquipmentTemplateCost cost) {
        return cost != null && available().covers(cost);
    }
}
