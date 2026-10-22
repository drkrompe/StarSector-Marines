package com.dillon.starsectormarines.battle.mech;

/**
 * Where on a mech a round landed: one of the sections a chassis is taken apart by. Each has its
 * own armor and structure ({@link MechSections}) and its own consequence at zero. Only the torso
 * kills; every other section at zero costs a capability and passes further damage to the torso.
 * See {@code mechs-nouns.md}.
 */
public enum MechSection {
    /** At zero the chassis is crippled: it pivots, turns its torso and fires, but cannot walk. */
    LEGS(null),
    /** At zero the mech is destroyed; the only way it dies. */
    TORSO(null),
    /** The centreline arms mount (a nose or chin gun): at zero that weapon is destroyed. */
    ARMS(MechMountSlot.ARMS),
    /** The left arm of a two-armed chassis: at zero its gun is destroyed. */
    LEFT_ARM(MechMountSlot.LEFT_ARM),
    /** The right arm of a two-armed chassis: at zero its gun is destroyed. */
    RIGHT_ARM(MechMountSlot.RIGHT_ARM),
    /** The left shoulder mount: at zero that weapon is destroyed. */
    LEFT_SHOULDER(MechMountSlot.LEFT_SHOULDER),
    /** The right shoulder mount: at zero that weapon is destroyed. */
    RIGHT_SHOULDER(MechMountSlot.RIGHT_SHOULDER),
    /** At zero the sensors are lost: vision and accuracy drop. */
    HEAD(null);

    static final MechSection[] VALUES = values();

    private final MechMountSlot mount;

    MechSection(MechMountSlot mount) {
        this.mount = mount;
    }

    /** The hardpoint this section carries, or null for a section that is not a mount. */
    public MechMountSlot mount() {
        return mount;
    }

    /** The section that carries {@code slot}. */
    public static MechSection of(MechMountSlot slot) {
        return switch (slot) {
            case ARMS -> ARMS;
            case LEFT_ARM -> LEFT_ARM;
            case RIGHT_ARM -> RIGHT_ARM;
            case LEFT_SHOULDER -> LEFT_SHOULDER;
            case RIGHT_SHOULDER -> RIGHT_SHOULDER;
        };
    }
}
