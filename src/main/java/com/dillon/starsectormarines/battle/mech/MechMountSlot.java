package com.dillon.starsectormarines.battle.mech;

/**
 * Physical hardpoints a modular strider chassis may expose. A chassis carries either one
 * centreline arms mount (the Hound's nose gun, the Warden's chin cannon) or a gun on each arm
 * (the Bulwark); {@link MechVariant#hasMount} says which.
 */
public enum MechMountSlot {
    /** The centreline arms mount: one gun on the nose or chin. */
    ARMS,
    LEFT_ARM,
    RIGHT_ARM,
    LEFT_SHOULDER,
    RIGHT_SHOULDER;

    public boolean isShoulder() {
        return this == LEFT_SHOULDER || this == RIGHT_SHOULDER;
    }

    /** Any mount that takes an arm gun: the centreline mount or either arm. */
    public boolean isArm() {
        return this == ARMS || isSideArm();
    }

    /** A gun out on one of the chassis's arms rather than on its centreline. */
    public boolean isSideArm() {
        return this == LEFT_ARM || this == RIGHT_ARM;
    }

    /** Hull widths from the centreline a gun on this mount is drawn at: left is negative. */
    public float armLateral() {
        return switch (this) {
            case LEFT_ARM -> -0.37f;
            case RIGHT_ARM -> 0.37f;
            default -> 0f;
        };
    }
}
