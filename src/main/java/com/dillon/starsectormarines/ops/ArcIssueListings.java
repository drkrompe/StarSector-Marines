package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.campaign.CampaignClock;
import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.CampaignStateScript;
import com.dillon.starsectormarines.marine.LoadoutCard;
import com.dillon.starsectormarines.marine.MarketListings;
import com.dillon.starsectormarines.marine.SquadEquipmentDoctrines;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import org.apache.log4j.Logger;

/**
 * The arc issue cards: each early arc beat that teaches a way of fighting stocks the weapon
 * card that names it, at the market where it happened ({@code progression-nouns.md}, Kit and
 * armory; {@code campaign-kit-arc.md}).
 *
 * <p>Each settlement calls {@link #afterLive} once its own receipt has closed. Listing is
 * idempotent per card and scope, so a settlement that reports the same earned beat again
 * (a recurring job after the story, a replayed result) creates nothing.
 */
public final class ArcIssueListings {

    private static final Logger LOG = Global.getLogger(ArcIssueListings.class);

    /** One arc beat and the card it stocks. */
    public enum Beat {
        /** Wayline opening: Relief settled. Bought beside A Proper Issue's free choice, never instead of it. */
        RELIEF(SquadEquipmentDoctrines.CHECKPOINT_WEAPONS, MarketListings.Scope.MARKET, "arcIssueListedRelief"),
        /** Broken Supply Route: Thunder Run settled and the road opened. */
        THUNDER_RUN(SquadEquipmentDoctrines.CONVOY_ESCORT_WEAPONS, MarketListings.Scope.MARKET,
                "arcIssueListedThunderRun"),
        /** Corvus Account: A Cold Account settled beside Kord. A nod, not a licence. */
        COLD_ACCOUNT(SquadEquipmentDoctrines.COUNTER_BATTERY_WEAPONS, MarketListings.Scope.FACTION,
                "arcIssueListedColdAccount"),
        /** Shepherd recovery: Working Passage settled and the Morrow flew. */
        WORKING_PASSAGE(SquadEquipmentDoctrines.BUILDERS_GUARD_WEAPONS, MarketListings.Scope.MARKET,
                "arcIssueListedWorkingPassage");

        public final String doctrineId;
        public final MarketListings.Scope scope;
        public final String reasonKey;

        Beat(String doctrineId, MarketListings.Scope scope, String reasonKey) {
            this.doctrineId = doctrineId;
            this.scope = scope;
            this.reasonKey = reasonKey;
        }

        public String cardId() {
            return new LoadoutCard(LoadoutCard.Kind.WEAPON, doctrineId).id();
        }
    }

    /** Counter-Battery Issue is Hegemony issue: it stands at every Hegemony open market. */
    static final String COLD_ACCOUNT_FACTION = "hegemony";

    private ArcIssueListings() {}

    /**
     * Lists {@code beat}'s card when {@code earned}.
     *
     * @param scopeId the market id for a market-scoped beat; ignored for a faction-scoped one
     * @return true when this call created the listing
     */
    public static boolean after(CampaignState state, Beat beat, boolean earned, String scopeId, int day) {
        if (!earned || state == null || beat == null) return false;
        String scope = beat.scope == MarketListings.Scope.FACTION ? COLD_ACCOUNT_FACTION : scopeId;
        if (scope == null || scope.isBlank()) return false;
        return MarketListings.list(state, beat.cardId(), beat.scope, scope, beat.reasonKey, day);
    }

    /**
     * The live seam: lists {@code beat}'s card at {@code site}'s market in the running campaign.
     * A listing that cannot be placed is logged and skipped; it never unwinds a settlement
     * that has already paid.
     */
    static void afterLive(Beat beat, boolean earned, PlanetAPI site) {
        if (!earned) return;
        CampaignStateScript script = CampaignStateScript.getInstance();
        if (script == null) return;
        MarketAPI market = site == null ? null : site.getMarket();
        String marketId = market == null ? null : market.getId();
        try {
            int day = Global.getSector() != null ? CampaignClock.day() : 0;
            if (after(script.state(), beat, true, marketId, day)) {
                LOG.info("MarineOps: " + beat + " listed " + beat.cardId() + " at " + marketId);
            }
        } catch (RuntimeException failure) {
            LOG.warn("MarineOps: could not list " + beat.cardId() + " after " + beat, failure);
        }
    }
}
