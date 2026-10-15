package com.dillon.starsectormarines.battle.combat;

/**
 * Where on a person a round lands. Each part carries its {@link #share} of the silhouette a round
 * can reach, so the head is a small target before any cover enters into it. Parts are stacked up
 * the body from the feet — legs, torso, arms, head — and a round takes the part at the height it
 * meets the man, so a low round hits the legs. Only bodies whose type {@code hasBodyParts()} have
 * parts; a hull or a creature is one whole-body contact.
 *
 * <p>See {@code body-part-cover.md}.
 */
public enum BodyPart {
    HEAD(0.10f),
    ARMS(0.20f),
    TORSO(0.40f),
    LEGS(0.30f);

    /** Bottom to top. */
    private static final BodyPart[] UP_THE_BODY = {LEGS, TORSO, ARMS, HEAD};

    /** Fraction of hits on an uncovered body that land on this part. The shares sum to one. */
    public final float share;

    BodyPart(float share) {
        this.share = share;
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
