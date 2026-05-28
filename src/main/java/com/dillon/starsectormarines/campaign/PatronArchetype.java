package com.dillon.starsectormarines.campaign;

import java.util.Random;

/**
 * Personality / posture of a patron house — the third content axis
 * orthogonal to {@link HouseFlavor} (Corporate / Feudal / Underworld /
 * Sectarian) and {@link HouseRank} (Tier 1-4). Drives briefing register,
 * payment quirks, contract-offer urgency, and the narrative hook the
 * patron offers the player.
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
 * mitigation #2 from {@code narrative/overview.md}) can be added without
 * migration.
 *
 * <p>Starter set is the six from
 * {@code roadmap/campaign/narrative/overview.md} §"Patron archetypes".
 *
 * <h2>Offer windows</h2>
 * Each archetype carries an inclusive (min, max) range for how many
 * days an OFFERED contract from this patron stays on the table before
 * it lapses. The window reinforces the briefing register — TIME_RUSHED
 * gives the player days, not weeks; ESTABLISHED takes its time. Picked
 * once at offer-generation per {@link #rollOfferWindowDays(Random)}.
 */
public enum PatronArchetype {
    /** Terse, no preamble, missing context, "no time to do it right." Volatile payment. */
    TIME_RUSHED  ( 2,  3),
    /** Formal-but-faded; references past status. Bad or delayed payment. The moral-hook archetype. */
    FALLEN_NOBLE ( 7, 10),
    /** Ideological, righteous, emotional appeals. Mediocre payment. */
    TRUE_BELIEVER( 7, 10),
    /** Polished, oblique, formal contract terms. Premium, reliable payment. */
    ESTABLISHED  (10, 14),
    /** Curt, no questions, ethically gray targets. Premium, no-questions cash. */
    SUSPICIOUS   ( 3,  5),
    /** Clumsy, over-specified, signals inexperience. Overpays. */
    NEWCOMER     ( 5,  8);

    /** Inclusive minimum number of days an offer from this archetype stays open. */
    public final int minOfferDays;
    /** Inclusive maximum number of days an offer from this archetype stays open. */
    public final int maxOfferDays;

    PatronArchetype(int minOfferDays, int maxOfferDays) {
        this.minOfferDays = minOfferDays;
        this.maxOfferDays = maxOfferDays;
    }

    /**
     * Rolls a per-offer window length in days from {@code [minOfferDays, maxOfferDays]}
     * inclusive. Caller seeds the {@link Random} (per-offer determinism lives at
     * the caller; this method is a pure transform).
     */
    public int rollOfferWindowDays(Random r) {
        return minOfferDays + r.nextInt(maxOfferDays - minOfferDays + 1);
    }

    private static final PatronArchetype[] VALUES = values();

    public static PatronArchetype fromByte(byte b) {
        return VALUES[b & 0xFF];
    }

    public byte toByte() {
        return (byte) ordinal();
    }
}
