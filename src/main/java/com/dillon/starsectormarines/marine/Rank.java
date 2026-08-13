package com.dillon.starsectormarines.marine;

/**
 * A captain's military rank caps the marine pool they can lead on a raid (their "squad cap").
 * Promotions widen the buffable pool. Powers-of-two squad caps for easy balancing.
 *
 * <p>{@link #xpToNext} is the XP threshold to advance from this rank to the next. XP scales
 * by ~2x per tier, so early promotions feel fast (a few medium-risk missions) and late ones
 * are real long-haul rewards. GENERAL returns {@link Integer#MAX_VALUE} — there's nowhere to go.
 */
public enum Rank {
    PRIVATE("Private", 5, 250),
    CORPORAL("Corporal", 10, 500),
    // A starting Sergeant must be able to command the full opening company:
    // seven six-marine fireteams. Higher ranks retain a strictly increasing
    // command ceiling from that baseline.
    SERGEANT("Sergeant", 42, 1000),
    LIEUTENANT("Lieutenant", 80, 2000),
    CAPTAIN("Captain", 160, 4000),
    MAJOR("Major", 320, 8000),
    COLONEL("Colonel", 640, 16000),
    GENERAL("General", 1280, Integer.MAX_VALUE);

    private final String displayName;
    private final int squadCap;
    private final int xpToNext;

    Rank(String displayName, int squadCap, int xpToNext) {
        this.displayName = displayName;
        this.squadCap = squadCap;
        this.xpToNext = xpToNext;
    }

    public String displayName() {
        return displayName;
    }

    public int squadCap() {
        return squadCap;
    }

    /** Whole persistent fireteams this rank can organize without rounding down its marine cap. */
    public int fireteamCap() {
        return (squadCap + MarineSquad.CAPACITY - 1) / MarineSquad.CAPACITY;
    }

    public int xpToNext() {
        return xpToNext;
    }

    public Rank promote() {
        Rank[] all = values();
        return ordinal() + 1 < all.length ? all[ordinal() + 1] : this;
    }
}
