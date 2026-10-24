package com.dillon.starsectormarines.battle.evacuation.flier;

import java.util.Locale;

/**
 * How much a Harrier's bite on a marine bleeds, on screen: the particles alone, heavier particles, or the
 * particles and a short-lived stain on the ground where it landed. Presentation only.
 */
public enum HarrierBlood {
    PARTICLES(1f, false),
    STRONG(2f, false),
    STAIN(1f, true);

    /**
     * {@code -Dbattle.harrier.blood=particles|strong|stain}. Temporary: the owner's picks question
     * {@code harrier-blood} decides it, and this switch, the losing modes and their code go with the answer.
     */
    public static final String PROPERTY = "battle.harrier.blood";

    /** The mode this process plays. */
    public static final HarrierBlood MODE = parse(System.getProperty(PROPERTY, "particles"));

    /** How heavy the blood spray is, 1 for the plain recipe. */
    public final float weight;
    /** Whether a bite leaves a stain on the ground. */
    public final boolean stains;

    HarrierBlood(float weight, boolean stains) {
        this.weight = weight;
        this.stains = stains;
    }

    static HarrierBlood parse(String value) {
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return PARTICLES;
        }
    }
}
