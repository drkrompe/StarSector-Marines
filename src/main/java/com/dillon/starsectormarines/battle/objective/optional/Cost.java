package com.dillon.starsectormarines.battle.objective.optional;

/**
 * A named difficulty modifier a failed optional objective hands downstream: the
 * campaign carries it and the arc decides what {@code key} at {@code size} means.
 */
public record Cost(String key, int size) {

    public static final Cost NONE = new Cost("", 0);

    public Cost {
        key = key == null ? "" : key;
        size = Math.max(0, size);
    }

    public boolean isEmpty() { return key.isEmpty() || size == 0; }
}
