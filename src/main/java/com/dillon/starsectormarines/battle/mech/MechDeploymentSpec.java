package com.dillon.starsectormarines.battle.mech;

/**
 * Frozen campaign-to-battle description of one support mech. Campaign objects
 * never cross the seam; the shuttle receives only these stable values.
 */
public record MechDeploymentSpec(
        MechVariant variant,
        MechRole role,
        MissileReplenisherComponent missileReplenisher,
        MechWeaponComponent arms,
        MechWeaponComponent leftShoulder,
        MechWeaponComponent rightShoulder,
        String displayName) {

    public MechDeploymentSpec {
        if (variant == null) throw new IllegalArgumentException("Mech variant is required");
        if (role == null) role = variant.defaultRole;
        if (missileReplenisher == null) {
            missileReplenisher = MissileReplenisherComponent.STANDARD;
        }
        if (arms == null) arms = variant.arms;
        if (displayName != null) {
            displayName = displayName.trim();
            if (displayName.isEmpty()) displayName = null;
        }
    }

    public MechDeploymentSpec(MechVariant variant, MechRole role,
                              MissileReplenisherComponent missileReplenisher,
                              MechWeaponComponent arms,
                              MechWeaponComponent leftShoulder,
                              MechWeaponComponent rightShoulder) {
        this(variant, role, missileReplenisher, arms, leftShoulder,
                rightShoulder, null);
    }

    public MechDeploymentSpec(MechVariant variant, MechRole role,
                              MissileReplenisherComponent missileReplenisher) {
        this(variant, role, missileReplenisher,
                variant != null ? variant.arms : null,
                variant != null ? variant.leftShoulder : null,
                variant != null ? variant.rightShoulder : null, null);
    }

    public static MechDeploymentSpec standard(MechVariant variant) {
        return new MechDeploymentSpec(variant, variant.defaultRole,
                MissileReplenisherComponent.STANDARD,
                variant.arms, variant.leftShoulder, variant.rightShoulder, null);
    }
}
