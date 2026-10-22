package com.dillon.starsectormarines.campaign.bloom;

/** How an outbreak ended. Append only. */
public enum BloomEnd {
    NONE,
    /** The mother bore was collapsed: the bores are down and it is quiet. */
    CONTAINED,
    /** Enough of the colony was lifted out that the bloom ran out of food. */
    STARVED_OUT,
    /** Nobody answered; the bloom ate what it could reach and went quiet on its own. */
    BURNED_OUT,
    /** Nobody answered, and the colony did not survive it. */
    DECIVILIZED,
    /** Nobody answered, and the world's own military put it down (the benign first stage). */
    PUT_DOWN;

    public boolean byThePlayer() {
        return this == CONTAINED || this == STARVED_OUT;
    }
}
