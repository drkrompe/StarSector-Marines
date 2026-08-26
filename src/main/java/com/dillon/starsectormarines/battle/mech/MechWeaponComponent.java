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

    DUAL_CHAINGUNS("Dual chainguns", MountFamily.ARMS, "weapon.mech-chaingun",
            12, -1, LayeredMechAppearance.ARMS_CHAINGUN),
    NOSE_CHAINGUN("Nose chaingun", MountFamily.ARMS, "weapon.mech-chaingun",
            6, -1, LayeredMechAppearance.ARMS_NOSE_CHAINGUN),
    DUAL_LINEAR_CANNONS("Dual linear cannons", MountFamily.ARMS, "weapon.mech-linear-cannon",
            2, -1, LayeredMechAppearance.ARMS_LINEAR_CANNON),
    SINGLE_HEAVY_CANNON("Heavy cannon", MountFamily.ARMS, "weapon.mech-heavy-cannon",
            1, -1, LayeredMechAppearance.ARMS_HEAVY_CANNON),

    SRM_5("SRM-5", MountFamily.SHOULDER, "weapon.mech-srm-pod",
            2, 6, LayeredMechAppearance.POD_SMALL_SRM),
    SRM_15("SRM-15", MountFamily.SHOULDER, "weapon.mech-srm-pod",
            4, 6, LayeredMechAppearance.POD_LARGE_SRM),
    LRM_5("LRM-5", MountFamily.SHOULDER, "weapon.mech-lrm-artillery",
            2, 4, LayeredMechAppearance.POD_SMALL_LRM),
    LRM_15("LRM-15", MountFamily.SHOULDER, "weapon.mech-lrm-artillery",
            5, 3, LayeredMechAppearance.POD_LARGE_LRM);

    public enum MountFamily { ARMS, SHOULDER }

    public final String displayName;
    public final MountFamily mountFamily;
    /** Stable catalog id for the installed gun or launcher. */
    public final String weaponId;
    /** Representative projectiles emitted per trigger, not literal launcher tubes. */
    public final int projectilesPerTrigger;
    /** Trigger pulls available at full supply; negative means unlimited. */
    public final int ammoCapacity;
    /** Arms or shoulder selector consumed by the layered compositor. */
    public final int appearanceSelector;

    MechWeaponComponent(String displayName, MountFamily mountFamily,
                        String weaponId, int projectilesPerTrigger,
                        int ammoCapacity, int appearanceSelector) {
        this.displayName = displayName;
        this.mountFamily = mountFamily;
        this.weaponId = weaponId;
        this.projectilesPerTrigger = projectilesPerTrigger;
        this.ammoCapacity = ammoCapacity;
        this.appearanceSelector = appearanceSelector;
    }

    public WeaponDef weaponDef() { return WeaponRegistry.require(weaponId); }

    public boolean accepts(MechMountSlot slot) {
        return mountFamily == MountFamily.ARMS
                ? slot == MechMountSlot.ARMS
                : slot.isShoulder();
    }
}
