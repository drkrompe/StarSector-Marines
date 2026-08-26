package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;

/** One permanent collectible capability and the cargo cost of fielding a copy. */
public record EquipmentTemplateCard(
        String id, String displayName, Kind kind, String equipmentId,
        EquipmentGrade grade, EquipmentTemplateCost issueCost) {

    public enum Kind {
        PRIMARY,
        ARMOR,
        SPECIAL
    }

    public EquipmentTemplateCard {
        if (id == null || id.isBlank() || displayName == null || displayName.isBlank()
                || kind == null || equipmentId == null || equipmentId.isBlank()
                || issueCost == null || (kind == Kind.PRIMARY) != (grade != null)) {
            throw new IllegalArgumentException(
                    "Equipment template cards require complete identity and cost");
        }
    }
}
