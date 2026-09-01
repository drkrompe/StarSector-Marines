package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.appearance.LayeredMechAppearance;
import com.dillon.starsectormarines.battle.weapon.WeaponDef;
import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;

/**
 * Immutable hardware installed in one mech hardpoint. The underlying
 * weapon catalog owns projectile behavior; this component owns the rack
 * size, ammunition bin and visual shell. A displayed LRM/SRM number is a
 * MechWarrior-style weight/readability class, while {@link #projectilesPerTrigger}
 * is the smaller representative packet the ground sim actually emits.
 */
public enum MechWeaponComponent {

    DUAL_CHAINGUNS("component.mech-dual-chainguns", "Dual chainguns", MountFamily.ARMS,
            HardpointType.BALLISTIC, 3, 2, "weapon.mech-chaingun",
            12, -1, LayeredMechAppearance.ARMS_CHAINGUN),
    NOSE_CHAINGUN("component.mech-nose-chaingun", "Nose chaingun", MountFamily.ARMS,
            HardpointType.BALLISTIC, 2, 2, "weapon.mech-chaingun",
            6, -1, LayeredMechAppearance.ARMS_NOSE_CHAINGUN),
    DUAL_LINEAR_CANNONS("component.mech-dual-linear-cannons", "Dual linear cannons",
            MountFamily.ARMS, HardpointType.BALLISTIC, 3, 2, "weapon.mech-linear-cannon",
            2, -1, LayeredMechAppearance.ARMS_LINEAR_CANNON),
    SINGLE_HEAVY_CANNON("component.mech-heavy-cannon", "Heavy cannon", MountFamily.ARMS,
            HardpointType.BALLISTIC, 3, 2, "weapon.mech-heavy-cannon",
            1, -1, LayeredMechAppearance.ARMS_HEAVY_CANNON),
    DUAL_PULSE_LASERS("component.mech-dual-pulse-lasers", "Dual pulse lasers",
            MountFamily.ARMS, HardpointType.ENERGY, 3, 2, "weapon.mech-pulse-laser",
            8, -1, LayeredMechAppearance.ARMS_PULSE_LASER),
    DUAL_BASTION_AUTOCANNONS("component.mech-bastion-autocannons",
            "Dual Bastion autocannons", MountFamily.ARMS,
            HardpointType.BALLISTIC, 3, 2, "weapon.mech-bastion-autocannon", 6, -1,
            LayeredMechAppearance.ARMS_BASTION_AUTOCANNON),
    DEMOLITION_CANNON("component.mech-demolition-cannon", "Demolition cannon",
            MountFamily.ARMS, HardpointType.BALLISTIC, 2, 2,
            "weapon.mech-demolition-cannon", 1, 5,
            LayeredMechAppearance.ARMS_DEMOLITION_CANNON),
    DUAL_MUSTER_AUTOGUNS("component.mech-dual-muster-autoguns", "Dual Muster autoguns",
            MountFamily.ARMS, HardpointType.BALLISTIC, 3, 2,
            "weapon.mech-muster-autogun", 6, -1,
            LayeredMechAppearance.ARMS_MUSTER_AUTOGUN),
    NOSE_MUSTER_AUTOGUN("component.mech-nose-muster-autogun", "Nose Muster autogun",
            MountFamily.ARMS, HardpointType.BALLISTIC, 2, 2,
            "weapon.mech-muster-autogun", 3, -1,
            LayeredMechAppearance.ARMS_NOSE_MUSTER_AUTOGUN),
    QUARRY_BREAKER_CANNON("component.mech-quarry-breaker", "Quarry breaker cannon",
            MountFamily.ARMS, HardpointType.BALLISTIC, 2, 2,
            "weapon.mech-quarry-breaker", 1, 4,
            LayeredMechAppearance.ARMS_QUARRY_BREAKER),

