package com.dillon.starsectormarines.marine;

/** Result of one squad-wide weapon-and-armour issue transaction. */
public enum SquadEquipmentResult {
    APPLIED,
    INVALID_SQUAD,
    SQUAD_NOT_READY,
    STATIONED,
    UNKNOWN_WEAPON_DOCTRINE,
    UNKNOWN_ARMOR_DOCTRINE,
    /** The squad would be given a card the company has not collected. */
    UNKNOWN_CARD,
    INSUFFICIENT_CARGO,
    /** A suspended or revoked military warrant withholds a suit some occupant is not already wearing. */
    WARRANT_SUSPENDED
}
