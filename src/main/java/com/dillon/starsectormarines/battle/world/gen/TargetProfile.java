package com.dillon.starsectormarines.battle.world.gen;

/**
 * The campaign → battle read of the <em>target world</em>, distilled to plain
 * data so the procedural core stays campaign-free. This is the value object the
 * campaign → battle bridge threads inward: extracted from the vanilla
 * {@code MarketAPI} at the launch boundary (see {@code TargetProfileResolver},
 * beside {@code DetachmentResolver}) and read by generator stages via
 * {@link com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys#MARKET_PROFILE}.
 *
 * <p><b>No game API here, by contract.</b> {@code battle.world.gen} must compile
 * and run headless (every generator/taxonomy test drives it without a sector),
 * so the bridge carries primitives + an interned faction id, never a
 * {@code MarketAPI}. See {@code roadmap/campaign-battle-bridge/overview.md}.
 *
 * <p>Extracted <em>whole</em> even though the first consumer reads only
 * {@link #defenseLevel} — the extraction is written once and later consumers
 * (urban composition, hard installations) opt into fields without re-touching
 * the resolver.
 *
 * @param marketSize    vanilla market size (~0–10, population/urbanization
 *                      proxy); {@code 0} when no market backs the battle.
 * @param stability     vanilla market stability (~0–10).
 * @param defenseLevel  weighted planetary-defense rating (~0–7): ground
 *                      defenses / heavy batteries + orbital station tier + high
 *                      command + planetary shield. {@code 0} = undefended /
 *                      baseline. Drives the overwatch line's intensity.
 * @param spaceportTier {@code 0} none, {@code 1} spaceport, {@code 2} megaport.
 * @param factionId     owning faction id, or {@code ""} when unknown. Never null.
 */
public record TargetProfile(int marketSize, int stability, int defenseLevel,
                            int spaceportTier, String factionId) {

    /**
     * The baseline read used when no campaign market backs the battle (headless
     * tests, legacy/preview generation, story ops with no target planet). Every
     * field reads as "no signal," so a stage handed this produces the same
     * output it did before the bridge existed — the invariant that keeps the
     * pre-bridge generation byte-identical.
     */
    public static final TargetProfile NEUTRAL = new TargetProfile(0, 0, 0, 0, "");

    public TargetProfile {
        if (factionId == null) factionId = "";
    }
}
