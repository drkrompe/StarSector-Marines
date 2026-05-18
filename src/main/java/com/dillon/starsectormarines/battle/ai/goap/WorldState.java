package com.dillon.starsectormarines.battle.ai.goap;

/**
 * Bag of {@link Predicate} facts the planner reasons over. Boolean-valued
 * with an explicit "specified" bit so we can distinguish "predicate is false"
 * from "predicate is not constrained" — required because action
 * preconditions/effects only mention the predicates that action cares about.
 *
 * <p><b>Data-oriented layout.</b> Backed by two primitive {@code long}s:
 * {@link #presentMask} marks which predicates are specified, {@link #truthMask}
 * holds their values. This makes {@code WorldState}:
 * <ul>
 *   <li><b>Cheap to copy</b> — two longs, no allocations on read paths. The
 *       planner expands many search nodes per replan; each expansion creates
 *       a derived state via {@link #apply(WorldState)} or {@link #with(Predicate, boolean)}
 *       — those allocate one tiny object instead of an {@link java.util.EnumMap}.</li>
 *   <li><b>Thread-safe by value</b> — fields are {@code final}; instances
 *       can be shared across the parallel replan pass without locking.</li>
 *   <li><b>Branchless {@link #satisfies(WorldState)}</b> — one XOR + one AND +
 *       one zero-compare across all predicates simultaneously.</li>
 * </ul>
 *
 * <p>The 64-predicate cap (one bit per {@link Predicate} enum entry) is
 * enforced at class-load via a static check. Stage 1 needs 5; projected
 * Stage 2/3 surfaces stay well below 64. If we ever push past, swap to
 * a {@code long[]} pair without changing the surface API.
 *
 * <p>Three flavors of state in play:
 * <ul>
 *   <li><b>Current</b> — what the sim looks like right now, produced by
 *       {@code WorldStateBuilder.build}.</li>
 *   <li><b>Desired</b> — what a {@link Goal} wants to be true. Used as the
 *       planner's search target.</li>
 *   <li><b>Effects / preconditions</b> — what an {@link Action} requires /
 *       produces. The same class fits all three roles; the convention is that
 *       effects/preconditions only set the predicates the action cares about.</li>
 * </ul>
 */
public final class WorldState {

    static {
        if (Predicate.values().length > 64) {
            throw new ExceptionInInitializerError(
                "WorldState bitmask supports at most 64 predicates; found "
                    + Predicate.values().length + ". Bump to long[] pair.");
        }
    }

    /** Singleton empty state. Shared across all callers — never mutated. */
    public static final WorldState EMPTY = new WorldState(0L, 0L);

    /** Bit i set ⇔ {@code Predicate.values()[i]} is specified in this state. Predicates outside this mask are "don't care". */
    private final long presentMask;
    /** Bit i set ⇔ {@code Predicate.values()[i]} is specified AND true. Bits outside {@link #presentMask} are zero by convention. */
    private final long truthMask;

    private WorldState(long presentMask, long truthMask) {
        this.presentMask = presentMask;
        this.truthMask = truthMask;
    }

    private static long bit(Predicate p) {
        return 1L << p.ordinal();
    }

    /** Reads a predicate. Unset predicates return {@code false}. */
    public boolean get(Predicate p) {
        return (truthMask & bit(p)) != 0L;
    }

    /** True iff this state has specified {@code p} (either true or false). Distinguishes "false" from "unconstrained" — used by the planner for regression. */
    public boolean isSpecified(Predicate p) {
        return (presentMask & bit(p)) != 0L;
    }

    /** Returns a new state with {@code p} set to {@code v}. Does not mutate this instance. */
    public WorldState with(Predicate p, boolean v) {
        long bit = bit(p);
        long newPresent = presentMask | bit;
        long newTruth = v ? (truthMask | bit) : (truthMask & ~bit);
        return new WorldState(newPresent, newTruth);
    }

    /**
     * True iff every predicate specified in {@code desired} has the same
     * truth value in this state. Predicates absent from {@code desired} are
     * unconstrained — they can be anything here. Branchless across all
     * predicates: one XOR (where do truth values differ) gated by
     * {@code desired.presentMask} (only the ones desired cares about).
     */
    public boolean satisfies(WorldState desired) {
        return ((this.truthMask ^ desired.truthMask) & desired.presentMask) == 0L;
    }

    /**
     * Returns a new state with {@code effects}' specified predicates merged
     * on top of this state's. Predicates not specified in {@code effects}
     * are preserved unchanged. Does not mutate either input.
     */
    public WorldState apply(WorldState effects) {
        long newPresent = this.presentMask | effects.presentMask;
        // Clear bits effects specifies, then OR in effects' values (already masked).
        long newTruth = (this.truthMask & ~effects.presentMask) | (effects.truthMask & effects.presentMask);
        return new WorldState(newPresent, newTruth);
    }

    /**
     * Backward regression for the planner: given an action whose
     * {@code effects} (partially) satisfies this state, returns the state
     * that must hold <i>before</i> that action runs. Drops the predicates
     * the action's effects already produce, then layers the action's
     * preconditions on top.
     *
     * <p>Pure bitmask math; one allocation. Callers must verify
     * {@link #helpsSatisfy(WorldState)} first — applying a non-helpful
     * action's regression produces a junk state.
     */
    public WorldState regress(WorldState effects, WorldState preconditions) {
        long clearedPresent = this.presentMask & ~effects.presentMask;
        long clearedTruth   = this.truthMask   & ~effects.presentMask;
        long newPresent = clearedPresent | preconditions.presentMask;
        long newTruth   = clearedTruth   | (preconditions.truthMask & preconditions.presentMask);
        return new WorldState(newPresent, newTruth);
    }

    /**
     * Applicability test for backward search: does {@code effects} produce
     * at least one predicate this state cares about, without conflicting on
     * any other shared predicate? An action with a conflicting effect (sets
     * {@code ENEMY_DAMAGED=false} when this wants {@code ENEMY_DAMAGED=true})
     * is rejected even if it also produces something useful.
     */
    public boolean helpsSatisfy(WorldState effects) {
        long overlap = this.presentMask & effects.presentMask;
        if (overlap == 0L) return false;
        return ((this.truthMask ^ effects.truthMask) & overlap) == 0L;
    }

    /**
     * Count of predicates this state specifies whose value differs from
     * {@code current}. Drives the planner's heuristic. Branchless across
     * all predicates: one XOR + one AND + one {@code Long.bitCount}.
     */
    public int countUnsatisfiedAgainst(WorldState current) {
        return Long.bitCount((this.truthMask ^ current.truthMask) & this.presentMask);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof WorldState other)) return false;
        return this.presentMask == other.presentMask && this.truthMask == other.truthMask;
    }

    @Override
    public int hashCode() {
        // Splittable: distinct (present, truth) pairs hash distinctly without collisions for the typical mix.
        return Long.hashCode(presentMask) * 31 + Long.hashCode(truthMask);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Predicate p : Predicate.values()) {
            if (!isSpecified(p)) continue;
            if (!first) sb.append(", ");
            sb.append(p.name()).append('=').append(get(p));
            first = false;
        }
        return sb.append('}').toString();
    }
}
