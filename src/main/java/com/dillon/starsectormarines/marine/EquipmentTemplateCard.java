package com.dillon.starsectormarines.marine;

/** One permanent collectible capability and the cargo cost of fielding a copy. */
public record EquipmentTemplateCard(
        String id, String displayName, Kind kind, EquipmentTemplateCost issueCost) {

    public enum Kind {
        PRIMARY,
        ARMOR,
        SPECIAL
    }

    public EquipmentTemplateCard {
        if (id == null || id.isBlank() || displayName == null || displayName.isBlank()
                || kind == null || issueCost == null) {
            throw new IllegalArgumentException(
                    "Equipment template cards require complete identity and cost");
        }
    }
}
