package com.dillon.starsectormarines.battle.world.gen.bsp;

import com.dillon.starsectormarines.battle.world.gen.BiomeKind;
import com.dillon.starsectormarines.battle.world.gen.EconomicFunction;
import com.dillon.starsectormarines.battle.world.gen.EconomicZoning;
import com.dillon.starsectormarines.battle.world.gen.MapDistrictTheme;
import com.dillon.starsectormarines.battle.world.gen.TraversalAxis;

import java.util.EnumSet;
import java.util.Random;
import java.util.Set;

/**
 * Large-region zoning layer used for conquest maps. Lays {@link BiomeKind}
 * bands along a {@link TraversalAxis} so a single map spans the full
 * progression "attacker beach → harbor → city → defender fortress" in a
 * deliberate axis-aligned order, rather than scattering themes uniformly via
 * {@link DistrictMap}.
 *
 * <p>Replaces {@link DistrictMap} in conquest mode — when
 * {@link com.dillon.starsectormarines.battle.world.gen.bsp.BspCityGenerator}
 * is given a {@link TraversalAxis}, leaves consult this class for theme
 * lookups instead of the legacy district overlay.
 *
 * <h2>Layout</h2>
 * Biome bands are placed at fixed percentile cutoffs along the axis:
 * <ul>
 *   <li>BEACH:             0%  – 15%</li>
 *   <li>PORT:              15% – 35%</li>
 *   <li>CITY:              35% – 75%</li>
 *   <li>FORTRESS_DISTRICT: 75% – 100%</li>
 * </ul>
 * Each boundary is jittered per-perpendicular-coordinate by a smoothed
 * random offset (amplitude {@link #BOUNDARY_AMPLITUDE_PER_100}% of map
 * dimension, 5-cell rolling smooth), giving wavy biome edges rather than
 * razor-straight strips.
 *
 * <h2>Theme mapping</h2>
 * Each {@link BiomeKind} maps to one primary {@link MapDistrictTheme}:
 * <ul>
 *   <li>BEACH → {@link MapDistrictTheme#COASTAL_BEACH}</li>
 *   <li>PORT → {@link MapDistrictTheme#HARBOR_PORT}</li>
 *   <li>CITY → {@link MapDistrictTheme#MIXED}</li>
 *   <li>FORTRESS_DISTRICT → {@link MapDistrictTheme#MILITARY_FORT}</li>
 *   <li>OUTSKIRTS → {@link MapDistrictTheme#OUTSKIRTS}</li>
 * </ul>
 * Intra-biome variety comes from each theme's weight table — multiple
 * sub-themes per biome (district-style) is a v2 refinement.
 */
public final class BiomeMap {

    // Percentile cutoffs for the 4-biome sequence along the traversal axis.
    private static final float BEACH_TOP_FRAC    = 0.15f;
    private static final float PORT_TOP_FRAC     = 0.35f;
    private static final float CITY_TOP_FRAC     = 0.75f;
    // FORTRESS_DISTRICT consumes the remainder.

    /** Boundary noise amplitude as a fraction of map dim. Tuned so 160-deep maps get ~6-cell wave amplitude. */
    private static final float BOUNDARY_AMPLITUDE_PER_100 = 0.04f;

    /** Rolling window radius for boundary smoothing (cells on each side). */
    private static final int SMOOTH_RADIUS = 5;

    private final int width;
    private final int height;
    private final TraversalAxis axis;
    private final BiomeKind[][] cells;
    /** Theme the CITY band resolves to — economy-flexed (see {@link #themeAt}). {@link MapDistrictTheme#MIXED} with no economic signal. */
    private final MapDistrictTheme cityTheme;

    /** No-economy overload — the CITY band stays {@link MapDistrictTheme#MIXED}, reproducing pre-bridge output. */
    public BiomeMap(int width, int height, TraversalAxis axis, Random rng) {
        this(width, height, axis, rng, EnumSet.noneOf(EconomicFunction.class));
    }

    /**
     * Economy-aware overload. The fixed band sequence (beach → port → city →
     * fortress) is structural and unchanged; the only flex is the CITY band's
     * theme — the urban bulk of the map (35–75% of the axis) — which leans
     * toward the world's {@link EconomicFunction} mix via
     * {@link EconomicZoning#dominantTheme}. An empty set ⇒ {@code MIXED}, i.e.
     * byte-identical to the no-economy overload.
     */
    public BiomeMap(int width, int height, TraversalAxis axis, Random rng, Set<EconomicFunction> functions) {
        this.width = width;
        this.height = height;
        this.axis = axis;
        this.cells = new BiomeKind[width][height];
        MapDistrictTheme econ = EconomicZoning.dominantTheme(functions);
        this.cityTheme = econ != null ? econ : MapDistrictTheme.MIXED;
        if (axis == TraversalAxis.SOUTH_TO_NORTH) {
            layoutAlongY(rng);
        } else {
            layoutAlongX(rng);
        }
    }

    public TraversalAxis axis()   { return axis; }
    public int width()            { return width; }
    public int height()           { return height; }

    public BiomeKind biomeAt(int x, int y) {
        int cx = Math.max(0, Math.min(width - 1, x));
        int cy = Math.max(0, Math.min(height - 1, y));
        return cells[cx][cy];
    }

