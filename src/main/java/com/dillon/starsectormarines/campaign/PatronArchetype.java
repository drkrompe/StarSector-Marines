package com.dillon.starsectormarines.campaign;

/**
 * Personality / posture of a patron house — the third content axis
 * orthogonal to {@link HouseFlavor} (Corporate / Feudal / Underworld /
 * Sectarian) and {@link HouseRank} (Tier 1-4). Drives briefing register,
 * payment quirks, and the narrative hook the patron offers the player.
 *
 * <p>Same archetype renders differently per flavor: a Feudal
 * {@link #FALLEN_NOBLE} is a deposed bloodline; an Underworld
 * {@link #FALLEN_NOBLE} is a demoted Boss clawing back territory; a
 * Corporate {@link #FALLEN_NOBLE} is a fired exec funding a comeback
 * from severance.
 *
 * <p>Backed by {@link #ordinal()} into the {@code byte} slot in
 * {@link CampaignState}{@code .houseArchetype[]} — never reorder
 * existing entries; append-only. The enum is intentionally
 * open-ended so future archetypes (the {@code procedural fatigue}
 * mitigation #2 from {@code contracts.md}) can be added without
 * migration.
 *
 * <p>Starter set is the six from
 * {@code roadmap/campaign/contracts.md} §"Patron archetypes".
 */
public enum PatronArchetype {
    /** Terse, no preamble, missing context, "no time to do it right." Volatile payment. */
    TIME_RUSHED,
    /** Formal-but-faded; references past status. Bad or delayed payment. The moral-hook archetype. */
    FALLEN_NOBLE,
    /** Ideological, righteous, emotional appeals. Mediocre payment. */
    TRUE_BELIEVER,
    /** Polished, oblique, formal contract terms. Premium, reliable payment. */
    ESTABLISHED,
    /** Curt, no questions, ethically gray targets. Premium, no-questions cash. */
    SUSPICIOUS,
    /** Clumsy, over-specified, signals inexperience. Overpays. */
    NEWCOMER;

    private static final PatronArchetype[] VALUES = values();

    public static PatronArchetype fromByte(byte b) {
        return VALUES[b & 0xFF];
    }

    public byte toByte() {
        return (byte) ordinal();
    }
}
