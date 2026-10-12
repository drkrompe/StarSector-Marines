package com.dillon.starsectormarines.battle.story;

import java.util.List;

/** Public approach facts; presentation owns the patrol exchange's speaking order and timing. */
public final class ColdAccountStoryHooks {
    public static final String PREFIX = "corvus.cold_account.";
    public static final List<String> PATROL_EXCHANGE = List.of(
            "patrol_hail", "patrol_requires_proof", "archive_offer", "patrol_standing_orders");
    private final StoryBeatLog beats;
    private boolean finished;

    public ColdAccountStoryHooks(StoryBeatLog beats) { this.beats = beats; }

    public void observe(int tick, boolean companyLanded, boolean nearDispatch, boolean secured) {
        finished |= secured;
        if (finished) return;
        // These turns belong to one authored opening contact, not timed simulation orders.
        for (String key : PATROL_EXCHANGE) emit(tick, key);
        if (companyLanded) emit(tick, "company_landed");
        if (companyLanded && nearDispatch) emit(tick, "dispatch_approach");
    }

    private void emit(int tick, String key) {
        beats.emitOnce(tick, PREFIX + key, StoryBeatLog.Audience.PLAYER);
    }
}
