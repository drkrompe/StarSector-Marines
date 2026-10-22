package com.dillon.starsectormarines.battle.world.gen.rural;

import com.dillon.starsectormarines.battle.world.gen.rural.RuralLayout.Plot;
import com.dillon.starsectormarines.battle.world.gen.rural.RuralLayout.Use;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * The fields a colony works on its other sides (`mapgen-nouns.md`, Rural
 * country): plots on the open ground round the town, fronting its edge and the
 * roads that leave it, thinner the farther from the town they lie.
 *
 * <p>The surveyed country along the landing's edge is only the country the
 * company crosses. A town farms whatever open, flat ground lies around it, on
 * every side and between its places, so fields are laid wherever such ground
 * fronts the town or one of its roads. Ground that is not open (a hill, a
 * building, a wall) breaks them, and so does distance: past {@link #REACH}
 * cells from the town nobody walks out to work, and nearer than that a plot is
 * laid less often the farther out its frontage is.
 *
 * <p>Pure geometry over masks; draws only from its own seeded random.
 */
public final class TownFields {
    private TownFields() {}

    /** Cells from the town past which nobody farms. */
    static final int REACH = 48;
    /** Shortest side of a field. */
    static final int MIN_SIDE = 14;
    /** A field's frontage and depth are drawn up to these. */
    static final int MAX_FRONTAGE = 36, MAX_DEPTH = 28;
    /** Wild ground kept between two fields, so they read as separate plots. */
    static final int GAP = 3;
    /** Verge between a field and the road or town edge it fronts. */
    static final int VERGE = 1;

    private static final int[][] STEPS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    /**
     * Plans the fields over {@code open} ground. {@code town} is the ground the
     * town's places stand on, {@code road} the roads, both of which a field may
     * front; neither is ever a field.
     */
    public static List<Plot> plan(int width, int height, boolean[][] open, boolean[][] town,
                                  boolean[][] road, long seed) {
        int[][] fromTown = distance(width, height, town);
        // A rect is good when every cell is open and within reach: a summed table of the bad ones.
        int[][] bad = new int[width + 1][height + 1];
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                boolean b = !open[x][y] || fromTown[x][y] > REACH;
                bad[x + 1][y + 1] = (b ? 1 : 0) + bad[x][y + 1] + bad[x + 1][y] - bad[x][y];
            }
        }
        // Frontage: open cells beside the town or a road, with the way out from it.
        List<int[]> frontage = new ArrayList<>();
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                if (!open[x][y] || fromTown[x][y] > REACH) continue;
                for (int[] step : STEPS) {
                    int fx = x - step[0], fy = y - step[1];
                    if (fx < 0 || fy < 0 || fx >= width || fy >= height) continue;
                    if (town[fx][fy] || road[fx][fy]) {
                        frontage.add(new int[]{x, y, step[0], step[1]});
                        break;
                    }
                }
            }
        }
        Random rng = new Random(seed ^ 0x544f574eL);
        Collections.shuffle(frontage, rng);

        Use[] crops = {Use.ROW_CROP, Use.GRAIN, Use.PASTURE, Use.FALLOW};
        int[] weights = {40, 25, 20, 15};
        List<Plot> fields = new ArrayList<>();
        for (int[] front : frontage) {
            int x = front[0], y = front[1], ox = front[2], oy = front[3];
            double chance = 1 - fromTown[x][y] / (double) REACH;
            if (rng.nextDouble() >= chance) continue;
            int along = MIN_SIDE + rng.nextInt(MAX_FRONTAGE - MIN_SIDE + 1);
            int deep = MIN_SIDE + rng.nextInt(MAX_DEPTH - MIN_SIDE + 1);
            int[] rect = null;
            for (; along >= MIN_SIDE && deep >= MIN_SIDE && rect == null; along -= 2, deep -= 2) {
                rect = fit(x, y, ox, oy, along, deep, width, height, bad, fields);
            }
            if (rect == null) continue;
            int pick = rng.nextInt(100);
            Use use = crops[crops.length - 1];
            for (int i = 0, sum = 0; i < crops.length; i++) {
                sum += weights[i];
                if (pick < sum) { use = crops[i]; break; }
            }
            boolean rowsAlongX = rect[2] - rect[0] >= rect[3] - rect[1];
            boolean hedged = use == Use.PASTURE || rng.nextInt(100) < RuralLayout.HEDGED_PERCENT;
            fields.add(new Plot(rect[0], rect[1], rect[2], rect[3], use, rowsAlongX, hedged));
        }
        return List.copyOf(fields);
    }

    /**
     * The field fronting at {@code (x, y)}, its near side a verge out from the
     * frontage and running {@code along} cells one way from there, {@code deep}
     * cells out; {@code null} when it does not fit.
     */
    private static int[] fit(int x, int y, int ox, int oy, int along, int deep, int width, int height,
                             int[][] bad, List<Plot> fields) {
        int nx = x + ox * VERGE, ny = y + oy * VERGE;
        int fx = nx + ox * (deep - 1), fy = ny + oy * (deep - 1);
        // Along runs across the way out: x when the way out is along y, and y otherwise.
        int ax = oy != 0 ? along - 1 : 0, ay = ox != 0 ? along - 1 : 0;
        int x0 = Math.min(nx, fx), x1 = Math.max(nx, fx) + ax;
        int y0 = Math.min(ny, fy), y1 = Math.max(ny, fy) + ay;
        if (x0 < 0 || y0 < 0 || x1 >= width || y1 >= height) return null;
        int count = bad[x1 + 1][y1 + 1] - bad[x0][y1 + 1] - bad[x1 + 1][y0] + bad[x0][y0];
        if (count > 0) return null;
        for (Plot field : fields) {
            if (x0 <= field.x1() + GAP && x1 >= field.x0() - GAP
                    && y0 <= field.y1() + GAP && y1 >= field.y0() - GAP) return null;
        }
        return new int[]{x0, y0, x1, y1};
    }

    /** Four-way steps from the nearest {@code from} cell, over every cell. */
    static int[][] distance(int width, int height, boolean[][] from) {
        int[][] out = new int[width][height];
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                out[x][y] = from[x][y] ? 0 : Integer.MAX_VALUE;
                if (from[x][y]) queue.add(new int[]{x, y});
            }
        }
        while (!queue.isEmpty()) {
            int[] c = queue.poll();
            for (int[] step : STEPS) {
                int x = c[0] + step[0], y = c[1] + step[1];
                if (x < 0 || y < 0 || x >= width || y >= height || out[x][y] != Integer.MAX_VALUE) continue;
                out[x][y] = out[c[0]][c[1]] + 1;
                queue.add(new int[]{x, y});
            }
        }
        return out;
    }
}
