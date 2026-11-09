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
 *   <li>{@link #NEX_INVASION} — the player's own Nexerelin invasion, fought as a
 *       Conquest and settled through Nexerelin rather than a contract.</li>
 *   <li>{@link #REPLAY} — a won campaign chapter flown again for credits only.</li>
 *   <li>{@link #MARKET_RAID} — the player's own raid on a market, fought as a Raid and
 *       committed through the game's own raid command.</li>
 *   <li>{@link #RUINS_EXPEDITION} — the company sent into a world's ruins, committed
 *       through the game's own ruins salvage.</li>
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
    RECURRING,
    /**
     * The player's own invasion of a market under Nexerelin, fought as a Conquest
     * against the market's whole garrison and settled by handing the result to
     * Nexerelin ({@code nexerelin-nouns.md}). No contract, no MRB credibility.
     * Append-only: ordinals are persisted.
     */
    NEX_INVASION,
    /**
     * A campaign chapter the company already won, flown again for its credits from the
     * board's replay contact ({@code CompletedReplays}). Never settles the arc that owns
     * the chapter. Append-only: ordinals are persisted.
     */
    REPLAY,
    /**
     * The player's own raid on a market, fought as a Raid and committed through the
     * game's own raid command (vanilla's, or Nexerelin's) once the battle ends
     * ({@code raid-resolution.md}). No contract, no MRB credibility. Append-only:
     * ordinals are persisted.
     */
    MARKET_RAID,
    /**
     * The company sent into an uncolonized world's ruins in place of vanilla's salvage team;
     * a won expedition hands the ruins to vanilla's own salvage once the battle ends
     * ({@code ruins-expedition.md}). No contract, no MRB credibility. Append-only: ordinals
     * are persisted.
     */
    RUINS_EXPEDITION;

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
