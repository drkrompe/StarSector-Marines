package com.dillon.starsectormarines.campaign;

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
 * / {@link MonthlyReport#getPreviousDebt} for trend.
 *
 * <h2>Runway bands</h2>
 * Per {@code roadmap/campaign/economy.md}'s "hard-fail early, steady-grind
 * mid, intentional-investment late" arc, DESPERATE is the long default —
 * a player needs ~{@value #DESPERATE_RUNWAY_MONTHS} months of upkeep on
 * hand before they leave it, and ~{@value #SEASONED_RUNWAY_MONTHS} before
 * a 6+ captain operation reads as truly comfortable. Tighter bands here
 * would let the player escape paycheck-to-paycheck too quickly.
 *
 * <p>Future inputs (not yet wired): licensing tier (Unregistered should
 * lean DESPERATE regardless of cash); per-faction reputation scarcity;
 * captain loyalty drift. Each will tighten the SEASONED gate further.
 */
public final class OfficerMoodReader {

    /** Captains required to qualify for SEASONED. */
    static final int SEASONED_CAPTAIN_FLOOR = 6;
    /** Captains below which GREEN triggers (absent SEASONED). */
    static final int GREEN_CAPTAIN_CEILING = 3;
    /** Fleet ships below which GREEN triggers (absent SEASONED). */
    static final int GREEN_SHIP_CEILING = 2;
    /** Runway in months of upkeep — below this, DESPERATE fires. */
    static final int DESPERATE_RUNWAY_MONTHS = 6;
    /** Runway in months of upkeep — SEASONED requires at least this much cushion. */
    static final int SEASONED_RUNWAY_MONTHS = 12;

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
        MonthlyReport prev = SharedData.getData().getPreviousReport();
        if (prev != null && prev.getRoot() != null) {
            netLastMonth = prev.getRoot().totalIncome - prev.getRoot().totalUpkeep;
            upkeepLastMonth = prev.getRoot().totalUpkeep;
            debt = prev.getDebt();
            previousDebt = prev.getPreviousDebt();
        }

        int activeCaptains = 0;
        MarineRosterScript rosterScript = MarineRosterScript.getInstance();
        if (rosterScript != null && rosterScript.roster() != null) {
            activeCaptains = rosterScript.roster().activeCount();
        }

        // TODO: playerMrbRep is currently a fixed 0 until contract-completion
        // code starts writing to it; the mrbRep >= 0 SEASONED gate is therefore
        // a no-op for now. Kept here so it activates the moment the MRB track
        // is wired without touching this file.
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
        // Two independent triggers: credits-on-hand below the runway floor
        // (6 months of upkeep — see class javadoc for why), OR two
        // consecutive months of debt. Upkeep > 0 guard prevents the
        // first-month empty-report case from flagging DESPERATE on a
        // flush wallet.
        boolean noRunway = upkeepLastMonth > 0f
                && credits < DESPERATE_RUNWAY_MONTHS * upkeepLastMonth;
        boolean cashTrendBleeding = debt > 0 && previousDebt > 0;
        if (noRunway || cashTrendBleeding) return OfficerMood.DESPERATE;

        // SEASONED next — established company AND in the black AND
        // MRB-credible AND meaningful runway. The runway gate keeps a
        // 6-captain operation living paycheck-to-paycheck out of
        // SEASONED; it should read as STEADY instead. The upkeep == 0
        // escape lets the (unlikely) "income with no upkeep" case still
        // qualify without a denominator.
        boolean comfortableRunway = upkeepLastMonth == 0f
                || credits >= SEASONED_RUNWAY_MONTHS * upkeepLastMonth;
        if (activeCaptains >= SEASONED_CAPTAIN_FLOOR
                && netLastMonth > 0f
                && mrbRep >= 0
                && comfortableRunway) {
            return OfficerMood.SEASONED;
        }

        // GREEN — small operation, officer (and player) still learning the ropes.
        if (activeCaptains < GREEN_CAPTAIN_CEILING || ships < GREEN_SHIP_CEILING) {
            return OfficerMood.GREEN;
        }

        return OfficerMood.STEADY;
    }
}
