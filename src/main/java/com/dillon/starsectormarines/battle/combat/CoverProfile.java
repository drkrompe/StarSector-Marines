package com.dillon.starsectormarines.battle.combat;

/**
 * What cover hides of a person, per {@link BodyPart}, from a shooter at full distance. A round that
 * lands on a hidden part has stopped in the cover; one that lands on an exposed part has beaten it,
 * so a body-part hit takes no further damage cut for the cover it got past.
 *
 * <p>Two kinds of cover reach a body. <b>Edge cover</b> is a wall, parapet or prop on the
 * boundary of the cell the man stands in, between him and the shooter. <b>Occupant cover</b> is
 * the ground he stands in — a trench — and hides him from every bearing. Each has one profile per
 * cover level; the distance ramps that scale them belong to {@link BallisticResolver}.
 *
 * <p>The rows were set so a level's expected interception equals what the old flat model's block
 * chance and damage cut took off together (level 3 hid about 70% of fire, level 2 half, level 1 a
 * quarter). What changes is which part of the man that leaves exposed. See
 * {@code body-part-cover.md}.
 */
public final class CoverProfile {

    /** Edge cover by level, indexed [level][part ordinal]: head, arms, torso, legs. */
    private static final float[][] EDGE = {
        {0f, 0f, 0f, 0f},
        {0f, 0.10f, 0.10f, 0.60f},
        {0f, 0.30f, 0.40f, 0.90f},
        {0.20f, 0.60f, 0.70f, 1.00f},
    };

    /** Occupant cover (a trench) by level: the legs and torso are below the lip, the head barely. */
    private static final float[][] OCCUPANT = {
        {0f, 0f, 0f, 0f},
        {0f, 0f, 0.30f, 0.80f},
        {0.10f, 0.30f, 0.70f, 1.00f},
        {0.20f, 0.50f, 0.90f, 1.00f},
    };

    private CoverProfile() {}

    /** Fraction of {@code part} edge cover of {@code level} hides at full distance. */
    public static float edge(int level, BodyPart part) {
        return EDGE[clampLevel(level)][part.ordinal()];
    }

    /** Fraction of {@code part} the ground of occupant-cover {@code level} hides at full distance. */
    public static float occupant(int level, BodyPart part) {
        return OCCUPANT[clampLevel(level)][part.ordinal()];
    }

    /** Share of all hits on an uncovered person that edge cover of {@code level} would stop. */
    public static float expectedEdgeInterception(int level) {
        float sum = 0f;
        for (BodyPart part : BodyPart.values()) sum += part.share * edge(level, part);
        return sum;
    }

    /** Share of all hits on an uncovered person that occupant cover of {@code level} would stop. */
    public static float expectedOccupantInterception(int level) {
        float sum = 0f;
        for (BodyPart part : BodyPart.values()) sum += part.share * occupant(level, part);
        return sum;
    }

    private static int clampLevel(int level) {
        return Math.max(0, Math.min(level, EDGE.length - 1));
    }
}
