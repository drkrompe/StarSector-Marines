package com.dillon.starsectormarines.battle.world.gen.bsp;

import com.dillon.starsectormarines.battle.world.gen.EconomicFunction;
import com.dillon.starsectormarines.battle.world.gen.EconomicZoning;
import com.dillon.starsectormarines.battle.world.gen.MapDistrictTheme;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Random;
import java.util.Set;

/**
 * Coarse district overlay used by {@link BspCityGenerator} to bias leaf
 * labeling toward thematically clustered regions. The map is divided into
 * a {@link #districtsX}×{@link #districtsY} grid where each cell holds a
 * {@link MapDistrictTheme}; BSP leaves look up their district by center
 * and roll their {@link com.dillon.starsectormarines.battle.world.gen.BlockKind}
 * from that theme's weight table.
 *
 * <p>Assignment is a two-pass process:
 * <ol>
 *   <li>Each district independently picks a theme with edge-bias rules —
 *       map-edge districts skew {@link MapDistrictTheme#WATERFRONT} /
 *       {@link MapDistrictTheme#OUTSKIRTS}, interior districts skew
 *       {@link MapDistrictTheme#CIVIC} / {@link MapDistrictTheme#RESIDENTIAL}.</li>
 *   <li>A smoothing pass nudges each district toward a randomly-picked
 *       neighbor's theme with {@link #SMOOTH_PROBABILITY} chance. Encourages
 *       same-theme clusters of 2-3 districts so a residential pocket reads
 *       as a neighborhood rather than a one-off building cluster.</li>
 * </ol>
 *
 * <p>{@link MapDistrictTheme#WATERFRONT} is constrained to map-edge
 * districts in both passes — the smoothing step refuses to propagate it
 * into the interior.
 *
 * <p><b>A map with places on it is asked per place.</b> The map-wide roll is
 * right for a map that is one city and wrong for one that is four places, so
 * a precinct may {@linkplain #overlay lay its own themes} over the blocks its
 * claim touches. {@link #themeAt} then answers from the layer wherever that
 * precinct owns the cell and from the map-wide roll everywhere else, which on
 * a precinct map is hinterland. Nothing here rolls a layer — that is
 * {@code PrecinctZoning}'s, from the precinct's character — and a map nobody
 * overlays behaves exactly as it did.
 */
public final class DistrictMap {

    /** Target district edge length in nav cells. 60-100-cell maps yield 3-5 districts per axis at this size. */
    private static final int TARGET_DISTRICT_SIZE = 20;
    /** Probability that a district adopts a neighbor's theme during the smoothing pass. Higher = bigger clusters. */
    private static final float SMOOTH_PROBABILITY = 0.45f;
    /** Probability an interior district is redirected to the world's economy-aligned theme. 0 when no economic signal. */
    private static final float ECON_BIAS_PROBABILITY = 0.55f;

    private final int districtsX;
    private final int districtsY;
    private final int cellW;
    private final int cellH;
    private final MapDistrictTheme[][] themes;
    /** The economy-aligned theme interior districts lean toward, or null when the world carries no economic signal (the pre-bridge path). */
    private final MapDistrictTheme econTheme;
    /**
     * Which precinct owns each cell, or null until something is overlaid. One
     * array shared by every layer: the owner index at a cell is the layer that
     * answers for it.
     */
    private int[][] owner;
    /**
     * One block grid per owner index, null for an owner that laid nothing.
     * Within a grid only the blocks that owner touches are non-null.
     */
    private MapDistrictTheme[][][] layers;

    /** No-economy overload — pure geographic theme rolls, reproducing pre-bridge output. */
    public DistrictMap(int gridW, int gridH, Random rng) {
        this(gridW, gridH, rng, EnumSet.noneOf(EconomicFunction.class));
    }

    /**
     * Economy-aware overload. Interior districts are nudged toward the world's
     * dominant {@link EconomicFunction} theme (via {@link EconomicZoning}) with
     * {@link #ECON_BIAS_PROBABILITY}; edge districts keep their geographic
     * character (coast / outskirts fade). With an empty function set there is no
     * economy theme and <em>no extra rng draw is taken</em>, so the output is
     * byte-identical to the no-economy overload.
     */
    public DistrictMap(int gridW, int gridH, Random rng, Set<EconomicFunction> functions) {
        this.districtsX = Math.max(1, Math.round((float) gridW / TARGET_DISTRICT_SIZE));
        this.districtsY = Math.max(1, Math.round((float) gridH / TARGET_DISTRICT_SIZE));
        this.cellW = gridW / districtsX;
        this.cellH = gridH / districtsY;
        this.themes = new MapDistrictTheme[districtsX][districtsY];
        this.econTheme = EconomicZoning.dominantTheme(functions);
        assignThemes(rng);
    }

