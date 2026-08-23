package com.dillon.starsectormarines.marine;

/**
 * An officer's rank, which caps how many {@link MarineSquad}s they can command.
 * Squads are led by their own NCOs; this ladder is the officer above them.
 *
 * <p>The cap is denominated in <em>squads</em>, not marines, because a squad is
 * the unit the player commands and the unit every command surface counts. It is
 * also the game's scale governor: together with lift capacity it bounds what can
 * reach one battle, which is what keeps the force readable. A rank whose cap
 * outruns what the player can read is a rank that made the game worse — pick
 * future numbers against that ceiling, not against the fiction alone.
 *
 * <p>{@link #xpToNext} is the XP threshold to advance from this rank to the next.
 * XP doubles per tier, so early promotions feel fast (a few medium-risk missions)
 * and late ones are real long-haul rewards. The terminal rank returns
 * {@link Integer#MAX_VALUE} — see {@link #isTerminal()}.
 */
public enum Rank {
    /** A platoon, and the rank the player's company starts under. */
    LIEUTENANT("Lieutenant", 3, 1000),
    /** A merc-sized company. */
    CAPTAIN("Captain", 6, 2000),
    MAJOR("Major", 10, 4000),
    LT_COLONEL("Lt. Colonel", 16, 8000),
    COLONEL("Colonel", 24, Integer.MAX_VALUE);

    private final String displayName;
    private final int squadCommandCap;
    private final int xpToNext;

    Rank(String displayName, int squadCommandCap, int xpToNext) {
        this.displayName = displayName;
        this.squadCommandCap = squadCommandCap;
        this.xpToNext = xpToNext;
    }

    public String displayName() {
        return displayName;
    }

    /** Whole squads this rank can command at once. */
    public int squadCommandCap() {
        return squadCommandCap;
    }

    /** Marines this rank commands at full manning — display only; the cap is in squads. */
    public int marineCommandCap() {
        return squadCommandCap * MarineSquad.CAPACITY;
    }

    public int xpToNext() {
        return xpToNext;
    }

    /** True at the top of the ladder, where {@link #promote()} is a no-op. */
    public boolean isTerminal() {
        return ordinal() + 1 >= values().length;
    }

    public Rank promote() {
        Rank[] all = values();
        return ordinal() + 1 < all.length ? all[ordinal() + 1] : this;
    }
}
