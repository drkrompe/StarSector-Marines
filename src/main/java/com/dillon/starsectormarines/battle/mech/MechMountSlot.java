package com.dillon.starsectormarines.battle.mech;

/** Physical hardpoints exposed by every modular strider chassis. */
public enum MechMountSlot {
    ARMS,
    LEFT_SHOULDER,
    RIGHT_SHOULDER;

    public boolean isShoulder() {
        return this == LEFT_SHOULDER || this == RIGHT_SHOULDER;
    }
}
