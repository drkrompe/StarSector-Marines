package com.dillon.starsectormarines.battle.combat;

/**
 * Where on a body a round lands: one part of a {@link BodyPlan}. Each part carries its
 * {@link #share} of the silhouette a round can reach, so a person's head is a small target before
 * any cover enters into it, and the cover columns that say how much of it each level of cover
 * hides. A plan stacks its parts up the body in height bands; which part a round takes is the
 * plan's question ({@link BodyPlan#atHeight}).
 *
 * <p>A part also decides what the hit is worth: its {@link #damageMultiplier} scales a payload
 * that landed on it, and its {@link #consequence} says what a hit that took structure leaves
 * behind. The infantry plan's multipliers are set so their share-weighted mean is one: in the
 * open a man takes the damage he always did on average, and only cover, which changes which parts
 * are left to hit, shifts it.
 *
 * <p>Parts are compared by identity. {@link #HEAD}, {@link #ARMS}, {@link #TORSO} and {@link #LEGS}
 * are the parts of {@link BodyPlan#INFANTRY}. See {@code body-part-cover.md}.
 */
public final class BodyPart {

    /** What a hit on a part leaves behind once it has taken structure from a body that lived. */
    public enum Consequence {
        /** Only damage. */
        NONE,
        /** A limb wound ({@link WoundService}): a leg slows the man, an arm costs him his aim. */
        LIMB_WOUND,
        /** A mech's legs: at zero the chassis is crippled and walks on at a crawl. */
        CRIPPLE,
        /** A mech's weapon mount: at zero the weapon on it is destroyed. */
        MOUNT_LOSS,
        /** A mech's head: at zero its sensors are lost, cutting vision and accuracy. */
        SENSOR_LOSS
    }

    /** Cover levels a part carries a column for: none, low, medium, high. */
    static final int COVER_LEVELS = 4;

    // Cover columns by level 0..3. The rows were set so a level's expected interception equals
    // what the old flat model's block chance and damage cut took off together; see CoverProfile.
    public static final BodyPart HEAD = new BodyPart("HEAD", 0, 0.10f, 1.60f, Consequence.NONE,
            new float[] {0f, 0f, 0f, 0.20f},
            new float[] {0f, 0f, 0.10f, 0.20f},
            new float[] {0f, 0f, 0f, 0.05f});
    public static final BodyPart ARMS = new BodyPart("ARMS", 1, 0.20f, 0.75f, Consequence.LIMB_WOUND,
            new float[] {0f, 0.10f, 0.30f, 0.60f},
            new float[] {0f, 0f, 0.30f, 0.50f},
            new float[] {0f, 0f, 0.05f, 0.20f});
    public static final BodyPart TORSO = new BodyPart("TORSO", 2, 0.40f, 1.10f, Consequence.NONE,
            new float[] {0f, 0.10f, 0.40f, 0.70f},
            new float[] {0f, 0.30f, 0.70f, 0.90f},
            new float[] {0f, 0.04f, 0.20f, 0.40f});
    public static final BodyPart LEGS = new BodyPart("LEGS", 3, 0.30f, 0.85f, Consequence.LIMB_WOUND,
            new float[] {0f, 0.60f, 0.90f, 1.00f},
            new float[] {0f, 0.80f, 1.00f, 1.00f},
            new float[] {0f, 0.45f, 0.70f, 0.85f});

    private final String name;
    private final int index;

    /** Fraction of hits on an uncovered body that land on this part. A plan's shares sum to one. */
    public final float share;

    /**
     * What a payload that landed on this part is worth, as a multiple of its authored damage.
     * Applied before mitigation and armour, which stay whole-body layers.
     */
    public final float damageMultiplier;

    /** What a hit here that took structure leaves behind. */
    public final Consequence consequence;

    private final float[] edgeCover;
    private final float[] occupantCover;
    private final float[] crossedCover;

    /**
     * @param index this part's position in its plan's {@link BodyPlan#parts()}, so per-part tables
     *        can be flat arrays
     * @param edgeCover fraction hidden by edge cover (a wall on the cell boundary), by level 0..3
     * @param occupantCover fraction hidden by the ground the body stands in (a trench), by level
     * @param crossedCover fraction of rounds headed here a prop crossed in the lane catches, by level
     */
    public BodyPart(String name, int index, float share, float damageMultiplier, Consequence consequence,
                    float[] edgeCover, float[] occupantCover, float[] crossedCover) {
        // A part with no rows at all keeps the flat whole-body cover chance (a mech section).
        boolean rowless = edgeCover == null && occupantCover == null && crossedCover == null;
        if (!rowless && (edgeCover == null || occupantCover == null || crossedCover == null
                || edgeCover.length != COVER_LEVELS || occupantCover.length != COVER_LEVELS
                || crossedCover.length != COVER_LEVELS)) {
            throw new IllegalArgumentException(name + ": a cover column needs " + COVER_LEVELS + " levels");
        }
        this.name = name;
        this.index = index;
        this.share = share;
        this.damageMultiplier = damageMultiplier;
        this.consequence = consequence;
        this.edgeCover = edgeCover != null ? edgeCover.clone() : null;
        this.occupantCover = occupantCover != null ? occupantCover.clone() : null;
        this.crossedCover = crossedCover != null ? crossedCover.clone() : null;
    }

    /**
     * A part with no cover rows of its own: cover meets the body it belongs to with the flat
     * whole-body chance and damage cut, exactly as it meets a body without parts.
     */
    public BodyPart(String name, int index, float share, float damageMultiplier, Consequence consequence) {
        this(name, index, share, damageMultiplier, consequence, null, null, null);
    }

    /**
     * Whether cover meets this part by its own rows. A part without them is hit wherever the round
     * crosses, but cover treats its body as one whole-body contact.
     */
    public boolean hasCoverRows() {
        return edgeCover != null;
    }

    /** The part's name, upper case (e.g. {@code LEGS}). */
    public String name() {
        return name;
    }

    /** This part's position in its plan's {@link BodyPlan#parts()}. */
    public int index() {
        return index;
    }

    /**
     * Whether a hit here that takes structure leaves a wound: a leg slows the man, an arm costs
     * him his aim. A head or torso hit is only damage.
     */
    public boolean wounds() {
        return consequence == Consequence.LIMB_WOUND;
    }

    /** Fraction of this part edge cover of {@code level} hides at full distance. */
    float edgeCover(int level) {
        return edgeCover[clampLevel(level)];
    }

    /** Fraction of this part occupant cover of {@code level} hides at full distance. */
    float occupantCover(int level) {
        return occupantCover[clampLevel(level)];
    }

    /** Fraction of rounds headed for this part a prop of {@code level} crossed in the lane catches. */
    float crossedCover(int level) {
        return crossedCover[clampLevel(level)];
    }

    /** The infantry part at {@code heightFraction} up the body; see {@link BodyPlan#atHeight}. */
    public static BodyPart atHeight(float heightFraction) {
        return BodyPlan.INFANTRY.atHeight(heightFraction);
    }

    @Override
    public String toString() {
        return name;
    }

    private static int clampLevel(int level) {
        return Math.max(0, Math.min(level, COVER_LEVELS - 1));
    }
}
