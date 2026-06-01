package com.dillon.starsectormarines.battle.world.gen.bsp;

import com.dillon.starsectormarines.battle.world.gen.BiomeKind;
import com.dillon.starsectormarines.battle.world.gen.EconomicFunction;
import com.dillon.starsectormarines.battle.world.gen.EconomicZoning;
import com.dillon.starsectormarines.battle.world.gen.MapDistrictTheme;
import com.dillon.starsectormarines.battle.world.gen.TraversalAxis;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Slice 0 of the economic-districts feature — the campaign → battle economy
 * substrate. Proves the {@link EconomicZoning} policy, that the two zoning maps
 * flex toward a world's {@link EconomicFunction} mix, and — the load-bearing
 * invariant — that an <em>empty</em> mix (the {@code NEUTRAL} / headless case)
 * leaves both maps byte-identical to their pre-bridge output.
 *
 * <p>No new districts here; the existing nine themes are the slice-0 stand-ins.
 * See {@code roadmap/economic-districts/overview.md}.
 */
public class EconomicZoningTest {

    // --- The policy: function mix → dominant theme, ranked by distinctiveness. ---

    @Test
    void emptyMixHasNoSignal() {
        assertNull(EconomicZoning.dominantTheme(EnumSet.noneOf(EconomicFunction.class)),
                "an empty mix must yield no theme so callers keep their pre-bridge roll");
        assertNull(EconomicZoning.dominantTheme(null), "null mix is treated as no signal");
    }

    @Test
    void eachRoleMapsToItsStandInTheme() {
        assertEquals(MapDistrictTheme.INDUSTRIAL, dom(EconomicFunction.HEAVY_INDUSTRY));
        assertEquals(MapDistrictTheme.INDUSTRIAL, dom(EconomicFunction.MINING));
        assertEquals(MapDistrictTheme.INDUSTRIAL, dom(EconomicFunction.REFINING));
        assertEquals(MapDistrictTheme.OUTSKIRTS, dom(EconomicFunction.AGRICULTURE));
        assertEquals(MapDistrictTheme.MILITARY_FORT, dom(EconomicFunction.MILITARY));
        assertEquals(MapDistrictTheme.CIVIC, dom(EconomicFunction.COMMERCE));
        assertEquals(MapDistrictTheme.RESIDENTIAL, dom(EconomicFunction.HABITATION));
        // SPACEPORT carries no theme of its own — tier drives its structures.
        assertNull(dom(EconomicFunction.SPACEPORT));
    }

    @Test
    void distinctiveRolesOutrankUniversalHabitation() {
        // Nearly every market houses people; a farming or trade world must still
        // read as such rather than collapsing to generic residential.
        assertEquals(MapDistrictTheme.OUTSKIRTS,
                EconomicZoning.dominantTheme(EnumSet.of(EconomicFunction.HABITATION, EconomicFunction.AGRICULTURE)));
        assertEquals(MapDistrictTheme.INDUSTRIAL,
                EconomicZoning.dominantTheme(EnumSet.of(EconomicFunction.HABITATION, EconomicFunction.HEAVY_INDUSTRY)));
        assertEquals(MapDistrictTheme.RESIDENTIAL,
                EconomicZoning.dominantTheme(EnumSet.of(EconomicFunction.HABITATION, EconomicFunction.SPACEPORT)));
    }

    // --- BiomeMap (conquest): the CITY band is the one economy-flexed band. ---

