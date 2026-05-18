package com.dillon.starsectormarines.battle.ai.goap.scoring;

import java.util.List;
import java.util.Objects;

/**
 * Composition helpers for {@link Scorer}. All returned scorers are pure
 * functions over their inputs; safe to share across threads.
 */
public final class Scorers {

    private Scorers() {}

    /**
     * Linear combination of weighted sub-scorers. Weights are applied as-is —
     * caller chooses whether to pre-normalize sub-scorers (typical) or to use
     * raw magnitudes (when the absolute ranges already line up).
     */
    public static <T> Scorer<T> weightedSum(List<WeightedScorer<T>> scorers) {
        Objects.requireNonNull(scorers, "scorers");
        // Defensive copy: caller mutating the list after construction would
        // break the stateless-pure-function contract the planner relies on.
        List<WeightedScorer<T>> snapshot = List.copyOf(scorers);
        return candidate -> {
            float sum = 0f;
            for (WeightedScorer<T> ws : snapshot) {
                sum += ws.weight() * ws.scorer().score(candidate);
            }
            return sum;
        };
    }

    /**
     * Rescales {@code s}'s output from {@code [min, max]} to {@code [0, 1]},
     * clamping outside the range. {@code min == max} collapses to a constant
     * {@code 0} to avoid divide-by-zero (a degenerate scorer carries no signal).
     */
    public static <T> Scorer<T> normalize(Scorer<T> s, float min, float max) {
        Objects.requireNonNull(s, "scorer");
        if (min == max) {
            return candidate -> 0f;
        }
        float range = max - min;
        return candidate -> {
            float raw = s.score(candidate);
            float n = (raw - min) / range;
            if (n < 0f) return 0f;
            if (n > 1f) return 1f;
            return n;
        };
    }

    /** Inverts the sign so "lower is better" scorers (distance, cost) compose cleanly into the higher-is-better convention. */
    public static <T> Scorer<T> negate(Scorer<T> s) {
        Objects.requireNonNull(s, "scorer");
        return candidate -> -s.score(candidate);
    }

    public record WeightedScorer<T>(float weight, Scorer<T> scorer) {
        public WeightedScorer {
            Objects.requireNonNull(scorer, "scorer");
        }
    }
}
