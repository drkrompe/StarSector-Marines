package com.dillon.starsectormarines.battle.appearance;

/**
 * The stateless weapon-up rule {@code FacingSystem} calls when it authors the
 * layered pose columns each tick.
 */
public final class LiveAppearance {

    /** Window (s) after a unit fires during which the weapon-up pose shows. */
    public static final float WEAPON_UP_TIME = 0.25f;

    private LiveAppearance() {}

    /**
     * Whether the weapon-up pose shows: while aiming a secondary ({@code inAim}),
     * or — for a combatant — during the trailing {@link #WEAPON_UP_TIME} window
     * after a primary shot (the cooldown timer freshly reset and still counting
     * down from it). A non-combatant with no aim in progress is never weapon-up.
     */
    public static boolean weaponUp(boolean inAim, boolean combatant, float cooldownTimer, float attackCooldown) {
        return inAim || (combatant && cooldownTimer > attackCooldown - WEAPON_UP_TIME && cooldownTimer > 0f);
    }
}
