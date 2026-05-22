package com.dillon.starsectormarines.campaign;

import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.MarineRosterScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.econ.MonthlyReport;
import com.fs.starfarer.api.impl.campaign.shared.SharedData;

/**
 * Derives the comms officer's current {@link OfficerMood} from the
 * company's state — cash trend, captain count, fleet size, MRB rep.
 * Lets the briefing surface react to "how the company is actually
 * doing" without authoring per-state content.
 *
 * <p>The bucket math lives in {@link #bucket} as a pure function on
 * primitive inputs; {@link #currentMood} is the world-facing entry
 * point that gathers from vanilla + mod state and delegates. The split
 * keeps the bucket boundaries unit-testable without mocking the Sector.
 *
 * <p>Vanilla cashflow comes from
 * {@code SharedData.getData().getPreviousReport()} — populated each
 * month at rollover with {@link MonthlyReport#getRoot}'s aggregated
 * {@code totalIncome / totalUpkeep} plus {@link MonthlyReport#getDebt}
 * / {@link MonthlyReport#getPreviousDebt} for trend. First-month saves
 * read as all-zero (no DESPERATE trigger), which falls naturally into
 * GREEN via the small-captain / small-fleet check.
 */
public final class OfficerMoodReader {

    /** Captains required to qualify for SEASONED. */
    static final int SEASONED_CAPTAIN_FLOOR = 6;
    /** Captains below which GREEN triggers (absent SEASONED). */
    static final int GREEN_CAPTAIN_CEILING = 3;
    /** Fleet ships below which GREEN triggers (absent SEASONED). */
    static final int GREEN_SHIP_CEILING = 2;

    private OfficerMoodReader() {}

    /**
     * Returns the comms officer's mood for the current player state.
     * Guards every external lookup defensively — same pattern as
     * {@code OfficerHeaderWidget.currentSectorDay} — so an early-load
     * frame (no sector, no campaign script) returns STEADY rather than
     * throwing.
     */
    public static OfficerMood currentMood() {
        SectorAPI sector = Global.getSector();
        if (sector == null) return OfficerMood.STEADY;

        float credits = 0f;
        int ships = 0;
        CampaignFleetAPI fleet = sector.getPlayerFleet();
        if (fleet != null) {
            if (fleet.getCargo() != null && fleet.getCargo().getCredits() != null) {
                credits = fleet.getCargo().getCredits().get();
            }
            if (fleet.getFleetData() != null) {
                ships = fleet.getFleetData().getMembersListCopy().size();
            }
        }

        float netLastMonth = 0f;
        float upkeepLastMonth = 0f;
        int debt = 0;
        int previousDebt = 0;
        try {
            MonthlyReport prev = SharedData.getData().getPreviousReport();
            if (prev != null && prev.getRoot() != null) {
                netLastMonth = prev.getRoot().totalIncome - prev.getRoot().totalUpkeep;
                upkeepLastMonth = prev.getRoot().totalUpkeep;
                debt = prev.getDebt();
                previousDebt = prev.getPreviousDebt();
            }
        } catch (Exception e) {
            // SharedData allocation reaches into Global.getSector().getPersistentData() —
            // on the very first frame after a fresh new-game that map may not be wired up
            // yet. Fall through with zeros; GREEN will dominate via captain/ship floors.
        }

        int activeCaptains = 0;
        MarineRosterScript rosterScript = MarineRosterScript.getInstance();
        if (rosterScript != null) {
            MarineRoster roster = rosterScript.roster();
            if (roster != null) activeCaptains = roster.active().size();
        }

        int mrbRep = 0;
        CampaignStateScript campaignScript = CampaignStateScript.getInstance();
        if (campaignScript != null && campaignScript.state() != null) {
            mrbRep = campaignScript.state().playerMrbRep;
        }

        return bucket(credits, netLastMonth, upkeepLastMonth, debt, previousDebt,
                activeCaptains, ships, mrbRep);
    }

    /**
     * Pure bucket math. Precedence: DESPERATE → SEASONED → GREEN → STEADY.
     * Public so tests can pin each boundary without spinning up a Sector.
     */
    public static OfficerMood bucket(float credits, float netLastMonth,
                                     float upkeepLastMonth, int debt, int previousDebt,
                                     int activeCaptains, int ships, int mrbRep) {
        // DESPERATE wins first — bleeding cash trumps every other read.
        // Two independent triggers: credits-on-hand below one month of
        // upkeep (no runway), OR two consecutive months of debt (the
        // trend has bitten). Upkeep > 0 guard prevents the first-month
        // empty-report case from flagging DESPERATE on a flush wallet.
        boolean noRunway = upkeepLastMonth > 0f && credits < upkeepLastMonth;
        boolean cashTrendBleeding = debt > 0 && previousDebt > 0;
        if (noRunway || cashTrendBleeding) return OfficerMood.DESPERATE;

        // SEASONED next — established company AND in the black AND MRB-credible.
        // Beats GREEN if both could apply (won't, given the captain floors,
        // but checked explicitly so the precedence is readable).
        if (activeCaptains >= SEASONED_CAPTAIN_FLOOR && netLastMonth > 0f && mrbRep >= 0) {
            return OfficerMood.SEASONED;
        }

        // GREEN — small operation, officer (and player) still learning the ropes.
        if (activeCaptains < GREEN_CAPTAIN_CEILING || ships < GREEN_SHIP_CEILING) {
            return OfficerMood.GREEN;
        }

        return OfficerMood.STEADY;
    }
}
