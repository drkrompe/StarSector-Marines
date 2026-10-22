package com.dillon.starsectormarines.campaign.bloom;

/**
 * The fixed rules of where and when the Rav Bloom surfaces ({@code colony-breach-nouns.md},
 * Campaign: blooms). Pure functions of a market and the save seed; the owner's picks are in
 * {@link BloomTuning}.
 */
public final class RavBloomRules {

    /** Nothing wakes in the first weeks of a campaign. */
    public static final int FIRST_WAKE_DAY = 30;
    /** A colony younger than this has not dug deep enough to wake anything. */
    public static final int MIN_MARKET_AGE_DAYS = 90;
    /** Colonies this young are still digging new workings. */
    public static final int YOUNG_MARKET_DAYS = 3 * 365;
    public static final int MIN_SIZE = 3;
    /** More running blooms than this and nothing new wakes. */
    public static final int MAX_RUNNING = 2;

    public static final int MIN_STIR_DAYS = 10;
    public static final int STIR_DAY_SPREAD = 11;
    /** Days the distress call stays open for relief to be committed. */
    public static final int CALL_WINDOW_DAYS = 14;
    /** An erupted colony goes dark this long after eruption if nobody has lifted anyone out. */
    public static final int DARK_AFTER_DAYS = 45;
    /** A starved bloom burns out this long after the lift. */
    public static final int STARVED_DAYS = 90;
    public static final int REST_AFTER_CONTAINED_DAYS = 3 * 365;
    public static final int REST_AFTER_OTHER_DAYS = 365;
    /** Seeded blooms of this generation seed nothing further. */
    public static final int MAX_GENERATION = 3;
    public static final float SPREAD_RANGE_LY = 6f;
    public static final float FRONTIER_LY = 25f;

    static final double BASE_RESERVOIR = 0.30;
    static final double RUINS_RESERVOIR = 0.15;
    static final double HABITABLE_RESERVOIR = 0.10;

    public static final String HEGEMONY = "hegemony";
    public static final String CHURCH = "luddic_church";

    private RavBloomRules() {}

    /** Whether a reservoir lies under this market: hidden, fixed for the save, never stored. */
    public static boolean hasReservoir(long seed, BloomMarket market) {
        double chance = BASE_RESERVOIR
                + (market.ruins ? RUINS_RESERVOIR : 0.0)
                + (market.habitable ? HABITABLE_RESERVOIR : 0.0);
        return unit(mix(seed ^ 0x5245534552564FL, market.id.hashCode())) < chance;
    }

    /** Relative weight of this market waking a bloom this week; 0 when it cannot. */
    public static double wakeWeight(BloomMarket market) {
        if (market.size < MIN_SIZE || market.hidden || market.daysInExistence < MIN_MARKET_AGE_DAYS) {
            return 0.0;
        }
        double size = market.size <= 3 ? 0.6 : market.size == 4 ? 1.0 : market.size == 5 ? 1.3 : 1.6;
        double age = market.daysInExistence < YOUNG_MARKET_DAYS ? 1.5 : 1.0;
        double industry = market.workings ? 1.5 : 1.0;
        double reach = market.core ? 0.5 : market.lyFromCore > FRONTIER_LY ? 1.3 : 1.0;
        return size * age * industry * reach;
    }

    /** Whether the Hegemony quarantines a bloom here: its own world, or the core while it lives. */
    public static boolean hegemonyPolices(BloomMarket market, boolean hegemonyAlive) {
        return HEGEMONY.equals(market.factionId) || (market.core && hegemonyAlive);
    }

    /** Chance this week that the sector wakes one bloom from a reservoir. */
    public static double weeklyWakeChance(BloomTuning tuning) {
        return tuning.frequency.bloomsPerYear * 7.0 / 365.25;
    }

    public static int stirDays(long seed, String marketId, int day) {
        return MIN_STIR_DAYS + (int) (unit(mix(seed ^ day, marketId.hashCode())) * STIR_DAY_SPREAD);
    }

    /** Owner reputation for an evacuation: one per quarter of the cohort lifted, at most four. */
    public static int ownerReputationFor(int evacuated, int atRisk) {
        if (evacuated <= 0 || atRisk <= 0) return 0;
        return Math.min(4, (int) (4L * evacuated / atRisk));
    }

    static long mix(long a, long b) {
        long value = a * 0x9E3779B97F4A7C15L ^ (b + 0x632BE59BD9B4E019L);
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        value ^= value >>> 31;
        return value;
    }

    static double unit(long hash) {
        return (hash >>> 11) * 0x1.0p-53;
    }
}
