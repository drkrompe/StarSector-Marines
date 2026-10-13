package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;
import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;

/** One permanent equipment technology. issueCost is retained only as legacy catalog metadata. */
public record EquipmentTemplateCard(
        String id, String displayName, Kind kind, String equipmentId,
        EquipmentGrade grade, EquipmentAccessTier accessTier,
        EquipmentTemplateCost issueCost) {
    /** A null authored name follows its equipment; explicit addon names remain literal or @key. */
    public String displayName() {
        if (displayName != null) return Strings.resolve(displayName);
        return switch (kind) {
            case PRIMARY -> WeaponRegistry.require(equipmentId).catalogName(grade);
            case ARMOR -> MarineArmorCatalogRegistry.require(equipmentId).displayName();
            case SPECIAL -> SpecialEquipmentRegistry.get(equipmentId).displayName();
        };
    }


    public enum Kind {
        PRIMARY,
        ARMOR,
        SPECIAL
    }

    public EquipmentTemplateCard {
        if (id == null || id.isBlank() || (displayName != null && displayName.isBlank())
                || kind == null || equipmentId == null || equipmentId.isBlank()
                || accessTier == null || issueCost == null
                || (kind == Kind.PRIMARY) != (grade != null)) {
            throw new IllegalArgumentException(
                    "Equipment template cards require complete identity and cost");
        }
    }
}
