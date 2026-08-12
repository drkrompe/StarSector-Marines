package com.dillon.starsectormarines.ops;

/**
 * Where a mission came from. Drives the generator's emit path:
 * <ul>
 *   <li>{@link #GENERATED} — produced by {@code MissionGenerator} from planet intel.
 *       Stateless, rerolled per visit.</li>
 *   <li>{@link #STORY} — hand-authored, eligibility-gated, single-completion. Persisted
 *       via {@code MarineRosterScript}'s completed-id set.</li>
 *   <li>{@link #STATIONING} — incident mission fought by a detachment already
 *       committed to a stationing contract.</li>
 *   <li>{@link #CAMPAIGN_EVENT} — non-contract black-swan work carrying its own
 *       stable event lineage.</li>
 *   <li>{@link #DEBUG_CIVILIAN_RESCUE} — picker-only swarm rescue with no
 *       campaign-event lineage or writeback.</li>
 * </ul>
 */
public enum MissionSource {
    GENERATED,
    STORY,
    /** Battle fought by personnel already committed to a stationing assignment. */
    STATIONING,
    /** Cost-shaped black-swan mission; never inherits contract economics. */
    CAMPAIGN_EVENT,
    /** Direct debug-picker swarm rescue; never resolves a campaign event. */
    DEBUG_CIVILIAN_RESCUE
}
