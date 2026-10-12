package com.dillon.starsectormarines.battle.warship;

/**
 * What a mission wants a ship to be doing about its objective right now. It is
 * set by whoever fits the ship (a mission launch, a script, a test) and can be
 * changed at any moment: "take the objective" becomes "defend it from the next
 * wave" without the ship being replaced. It is never derived from the ship's
 * faction.
 */
public enum MissionStance {
    /** The ship supports an assault on the objective. */
    ATTACK,
    /** The ship defends held ground and denies inbound drops. */
    DEFEND,
    /** No objective job: the ship keeps its default behaviour. */
    HOLD
}
