package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.mech.MechSection;

/**
 * The locations a damage doll colours: a vehicle's plates, hull, turret and drive, and a mech's
 * sections with its rear torso. The names are the {@code part-*} group ids in the doll SVGs and
 * the location contract in {@code d5-located-armor.md}; they change only in both.
 */
public enum DollLocation {
    FRONT, LEFT, RIGHT, REAR, HULL, TURRET, DRIVE,
    HEAD, TORSO, TORSO_REAR, ARMS, LEFT_ARM, RIGHT_ARM, LEFT_SHOULDER, RIGHT_SHOULDER, LEGS;

    private static final DollLocation[] VALUES = values();

    /** The location a doll part names, or null for a part this model does not know. */
    public static DollLocation ofPart(String part) {
        for (DollLocation location : VALUES) if (location.name().equals(part)) return location;
        return null;
    }

    /** The doll location a mech section draws in. */
    public static DollLocation of(MechSection section) {
        return switch (section) {
            case HEAD -> HEAD;
            case TORSO -> TORSO;
            case ARMS -> ARMS;
            case LEFT_ARM -> LEFT_ARM;
            case RIGHT_ARM -> RIGHT_ARM;
            case LEFT_SHOULDER -> LEFT_SHOULDER;
            case RIGHT_SHOULDER -> RIGHT_SHOULDER;
            case LEGS -> LEGS;
        };
    }
}
