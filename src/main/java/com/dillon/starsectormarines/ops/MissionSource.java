package com.dillon.starsectormarines.ops;

/**
 * Where a mission came from. Drives the generator's emit path:
 * <ul>
 *   <li>{@link #GENERATED} — produced by {@code MissionGenerator} from planet intel.
 *       Stateless, rerolled per visit.</li>
 *   <li>{@link #STORY} — hand-authored, eligibility-gated. Completion is
 *       persisted via {@code MarineRosterScript}'s completed-id set, but a def
 *       decides for itself whether completion retires it: one-shot beats
 *       (the veteran's job) and recurring work (militia support) share this
 *       source because both need the authored build path.</li>
 *   <li>{@link #STATIONING} — incident mission fought by a detachment already
 *       committed to a stationing contract.</li>
 *   <li>{@link #CAMPAIGN_EVENT} — non-contract black-swan work carrying its own
 *       stable event lineage.</li>
 *   <li>{@link #DEBUG_CANONICAL_CIVILIAN_RESCUE} — picker-only production-shaped
 *       rescue with no campaign-event lineage or writeback.</li>
 *   <li>{@link #DEBUG_CIVILIAN_RESCUE} — picker-only force-scaled swarm stress
 *       test with no campaign-event lineage or writeback.</li>
 *   <li>{@link #DEBUG} — ordinary picker-only mission with fixture personnel
 *       and no campaign writeback.</li>
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
    DEBUG_CIVILIAN_RESCUE,
    /** Ordinary debug-picker mission; never mutates campaign state. */
    DEBUG,
    /** Production-shaped debug rescue; append-only to preserve prior ordinals. */
    DEBUG_CANONICAL_CIVILIAN_RESCUE;

    public boolean isCivilianRescue() {
        return this == CAMPAIGN_EVENT
                || this == DEBUG_CANONICAL_CIVILIAN_RESCUE
                || this == DEBUG_CIVILIAN_RESCUE;
    }

    public boolean isDebug() {
        return this == DEBUG
                || this == DEBUG_CANONICAL_CIVILIAN_RESCUE
                || this == DEBUG_CIVILIAN_RESCUE;
    }
}
