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
 */
public record MarketGarrison(float strength, float quality, float heavy, float readiness) {

    /** No market, no garrison: a consumer that reads it keeps its own mission-authored defaults. */
    public static final MarketGarrison NONE = new MarketGarrison(0f, 0f, 0f, 1f);

    public MarketGarrison {
        strength = clamp(strength, 0f, Float.MAX_VALUE);
        quality = clamp(quality, 0f, 1f);
        heavy = clamp(heavy, 0f, 1f);
        readiness = clamp(readiness, 0f, 1f);
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
