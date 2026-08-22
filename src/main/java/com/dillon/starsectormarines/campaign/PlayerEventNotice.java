package com.dillon.starsectormarines.campaign;

/**
 * One campaign decision waiting on the player, flattened into everything a
 * presentation layer needs to render it without touching {@link CampaignState}
 * again.
 *
 * <p>Deliberately carries a string <em>key</em> rather than resolved display text:
 * {@link PlayerEventInbox} stays free of {@code Global} and remains headless-testable,
 * and the popup resolves the key through {@code Strings} at draw time so a translation
 * mod still wins.
 *
 * <p>The {@link #sourceKey} is the notice's identity for acknowledgement purposes —
 * the Garrison defense event key, or the Cadre incident's due day. It changes when a
 * new event arms on the same contract, which is exactly when the player should be
 * told again.
 */
public final class PlayerEventNotice {

    public enum Kind {
        GARRISON_DEFENSE,
        CADRE_INCIDENT
    }

    public final Kind kind;
    public final long contractId;
    /** Identity of this specific event on its contract; see the class javadoc. */
    public final long sourceKey;
    /** Registry slot of the market the stationed detachment is protecting. */
    public final int marketId;
    /** Day the event armed — the defense trigger day, or the incident's due day. */
    public final int triggeredDay;
    /** Day the response lapses, from {@code contractResponseDeadlineTick} or projected. */
    public final int deadlineDay;
    /** Captain bound to the stationing assignment, or null for a legacy count-only row. */
    public final String captainId;
    public final int committedMarines;
    /** Marines actually fit to fight; 0 means the detachment cannot field a response. */
    public final int activeSeats;
    /** {@code Strings} key for the popup's headline. */
    public final String headerKey;
    /** {@code Strings} key for the one-line description of what happened. */
    public final String labelKey;

    PlayerEventNotice(Kind kind, long contractId, long sourceKey, int marketId,
                      int triggeredDay, int deadlineDay, String captainId,
                      int committedMarines, int activeSeats,
                      String headerKey, String labelKey) {
        this.kind = kind;
        this.contractId = contractId;
        this.sourceKey = sourceKey;
        this.marketId = marketId;
        this.triggeredDay = triggeredDay;
        this.deadlineDay = deadlineDay;
        this.captainId = captainId;
        this.committedMarines = committedMarines;
        this.activeSeats = activeSeats;
        this.headerKey = headerKey;
        this.labelKey = labelKey;
    }

    /** Days the player has left to answer, floored at zero. */
    public int daysRemaining(int currentDay) {
        return Math.max(0, deadlineDay - currentDay);
    }

    @Override
    public String toString() {
        return "PlayerEventNotice[" + kind + " contract=" + contractId
                + " source=" + sourceKey + " deadline=" + deadlineDay + "]";
    }
}
