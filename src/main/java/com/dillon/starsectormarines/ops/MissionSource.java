package com.dillon.starsectormarines.ops;

/**
 * Where a mission came from. Drives the generator's emit path:
 * <ul>
 *   <li>{@link #GENERATED} — produced by {@code MissionGenerator} from planet intel.
 *       Stateless, rerolled per visit.</li>
 *   <li>{@link #STORY} — hand-authored, eligibility-gated, single-completion. Persisted
 *       via {@code MarineRosterScript}'s completed-id set.</li>
 *   <li>{@link #STATIONING} — event mission fought by a detachment already
 *       committed to a stationing contract.</li>
 * </ul>
 */
public enum MissionSource {
    GENERATED,
    STORY,
    /** Battle fought by personnel already committed to a stationing assignment. */
    STATIONING
}
