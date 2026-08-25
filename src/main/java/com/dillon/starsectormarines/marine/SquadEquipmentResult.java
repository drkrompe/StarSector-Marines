package com.dillon.starsectormarines.marine;

/** Result of one squad-wide weapon-and-armour issue transaction. */
public enum SquadEquipmentResult {
    APPLIED,
    INVALID_SQUAD,
    SQUAD_NOT_READY,
    STATIONED,
    UNKNOWN_WEAPON_DOCTRINE,
    UNKNOWN_ARMOR_DOCTRINE,
    MISSING_TEMPLATE,
    INSUFFICIENT_CARGO
}
