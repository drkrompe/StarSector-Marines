package com.dillon.starsectormarines.campaign.bloom;

import java.util.List;

/**
 * The sector as the bloom system sees it: what it reads about markets, and the levers it pulls
 * on them. The live implementation is the Starsector economy; evidence runs a synthetic sector.
 * Everything a bloom does to a market goes through here, so the system itself stays pure.
 */
public interface BloomWorld {

    /** Fixed for the save: reservoirs and weekly draws are hashed from it. */
    long seed();

    /** Every market that could bloom, in a stable order (by id). */
    List<BloomMarket> markets();

    /** One market, or null when it no longer exists. */
    BloomMarket market(String marketId);

    /** Whether the Hegemony still holds any market, so it can police the core. */
    boolean hegemonyAlive();

    /** Bores opened: the market takes the bloom condition. */
    void erupted(RavBloom bloom);

    /** The colony went quiet. */
    void wentDark(RavBloom bloom);

    /** Lifted out enough that the bloom is starving. */
    void starved(RavBloom bloom);

    /** Takes one size off the market; false when it is at the floor and nothing was taken. */
    boolean reduceSize(String marketId);

    /** The colony does not survive (deadly severity only). */
    void decivilize(String marketId);

    /** The outbreak is over; lift the condition and leave unrest behind. */
    void ended(RavBloom bloom);

    /** A faction's view of the player moved because of how this bloom went. */
    void reputation(String factionId, int delta, RavBloom bloom);
}
