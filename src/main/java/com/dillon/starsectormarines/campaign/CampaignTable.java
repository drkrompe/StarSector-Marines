package com.dillon.starsectormarines.campaign;

/**
 * The persistent SoA tables in {@link CampaignState}. Systems declare which
 * of these they read and which they write so a scheduler can reason about
 * safe parallelism — see <code>architecture.md</code>.
 *
 * <p>Adding a table here means {@link CampaignState} grows a new set of parallel
 * arrays, and every {@link CampaignSystem} should reconsider whether it touches
 * the new table.
 */
public enum CampaignTable {
    HOUSES,
    STAKES,
    RELATIONSHIPS,
    CHAINS,
    CHRONICLE,
    THRONE_CLAIMS,
    KINGMAKER_TESTAMENTS,
    PLAYER_REP,
    MORAL_COMPASS,
    EVENTS,
    CONTRACTS,
    PATRON_MEMORY,
    /** Equipment templates the company has released to its own polity, and the doctrine they are shaped by. */
    RELEASED_KIT
}
