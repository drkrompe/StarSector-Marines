package com.dillon.starsectormarines.campaign;

/**
 * Derives the comms officer's current {@link OfficerMood} from the
 * company's state — cash trend, captain count, fleet size, MRB rep.
 * Lets the briefing surface react to "how the company is actually
 * doing" without authoring per-state content.
 *
 * <p><b>Currently stubbed</b>: always returns {@link OfficerMood#STEADY}.
 * The wiring is in place at the composer + mission-generator layer so
 * the rest of the pipeline can be exercised end-to-end; populating the
 * real bucket math from {@link CampaignState} plus vanilla
 * {@code Global.getSector().getPlayerFleet().getCargo().getCredits()} /
 * captain count is the next planned step. Keeping this stubbed avoids
 * pulling Sector-state queries into a content pipeline before the
 * bucket boundaries have been tuned against playtest.
 *
 * <p>Bucket plan (for reference when wiring the real derivation):
 * <ul>
 *   <li>{@link OfficerMood#DESPERATE} — credits below 30-day burn rate
 *       OR three consecutive months of negative net</li>
 *   <li>{@link OfficerMood#GREEN} — fewer than ~3 captains or fewer
 *       than ~2 ships <em>and</em> no SEASONED triggers</li>
 *   <li>{@link OfficerMood#SEASONED} — 6+ captains AND positive
 *       monthly net AND non-negative MRB rep</li>
 *   <li>{@link OfficerMood#STEADY} — default fallthrough</li>
 * </ul>
 */
public final class OfficerMoodReader {

    private OfficerMoodReader() {}

    /**
     * Returns the comms officer's mood for the current player state.
     * Stubbed to {@link OfficerMood#STEADY} until the derivation is
     * wired — see class javadoc.
     */
    public static OfficerMood currentMood() {
        return OfficerMood.STEADY;
    }
}
