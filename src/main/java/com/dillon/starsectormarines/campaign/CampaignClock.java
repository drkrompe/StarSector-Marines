package com.dillon.starsectormarines.campaign;

import com.dillon.starsectormarines.DebugOnly;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignClockAPI;
import com.fs.starfarer.api.campaign.SectorAPI;

/**
 * The campaign tier's monotonic day counter.
 *
 * <p><b>Do not call {@code Global.getSector().getClock().getDay()} for elapsed time.</b>
 * That is a calendar component, not a counter — vanilla gates on
 * {@code getClock().getDay() == 15} and {@code == 28} — so it wraps roughly monthly and
 * sits beside {@code getCycle()} / {@code getMonth()} / {@code getHour()}. Every duration
 * this mod measures (retainer months, default checkpoints, incident cadence, offer
 * expiry, injury recovery, stationing response deadlines) needs a value that only ever
 * increases. Vanilla's own idiom for that is
 * {@link CampaignClockAPI#getElapsedDaysSince(long)} against a stored timestamp, which is
 * what this class wraps.
 *
 * <p>The counter is anchored rather than absolute: {@link CampaignState#clockEpochTimestamp}
 * is stamped on first use and paired with the day number in effect at that moment. A save
 * written before this class existed therefore continues the numbering it already had, so
 * stored timers keep their relative distances instead of being stranded in the past by a
 * jump to a fixed epoch. New games start the counter at zero.
 */
public final class CampaignClock {

    /**
     * Per-frame memo. Resolving the anchor walks the sector's script list, and UI code
     * reads the day inside {@code render}, once per card per frame. The sector clock's
     * timestamp is fixed within a frame, so keying on it collapses that to one lookup;
     * the epoch is part of the key so a different save can never read a stale value.
     */
    private static long memoTimestamp = Long.MIN_VALUE;
    private static long memoEpoch = Long.MIN_VALUE;
    private static float memoDay;

    private CampaignClock() {}

    /** Current campaign day. Monotonic for the life of a save. */
    public static int day() {
        return (int) dayFloat();
    }

    /** Current campaign day with sub-day precision, for the personnel graph's float fields. */
    public static float dayFloat() {
        SectorAPI sector = Global.getSector();
        if (sector == null || sector.getClock() == null) return 0f;
        CampaignClockAPI clock = sector.getClock();
        CampaignStateScript script = CampaignStateScript.getInstance();
        if (script == null) {
            // Pre-initialisation (the state script is installed early in onGameLoad, before
            // anything that measures time). Anchorless, but still same-frame consistent.
            return 0f;
        }
        CampaignState state = script.state();
        if (state.clockEpochTimestamp == 0L) {
            state.clockEpochTimestamp = clock.getTimestamp();
            state.clockEpochDay = Math.max(0, state.lastTickDay);
        }
        long now = clock.getTimestamp();
        if (now == memoTimestamp && state.clockEpochTimestamp == memoEpoch) {
            return memoDay;
        }
        float value = dayFrom(state.clockEpochDay,
                clock.getElapsedDaysSince(state.clockEpochTimestamp));
        memoTimestamp = now;
        memoEpoch = state.clockEpochTimestamp;
        memoDay = value;
        return value;
    }

    /**
     * Moves the campaign tier's day counter forward without waiting for real game time.
     *
     * <p>Vanilla's {@link CampaignClockAPI} is read-only — there is no way to advance the
     * sector clock from the API — so this re-anchors instead: the epoch timestamp is
     * restamped to now and the epoch day set to the target, which leaves future real time
     * accruing normally from the new anchor.
     *
     * <p><b>This advances only what reads {@link #day()}</b> — every campaign-tier timer,
     * deadline, and cadence in this mod. It does not advance vanilla's economy, so a
     * monthly report will still only appear when a real in-game month rolls over.
     *
     * @return the new campaign day
     */
    @DebugOnly
    public static int skipDays(CampaignState state, int days) {
        SectorAPI sector = Global.getSector();
        if (state == null || sector == null || sector.getClock() == null || days <= 0) {
            return day();
        }
        int target = day() + days;
        state.clockEpochTimestamp = sector.getClock().getTimestamp();
        state.clockEpochDay = target;
        // The memo keys on the epoch timestamp, and re-anchoring inside one frame can
        // land on the same timestamp it already cached. Drop it explicitly rather than
        // relying on the key to have changed.
        memoTimestamp = Long.MIN_VALUE;
        memoEpoch = Long.MIN_VALUE;
        return target;
    }

    /**
     * Pure counter arithmetic, split out so the anchoring is testable without a sector.
     * Elapsed days are clamped at zero: a clock that reports a negative delta (a
     * re-anchored or rewound save) must never walk the counter backwards, because every
     * "have N days passed" check in the campaign tier would then re-fire.
     */
    static float dayFrom(int epochDay, float elapsedDays) {
        return epochDay + Math.max(0f, elapsedDays);
    }
}
