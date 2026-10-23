package com.dillon.starsectormarines.battle.evacuation.flier;

import java.util.List;

/**
 * What a Harrier pack dives on: the things the ground line is there to protect, over the line itself.
 *
 * <p>A candidate is scored by what it is ({@link Prey}), less its distance from the pack and less the air guns
 * that cover it. So a gun with nothing overhead is the first choice, a medic or an ammunition truck the next, a
 * marine alone the one after, and a rifleman in a squad last; a target under an anti-air umbrella is worth less
 * by every air gun that reaches it, which is how anti-air protects a line without shooting anything down.
 * Ties go to the lower id, so the choice is the same every replay.
 */
public final class HarrierTargeting {

    /** What a candidate is, to a flier. */
    public enum Prey {
        /** An emplacement or crew-served gun: what the line is built round. */
        GUN(5f),
        /** An unarmed hull: an ammunition or supply truck. */
        LOGISTICS(4f),
        /** A squad's medic. */
        MEDIC(3f),
        /** A body on foot with no friend near it. */
        ISOLATED(2f),
        /** A body on foot among its squad. */
        INFANTRY(1f),
        /** A mech or an armed hull: its bite only scratches it. */
        ARMOUR(0.25f);

        public final float value;

        Prey(float value) {
            this.value = value;
        }
    }

    /** Points a candidate's kind is worth, against a cell of distance. */
    public static final float VALUE_WEIGHT = 10f;
    public static final float DISTANCE_WEIGHT = 0.25f;
    /** Points lost per air gun whose reach covers the candidate. */
    public static final float COVER_WEIGHT = 6f;
    /** A loitering pack keeps its prey unless another beats it by this much. */
    public static final float HYSTERESIS = 3f;

    /** One body a pack has sensed. {@code airCover} is how many air guns reach it. */
    public record Candidate(long id, float x, float y, Prey prey, int airCover) {}

    private HarrierTargeting() {}

    public static float score(Candidate c, float packX, float packY) {
        float d = (float) Math.hypot(c.x() - packX, c.y() - packY);
        return c.prey().value * VALUE_WEIGHT - d * DISTANCE_WEIGHT - c.airCover() * COVER_WEIGHT;
    }

    /**
     * The best candidate within {@link HarrierRules#SENSE_RANGE} of the pack, or {@code 0} if none: the current
     * prey is kept unless another beats it by {@link #HYSTERESIS}.
     */
    public static long pick(List<Candidate> candidates, float packX, float packY, long current) {
        long best = 0L;
        float bestScore = Float.NEGATIVE_INFINITY;
        float currentScore = Float.NEGATIVE_INFINITY;
        float range2 = HarrierRules.SENSE_RANGE * HarrierRules.SENSE_RANGE;
        for (int i = 0, n = candidates.size(); i < n; i++) {
            Candidate c = candidates.get(i);
            float dx = c.x() - packX, dy = c.y() - packY;
            if (dx * dx + dy * dy > range2) continue;
            float s = score(c, packX, packY);
            if (c.id() == current) currentScore = s;
            if (s > bestScore || s == bestScore && c.id() < best) {
                bestScore = s;
                best = c.id();
            }
        }
        if (current != 0L && currentScore > Float.NEGATIVE_INFINITY && bestScore - currentScore < HYSTERESIS) {
            return current;
        }
        return best;
    }
}
