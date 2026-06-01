package com.dillon.starsectormarines.battle.world.gen;

import java.util.Set;

/**
 * The selection-layer policy that turns a world's {@link EconomicFunction} mix
 * into a {@link MapDistrictTheme} bias — the shared substrate both zoning paths
 * consult so a heavy-industry world's ground reads industrial, a farming colony
 * rural, and so on. Keeping the mapping here (rather than inlined in each map)
 * means the slice-1-to-4 districts extend one policy, not two.
 *
 * <p><b>Slice 0 maps onto the existing nine themes</b> — no new district kinds
 * yet; the point is to prove the campaign → battle plumbing on known output. The
 * dedicated economic districts (mining, refinery, agriculture, …) replace these
 * stand-in mappings as they ship. See {@code roadmap/economic-districts/overview.md}.
 */
public final class EconomicZoning {

    private EconomicZoning() {}

    /**
     * The single theme that best expresses a world's economic character, or
     * {@code null} when the mix carries no signal (an empty set — the
     * {@link TargetProfile#NEUTRAL} / headless case — so callers fall back to
     * their pre-bridge default and reproduce byte-identical output).
     *
     * <p>Ordered by how <em>distinctively</em> a function paints the ground, not
     * by how common it is: the extractive / industrial / rural roles win over
     * the near-universal {@link EconomicFunction#HABITATION} so a farming or
     * trade world doesn't collapse into generic residential just because it also
     * houses people. {@link EconomicFunction#SPACEPORT} contributes no theme here
     * — it drives landing structures via {@link TargetProfile#spaceportTier()},
     * and in conquest the port band is already structural.
     */
    public static MapDistrictTheme dominantTheme(Set<EconomicFunction> functions) {
        if (functions == null || functions.isEmpty()) return null;
        if (functions.contains(EconomicFunction.HEAVY_INDUSTRY)
                || functions.contains(EconomicFunction.REFINING)
                || functions.contains(EconomicFunction.MINING)) {
            return MapDistrictTheme.INDUSTRIAL;
        }
        if (functions.contains(EconomicFunction.AGRICULTURE)) return MapDistrictTheme.OUTSKIRTS;
        if (functions.contains(EconomicFunction.MILITARY))     return MapDistrictTheme.MILITARY_FORT;
        if (functions.contains(EconomicFunction.COMMERCE))     return MapDistrictTheme.CIVIC;
        if (functions.contains(EconomicFunction.HABITATION))   return MapDistrictTheme.RESIDENTIAL;
        return null;
    }
}
