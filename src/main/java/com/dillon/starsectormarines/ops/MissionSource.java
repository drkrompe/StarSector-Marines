package com.dillon.starsectormarines.ops;

/**
 * Where a mission came from. Drives the generator's emit path:
 * <ul>
 *   <li>{@link #GENERATED} — produced by {@code MissionGenerator} from planet intel.
 *       Stateless, rerolled per visit.</li>
 *   <li>{@link #STORY} — hand-authored, eligibility-gated, single-completion. Persisted
 *       via {@code MarineRosterScript}'s completed-id set.</li>
 * </ul>
 */
public enum MissionSource {
    GENERATED,
    STORY
}
