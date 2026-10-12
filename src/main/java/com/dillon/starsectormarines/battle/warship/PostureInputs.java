package com.dillon.starsectormarines.battle.warship;

/**
 * Everything {@link WarshipDoctrine} reads to choose a posture, as plain
 * values, so the choice is a function of a snapshot and testable with no world.
 *
 * @param hullFraction    remaining hull, 0 to 1
 * @param fluxFraction    flux over capacity, 0 to 1
 * @param overloaded      whether every mount is silent from an overload
 * @param shieldDown      whether the ship carries a shield that is not raised
 * @param airScore        the weighted sum of threats to this ship ({@link AirThreatPicture#score()})
 * @param airDescription  the threats in words, for the readout's reason
 * @param ownAir          friendly aircraft in the air, cover the ship is reluctant to abandon
 * @param stance          what the mission wants done about the objective
 */
public record PostureInputs(float hullFraction, float fluxFraction, boolean overloaded, boolean shieldDown,
                            float airScore, String airDescription, int ownAir, MissionStance stance) {

    /** A calm, healthy ship with nothing about it and no job: the doctrine's resting snapshot. */
    public static PostureInputs calm() {
        return new PostureInputs(1f, 0f, false, false, 0f, "", 0, MissionStance.HOLD);
    }
}
