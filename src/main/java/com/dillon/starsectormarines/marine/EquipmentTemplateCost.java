package com.dillon.starsectormarines.marine;

import java.util.ArrayList;
import java.util.List;

/** Base-game cargo consumed by one atomic equipment issue or refit. */
public record EquipmentTemplateCost(
        int supplies, int heavyArmaments, int heavyMachinery, int food) {

    public static final EquipmentTemplateCost ZERO =
            new EquipmentTemplateCost(0, 0, 0, 0);

    public EquipmentTemplateCost {
        if (supplies < 0 || heavyArmaments < 0 || heavyMachinery < 0 || food < 0) {
            throw new IllegalArgumentException("Equipment cargo costs cannot be negative");
        }
    }

    public EquipmentTemplateCost plus(EquipmentTemplateCost other) {
        if (other == null) return this;
        return new EquipmentTemplateCost(
                supplies + other.supplies,
                heavyArmaments + other.heavyArmaments,
                heavyMachinery + other.heavyMachinery,
                food + other.food);
    }

    public boolean isZero() {
        return supplies == 0 && heavyArmaments == 0 && heavyMachinery == 0 && food == 0;
    }

    public boolean covers(EquipmentTemplateCost required) {
        return required != null
                && supplies >= required.supplies
                && heavyArmaments >= required.heavyArmaments
                && heavyMachinery >= required.heavyMachinery
                && food >= required.food;
    }

    public String display() {
        if (isZero()) return "no cargo";
        List<String> parts = new ArrayList<>();
        if (supplies > 0) parts.add(supplies + " supplies");
        if (heavyArmaments > 0) parts.add(heavyArmaments + " heavy armaments");
        if (heavyMachinery > 0) parts.add(heavyMachinery + " heavy machinery");
        if (food > 0) parts.add(food + " food");
        return String.join("  ·  ", parts);
    }
}
