package com.dillon.starsectormarines.battle.world.gen;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Generic weighted-random table. Built up via {@link #builder()} entries and
 * sampled deterministically from a supplied {@link Random}. Used by
 * {@link MapDistrictTheme} to roll {@link BlockKind}s under per-theme
 * distributions; other callers welcome.
 *
 * <p>Performance: pick is O(n) over the entry count with a linear scan over
 * pre-computed cumulative weights. Acceptable since maps roll ~30 picks per
 * generation; if usage grows hot we can switch to alias-method sampling.
 */
public final class WeightedTable<T> {

    private final Object[] values;
    private final int[] cumulative;
    private final int total;

    private WeightedTable(List<T> values, List<Integer> weights) {
        if (values.isEmpty()) throw new IllegalArgumentException("empty weighted table");
        this.values = values.toArray();
        this.cumulative = new int[weights.size()];
        int acc = 0;
        for (int i = 0; i < weights.size(); i++) {
            int w = weights.get(i);
            if (w <= 0) throw new IllegalArgumentException("non-positive weight: " + w);
            acc += w;
            cumulative[i] = acc;
        }
        this.total = acc;
    }

    @SuppressWarnings("unchecked")
    public T pick(Random rng) {
        int r = rng.nextInt(total);
        for (int i = 0; i < cumulative.length; i++) {
            if (r < cumulative[i]) return (T) values[i];
        }
        return (T) values[values.length - 1];
    }

    public static <T> Builder<T> builder() {
        return new Builder<>();
    }

    public static final class Builder<T> {
        private final List<T> values  = new ArrayList<>();
        private final List<Integer> weights = new ArrayList<>();

        public Builder<T> add(T value, int weight) {
            values.add(value);
            weights.add(weight);
            return this;
        }

        public WeightedTable<T> build() {
            return new WeightedTable<>(values, weights);
        }
    }
}
