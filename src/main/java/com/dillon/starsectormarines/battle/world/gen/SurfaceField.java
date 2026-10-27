package com.dillon.starsectormarines.battle.world.gen;

import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;

/**
 * Which {@link SurfacePalette} the wild ground of each cell is made of: the world's own surface, except inside a
 * {@link Basin}, whose floor is another. What every wild-ground pass consults instead of
 * {@link TargetProfile#surface} once a map may hold two palettes.
 *
 * <p><b>The edge.</b> The basin's ellipse is ragged by a slow noise, and across a band at its edge floor and rim
 * alternate in patches, the rim growing from the outside in, so the change reads as country thinning out rather than a
 * drawn line. {@link #rimShare} is how far into that band a cell is, for passes that thin rather than switch (woods).
 *
 * <p>Pure arithmetic on the map's size and seed: no random draws, so a map with no basin is the map it always was.
 */
public final class SurfaceField {

    /** Width of the band in basin distance, the ragged edge's swing, and the noise scales in cells. */
    static final float BAND = 0.2f, RAGGED = 0.12f, EDGE_CELLS = 30f, PATCH_CELLS = 9f;
    /** How far in from the edge band, in basin distance, the floor's woods begin to thin. */
    public static final float WOODS_THINNING = 0.25f;
    private static final long PATCH_SALT = 0x5A5A5A5AL;

    private final SurfacePalette world;
    private final Basin basin;
    private final int width, height;
    private final long seed;

    public SurfaceField(SurfacePalette world, Basin basin, int width, int height, long seed) {
        this.world = world == null ? SurfacePalette.ROCK : world;
        this.basin = basin == null ? Basin.NONE : basin;
        this.width = width;
        this.height = height;
        this.seed = seed;
    }

    /** The field of a generation run, from its market profile. */
    public static SurfaceField of(GenContext c) {
        TargetProfile profile = c.get(BspKeys.MARKET_PROFILE);
        return profile == null
                ? new SurfaceField(SurfacePalette.ROCK, Basin.NONE, c.width, c.height, c.seed)
                : new SurfaceField(profile.surface(), profile.basin(), c.width, c.height, c.seed);
    }

    /** The world's own surface: the rim, and the whole map when there is no basin. */
    public SurfacePalette world() {
        return world;
    }

    /** The basin floor's palette: the world's own surface when there is no basin. */
    public SurfacePalette floor() {
        return uniform() ? world : basin.floor();
    }

    /** Whether every cell is the world's own surface. */
    public boolean uniform() {
        return !basin.present() || basin.floor() == world;
    }

    /** What this cell's wild ground is made of. */
    public SurfacePalette at(int x, int y) {
        if (uniform()) return world;
        return rim(x, y) ? world : basin.floor();
    }

    /** Whether this cell is rim: the world's own ground. Every cell is, with no basin. */
    public boolean rim(int x, int y) {
        if (uniform()) return true;
        float share = rimShare(x, y);
        if (share <= 0f) return false;
        if (share >= 1f) return true;
        return noise(x / PATCH_CELLS, y / PATCH_CELLS, seed ^ PATCH_SALT) < share;
    }

    /**
     * How far into the rim this cell is: 0 on the floor, 1 past the edge band, between across it. 1 everywhere with
     * no basin.
     */
    public float rimShare(int x, int y) {
        if (uniform()) return 1f;
        return clamp((distance(x, y) - (1f - BAND / 2f)) / BAND);
    }

    /**
     * How much of the woods a cell keeps: all of them with no basin, and in one all of them deep in the floor, thinning
     * from {@link #WOODS_THINNING} in from the edge band to none at its outer side, and none on the rim.
     */
    public float woods(int x, int y) {
        if (uniform()) return 1f;
        if (rim(x, y)) return 0f;
        return clamp((1f + BAND / 2f - distance(x, y)) / (WOODS_THINNING + BAND));
    }

    /**
     * Whether a brook here runs dry: on a basin's rim, on a world that bears no open water. Never with no basin, where
     * a brook is the mission's as authored whatever the world.
     */
    public boolean dryRim(int x, int y) {
        return !uniform() && !world.bearsOpenWater() && rim(x, y);
    }

    /** The cell's distance from the basin's middle, 1 on its ellipse, ragged by a slow noise. */
    private float distance(int x, int y) {
        float nx = (x + 0.5f - width / 2f) / (basin.reachX() * width / 2f);
        float ny = (y + 0.5f - height / 2f) / (basin.reachY() * height / 2f);
        return (float) Math.sqrt(nx * nx + ny * ny) + RAGGED * (2f * noise(x / EDGE_CELLS, y / EDGE_CELLS, seed) - 1f);
    }

    private static float clamp(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    /** Smooth value noise in [0, 1]. */
    static float noise(float x, float y, long seed) {
        int x0 = (int) Math.floor(x), y0 = (int) Math.floor(y);
        float fx = x - x0, fy = y - y0;
        fx = fx * fx * (3 - 2 * fx);
        fy = fy * fy * (3 - 2 * fy);
        float a = lattice(x0, y0, seed), b = lattice(x0 + 1, y0, seed);
        float c = lattice(x0, y0 + 1, seed), d = lattice(x0 + 1, y0 + 1, seed);
        float top = a + (b - a) * fx, bottom = c + (d - c) * fx;
        return top + (bottom - top) * fy;
    }

    /** A hash of a lattice point in [0, 1). */
    static float lattice(int x, int y, long seed) {
        long z = seed + x * 0x9E3779B97F4A7C15L + y * 0xC2B2AE3D27D4EB4FL;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        z ^= z >>> 31;
        return (z >>> 40) / (float) (1L << 24);
    }
}