    public int districtsX() { return districtsX; }
    public int districtsY() { return districtsY; }
    public int districtCellWidth()  { return cellW; }
    public int districtCellHeight() { return cellH; }

    /** The block column a nav-grid x falls in; out-of-range clamps to the edge. */
    public int districtX(int x) {
        return Math.max(0, Math.min(districtsX - 1, x / cellW));
    }

    /** The block row a nav-grid y falls in; out-of-range clamps to the edge. */
    public int districtY(int y) {
        return Math.max(0, Math.min(districtsY - 1, y / cellH));
    }

    /** Whether a block lies on the map's border, where the coast is allowed to be. */
    public boolean isEdgeDistrict(int dx, int dy) {
        return isEdge(dx, dy);
    }

    /**
     * Look up the theme at a nav-grid cell. Out-of-range coords clamp to the
     * edge district. A cell some precinct has overlaid answers from that
     * precinct's layer; every other cell answers from the map-wide roll.
     */
    public MapDistrictTheme themeAt(int x, int y) {
        int dx = districtX(x);
        int dy = districtY(y);
        MapDistrictTheme[][] layer = layerAt(x, y);
        if (layer != null && layer[dx][dy] != null) return layer[dx][dy];
        return themes[dx][dy];
    }

    /** The map-wide roll at a block, ignoring any layer. Null off the grid. */
    public MapDistrictTheme themeAtDistrict(int dx, int dy) {
        if (dx < 0 || dx >= districtsX || dy < 0 || dy >= districtsY) return null;
        return themes[dx][dy];
    }

    /**
     * Force the theme of the district containing nav-grid cell ({@code navX},
     * {@code navY}). Used by the orchestrator to seed CIVIC at the trunk-road
     * intersection (the natural city center). No-op if the resolved district
     * is already WATERFRONT — preserving the "coast stays coastal" invariant
     * even when an intersection lands on the map edge.
     *
     * <p>A forced block is forced on every layer that holds it as well as on
     * the map-wide roll, because the thing being forced is a block of the map
     * — a port pocket is a port whichever place's parcels happen to lie in it —
     * and a force that landed on one layer would be invisible to a leaf whose
     * cell another precinct owns.
     */
    public void forceThemeAt(int navX, int navY, MapDistrictTheme theme) {
        int dx = districtX(navX);
        int dy = districtY(navY);
        if (themes[dx][dy] != MapDistrictTheme.WATERFRONT) themes[dx][dy] = theme;
        if (layers == null) return;
        for (MapDistrictTheme[][] layer : layers) {
            if (layer == null || layer[dx][dy] == null) continue;
            if (layer[dx][dy] != MapDistrictTheme.WATERFRONT) layer[dx][dy] = theme;
        }
    }

    /**
     * Lays one owner's own themes over the blocks it touches.
     *
     * @param owner       which owner index holds each nav-grid cell; the same
     *                    array for every call, since it is what keys the layers
     * @param who         the owner this layer belongs to
     * @param blockThemes a {@link #districtsX()} x {@link #districtsY()} grid,
     *                    null where the owner touches nothing
     */
    public void overlay(int[][] owner, int who, MapDistrictTheme[][] blockThemes) {
        if (owner == null || who < 0) {
            throw new IllegalArgumentException("a layer belongs to an owner on an ownership map");
        }
        if (this.owner != null && this.owner != owner) {
            throw new IllegalArgumentException("one ownership map keys every layer; "
                    + "a second one would leave a cell claimed by two");
        }
        if (blockThemes.length != districtsX || blockThemes[0].length != districtsY) {
            throw new IllegalArgumentException("a layer is drawn on this map's own "
                    + districtsX + "x" + districtsY + " blocks");
        }
        this.owner = owner;
        if (layers == null) layers = new MapDistrictTheme[who + 1][][];
        if (layers.length <= who) layers = Arrays.copyOf(layers, who + 1);
        layers[who] = blockThemes;
    }

