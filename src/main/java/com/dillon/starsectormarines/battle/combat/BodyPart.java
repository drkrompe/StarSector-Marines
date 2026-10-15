package com.dillon.starsectormarines.battle.combat;

/**
 * Where on a person a round lands. Each part carries its {@link #share} of the silhouette a round
 * can reach, so the head is a small target before any cover enters into it. Parts are stacked up
 * the body from the feet — legs, torso, arms, head — and a round takes the part at the height it
 * meets the man, so a low round hits the legs. Only bodies whose type {@code hasBodyParts()} have
 * parts; a hull or a creature is one whole-body contact.
 *
 * <p>A part also decides what the hit is worth: its {@link #damageMultiplier} scales a payload
 * that landed on it, and a limb that takes structure is {@linkplain #wounds wounded}. The
 * multipliers are set so their share-weighted mean is one: in the open a man takes the damage he
 * always did on average, and only cover, which changes which parts are left to hit, shifts it.
 *
 * <p>See {@code body-part-cover.md}.
 */
public enum BodyPart {
    HEAD(0.10f, 1.60f),
    ARMS(0.20f, 0.75f),
    TORSO(0.40f, 1.10f),
    LEGS(0.30f, 0.85f);

    /** Bottom to top. */
    private static final BodyPart[] UP_THE_BODY = {LEGS, TORSO, ARMS, HEAD};

    /** Fraction of hits on an uncovered body that land on this part. The shares sum to one. */
    public final float share;

    /**
     * What a payload that landed on this part is worth, as a multiple of its authored damage.
     * Applied before mitigation and armour, which stay whole-body layers.
     */
    public final float damageMultiplier;

    BodyPart(float share, float damageMultiplier) {
        this.share = share;
        this.damageMultiplier = damageMultiplier;
    }

    /**
     * Whether a hit here that takes structure leaves a wound: a leg slows the man, an arm costs
     * him his aim. A head or torso hit is only damage.
     */
    public boolean wounds() {
        return this == LEGS || this == ARMS;
    }

    /**
     * The part at {@code heightFraction} up the body, 0 at the feet and 1 at the top. Each part
     * spans a band as tall as its share, so a uniform height lands on each part in proportion to
     * its share.
     */
    public static BodyPart atHeight(float heightFraction) {
        float r = heightFraction;
        for (BodyPart part : UP_THE_BODY) {
            r -= part.share;
            if (r < 0f) return part;
        }
        return HEAD;
    }
}
