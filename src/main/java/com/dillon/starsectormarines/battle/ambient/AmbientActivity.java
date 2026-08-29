package com.dillon.starsectormarines.battle.ambient;

/**
 * Presentation-sized action vocabulary for authored, interruptible world work.
 *
 * <p>These actions do not resolve combat or campaign effects. They tell the
 * battle-owned ambient-task service how an actor should move and present its
 * existing equipment while the assignment owns that actor.</p>
 */
public enum AmbientActivity {
    IDLE,
    RESTING,
    /**
     * Off watch and choosing it: at a mess table, or in the lounge with nothing
     * in particular to do.
     *
     * <p>Worth keeping apart from {@link #IDLE}, which is an actor with nowhere
     * to be. They look the same standing still and mean opposite things — one is
     * a ship people live on, the other is a rotation that has failed somebody.
     */
    SOCIALIZING,
    EXERCISING,
    WORKING,
    INSPECTING,
    FIRING_PRIMARY,
    PRACTICING_EQUIPMENT,
    WALKING
}
