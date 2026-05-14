package com.dillon.starsectormarines.marine;

/**
 * A captain's military rank caps the marine pool they can lead on a raid (their "squad cap").
 * Promotions widen the buffable pool. Powers-of-two squad caps for easy balancing.
 */
public enum Rank {
    PRIVATE("Private", 5),
    CORPORAL("Corporal", 10),
    SERGEANT("Sergeant", 20),
    LIEUTENANT("Lieutenant", 40),
    CAPTAIN("Captain", 80),
    MAJOR("Major", 160),
    COLONEL("Colonel", 320),
    GENERAL("General", 640);

    private final String displayName;
    private final int squadCap;

    Rank(String displayName, int squadCap) {
        this.displayName = displayName;
        this.squadCap = squadCap;
    }

    public String displayName() {
        return displayName;
    }

    public int squadCap() {
        return squadCap;
    }

    public Rank promote() {
        Rank[] all = values();
        return ordinal() + 1 < all.length ? all[ordinal() + 1] : this;
    }
}