    private MapDistrictTheme[][] layerAt(int x, int y) {
        if (owner == null || layers == null) return null;
        int cx = Math.max(0, Math.min(owner.length - 1, x));
        int cy = Math.max(0, Math.min(owner[cx].length - 1, y));
        int who = owner[cx][cy];
        if (who < 0 || who >= layers.length) return null;
        return layers[who];
    }

    private void assignThemes(Random rng) {
        for (int dx = 0; dx < districtsX; dx++) {
            for (int dy = 0; dy < districtsY; dy++) {
                boolean edge = isEdge(dx, dy);
                themes[dx][dy] = pickInitialTheme(edge, rng);
            }
        }
        smoothPass(rng);
    }

    private boolean isEdge(int dx, int dy) {
        return dx == 0 || dy == 0 || dx == districtsX - 1 || dy == districtsY - 1;
    }

    /**
     * First-pass theme roll. Edge districts skew toward WATERFRONT / OUTSKIRTS
     * (matches real city geography — water and edges fade into wasteland);
     * interior districts skew toward CIVIC / RESIDENTIAL (city center is dense).
     * INDUSTRIAL and MIXED appear in both regimes.
     *
     * <p>When the world carries an economic signal ({@link #econTheme} non-null),
     * interior districts are then redirected to that theme with
     * {@link #ECON_BIAS_PROBABILITY} — the economy's read concentrated in the
     * city core. With no signal the geographic roll stands and no extra rng draw
     * is consumed, preserving pre-bridge output exactly.
     */
    private MapDistrictTheme pickInitialTheme(boolean edge, Random rng) {
        MapDistrictTheme base = rollGeographicTheme(edge, rng);
        if (econTheme == null || edge) return base;
        return rng.nextFloat() < ECON_BIAS_PROBABILITY ? econTheme : base;
    }

    private MapDistrictTheme rollGeographicTheme(boolean edge, Random rng) {
        float r = rng.nextFloat();
        if (edge) {
            if (r < 0.25f) return MapDistrictTheme.WATERFRONT;
            if (r < 0.50f) return MapDistrictTheme.OUTSKIRTS;
            if (r < 0.70f) return MapDistrictTheme.RESIDENTIAL;
            if (r < 0.85f) return MapDistrictTheme.INDUSTRIAL;
            return MapDistrictTheme.MIXED;
        }
        if (r < 0.30f) return MapDistrictTheme.RESIDENTIAL;
        if (r < 0.50f) return MapDistrictTheme.CIVIC;
        if (r < 0.65f) return MapDistrictTheme.MIXED;
        if (r < 0.85f) return MapDistrictTheme.INDUSTRIAL;
        return MapDistrictTheme.OUTSKIRTS;
    }

    /**
     * Second pass — each district may adopt a random neighbor's theme,
     * encouraging clusters. {@code WATERFRONT} doesn't propagate into the
     * interior; interior districts that try to adopt a WATERFRONT neighbor
     * keep their existing theme instead.
     */
    private void smoothPass(Random rng) {
        for (int dx = 0; dx < districtsX; dx++) {
            for (int dy = 0; dy < districtsY; dy++) {
                if (rng.nextFloat() >= SMOOTH_PROBABILITY) continue;
                MapDistrictTheme neighbor = randomNeighborTheme(dx, dy, rng);
                if (neighbor == null) continue;
                if (neighbor == MapDistrictTheme.WATERFRONT && !isEdge(dx, dy)) continue;
                themes[dx][dy] = neighbor;
            }
        }
    }

    private MapDistrictTheme randomNeighborTheme(int dx, int dy, Random rng) {
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        // Shuffle so we don't bias the smoothing direction.
        for (int i = dirs.length - 1; i > 0; i--) {
            int j = rng.nextInt(i + 1);
            int[] tmp = dirs[i]; dirs[i] = dirs[j]; dirs[j] = tmp;
        }
        for (int[] d : dirs) {
            int nx = dx + d[0];
            int ny = dy + d[1];
            if (nx >= 0 && nx < districtsX && ny >= 0 && ny < districtsY) {
                return themes[nx][ny];
            }
        }
        return null;
    }
}
