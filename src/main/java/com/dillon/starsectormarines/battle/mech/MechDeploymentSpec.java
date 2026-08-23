package com.dillon.starsectormarines.battle.mech;

/**
 * Frozen campaign-to-battle description of one support mech. Campaign objects
 * never cross the seam; the shuttle receives only these stable values.
 */
public record MechDeploymentSpec(
        MechVariant variant,
        MechRole role,
        MissileReplenisherComponent missileReplenisher) {

    public MechDeploymentSpec {
        if (variant == null) throw new IllegalArgumentException("Mech variant is required");
        if (role == null) role = variant.defaultRole;
        if (missileReplenisher == null) {
            missileReplenisher = MissileReplenisherComponent.STANDARD;
        }
    }

    public static MechDeploymentSpec standard(MechVariant variant) {
        return new MechDeploymentSpec(variant, variant.defaultRole,
                MissileReplenisherComponent.STANDARD);
    }
}
