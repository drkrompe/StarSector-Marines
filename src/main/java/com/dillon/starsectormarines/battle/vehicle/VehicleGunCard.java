package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.mech.MechFittingLayout;
import com.dillon.starsectormarines.battle.mech.MechWeaponComponent;
import com.dillon.starsectormarines.battle.mech.MechWeaponComponent.HardpointType;
import com.dillon.starsectormarines.battle.turret.TurretCatalogRegistry;

/**
 * The gun card on a vehicle's doll (mechs-nouns.md, a vehicle is a mech for parts): the socket its turret fills, set by
 * the hull's size class and lock (vehicle-card-sizes.md), as a mech's arm socket is typed and sized. A mech weapon
 * fits it by the mech's own rule: the lock's hardpoint type, if it has one, and a footprint the card accommodates; the
 * footprint is the only cap on a weapon's power. The small card and the gun tanks' cards are unlocked, omni slots that
 * take any part of their height: the owner's pick over yellow, green and red builds of one tank (hegemony-armor-line.md).
 * Only the specialists are locked: the APC carriers' mortar and missile cards and the laser tank's.
 */
public enum VehicleGunCard {
    /** Small, unlocked: the APCs, the technicals and the hover scout. */
    SMALL(null, 1),
    /** The Onager's: any three-row part, a heavy cannon, a three-row rack or emitter, or anything smaller. */
    HEAVY(null, 3),
    /** The Scorpion's: any two-row part, never a three-row one. */
    MEDIUM(null, 2),
    /** The fire-support APC's: two rows, ballistic, for its mortar. */
    MORTAR(HardpointType.BALLISTIC, 2),
    LASER(HardpointType.ENERGY, 3),
    /** The LRM carrier's: three rows, missile, room for a fifteen-tube rack. */
    LRM(HardpointType.MISSILE, 3),
    /**
     * A hull's second card, beside its turret ({@link VehicleType#coaxMount}): one row, ballistic, for a machine gun
     * or another one-row ballistic part (owner, 2026-11-10). Only the Onager carries one.
     */
    COAX(HardpointType.BALLISTIC, 1);

    /** Every gun card is two columns wide, as every mech weapon is. */
    public static final int COLUMNS = 2;

    /** The one hardpoint type the card takes; null for an omni card, which takes any. */
    public final HardpointType hardpoint;
    public final int rows;

    VehicleGunCard(HardpointType hardpoint, int rows) {
        this.hardpoint = hardpoint;
        this.rows = rows;
    }

    /** The card for a turret structure; the small card for a hull with none authored. */
    public static VehicleGunCard of(String structureId) {
        if (structureId == null) return SMALL;
        return switch (structureId) {
            case TurretCatalogRegistry.TANK_CANNON_STRUCTURE_ID -> HEAVY;
            case TurretCatalogRegistry.TANK_MEDIUM_CANNON_STRUCTURE_ID -> MEDIUM;
            case TurretCatalogRegistry.HEAVY_MORTAR_STRUCTURE_ID,
                    TurretCatalogRegistry.VEHICLE_MORTAR_STRUCTURE_ID -> MORTAR;
            case TurretCatalogRegistry.TANK_PL_3_STRUCTURE_ID -> LASER;
            case TurretCatalogRegistry.VEHICLE_LRM_STRUCTURE_ID -> LRM;
            default -> SMALL;
        };
    }

    /** The card on {@code type}'s doll; null for an unarmed hull, which has none. */
    public static VehicleGunCard of(VehicleType type) {
        return type != null && type.hasTurretWeapon() ? of(type.turretStructureId) : null;
    }

    /** The coax card on {@code type}'s doll; null for a hull with no coax mount, which is every hull but the Onager. */
    public static VehicleGunCard coaxOf(VehicleType type) {
        return type != null && type.coaxMount() != null ? COAX : null;
    }

    /** Whether {@code weapon} goes in this card: the lock's hardpoint type if any, no wider or taller than the card. */
    public boolean accepts(MechWeaponComponent weapon) {
        return weapon != null && (hardpoint == null || weapon.hardpointType == hardpoint)
                && MechFittingLayout.hasRoom(COLUMNS, rows, weapon.footprintColumns, weapon.footprintRows);
    }
}
