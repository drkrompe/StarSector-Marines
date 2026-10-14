package com.dillon.starsectormarines.battle.airsupport;

/**
 * What a pooled sortie is for. The airframe kind says what flies ({@link AirPoolKind}); the mission says what it was
 * called to do, and is the unit the pool's ledger and the evidence count by.
 *
 * <p>A bomber has one mission. A fighter has two, and which one is the caller's choice: a comms operator calls a
 * {@link #STRAFE} on ground the side has sighted (the same call that sends a bomber, answered with the other kind
 * when the target suits it), and a commander requests {@link #COVER} over ground of its own. Both draw on the one
 * fighter stock, so cover and strafe compete for the same airframes and the same regeneration clock.
 */
public enum AirMission {
    /** A stick of bombs on sighted ground. */
    BOMB,
    /** A gun run on sighted ground. */
    STRAFE,
    /** A bounded patrol over friendly ground, engaging hostile aircraft and, secondarily, known ground threats. */
    COVER;

    /** The mission a call for an airframe of {@code kind} that is not a cover request is. */
    public static AirMission strikeOf(AirPoolKind kind) {
        return kind == AirPoolKind.BOMBER ? BOMB : STRAFE;
    }
}
