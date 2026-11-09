package com.dillon.starsectormarines.battle.derelict;

import com.dillon.starsectormarines.battle.mech.MechRole;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.mech.MechWeaponComponent;
import com.dillon.starsectormarines.battle.mech.components.MechLoadoutComponent;
import com.dillon.starsectormarines.battle.unit.EntitySpec;

/**
 * The three loadouts of the derelict security walker (`derelict-lore-guide.md`, Units): one chassis
 * ({@link MechVariant#SECURITY_WALKER}), its variants by loadout and plate (`mech-lines.md`, law 2). The frame also names
 * the walker's posture in the security network ({@link WalkerPosture}): what a line frame holds, a siege frame fires on
 * and a vault frame guards.
 */
public enum DerelictWalkerFrame {

    /** Heavy cannon, autocannon and plate: holds a layer, comes to what the flocks pin, crushes cover. */
    LINE(MechWeaponComponent.HC_10, MechWeaponComponent.AC_6,
            MechWeaponComponent.SRM_5, MechWeaponComponent.SRM_5, 1.0f, MechRole.BALANCED),
    /** Mortar-weight missile racks over two light guns: fires on what the flocks see, from its post. */
    SIEGE(MechWeaponComponent.CHAINGUN, MechWeaponComponent.CHAINGUN,
            MechWeaponComponent.LRM_15_FRAG, MechWeaponComponent.LRM_15_FRAG, 0.85f, MechRole.LR_SUPPORT),
    /** Two heavy cannon under the heaviest plate: the vault's last guard. */
    VAULT(MechWeaponComponent.HC_10, MechWeaponComponent.HC_10,
            MechWeaponComponent.SRM_15, MechWeaponComponent.SRM_15, 1.45f, MechRole.BALANCED);

    /** The one chassis every frame is built on. */
    public static final MechVariant CHASSIS = MechVariant.SECURITY_WALKER;

    public final MechWeaponComponent leftArm;
    public final MechWeaponComponent rightArm;
    public final MechWeaponComponent leftShoulder;
    public final MechWeaponComponent rightShoulder;
    /** Plate as a share of the chassis's stock armour capacity. UNTUNED. */
    public final float plateShare;
    public final MechRole role;

    DerelictWalkerFrame(MechWeaponComponent leftArm, MechWeaponComponent rightArm,
                        MechWeaponComponent leftShoulder, MechWeaponComponent rightShoulder,
                        float plateShare, MechRole role) {
        this.leftArm = leftArm;
        this.rightArm = rightArm;
        this.leftShoulder = leftShoulder;
        this.rightShoulder = rightShoulder;
        this.plateShare = plateShare;
        this.role = role;
    }

    /** This frame's armour capacity on the walker. */
    public float armorCapacity() {
        return CHASSIS.armorCapacity * plateShare;
    }

    /** Whether this frame fires on contacts its zone's flocks hold rather than closing on them. */
    public boolean spots() {
        return this == SIEGE;
    }

    /** The frame's mounts on the walker. */
    public MechLoadoutComponent createLoadout() {
        return new MechLoadoutComponent(CHASSIS, null, leftArm, rightArm, leftShoulder, rightShoulder, role);
    }

    /** The walker's spawn stats with this frame's plate; range and vision follow the frame's own guns. */
    public EntitySpec applyTo(EntitySpec spec) {
        CHASSIS.applyTo(spec).armor(armorCapacity(), CHASSIS.armorRating);
        float range = maxWeaponRange();
        spec.attackRange(range).visionRange(Math.max(CHASSIS.visionRange, range));
        return spec;
    }

    /** The longest reach among this frame's guns. */
    public float maxWeaponRange() {
        float max = 0f;
        for (MechWeaponComponent weapon : new MechWeaponComponent[] {leftArm, rightArm, leftShoulder, rightShoulder}) {
            if (weapon != null) max = Math.max(max, weapon.weaponDef().range);
        }
        return max;
    }
}
