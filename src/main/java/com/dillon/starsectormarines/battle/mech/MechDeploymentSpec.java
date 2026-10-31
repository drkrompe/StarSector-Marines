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
 *
 * <p>{@code refit} is what the company ship's Mech Lab was worth at launch ({@link MechRefit});
 * neutral for every mech the company does not own. {@code frame} stays the fit's own values, so
 * the refit is never folded in twice: {@link #deployedFrame()} is the two together.
 *
 * <p>{@code wear} is what an unfinished repair still leaves missing ({@link MechCarriedWear});
 * {@link MechCarriedWear#NONE} for a whole mech and every mech the company does not own.
 */
public record MechDeploymentSpec(
        MechVariant variant,
        MechRole role,
        MissileReplenisherComponent missileReplenisher,
        MechWeaponComponent arms,
        MechWeaponComponent leftArm,
        MechWeaponComponent rightArm,
        MechWeaponComponent leftShoulder,
        MechWeaponComponent rightShoulder,
        String displayName,
        MechFrame frame,
        String campaignId,
        MechRefit refit,
        MechCarriedWear wear) {

    public MechDeploymentSpec {
        if (variant == null) throw new IllegalArgumentException("Mech variant is required");
        if (role == null) role = variant.defaultRole;
        if (missileReplenisher == null) {
            missileReplenisher = MissileReplenisherComponent.STANDARD;
        }
        if (frame == null) frame = MechFrame.stock(variant);
        if (refit == null) refit = MechRefit.NEUTRAL;
        if (wear == null) wear = MechCarriedWear.NONE;
        if (displayName != null) {
            displayName = displayName.trim();
            if (displayName.isEmpty()) displayName = null;
        }
    }

    /** A whole mech. */
    public MechDeploymentSpec(MechVariant variant, MechRole role,
                              MissileReplenisherComponent missileReplenisher,
                              MechWeaponComponent arms,
                              MechWeaponComponent leftArm,
                              MechWeaponComponent rightArm,
                              MechWeaponComponent leftShoulder,
                              MechWeaponComponent rightShoulder,
                              String displayName,
                              MechFrame frame,
                              String campaignId,
                              MechRefit refit) {
        this(variant, role, missileReplenisher, arms, leftArm, rightArm, leftShoulder, rightShoulder,
                displayName, frame, campaignId, refit, MechCarriedWear.NONE);
    }

    /** A mech off the founding hull's refits: neutral. */
    public MechDeploymentSpec(MechVariant variant, MechRole role,
                              MissileReplenisherComponent missileReplenisher,
                              MechWeaponComponent arms,
                              MechWeaponComponent leftArm,
                              MechWeaponComponent rightArm,
                              MechWeaponComponent leftShoulder,
                              MechWeaponComponent rightShoulder,
                              String displayName,
                              MechFrame frame,
                              String campaignId) {
        this(variant, role, missileReplenisher, arms, leftArm, rightArm, leftShoulder, rightShoulder,
                displayName, frame, campaignId, MechRefit.NEUTRAL);
    }

    /**
     * An unowned mech with {@code armWeapon} on every arm mount the chassis has (its centreline
     * mount, or both arms of a two-armed chassis): nothing it suffers writes back.
     */
    public MechDeploymentSpec(MechVariant variant, MechRole role,
                              MissileReplenisherComponent missileReplenisher,
                              MechWeaponComponent armWeapon,
                              MechWeaponComponent leftShoulder,
                              MechWeaponComponent rightShoulder,
                              String displayName,
                              MechFrame frame) {
        this(variant, role, missileReplenisher,
                centreline(variant, armWeapon), side(variant, armWeapon), side(variant, armWeapon),
                leftShoulder, rightShoulder, displayName, frame, null, MechRefit.NEUTRAL);
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
                variant != null ? variant.leftArm : null,
                variant != null ? variant.rightArm : null,
                variant != null ? variant.leftShoulder : null,
                variant != null ? variant.rightShoulder : null, null, null, null, MechRefit.NEUTRAL);
    }

    private static MechWeaponComponent centreline(MechVariant variant, MechWeaponComponent armWeapon) {
        return variant != null && !variant.twoArmed() ? armWeapon : null;
    }

    private static MechWeaponComponent side(MechVariant variant, MechWeaponComponent armWeapon) {
        return variant != null && variant.twoArmed() ? armWeapon : null;
    }

    /** The weapon fitted on {@code slot}, or null when it is empty. */
    public MechWeaponComponent weaponAt(MechMountSlot slot) {
        return switch (slot) {
            case ARMS -> arms;
            case LEFT_ARM -> leftArm;
            case RIGHT_ARM -> rightArm;
            case LEFT_SHOULDER -> leftShoulder;
            case RIGHT_SHOULDER -> rightShoulder;
        };
    }

    /** This deployment with the ship's refit frozen over it. */
    public MechDeploymentSpec withRefit(MechRefit refit) {
        return new MechDeploymentSpec(variant, role, missileReplenisher, arms, leftArm, rightArm,
                leftShoulder, rightShoulder, displayName, frame, campaignId, refit, wear);
    }

    /** This deployment carrying {@code carried} out of an unfinished repair. */
    public MechDeploymentSpec withWear(MechCarriedWear carried) {
        return new MechDeploymentSpec(variant, role, missileReplenisher, arms, leftArm, rightArm,
                leftShoulder, rightShoulder, displayName, frame, campaignId, refit, carried);
    }

    /** A live loadout carrying exactly this fit, deliberately empty mounts included. */
    public MechLoadoutComponent createLoadout(MechRole loadoutRole) {
        return new MechLoadoutComponent(variant, arms, leftArm, rightArm, leftShoulder,
                rightShoulder, loadoutRole != null ? loadoutRole : role);
    }

    /** The frame the mech fights on: the fit's values with the refit's plate and coolant over them. */
    public MechFrame deployedFrame() {
        return refit.over(frame);
    }

    /**
     * The one place a deployment becomes a live mech's entity values: the chassis variant,
     * then the deployed frame's armor capacity and speed over it (armor rating stays the chassis's).
     * Pair with {@link #applyTo(MechLoadoutComponent)} for the flux reservoir and fire control.
     */
    public EntitySpec applyTo(EntitySpec spec) {
        MechFrame deployed = deployedFrame();
        spec.mechVariant(variant);
        spec.armor(deployed.armorCapacity(), variant.armorRating).moveSpeed(deployed.moveSpeed());
        // A spall liner's structure: the sections divide whatever the chassis lands with.
        if (deployed.systems().structureFactor() != 1f) {
            spec.health(variant.maxStructure * deployed.systems().structureFactor());
        }
        return spec;
    }

    /** The loadout's half: the deployed frame's flux reservoir, and the refit's aim and traverse. */
    public MechLoadoutComponent applyTo(MechLoadoutComponent loadout) {
        loadout.applyFrame(deployedFrame());
        loadout.fireControlAccuracy = refit.accuracyMult();
        loadout.torsoTurnRateDegrees *= refit.traverseMult();
        return loadout;
    }

    public static MechDeploymentSpec standard(MechVariant variant) {
        return new MechDeploymentSpec(variant, variant.defaultRole,
                MissileReplenisherComponent.STANDARD);
    }
}
