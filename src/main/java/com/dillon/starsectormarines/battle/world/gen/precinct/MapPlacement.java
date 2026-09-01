package com.dillon.starsectormarines.battle.world.gen.precinct;

import java.util.Random;

/**
 * Roughly where on a map something goes, stated so a mission can say it.
 *
 * <p>A precinct is seeded at a cell, which is the wrong thing for mission design
 * to have to name: a scenario wants "the objective is in the north-east and the
 * marines come from the south-west", and the cell that means depends on how big
 * the map is. A placement is a fraction of the map, so the same brief lays out
 * at any scale.
 *
 * <p>Deliberately coarse. This is a region to land somewhere inside, not a
 * position — two missions asking for the north-east should not produce the same
 * map, and a placement that pinned a cell would be an authored map wearing a
 * generator's clothes.
 *
 * <p><b>Y is up.</b> {@link #NORTH} is the high-y edge, matching the convention
 * the rest of the project uses for facings and doodad edges.
 */
public record MapPlacement(float x0, float y0, float x1, float y1) {

    public MapPlacement {
        if (x0 < 0 || y0 < 0 || x1 > 1 || y1 > 1 || x0 >= x1 || y0 >= y1) {
            throw new IllegalArgumentException(
                    "a placement is a fraction of the map: " + x0 + "," + y0 + ".." + x1 + "," + y1);
        }
    }

    /** Thirds, so the middle band of each axis is a real place rather than a seam. */
    private static final float LO = 1f / 3f;
    private static final float HI = 2f / 3f;

    public static final MapPlacement SOUTH_WEST = new MapPlacement(0f, 0f, LO, LO);
    public static final MapPlacement SOUTH = new MapPlacement(LO, 0f, HI, LO);
    public static final MapPlacement SOUTH_EAST = new MapPlacement(HI, 0f, 1f, LO);
    public static final MapPlacement WEST = new MapPlacement(0f, LO, LO, HI);
    public static final MapPlacement CENTRE = new MapPlacement(LO, LO, HI, HI);
    public static final MapPlacement EAST = new MapPlacement(HI, LO, 1f, HI);
    public static final MapPlacement NORTH_WEST = new MapPlacement(0f, HI, LO, 1f);
    public static final MapPlacement NORTH = new MapPlacement(LO, HI, HI, 1f);
    public static final MapPlacement NORTH_EAST = new MapPlacement(HI, HI, 1f, 1f);

    /** No preference — the whole map, which is what a derived plan uses. */
    public static final MapPlacement ANYWHERE = new MapPlacement(0f, 0f, 1f, 1f);

    /**
     * A cell inside this placement, kept far enough from the border that
     * whatever lands here has room to grow both ways.
     *
     * @param margin cells to stay clear of the map edge
     */
    public int[] resolve(int width, int height, int margin, Random rng) {
        int x = span(x0, x1, width, margin, rng);
        int y = span(y0, y1, height, margin, rng);
        return new int[]{x, y};
    }

    /**
     * The cells this placement covers, as an inclusive rect. What a spawn zone
     * needs, where a precinct only needs a point to grow from.
     */
    public int[] bounds(int width, int height) {
        return new int[]{
                Math.round(x0 * (width - 1)), Math.round(y0 * (height - 1)),
                Math.round(x1 * (width - 1)), Math.round(y1 * (height - 1))};
    }

    /** The middle of the placement, for measuring how far apart two of them are. */
    public int[] centre(int width, int height) {
        return new int[]{Math.round((x0 + x1) / 2f * (width - 1)),
                Math.round((y0 + y1) / 2f * (height - 1))};
    }

    /**
     * One axis of the resolve.
     *
     * <p>The margin is applied inside the placement rather than to the map, so a
     * request for the north-east corner still lands in the north-east: clamping
     * to the map afterwards would quietly walk a corner placement inward until
     * it was somewhere else.
     */
    private static int span(float lo, float hi, int size, int margin, Random rng) {
        int from = Math.round(lo * (size - 1));
        int to = Math.round(hi * (size - 1));
        from = Math.max(from, margin);
        to = Math.min(to, size - 1 - margin);
        if (to <= from) return Math.max(0, Math.min(size - 1, (from + to) / 2));
        return from + rng.nextInt(to - from + 1);
    }
}
