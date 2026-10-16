package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.mech.components.MechLoadoutComponent;
import com.dillon.starsectormarines.battle.unit.EntitySpec;

/**
 * Frozen campaign-to-battle description of one support mech. Campaign objects
 * never cross the seam; the shuttle receives only these stable values.
 *
 * <p>{@code campaignId} is the owned mech a battle's section damage writes back to
 * ({@link CompanyMechLedger}); null for a debug, story or mission-supplied mech the company
 * does not own, which has nothing to write back.
 */
public record MechDeploymentSpec(
        MechVariant variant,
        MechRole role,
        MissileReplenisherComponent missileReplenisher,
        MechWeaponComponent arms,
        MechWeaponComponent leftShoulder,
        MechWeaponComponent rightShoulder,
        String displayName,
        MechFrame frame,
        String campaignId) {

    public MechDeploymentSpec {
        if (variant == null) throw new IllegalArgumentException("Mech variant is required");
        if (role == null) role = variant.defaultRole;
        if (missileReplenisher == null) {
            missileReplenisher = MissileReplenisherComponent.STANDARD;
        }
        if (frame == null) frame = MechFrame.stock(variant);
        if (displayName != null) {
            displayName = displayName.trim();
            if (displayName.isEmpty()) displayName = null;
        }
    }

    /** An unowned mech: nothing it suffers writes back. */
    public MechDeploymentSpec(MechVariant variant, MechRole role,
                              MissileReplenisherComponent missileReplenisher,
                              MechWeaponComponent arms,
                              MechWeaponComponent leftShoulder,
                              MechWeaponComponent rightShoulder,
                              String displayName,
                              MechFrame frame) {
        this(variant, role, missileReplenisher, arms, leftShoulder, rightShoulder,
                displayName, frame, null);
    }

    public MechDeploymentSpec(MechVariant variant, MechRole role,
                              MissileReplenisherComponent missileReplenisher,
                              MechWeaponComponent arms,
                              MechWeaponComponent leftShoulder,
                              MechWeaponComponent rightShoulder,
                              String displayName) {
        this(variant, role, missileReplenisher, arms, leftShoulder,
                rightShoulder, displayName, null);
    }

    public MechDeploymentSpec(MechVariant variant, MechRole role,
                              MissileReplenisherComponent missileReplenisher,
                              MechWeaponComponent arms,
                              MechWeaponComponent leftShoulder,
                              MechWeaponComponent rightShoulder) {
        this(variant, role, missileReplenisher, arms, leftShoulder,
                rightShoulder, null, null);
    }

    public MechDeploymentSpec(MechVariant variant, MechRole role,
                              MissileReplenisherComponent missileReplenisher) {
        this(variant, role, missileReplenisher,
                variant != null ? variant.arms : null,
                variant != null ? variant.leftShoulder : null,
                variant != null ? variant.rightShoulder : null, null, null);
    }

    /**
     * The one place a deployment becomes a live mech's entity values: the chassis variant,
     * then the frozen frame's armor capacity and speed over it (armor rating stays the chassis's).
     * Pair with {@link MechLoadoutComponent#applyFrame(MechFrame)} for the heat reservoir.
     */
    public EntitySpec applyTo(EntitySpec spec) {
        spec.mechVariant(variant);
        spec.armor(frame.armorCapacity(), variant.armorRating).moveSpeed(frame.moveSpeed());
        return spec;
    }

    public static MechDeploymentSpec standard(MechVariant variant) {
        return new MechDeploymentSpec(variant, variant.defaultRole,
                MissileReplenisherComponent.STANDARD,
                variant.arms, variant.leftShoulder, variant.rightShoulder, null, null);
    }
}