    @Test
    void biomeCityBandFlexesWithEconomyButOtherBandsAreStructural() {
        BiomeMap neutral = new BiomeMap(240, 160, TraversalAxis.SOUTH_TO_NORTH, new Random(42),
                EnumSet.noneOf(EconomicFunction.class));
        BiomeMap industrial = new BiomeMap(240, 160, TraversalAxis.SOUTH_TO_NORTH, new Random(42),
                EnumSet.of(EconomicFunction.HEAVY_INDUSTRY));

        int[] city = firstCellOfBiome(neutral, BiomeKind.CITY);
        assertEquals(MapDistrictTheme.MIXED, neutral.themeAt(city[0], city[1]),
                "neutral world's CITY band stays MIXED — pre-bridge default");
        assertEquals(MapDistrictTheme.INDUSTRIAL, industrial.themeAt(city[0], city[1]),
                "heavy-industry world's CITY band reads INDUSTRIAL");

        // Non-CITY bands ignore the economy entirely.
        int[] fortress = firstCellOfBiome(neutral, BiomeKind.FORTRESS_DISTRICT);
        assertEquals(industrial.themeAt(fortress[0], fortress[1]), neutral.themeAt(fortress[0], fortress[1]),
                "fortress band theme must not depend on economy");
    }

    // --- DistrictMap (legacy/preview): empty mix is byte-identical; economy shifts the interior. ---

    @Test
    void districtMapEmptyMixIsByteIdentical() {
        for (long seed : new long[] { 1L, 2L, 3L, 7L, 99L }) {
            DistrictMap noArg = new DistrictMap(100, 100, new Random(seed));
            DistrictMap emptyMix = new DistrictMap(100, 100, new Random(seed),
                    EnumSet.noneOf(EconomicFunction.class));
            assertEquals(noArg.districtsX(), emptyMix.districtsX());
            assertEquals(noArg.districtsY(), emptyMix.districtsY());
            for (int dx = 0; dx < noArg.districtsX(); dx++) {
                for (int dy = 0; dy < noArg.districtsY(); dy++) {
                    assertEquals(noArg.themeAtDistrict(dx, dy), emptyMix.themeAtDistrict(dx, dy),
                            "empty-mix DistrictMap diverged from the no-arg path at " + dx + "," + dy
                                    + " (seed " + seed + ") — economy bias must take no rng draw");
                }
            }
        }
    }

    /**
     * Statistical, pinned to fixed seeds so it can't flake: the p=0.55 interior
     * redirect makes a heavy-industry world field <em>comfortably</em> more
     * interior INDUSTRIAL districts than a neutral one. The {@code + 5} margin
     * (expected delta ~20 over these seeds) keeps it a real-shift assertion, not
     * a knife-edge one — a seed-set change would have to halve the effect to
     * surprise it.
     */
    @Test
    void districtMapInteriorLeansIndustrialOnAHeavyIndustryWorld() {
        int emptyIndustrial = 0, heavyIndustrial = 0;
        for (long seed : new long[] { 1L, 2L, 3L, 4L, 5L }) {
            emptyIndustrial += interiorIndustrial(new DistrictMap(100, 100, new Random(seed),
                    EnumSet.noneOf(EconomicFunction.class)));
            heavyIndustrial += interiorIndustrial(new DistrictMap(100, 100, new Random(seed),
                    EnumSet.of(EconomicFunction.HEAVY_INDUSTRY)));
        }
        assertTrue(heavyIndustrial >= emptyIndustrial + 5,
                "heavy-industry world should field comfortably more interior INDUSTRIAL districts ("
                        + heavyIndustrial + ") than a neutral one (" + emptyIndustrial + ")");
    }

    private static MapDistrictTheme dom(EconomicFunction f) {
        return EconomicZoning.dominantTheme(EnumSet.of(f));
    }

    private static int[] firstCellOfBiome(BiomeMap map, BiomeKind want) {
        for (int y = 0; y < map.height(); y++) {
            for (int x = 0; x < map.width(); x++) {
                if (map.biomeAt(x, y) == want) return new int[] { x, y };
            }
        }
        throw new IllegalStateException("no " + want + " cell on this map");
    }

    /** Interior (non-edge) districts labeled INDUSTRIAL — the economy bias only touches the interior. */
    private static int interiorIndustrial(DistrictMap map) {
        int count = 0;
        for (int dx = 1; dx < map.districtsX() - 1; dx++) {
            for (int dy = 1; dy < map.districtsY() - 1; dy++) {
                if (map.themeAtDistrict(dx, dy) == MapDistrictTheme.INDUSTRIAL) count++;
            }
        }
        return count;
    }
}
