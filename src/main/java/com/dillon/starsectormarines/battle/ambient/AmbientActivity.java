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
    SOCIALIZING,
    WORKING,
    INSPECTING,
    FIRING_PRIMARY,
    PRACTICING_EQUIPMENT,
    WALKING
}
