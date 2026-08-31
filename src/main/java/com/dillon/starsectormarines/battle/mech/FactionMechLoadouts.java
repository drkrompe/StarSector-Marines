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
        return variant.createLoadout(deployedRole);
    }

    private static boolean isTriTachyon(String factionId) {
        return factionId != null
                && TRI_TACHYON_ID.equals(factionId.trim().toLowerCase(Locale.ROOT));
    }
}
