package com.dillon.starsectormarines.battle.world.gen.bsp;

import com.dillon.starsectormarines.battle.world.gen.MapDistrictTheme;

import java.util.Random;

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
 */
public final class DistrictMap {

    /** Target district edge length in nav cells. 60-100-cell maps yield 3-5 districts per axis at this size. */
    private static final int TARGET_DISTRICT_SIZE = 20;
    /** Probability that a district adopts a neighbor's theme during the smoothing pass. Higher = bigger clusters. */
    private static final float SMOOTH_PROBABILITY = 0.45f;

    private final int districtsX;
    private final int districtsY;
    private final int cellW;
    private final int cellH;
    private final MapDistrictTheme[][] themes;

    public DistrictMap(int gridW, int gridH, Random rng) {
        this.districtsX = Math.max(1, Math.round((float) gridW / TARGET_DISTRICT_SIZE));
        this.districtsY = Math.max(1, Math.round((float) gridH / TARGET_DISTRICT_SIZE));
        this.cellW = gridW / districtsX;
        this.cellH = gridH / districtsY;
        this.themes = new MapDistrictTheme[districtsX][districtsY];
        assignThemes(rng);
    }

    public int districtsX() { return districtsX; }
    public int districtsY() { return districtsY; }
    public int districtCellWidth()  { return cellW; }
    public int districtCellHeight() { return cellH; }

    /** Look up the theme at a nav-grid cell. Out-of-range coords clamp to the edge district. */
    public MapDistrictTheme themeAt(int x, int y) {
        int dx = Math.max(0, Math.min(districtsX - 1, x / cellW));
        int dy = Math.max(0, Math.min(districtsY - 1, y / cellH));
        return themes[dx][dy];
    }

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
     */
    public void forceThemeAt(int navX, int navY, MapDistrictTheme theme) {
        int dx = Math.max(0, Math.min(districtsX - 1, navX / cellW));
        int dy = Math.max(0, Math.min(districtsY - 1, navY / cellH));
        if (themes[dx][dy] == MapDistrictTheme.WATERFRONT) return;
        themes[dx][dy] = theme;
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
     */
    private MapDistrictTheme pickInitialTheme(boolean edge, Random rng) {
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
