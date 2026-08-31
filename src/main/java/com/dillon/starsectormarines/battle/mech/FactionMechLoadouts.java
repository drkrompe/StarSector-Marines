package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.mech.components.MechLoadoutComponent;

import java.util.Locale;

/**
 * Campaign-faction hardware doctrine, deliberately separate from appearance-only
 * mech liveries. The chassis catalog remains the neutral production fit; this
 * seam swaps faction equipment onto compatible hardpoints at battle spawn.
 */
public final class FactionMechLoadouts {

    public static final String TRI_TACHYON_ID = "tritachyon";
    public static final String HEGEMONY_ID = "hegemony";
    public static final String LUDDIC_PATH_ID = "luddic_path";
    public static final String LIONS_GUARD_ID = "lions_guard";

    private FactionMechLoadouts() {
    }

    /** Builds the faction issue for one chassis without mutating its neutral fit. */
    public static MechLoadoutComponent create(MechVariant variant, MechRole role,
                                               String factionId) {
        if (variant == null) throw new IllegalArgumentException("Mech variant is required");
        MechRole deployedRole = role != null ? role : variant.defaultRole;
        if (variant == MechVariant.BULWARK && isTriTachyon(factionId)) {
            return new MechLoadoutComponent(variant,
                    MechWeaponComponent.DUAL_PULSE_LASERS,
                    variant.leftShoulder,
                    MechWeaponComponent.SHOULDER_LASER_CANNON,
                    deployedRole);
        }
        if (variant == MechVariant.BULWARK && isFaction(factionId, HEGEMONY_ID)) {
            return new MechLoadoutComponent(variant,
                    MechWeaponComponent.DUAL_BASTION_AUTOCANNONS,
                    variant.leftShoulder, variant.rightShoulder, deployedRole);
        }
        if (variant == MechVariant.HOUND && isFaction(factionId, LUDDIC_PATH_ID)) {
            return new MechLoadoutComponent(variant,
                    MechWeaponComponent.DEMOLITION_CANNON,
                    variant.leftShoulder, variant.rightShoulder, deployedRole);
        }
        if (variant == MechVariant.SIROCCO && isFaction(factionId, LIONS_GUARD_ID)) {
            return new MechLoadoutComponent(variant,
                    variant.arms, variant.leftShoulder,
                    MechWeaponComponent.THERMAL_LANCE, deployedRole);
        }
        return variant.createLoadout(deployedRole);
    }

    private static boolean isTriTachyon(String factionId) {
        return isFaction(factionId, TRI_TACHYON_ID);
    }

    private static boolean isFaction(String factionId, String expected) {
        return factionId != null
                && expected.equals(factionId.trim().toLowerCase(Locale.ROOT));
    }
}
