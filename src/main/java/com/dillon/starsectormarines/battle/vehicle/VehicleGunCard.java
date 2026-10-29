package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.mech.MechWeaponComponent;
import com.dillon.starsectormarines.battle.mech.MechWeaponComponent.HardpointType;
import com.dillon.starsectormarines.battle.turret.TurretCatalogRegistry;

/**
 * The gun card on a vehicle's doll (mechs-nouns.md, a vehicle is a mech for parts): the socket its turret fills, set by
 * the hull's size class and lock (vehicle-card-sizes.md), as a mech's arm socket is typed and sized. A mech weapon
 * fits it by the mech's own rule: the lock's hardpoint type, if it has one, and a footprint the card accommodates; the
 * footprint is the only cap on a weapon's power. A small card is unlocked, an omni slot that takes any one-row part.
 */
public enum VehicleGunCard {
    /** Small, unlocked: the APCs, the dozer, the technicals and the hover scout. */
    SMALL(null, 1),
    CANNON(HardpointType.BALLISTIC, 3),
    MORTAR(HardpointType.BALLISTIC, 2),
    LASER(HardpointType.ENERGY, 3),
    LRM(HardpointType.MISSILE, 2);

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
            case TurretCatalogRegistry.TANK_CANNON_STRUCTURE_ID -> CANNON;
            case TurretCatalogRegistry.HEAVY_MORTAR_STRUCTURE_ID,
                    TurretCatalogRegistry.VEHICLE_MORTAR_STRUCTURE_ID -> MORTAR;
            case TurretCatalogRegistry.TANK_BURST_LASER_STRUCTURE_ID -> LASER;
            case TurretCatalogRegistry.VEHICLE_LRM_STRUCTURE_ID -> LRM;
            default -> SMALL;
        };
    }

    /** The card on {@code type}'s doll; null for an unarmed hull, which has none. */
    public static VehicleGunCard of(VehicleType type) {
        return type != null && type.hasTurretWeapon() ? of(type.turretStructureId) : null;
    }

    /** Whether {@code weapon} goes in this card: the lock's hardpoint type if any, no wider or taller than the card. */
    public boolean accepts(MechWeaponComponent weapon) {
        return weapon != null && (hardpoint == null || weapon.hardpointType == hardpoint)
                && weapon.footprintColumns <= COLUMNS && weapon.footprintRows <= rows;
    }
}
