package com.dillon.starsectormarines.battle.world.gen;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

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
 * {@code MarketAPI}. See {@code campaign-battle-bridge-nouns.md}.
 *
 * <p>Extracted as one whole snapshot so defense, urban-composition, port, and
 * later consumers opt into stable fields without re-touching the resolver.
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
 * @param functions     the world's {@link EconomicFunction} mix (presence-only),
 *                      driving economy-reflective district selection. Empty when
 *                      no market backs the battle. Never null; stored as an
 *                      unmodifiable {@link EnumSet} copy.
 * @param surface       what the world's own wild ground is made of. Never null;
 *                      a null argument normalizes to {@link SurfacePalette#ROCK}
 *                      rather than to a living world, because most of the Sector
 *                      is not a garden and silence should not claim otherwise.
 *                      Cultivated ground -- parks, street verges -- does not
 *                      consult this; see {@link SurfacePalette}.
 * @param link          how the settlement joins the rest of its world. Never
 *                      null; a null argument normalizes to
 *                      {@link SettlementLink#ROAD}, because a battle with no
 *                      stated lifeline is more likely an ordinary place than an
 *                      off-grid one.
 */
public record TargetProfile(int marketSize, int stability, int defenseLevel,
                            int spaceportTier, String factionId,
                            Set<EconomicFunction> functions,
                            SurfacePalette surface,
                            SettlementLink link) {

    /**
     * The baseline read used when no campaign market backs the battle (headless
     * tests, legacy/preview generation, story ops with no target planet). Every
     * field reads as "no signal" — including an empty {@link #functions} set, so
     * the selection layer falls back to its pre-bridge theme rolls.
     *
     * <p>{@link #surface} is the one field that cannot read as "no signal",
     * because ground has to be made of something. It is
     * {@link SurfacePalette#ROCK} here, so a battle with no world behind it
     * comes out bare rather than green — which does change wild ground on maps
     * that previously defaulted to grassland, deliberately.
     */
    public static final TargetProfile NEUTRAL =
            new TargetProfile(0, 0, 0, 0, "", EnumSet.noneOf(EconomicFunction.class),
                    SurfacePalette.ROCK, SettlementLink.ROAD);

    public TargetProfile {
        if (factionId == null) factionId = "";
        if (surface == null) surface = SurfacePalette.ROCK;
        if (link == null) link = SettlementLink.ROAD;
        functions = (functions == null || functions.isEmpty())
                ? Collections.unmodifiableSet(EnumSet.noneOf(EconomicFunction.class))
                : Collections.unmodifiableSet(EnumSet.copyOf(functions));
    }

    /**
     * This profile with a different owner, every other field left exactly as the
     * market gave it. The whole point of the wither is that {@link #factionId} is
     * an axis of its own: no generator stage reads it — it selects the defending
     * ground roster and nothing else — so swapping it changes who is standing on
     * the battlefield without disturbing the battlefield. That is what makes a
     * faction swap a controlled comparison rather than a reroll.
     *
     * @param overrideFactionId the replacement owner; {@code null} means "no
     *     override" and returns {@code this} unchanged, so a caller can apply an
     *     optional override without branching.
     */
    public TargetProfile withFactionId(String overrideFactionId) {
        if (overrideFactionId == null || overrideFactionId.equals(factionId)) return this;
        return new TargetProfile(marketSize, stability, defenseLevel, spaceportTier,
                overrideFactionId, functions, surface, link);
    }
}
