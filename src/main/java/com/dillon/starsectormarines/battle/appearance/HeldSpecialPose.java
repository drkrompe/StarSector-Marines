package com.dillon.starsectormarines.battle.appearance;

/**
 * A controlled Marine holding the direct-fire special its left button is on, instead of the primary:
 * the launcher or the anti-materiel rifle, carried while walking and raised to the aim while standing.
 *
 * @param readiness how far the special has been readied, 0 to 1; it is raised over the wind-up
 * @param sinceFired seconds since its last round, or infinity; a recent round plays the firing clip
 */
public record HeldSpecialPose(float readiness, float sinceFired) {
}
