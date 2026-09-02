package com.dillon.starsectormarines.campaign.systems;

/**
 * Sends a vanilla raid home after the company wins the ground defence it armed.
 *
 * <p>Only vanilla's own end paths are used, per law 11 of {@code contracts-nouns.md}: the
 * ending never touches market ownership, stability, or industries directly.
 */
public interface RaidEnder {

    /** A no-op ender, for hosts with no live sector. */
    RaidEnder NONE = (marketId, factionId) -> 0;

    /**
     * Ends every live raid by {@code factionId} that has {@code marketId} among its
     * targets.
     *
     * @return how many raids were ended; 0 when nothing matched or nothing was live.
     */
    int endRaidsTargeting(String marketId, String factionId);
}
