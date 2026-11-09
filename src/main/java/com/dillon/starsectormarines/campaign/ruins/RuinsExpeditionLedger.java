package com.dillon.starsectormarines.campaign.ruins;

import com.dillon.starsectormarines.campaign.CampaignState;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Makes a ruins expedition commit exactly once, in two steps ({@code ruins-expedition.md}, law 8).
 *
 * <p>Every briefing opened draws a new serial into its mission key. Results {@link #record records} the battle's
 * outcome against that serial; the planet dialog, once the Marine Operations panel closes, {@link #take takes} it: a
 * won expedition hands the ruins to vanilla's own salvage, a pulled-out or lost one leaves them unexplored. A briefing backed out of
 * leaves its serial unrecorded, so nothing is taken. Replaying Results, or an older briefing's result, records
 * nothing, and a recorded outcome is taken once.
 */
public final class RuinsExpeditionLedger {

    /** What the planet dialog finds when the panel closes. */
    public enum Pending {
        /** No battle was fought for this briefing: back to the choice. */
        NONE,
        /** The company won: vanilla salvages the ruins. */
        WON,
        /** The company pulled out: its banked finds came home, the ruins stay unexplored. */
        PULLED_OUT,
        /** The company lost: the ruins stay unexplored. */
        LOST
    }

    private RuinsExpeditionLedger() {}

    /** The serial for a newly opened expedition briefing. */
    public static int issue(CampaignState state) {
        if (state == null) return 0;
        return ++state.ruinsExpeditionIssued;
    }

    /**
     * Records the battle fought for {@code serial}; false when it, or a later expedition, is already recorded.
     *
     * @param outcome {@link Pending#WON}, {@link Pending#PULLED_OUT} or {@link Pending#LOST}
     */
    public static boolean record(CampaignState state, int serial, Pending outcome) {
        return record(state, serial, outcome, List.of());
    }

    /**
     * Records the battle fought for {@code serial} with the ids of the finds it brought home, which the commit writes
     * into the site's set ({@link RuinsSiteFinds}).
     */
    public static boolean record(CampaignState state, int serial, Pending outcome, Collection<String> broughtHome) {
        if (outcome == null || outcome == Pending.NONE) throw new IllegalArgumentException("a fought expedition has an outcome");
        if (state == null || serial <= 0 || serial > state.ruinsExpeditionIssued) return false;
        if (serial <= state.ruinsExpeditionRecorded) return false;
        state.ruinsExpeditionRecorded = serial;
        state.ruinsExpeditionRecordedVictory = outcome == Pending.WON;
        state.ruinsExpeditionRecordedPulledOut = outcome == Pending.PULLED_OUT;
        state.ruinsExpeditionRecordedFinds = outcome == Pending.LOST ? "" : RuinsSiteFinds.merged("", broughtHome);
        return true;
    }

    /** The finds recorded for {@code serial} as brought home; empty when none, or when it is not the one recorded. */
    public static Set<String> recordedFinds(CampaignState state, int serial) {
        if (state == null || serial <= 0 || state.ruinsExpeditionRecorded != serial) return Set.of();
        return RuinsSiteFinds.parse(state.ruinsExpeditionRecordedFinds);
    }

    /** Takes the outcome recorded for {@code serial}, once; {@link Pending#NONE} when there is none to take. */
    public static Pending take(CampaignState state, int serial) {
        if (state == null || serial <= 0 || state.ruinsExpeditionRecorded != serial) return Pending.NONE;
        if (serial <= state.ruinsExpeditionCommitted) return Pending.NONE;
        state.ruinsExpeditionCommitted = serial;
        if (state.ruinsExpeditionRecordedVictory) return Pending.WON;
        return state.ruinsExpeditionRecordedPulledOut ? Pending.PULLED_OUT : Pending.LOST;
    }
}
