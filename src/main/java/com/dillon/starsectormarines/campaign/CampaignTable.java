package com.dillon.starsectormarines.campaign;

/**
 * The persistent SoA tables in {@link CampaignState}. Systems declare which
 * of these they read and which they write so a future scheduler can determine
 * safe parallelism — see <code>roadmap/campaign/architecture.md</code> §3.
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
    PLAYER_REP
}
