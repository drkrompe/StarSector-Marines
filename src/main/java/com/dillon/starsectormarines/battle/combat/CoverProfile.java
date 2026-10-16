package com.dillon.starsectormarines.battle.combat;

/**
 * What cover hides of a body, per {@link BodyPart}, from a shooter at full distance. A round that
 * lands on a hidden part has stopped in the cover; one that lands on an exposed part has beaten it,
 * so a body-part hit takes no further damage cut for the cover it got past.
 *
 * <p>Three kinds of cover reach a body. <b>Edge cover</b> is a wall, parapet or prop on the
 * boundary of the cell the man stands in, between him and the shooter. <b>Occupant cover</b> is
 * the ground he stands in — a trench — and hides him from every bearing. <b>Crossed cover</b> is a
 * walkable prop the round passes over somewhere in the lane, short of the man; it catches the
 * round by the part the round is headed for. Each has one profile per cover level; the distance
 * ramps that scale them belong to {@link BallisticResolver}.
 *
 * <p>The rows were set so a level's expected interception equals what the old flat model's block
 * chance and damage cut took off together (level 3 hid about 70% of fire, level 2 half, level 1 a
 * quarter). What changes is which part of the man that leaves exposed. See
 * {@code body-part-cover.md}.
 *
 * <p>The columns themselves belong to the parts, so each {@link BodyPlan} authors its own: a
 * plan's parts carry what each level of each kind of cover hides of them. The expected
 * interceptions read a plan; the one-argument forms read {@link BodyPlan#INFANTRY}.
 */
public final class CoverProfile {

    private CoverProfile() {}

    /** Fraction of {@code part} edge cover of {@code level} hides at full distance. */
    public static float edge(int level, BodyPart part) {
        return part.edgeCover(level);
    }

    /** Fraction of {@code part} the ground of occupant-cover {@code level} hides at full distance. */
    public static float occupant(int level, BodyPart part) {
        return part.occupantCover(level);
    }

    /** Fraction of the rounds headed for {@code part} a prop of {@code level} crossed in the lane catches at full distance. */
    public static float crossed(int level, BodyPart part) {
        return part.crossedCover(level);
    }

    /** Share of all part-bound rounds a prop of {@code level} crossed in the lane would catch. */
    public static float expectedCrossedInterception(int level) {
        return expectedCrossedInterception(BodyPlan.INFANTRY, level);
    }

    /** {@link #expectedCrossedInterception(int)} for the body {@code plan} describes. */
    public static float expectedCrossedInterception(BodyPlan plan, int level) {
        float sum = 0f;
        for (BodyPart part : plan.parts()) sum += part.share * crossed(level, part);
        return sum;
    }

    /** Share of all hits on an uncovered person that edge cover of {@code level} would stop. */
    public static float expectedEdgeInterception(int level) {
        return expectedEdgeInterception(BodyPlan.INFANTRY, level);
    }

    /** {@link #expectedEdgeInterception(int)} for the body {@code plan} describes. */
    public static float expectedEdgeInterception(BodyPlan plan, int level) {
        float sum = 0f;
        for (BodyPart part : plan.parts()) sum += part.share * edge(level, part);
        return sum;
    }

    /** Share of all hits on an uncovered person that occupant cover of {@code level} would stop. */
    public static float expectedOccupantInterception(int level) {
        return expectedOccupantInterception(BodyPlan.INFANTRY, level);
    }

    /** {@link #expectedOccupantInterception(int)} for the body {@code plan} describes. */
    public static float expectedOccupantInterception(BodyPlan plan, int level) {
        float sum = 0f;
        for (BodyPart part : plan.parts()) sum += part.share * occupant(level, part);
        return sum;
    }
}