    SRM_5("component.mech-srm-5", "SRM-5", MountFamily.SHOULDER,
            HardpointType.MISSILE, 1, 1, "weapon.mech-srm-pod",
            2, 6, LayeredMechAppearance.POD_SMALL_SRM),
    SRM_15("component.mech-srm-15", "SRM-15", MountFamily.SHOULDER,
            HardpointType.MISSILE, 3, 2, "weapon.mech-srm-pod",
            4, 6, LayeredMechAppearance.POD_LARGE_SRM),
    LRM_5("component.mech-lrm-5", "LRM-5", MountFamily.SHOULDER,
            HardpointType.MISSILE, 1, 1, "weapon.mech-lrm-artillery",
            2, 4, LayeredMechAppearance.POD_SMALL_LRM),
    LRM_15("component.mech-lrm-15", "LRM-15", MountFamily.SHOULDER,
            HardpointType.MISSILE, 3, 2, "weapon.mech-lrm-artillery",
            5, 3, LayeredMechAppearance.POD_LARGE_LRM),
    SHOULDER_LASER_CANNON("component.mech-shoulder-laser", "Shoulder laser cannon",
            MountFamily.SHOULDER, HardpointType.ENERGY, 3, 2,
            "weapon.mech-shoulder-laser", 1, -1,
            LayeredMechAppearance.POD_SHOULDER_LASER),
    THERMAL_LANCE("component.mech-thermal-lance", "Thermal lance",
            MountFamily.SHOULDER, HardpointType.ENERGY, 1, 2,
            "weapon.mech-thermal-lance", 1, -1,
            LayeredMechAppearance.POD_THERMAL_LANCE),
    PIONEER_ROCKET_CRADLE("component.mech-pioneer-rocket", "Pioneer rocket cradle",
            MountFamily.SHOULDER, HardpointType.MISSILE, 1, 1,
            "weapon.mech-pioneer-rocket", 4, 3,
            LayeredMechAppearance.POD_PIONEER_ROCKET);

    public enum MountFamily { ARMS, SHOULDER }
    public enum HardpointType { BALLISTIC, ENERGY, MISSILE }

    public final String id;
    public final String displayName;
    public final MountFamily mountFamily;
    public final HardpointType hardpointType;
    public final int footprintColumns;
    public final int footprintRows;
    public final int slotCost;
    /** Stable catalog id for the installed gun or launcher. */
    public final String weaponId;
    /** Representative projectiles emitted per trigger, not literal launcher tubes. */
    public final int projectilesPerTrigger;
    /** Trigger pulls available at full supply; negative means unlimited. */
    public final int ammoCapacity;
    /** Arms or shoulder selector consumed by the layered compositor. */
    public final int appearanceSelector;

    MechWeaponComponent(String id, String displayName, MountFamily mountFamily,
                        HardpointType hardpointType,
                        int footprintColumns, int footprintRows,
                        String weaponId, int projectilesPerTrigger,
                        int ammoCapacity, int appearanceSelector) {
        this.id = id;
        this.displayName = displayName;
        this.mountFamily = mountFamily;
        this.hardpointType = hardpointType;
        if (footprintColumns < 1
                || footprintColumns > MechFittingLayout.MAX_GRID_COLUMNS
                || footprintRows < 1
                || footprintRows > MechFittingLayout.MAX_GRID_ROWS) {
            throw new IllegalArgumentException("weapon footprint must fit the common grid");
        }
        this.footprintColumns = footprintColumns;
        this.footprintRows = footprintRows;
        this.slotCost = footprintColumns * footprintRows;
        this.weaponId = weaponId;
        this.projectilesPerTrigger = projectilesPerTrigger;
        this.ammoCapacity = ammoCapacity;
        this.appearanceSelector = appearanceSelector;
    }

    public WeaponDef weaponDef() { return WeaponRegistry.require(weaponId); }

    public static MechWeaponComponent findById(String id) {
        if (id == null) return null;
        for (MechWeaponComponent component : values()) {
            if (component.id.equals(id)) return component;
        }
        return null;
    }

    public static MechWeaponComponent resolve(String id, MechWeaponComponent fallback) {
        MechWeaponComponent component = findById(id);
        return component != null ? component : fallback;
    }

    public boolean accepts(MechMountSlot slot) {
        return mountFamily == MountFamily.ARMS
                ? slot == MechMountSlot.ARMS
                : slot.isShoulder();
    }
}
