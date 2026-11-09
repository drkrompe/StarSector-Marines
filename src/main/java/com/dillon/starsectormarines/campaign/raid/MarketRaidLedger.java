package com.dillon.starsectormarines.campaign.raid;

import com.dillon.starsectormarines.campaign.CampaignState;

/**
 * Makes a Marine Operations raid commit exactly once, in two steps ({@code raid-resolution.md}).
 *
 * <p>Every briefing opened draws a new serial into its mission key. Results {@link #record records} the battle's
 * outcome against that serial; the raid dialog, once the Marine Operations panel closes, {@link #take takes} it and
 * hands it to the game's own raid command. A briefing backed out of leaves its serial unrecorded, so nothing is
 * taken and the raid is not committed. Replaying Results, or an older briefing's result, records nothing, and a
 * recorded outcome is taken once.
 */
public final class MarketRaidLedger {

    /** What the raid dialog finds when the panel closes. */
    public enum Pending {
        /** No battle was fought for this briefing: nothing to commit. */
        NONE,
        /** The company won: commit the raid at its own effectiveness. */
        WON,
        /** The company lost or withdrew: commit the raid with nothing achieved. */
        LOST
    }

    private MarketRaidLedger() {}

    /** The serial for a newly opened raid briefing. */
    public static int issue(CampaignState state) {
        if (state == null) return 0;
        return ++state.marketRaidIssued;
    }

    /** Records the battle fought for {@code serial}; false when it, or a later raid, is already recorded. */
    public static boolean record(CampaignState state, int serial, boolean victory) {
        return record(state, serial, victory, 1f);
    }

    /**
     * Records the battle fought for {@code serial} with the share of a disrupting raid's works it wrecked
     * ({@code raid-mission.md}, Break); false when it, or a later raid, is already recorded.
     */
    public static boolean record(CampaignState state, int serial, boolean victory, float worksShare) {
        if (state == null || serial <= 0 || serial > state.marketRaidIssued) return false;
        if (serial <= state.marketRaidRecorded) return false;
        state.marketRaidRecorded = serial;
        state.marketRaidRecordedVictory = victory;
        state.marketRaidRecordedShare = worksShare;
        return true;
    }

    /**
     * The share of the works the recorded battle wrecked, 0 to 1. A save from before the share was kept reads
     * nothing there, and a won raid always wrecked something, so anything outside (0, 1] reads as the whole.
     */
    public static float recordedShare(CampaignState state) {
        if (state == null) return 1f;
        float share = state.marketRaidRecordedShare;
        return share > 0f && share <= 1f ? share : 1f;
    }

    /** Takes the outcome recorded for {@code serial}, once; {@link Pending#NONE} when there is none to take. */
    public static Pending take(CampaignState state, int serial) {
        if (state == null || serial <= 0 || state.marketRaidRecorded != serial) return Pending.NONE;
        if (serial <= state.marketRaidCommitted) return Pending.NONE;
        state.marketRaidCommitted = serial;
        return state.marketRaidRecordedVictory ? Pending.WON : Pending.LOST;
    }
}
