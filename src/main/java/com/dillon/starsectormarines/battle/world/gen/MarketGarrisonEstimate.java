package com.dillon.starsectormarines.battle.world.gen;

import java.util.Collection;
import java.util.List;

/**
 * Estimates a market's {@link MarketGarrison} from plain facts the launch boundary reads off it. Campaign-free so it
 * can be asked directly; {@code TargetProfileResolver} supplies the facts.
 *
 * <p>The shape follows how vanilla and Nexerelin already reason about ground defence, so the mod agrees with both
 * whether or not Nexerelin is installed:
 * <ul>
 *   <li><b>Strength</b> is vanilla's own ground-defence value, which already carries market size, stability, hazard
 *       and every defence industry's multiplier, and every other mod's modifier on that stat. The company's stationed
 *       strength comes out of it once, and readiness scales what is left.</li>
 *   <li><b>Quality and heavy lean</b> come from three weights — bulk, elite and heavy — that start from size, shift
 *       toward elites on a military market, gain whatever the market's structures add, and lose elite and heavy weight
 *       to shortages of the things that equip them. Every garrison carries some elites: the weights decide how thick
 *       the stiffening is, never whether a squad is all militia.</li>
 * </ul>
 * See {@code market-garrison-model.md}.
 */
public final class MarketGarrisonEstimate {

    /**
     * The market size from which a garrison's professional core thickens. UNTUNED: the size at which Nexerelin starts
     * fielding marines beside militia, so the two mods agree on where a colony stops being mostly its own people under
     * arms.
     */
    public static final int PROFESSIONAL_FROM_SIZE = 5;

    /** Elite weight beside one part bulk below {@link #PROFESSIONAL_FROM_SIZE}: a fifth of the infantry. UNTUNED. */
    static final float ELITE_BASE_SMALL = 0.25f;

    /** Elite weight beside one part bulk from {@link #PROFESSIONAL_FROM_SIZE}: a third of the infantry. UNTUNED. */
    static final float ELITE_BASE_LARGE = 0.5f;

    /** How much bulk weight a military market converts to elite weight. UNTUNED, as Nexerelin. */
    static final float MILITARY_SHIFT = 0.25f;

    /**
     * The least a shortage leaves of a share it shrinks: a market with none of what equips its elites still fields
     * half of them. UNTUNED, as Nexerelin.
     */
    static final float SHORTAGE_FLOOR = 0.5f;

    private MarketGarrisonEstimate() {}

    /**
     * The plain facts an estimate reads.
     *
     * @param marketSize        vanilla market size; {@code 0} or less means no market and no garrison
     * @param groundDefence     vanilla's defender strength for the market ({@code MarketCMD.getDefenderStr})
     * @param stationedStrength the company's own contribution to {@code groundDefence}, taken out once
     * @param military          the market is flagged military by vanilla (a military base or better)
     * @param industryIds       the ids of the market's industries and structures; null reads as none
     * @param marinesSupply     marines available against demand, {@code 0..1} ({@code 1} = no shortage)
     * @param handWeaponsSupply heavy armaments available against demand, {@code 0..1}
     * @param suppliesSupply    supplies available against demand, {@code 0..1}
     * @param readiness         {@code 0..1}; below one after recent fighting at the market
     */
    public record Inputs(int marketSize, float groundDefence, float stationedStrength, boolean military,
                         Collection<String> industryIds, float marinesSupply, float handWeaponsSupply,
                         float suppliesSupply, float readiness) {

        public Inputs {
            industryIds = industryIds == null ? List.of() : List.copyOf(industryIds);
            marinesSupply = fraction(marinesSupply);
            handWeaponsSupply = fraction(handWeaponsSupply);
            suppliesSupply = fraction(suppliesSupply);
            readiness = fraction(readiness);
        }

        /** A market with no shortages and no recent fighting. */
        public static Inputs unstrained(int marketSize, float groundDefence, float stationedStrength,
                                        boolean military, Collection<String> industryIds) {
            return new Inputs(marketSize, groundDefence, stationedStrength, military, industryIds, 1f, 1f, 1f, 1f);
        }
    }

    /** The garrison these facts describe; {@link MarketGarrison#NONE} when there is no market. */
    public static MarketGarrison estimate(Inputs in, GarrisonStructureTable table) {
        if (in == null || in.marketSize() <= 0) return MarketGarrison.NONE;
        if (table == null) table = GarrisonStructureTable.EMPTY;

        float bulk = 1f;
        float elite = in.marketSize() >= PROFESSIONAL_FROM_SIZE ? ELITE_BASE_LARGE : ELITE_BASE_SMALL;
        float heavy = 0f;
        if (in.military()) {
            float shift = Math.min(bulk, MILITARY_SHIFT);
            bulk -= shift;
            elite += shift;
        }
        for (String id : in.industryIds()) {
            GarrisonStructureTable.Contribution c = table.of(id);
            bulk += c.bulk();
            elite += c.elite();
            heavy += c.heavy();
        }

        elite *= shortage(in.marinesSupply());
        heavy *= shortage(Math.min(in.marinesSupply(), Math.min(in.handWeaponsSupply(), in.suppliesSupply())));

        float infantry = bulk + elite;
        float quality = elite / infantry;
        float heavyLean = heavy / (infantry + heavy);
        float strength = Math.max(0f, nonNegative(in.groundDefence()) - nonNegative(in.stationedStrength()))
                * in.readiness();
        return new MarketGarrison(strength, quality, heavyLean, in.readiness());
    }

    private static float shortage(float supply) {
        return SHORTAGE_FLOOR + (1f - SHORTAGE_FLOOR) * supply;
    }

    private static float fraction(float value) {
        if (Float.isNaN(value)) return 1f;
        return Math.max(0f, Math.min(1f, value));
    }

    private static float nonNegative(float value) {
        return Float.isNaN(value) || value < 0f ? 0f : value;
    }
}
