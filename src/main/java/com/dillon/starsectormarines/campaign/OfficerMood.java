package com.dillon.starsectormarines.campaign;

/**
 * The comms officer's read on the company's current situation. Wraps the
 * archetype-driven briefing prose with prefix/suffix lines that color
 * the delivery — a DESPERATE officer frames the same TIME_RUSHED brief
 * differently than a SEASONED one.
 *
 * <p>Mood is derived from campaign state (cash trend, captain count,
 * fleet size, MRB rep) rather than authored per-contract. The mapping
 * lives in {@link OfficerMoodReader}.
 *
 * <p>Buckets kept deliberately coarse — four buckets keep the content
 * authoring cost flat and the texture readable; finer gradations would
 * pull toward "what bucket am I in?" optimization which violates
 * {@code [[project_moral_compass]]}-adjacent invisibility principles.
 *
 * <p>Composable: per {@code [[project_comms_officer_narrator]]}, mood
 * is one axis; officer <em>characterization</em> (veteran vs novice
 * personality) is a separate axis on top of mood, modeled when there's
 * a second officer to swap to.
 */
public enum OfficerMood {

    /** Cash bleeding, no runway. Take what you can. */
    DESPERATE,

    /** New officer, new company — earnest, over-explains, missing context. */
    GREEN,

    /** Default working state — terse, businesslike. */
    STEADY,

    /** Experienced, reads the room, editorializes with weight. */
    SEASONED;

    private static final OfficerMood[] VALUES = values();

    public static OfficerMood fromByte(byte b) { return VALUES[b & 0xFF]; }
    public byte toByte() { return (byte) ordinal(); }
}