    public MapDistrictTheme themeAt(int x, int y) {
        BiomeKind biome = biomeAt(x, y);
        // CITY is the one economy-flexed band; every other band's theme is structural.
        if (biome == BiomeKind.CITY) return cityTheme;
        return themeFor(biome);
    }

    /**
     * The structural (economy-independent) theme for a biome band. CITY's
     * baseline is {@link MapDistrictTheme#MIXED}; the live lookup the labeler
     * uses is the instance {@link #themeAt}, which substitutes the economy-flexed
     * {@link #cityTheme} for CITY.
     */
    public static MapDistrictTheme themeFor(BiomeKind biome) {
        switch (biome) {
            case BEACH:             return MapDistrictTheme.COASTAL_BEACH;
            case PORT:              return MapDistrictTheme.HARBOR_PORT;
            case CITY:              return MapDistrictTheme.MIXED;
            case FORTRESS_DISTRICT: return MapDistrictTheme.MILITARY_FORT;
            case OUTSKIRTS:         return MapDistrictTheme.OUTSKIRTS;
        }
        throw new IllegalStateException("Unhandled biome " + biome);
    }

    /**
     * Y-axis traversal (attacker enters from south, fortress at north). Biome
     * bands run left-right; their boundaries vary per-column along x.
     */
    private void layoutAlongY(Random rng) {
        int nominalBeachTop = Math.round(height * BEACH_TOP_FRAC);
        int nominalPortTop  = Math.round(height * PORT_TOP_FRAC);
        int nominalCityTop  = Math.round(height * CITY_TOP_FRAC);
        int amplitude       = Math.max(2, Math.round(height * BOUNDARY_AMPLITUDE_PER_100));
        int[] beachTop = noisyBoundary(width, nominalBeachTop, amplitude, rng);
        int[] portTop  = noisyBoundary(width, nominalPortTop,  amplitude, rng);
        int[] cityTop  = noisyBoundary(width, nominalCityTop,  amplitude, rng);
        for (int x = 0; x < width; x++) {
            int bt = clamp(beachTop[x], 1, height - 1);
            int pt = clamp(portTop[x],  bt + 1, height - 1);
            int ct = clamp(cityTop[x],  pt + 1, height - 1);
            for (int y = 0; y < height; y++) {
                if (y < bt)      cells[x][y] = BiomeKind.BEACH;
                else if (y < pt) cells[x][y] = BiomeKind.PORT;
                else if (y < ct) cells[x][y] = BiomeKind.CITY;
                else             cells[x][y] = BiomeKind.FORTRESS_DISTRICT;
            }
        }
    }

    /**
     * X-axis traversal (attacker enters from west, fortress at east). Biome
     * bands run top-bottom; their boundaries vary per-row along y.
     */
    private void layoutAlongX(Random rng) {
        int nominalBeachRight = Math.round(width * BEACH_TOP_FRAC);
        int nominalPortRight  = Math.round(width * PORT_TOP_FRAC);
        int nominalCityRight  = Math.round(width * CITY_TOP_FRAC);
        int amplitude         = Math.max(2, Math.round(width * BOUNDARY_AMPLITUDE_PER_100));
        int[] beachRight = noisyBoundary(height, nominalBeachRight, amplitude, rng);
        int[] portRight  = noisyBoundary(height, nominalPortRight,  amplitude, rng);
        int[] cityRight  = noisyBoundary(height, nominalCityRight,  amplitude, rng);
        for (int y = 0; y < height; y++) {
            int br = clamp(beachRight[y], 1, width - 1);
            int pr = clamp(portRight[y],  br + 1, width - 1);
            int cr = clamp(cityRight[y],  pr + 1, width - 1);
            for (int x = 0; x < width; x++) {
                if (x < br)      cells[x][y] = BiomeKind.BEACH;
                else if (x < pr) cells[x][y] = BiomeKind.PORT;
                else if (x < cr) cells[x][y] = BiomeKind.CITY;
                else             cells[x][y] = BiomeKind.FORTRESS_DISTRICT;
            }
        }
    }

    /**
     * Produce a per-coordinate boundary array of length {@code len}, centered
     * on {@code nominal} with ±{@code amplitude} cells of smoothed noise. The
     * raw uniform noise is averaged over a {@link #SMOOTH_RADIUS}-cell rolling
     * window to remove single-cell spikes; the result reads as gentle wavy
     * curves between biomes rather than a saw-tooth.
     */
    private static int[] noisyBoundary(int len, int nominal, int amplitude, Random rng) {
        float[] raw = new float[len];
        for (int i = 0; i < len; i++) {
            raw[i] = (rng.nextFloat() - 0.5f) * 2f * amplitude;
        }
        int[] out = new int[len];
        for (int i = 0; i < len; i++) {
            int lo = Math.max(0, i - SMOOTH_RADIUS);
            int hi = Math.min(len - 1, i + SMOOTH_RADIUS);
            float sum = 0f;
            for (int j = lo; j <= hi; j++) sum += raw[j];
            out[i] = nominal + Math.round(sum / (hi - lo + 1));
        }
        return out;
    }

    private static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}
