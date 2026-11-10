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
            MechWeaponComponent.SRM_15, MechWeaponComponent.SRM_15, 1.45f, MechRole.BALANCED),
    /**
     * The medium, the MG-25 rounds walker (`derelict-watch.md`): walks its zone's round while the zone sleeps, and once
     * awake answers a pin anywhere in its perimeter, one-cell doors included.
     */
    ROUNDS(MechVariant.SECURITY_ROUNDS, MechWeaponComponent.CHAINGUN, null,
            MechWeaponComponent.SRM_5, null, 1.0f, MechRole.ASSAULT),
    /**
     * The large, the MG-70 hall frame (`derelict-watch.md`): keeps the plant's largest hall, too wide for a corridor,
     * and forces a dead door in its way.
     */
    HALL(MechVariant.SECURITY_HALL, MechWeaponComponent.HC_10, MechWeaponComponent.HC_10,
            MechWeaponComponent.SRM_15_FRAG, MechWeaponComponent.SRM_15_FRAG, 1.0f, MechRole.BALANCED),
    /**
     * The smallest, the MG-12 tender (`derelict-watch.md`, Field repair): unarmed, it follows its zone's walkers and
     * welds their plate back in the fight.
     */
    TENDER(MechVariant.SECURITY_TENDER, null, null, null, null, 1.0f, MechRole.ASSAULT),
    /**
     * A fallen hull's MG-18 stripper (`fallen-hull-nouns.md`, Stripper): unarmed, it cuts the metal on the claim into
     * feedstock ({@link StripperWork}).
     */
    STRIPPER(MechVariant.SECURITY_STRIPPER, null, null, null, null, 1.0f, MechRole.ASSAULT),
    /**
     * A fallen hull's MG-30 hauler (`fallen-hull-nouns.md`, Hauler): unarmed, it carries metal home and stakes out
     * ({@link HaulerWork}, {@link StakeTrain}).
     */
    HAULER(MechVariant.SECURITY_HAULER, null, null, null, null, 1.0f, MechRole.ASSAULT);

    /** The heavy chassis the line, siege and vault frames are built on. */
    public static final MechVariant CHASSIS = MechVariant.SECURITY_WALKER;

    /** The chassis this frame is built on: the heavy's, or the medium's or the large's. */
    public final MechVariant chassis;

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
        this(MechVariant.SECURITY_WALKER, leftArm, rightArm, leftShoulder, rightShoulder, plateShare, role);
    }

    DerelictWalkerFrame(MechVariant chassis, MechWeaponComponent leftArm, MechWeaponComponent rightArm,
                        MechWeaponComponent leftShoulder, MechWeaponComponent rightShoulder,
                        float plateShare, MechRole role) {
        this.chassis = chassis;
        this.leftArm = leftArm;
        this.rightArm = rightArm;
        this.leftShoulder = leftShoulder;
        this.rightShoulder = rightShoulder;
        this.plateShare = plateShare;
        this.role = role;
    }

    /** This frame's armour capacity on the walker. */
    public float armorCapacity() {
        return chassis.armorCapacity * plateShare;
    }

    /** Whether this frame carries a gun at all: the tender, stripper and hauler carry none. */
    public boolean armed() {
        return leftArm != null || rightArm != null || leftShoulder != null || rightShoulder != null;
    }

    /** Whether this frame is one of a fallen hull's workers, whose work the hull's rules direct rather than a posture. */
    public boolean worker() {
        return this == STRIPPER || this == HAULER;
    }

    /** Whether this frame fires on contacts its zone's flocks hold rather than closing on them. */
    public boolean spots() {
        return this == SIEGE;
    }

    /** The size class of this frame's chassis: which of the plant's ways it fits. */
    public DerelictWalkerClass size() {
        return DerelictWalkerClass.of(chassis);
    }

    /** The designation the frame's walker is named by. */
    public String designation() {
        return switch (this) {
            case LINE, SIEGE, VAULT -> "MG-40 " + name().toLowerCase() + " frame";
            case ROUNDS -> "MG-25 rounds walker";
            case HALL -> "MG-70 hall frame";
            case TENDER -> "MG-12 tender";
            case STRIPPER -> "MG-18 stripper";
            case HAULER -> "MG-30 hauler";
        };
    }

    /** The frame's mounts on the walker. */
    public MechLoadoutComponent createLoadout() {
        return new MechLoadoutComponent(chassis, null, leftArm, rightArm, leftShoulder, rightShoulder, role);
    }

    /** The walker's spawn stats with this frame's plate; range and vision follow the frame's own guns. */
    public EntitySpec applyTo(EntitySpec spec) {
        chassis.applyTo(spec).armor(armorCapacity(), chassis.armorRating);
        float range = maxWeaponRange();
        spec.attackRange(range).visionRange(Math.max(chassis.visionRange, range));
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
