package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.i18n.Strings;
/**
 * An officer's rank, which caps how many {@link MarineSquad}s they can command.
 * Squads are led by their own NCOs; this ladder is the officer above them.
 *
 * <p>The cap is denominated in <em>squads</em>, not marines, because a squad is
 * the unit the player commands and the unit every command surface counts. It is
 * also bounds named multi-squad coordination. Ordinary deployments beyond that
 * capacity use independent acting NCOs; stationing still requires named leadership.
 *
 * <p>One officer commands at most twelve squads (owner, 2026-10-27): the ladder tops
 * out there, so a company larger than twelve squads needs a second officer.
 *
 * <p>{@link #xpToNext} is the XP threshold to advance from this rank to the next.
 * XP doubles per tier. Mission XP is sized against it ({@code OfficerMissionResult})
 * so the opening's second mission promotes a lieutenant and Thunder Run a captain,
 * each played once; later ranks are real long-haul rewards. The terminal rank
 * returns {@link Integer#MAX_VALUE} — see {@link #isTerminal()}.
 */
public enum Rank {
    /** A platoon, and the rank the player's company starts under. */
    LIEUTENANT("openingRankLieutenant", 3, 1000),
    /** A merc-sized company. */
    CAPTAIN("openingRankCaptain", 6, 2000),
    MAJOR("openingRankMajor", 9, 4000),
    LT_COLONEL("openingRankLtColonel", 12, 8000),
    /** Seniority past the command cap: no more squads than a lieutenant colonel. */
    COLONEL("openingRankColonel", 12, Integer.MAX_VALUE);

    private final String displayName;
    private final int squadCommandCap;
    private final int xpToNext;

    Rank(String displayName, int squadCommandCap, int xpToNext) {
        this.displayName = displayName;
        this.squadCommandCap = squadCommandCap;
        this.xpToNext = xpToNext;
    }

    /** The catalog key of {@link #displayName()}, for text saved now and read in another language. */
    public String displayKey() {
        return displayName;
    }

    public String displayName() {
        return Strings.get(displayName);
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
