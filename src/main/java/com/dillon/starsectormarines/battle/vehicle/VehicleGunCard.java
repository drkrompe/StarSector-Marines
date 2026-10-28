package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.mech.MechWeaponComponent;
import com.dillon.starsectormarines.battle.mech.MechWeaponComponent.HardpointType;
import com.dillon.starsectormarines.battle.turret.TurretCatalogRegistry;

/**
 * The gun card on a vehicle's doll (mechs-nouns.md, a vehicle is a mech for parts): the socket its turret fills, typed
 * by the stock gun's hardpoint family and as tall as that gun is, as a mech's arm socket is typed and sized. A mech
 * weapon fits it by the mech's own rule: the same hardpoint type, and a footprint the card accommodates. A hull is
 * built around the gun it was designed for, so it carries what its turret was built for and no more.
 */
public enum VehicleGunCard {
    CANNON(HardpointType.BALLISTIC, 3),
    HEAVY_MG(HardpointType.BALLISTIC, 1),
    MORTAR(HardpointType.BALLISTIC, 2),
    LASER(HardpointType.ENERGY, 3),
    PULSE(HardpointType.ENERGY, 2),
    LRM(HardpointType.MISSILE, 2),
    SRM(HardpointType.MISSILE, 2);

    /** Every gun card is two columns wide, as every mech weapon is. */
    public static final int COLUMNS = 2;

    public final HardpointType hardpoint;
    public final int rows;

    VehicleGunCard(HardpointType hardpoint, int rows) {
        this.hardpoint = hardpoint;
        this.rows = rows;
    }

    /** The card for a turret structure; the heavy MG's for a hull with none authored. */
    public static VehicleGunCard of(String structureId) {
        if (structureId == null) return HEAVY_MG;
        return switch (structureId) {
            case TurretCatalogRegistry.TANK_CANNON_STRUCTURE_ID -> CANNON;
            case TurretCatalogRegistry.HEAVY_MORTAR_STRUCTURE_ID,
                    TurretCatalogRegistry.VEHICLE_MORTAR_STRUCTURE_ID -> MORTAR;
            case TurretCatalogRegistry.TANK_BURST_LASER_STRUCTURE_ID -> LASER;
            case TurretCatalogRegistry.TANK_PD_LASER_STRUCTURE_ID,
                    TurretCatalogRegistry.HOVER_PULSE_STRUCTURE_ID -> PULSE;
            case TurretCatalogRegistry.VEHICLE_LRM_STRUCTURE_ID -> LRM;
            case TurretCatalogRegistry.VEHICLE_SRM_STRUCTURE_ID -> SRM;
            default -> HEAVY_MG;
        };
    }

    /** The card on {@code type}'s doll; null for an unarmed hull, which has none. */
    public static VehicleGunCard of(VehicleType type) {
        return type != null && type.hasTurretWeapon() ? of(type.turretStructureId) : null;
    }

    /** Whether {@code weapon} goes in this card: the same hardpoint type, no wider or taller than the card. */
    public boolean accepts(MechWeaponComponent weapon) {
        return weapon != null && weapon.hardpointType == hardpoint
                && weapon.footprintColumns <= COLUMNS && weapon.footprintRows <= rows;
    }
}
