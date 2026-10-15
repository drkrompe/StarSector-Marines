package com.dillon.starsectormarines.battle.world.gen;

/**
 * What a market keeps on its ground: how much defending strength, how professional it is, how heavy, and how ready.
 * The campaign → battle bridge's garrison estimate, computed once at the launch boundary by
 * {@link MarketGarrisonEstimate} and carried inward on the {@link TargetProfile}, so battle code never reads a market
 * to learn who is standing on it.
 *
 * <p>These are dials, not tiers of squad. {@link #quality} is the share of the infantry drawn from the owner's elite
 * issue rather than its bulk issue, and the two are mixed through every squad, each soldier rolling their own weapon,
 * armour and grade from the faction roster. A small colony is therefore a thinly stiffened garrison, not a squad of
 * militia; a fortress world is a professional one. {@link #heavy} leans the force toward mechs, vehicles and
 * emplacements. See {@code market-garrison-model.md}; the estimate is identical whether or not Nexerelin is installed.
 *
 * @param strength  the garrison's defending strength in vanilla ground-defence units, after the company's own
 *                  stationed strength is taken out and readiness is applied. Never negative.
 * @param quality   share of the infantry issued from the elite tier, {@code 0..1}
 * @param heavy     how far the force leans toward heavy support, {@code 0..1}
 * @param readiness the multiplier already applied to {@link #strength} for recent fighting at the market,
 *                  {@code 0..1}; {@code 1} for a garrison nobody has recently fought
 * @param whole     whether this operation meets the whole garrison rather than the share its tier engages. Only a
 *                  player invasion does ({@code nexerelin-nouns.md}); every contract operation is banded by its tier.
 */
public record MarketGarrison(float strength, float quality, float heavy, float readiness, boolean whole) {

    /** No market, no garrison: a consumer that reads it keeps its own mission-authored defaults. */
    public static final MarketGarrison NONE = new MarketGarrison(0f, 0f, 0f, 1f);

    /** A garrison an operation meets only in part, as every contract operation does. */
    public MarketGarrison(float strength, float quality, float heavy, float readiness) {
        this(strength, quality, heavy, readiness, false);
    }

    public MarketGarrison {
        strength = clamp(strength, 0f, Float.MAX_VALUE);
        quality = clamp(quality, 0f, 1f);
        heavy = clamp(heavy, 0f, 1f);
        readiness = clamp(readiness, 0f, 1f);
    }

    /**
     * The least share of a mission's heavy candidates (mechs, emplacements) a garrison keeps: one with no heavy lean
     * at all still keeps this much. UNTUNED: the mission's own count is the ceiling, so nothing grows, and the
     * market's armour picks the place between.
     */
    public static final float HEAVY_BAND_FLOOR = 0.5f;

    /**
     * Heavy lean at which a garrison keeps every heavy candidate. UNTUNED: calibrated against Nexerelin's own garrison
     * estimate, where a market with Ground Defenses carries about a fifth of its strength as heavy units and one with
     * Heavy Batteries about a quarter; at this value both keep nearly all of theirs.
     */
    public static final float HEAVY_LEAN_FOR_FULL = 0.25f;

    /**
     * How many of a mission's {@code candidates} heavy pieces this garrison keeps: between {@link #HEAVY_BAND_FLOOR}
     * of them and all, by heavy lean, and at least one when there were any. {@link #NONE} keeps them all.
     */
    public int keepHeavy(int candidates) {
        if (candidates <= 0 || isNone()) return Math.max(0, candidates);
        float fill = Math.min(1f, heavy / HEAVY_LEAN_FOR_FULL);
        float share = HEAVY_BAND_FLOOR + (1f - HEAVY_BAND_FLOOR) * fill;
        return Math.min(candidates, Math.max(1, (int) Math.ceil(candidates * share - 1e-4f)));
    }

    /** The same garrison, met whole: the defender count is its whole strength, not its tier's band. */
    public MarketGarrison asWhole() {
        return whole ? this : new MarketGarrison(strength, quality, heavy, readiness, true);
    }

    /** Whether this is the absence of a garrison rather than a garrison. */
    public boolean isNone() {
        return strength <= 0f && quality <= 0f && heavy <= 0f;
    }

    private static float clamp(float value, float lo, float hi) {
        if (Float.isNaN(value)) return lo;
        return Math.max(lo, Math.min(hi, value));
    }
}
