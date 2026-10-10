package com.dillon.starsectormarines.ops;

/**
 * Where a mission came from. Drives the generator's emit path:
 * <ul>
 *   <li>{@link #GENERATED} — projected from a persisted campaign contract.
 *       The contract row owns availability and settlement; this value names the
 *       ordinary battle-construction path.</li>
 *   <li>{@link #STORY} — authored chapters whose victories are persisted in the
 *       roster's completed-id set.</li>
 *   <li>{@link #RECURRING} — authored repeatable work with separate identities;
 *       never records a story victory.</li>
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
 *   <li>{@link #POLITY_DEFENCE} — a vanilla raid on the player's own colony,
 *       fought by a briefing-time selection rather than a contract.</li>
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
    DEBUG_CANONICAL_CIVILIAN_RESCUE,
    /**
     * A vanilla raid met on the ground on the player's own colony. Offered by a
     * briefing-time selection rather than by a contract row, resolved by its own key,
     * and never inherits contract economics. Append-only: ordinals are persisted.
     */
    POLITY_DEFENCE,
    /** Repeatable authored jobs. Append-only: ordinals are persisted. */
    RECURRING;

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
