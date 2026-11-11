package com.dillon.starsectormarines.battle.world.gen.raid;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntPredicate;

/**
 * A refinery's tank farm ({@code RefiningWorks}): the tanks the product is stored in before the tankers take it,
 * standing in bunds, on the ground the site held. GL-free; {@link RefineryGround} stamps what it returns.
 *
 * <p>The farm is laid on the largest clear rectangle of its ground. Along the side nearest the haul road runs the
 * <b>loading lane</b>, open ground the tankers stand on, with the loading rack at its near end. The rest is a grid of
 * <b>bunds</b>: a low concrete wall round {@link #TANKS_PER_BUND} tanks, so a burst tank's product stays in its bund,
 * with a {@link #WALKWAY} between bunds for the fire crews and the pipe runs. Each tank keeps {@link #FIRE_GAP} from
 * its neighbour and its wall. Each bund's wall is broken by a stair gap in the middle of each long side, so a figure
 * can get in among the tanks, and the bund wall is cover to anyone behind it. The tanks are the biggest that fit:
 * {@link #BIG} bunds first, {@link #SMALL} ones on the ground they leave.
 */
public final class TankFarm {

    /** A big tank's diameter, in cells: a crude or product tank. Art scales whole: a divisor of 512. UNTUNED. */
    public static final int BIG = 16;
    /** A small tank's diameter: a day or blending tank, on the ground the big bunds leave. */
    public static final int SMALL = 8;
    /** Tanks in a bund, in a row along the farm. */
    static final int TANKS_PER_BUND = 2;
    /** Cells a tank keeps from its neighbour and from its bund's wall. UNTUNED. */
    static final int FIRE_GAP = 3;
    /** Cells between two bunds' walls. UNTUNED. */
    static final int WALKWAY = 4;
    /** Width of the loading lane along the haul-road side. UNTUNED. */
    static final int LANE = 7;
    /** Length of the loading rack's bay at the lane's near end. UNTUNED. */
    static final int BAY = 32;
    /** Cells of a stair gap in a bund wall: one barrier's length. */
    static final int GAP = 2;

    /** A tank: its corner and its diameter. */
    public record Tank(int x, int y, int size) { }

    /** A two-cell piece of bund wall from {@code (x, y)}, lying along x when {@code alongX}. */
    public record Wall(int x, int y, boolean alongX) { }

    /** A bund: its wall's outer rectangle (inclusive), its tanks, its wall pieces. */
    public record Bund(int x0, int y0, int x1, int y1, List<Tank> tanks, List<Wall> walls) { }

    /**
     * The laid farm: its bunds; the loading lane (inclusive rectangle) and the bay at its near end where the rack
     * stands; which way the lane faces, toward the haul road ({@code laneDy} of -1 or 1); and the clear rectangle the
     * whole farm stands on.
     */
    public record Plan(List<Bund> bunds, int[] lane, int[] bay, int laneDy, int[] extent) {

        /**
         * The made ground of the farm: across the lane and the bunds, along the map from the first bund or the bay to
         * the last; the clear ground beyond them stays the planet's.
         */
        public int[] paved() {
            int x0 = bay[0], x1 = bay[2], y0 = lane[1], y1 = lane[3];
            for (Bund b : bunds) {
                x0 = Math.min(x0, b.x0());
                x1 = Math.max(x1, b.x1());
                y0 = Math.min(y0, b.y0());
                y1 = Math.max(y1, b.y1());
            }
            return new int[]{x0, y0, x1, y1};
        }
    }

    private TankFarm() { }

    /**
     * The farm on the clear ground of the inclusive box {@code x0..x1, y0..y1}, or {@code null} when not even one
     * bund of small tanks fits.
     *
     * @param free  cells the farm may take, row-major over a map {@code w} wide
     * @param roadY the row the haul road runs along; the lane is laid on the farm's side nearest it
     * @param nearX the column the town lies toward along the lane; the bay is laid at that end
     */
    public static Plan plan(int w, int x0, int y0, int x1, int y1, IntPredicate free, int roadY, int nearX) {
        return plan(w, x0, y0, x1, y1, free, roadY, nearX, LANE);
    }

    /** As {@link #plan(int, int, int, int, int, IntPredicate, int, int)}, its loading lane {@code laneWidth} wide. */
    public static Plan plan(int w, int x0, int y0, int x1, int y1, IntPredicate free, int roadY, int nearX,
                            int laneWidth) {
        int[] r = largestClear(w, x0, y0, x1, y1, free);
        if (r == null) return null;
        int laneDy = Math.abs(roadY - r[1]) <= Math.abs(roadY - r[3]) ? -1 : 1;
        int[] lane = laneDy < 0 ? new int[]{r[0], r[1], r[2], r[1] + laneWidth - 1}
                : new int[]{r[0], r[3] - laneWidth + 1, r[2], r[3]};
        int[] bundsAt = laneDy < 0 ? new int[]{r[0], r[1] + laneWidth, r[2], r[3]}
                : new int[]{r[0], r[1], r[2], r[3] - laneWidth};
        if (bundsAt[3] < bundsAt[1]) return null;
        List<Bund> bunds = filled(bundsAt);
        // The big bunds stand against the lane, the leftover ground and the small bunds beyond them.
        if (laneDy > 0) bunds = flipped(bunds, bundsAt);
        if (bunds.isEmpty()) return null;
        boolean nearWest = Math.abs(nearX - lane[0]) <= Math.abs(nearX - lane[2]);
        int bayLen = Math.min(BAY, lane[2] - lane[0] + 1);
        int[] bay = nearWest ? new int[]{lane[0], lane[1], lane[0] + bayLen - 1, lane[3]}
                : new int[]{lane[2] - bayLen + 1, lane[1], lane[2], lane[3]};
        return new Plan(bunds, lane, bay, laneDy, r);
    }

    /** The bunds reflected top to bottom within the box, each rebuilt so its tanks and gaps stand as a bund's do. */
    static List<Bund> flipped(List<Bund> bunds, int[] box) {
        List<Bund> out = new ArrayList<>();
        for (Bund b : bunds) {
            int size = b.tanks().get(0).size();
            out.add(bund(b.x0(), box[1] + box[3] - b.y1(), b.x1(), box[1] + box[3] - b.y0(), size));
        }
        return out;
    }

    /** The outer width of a bund of tanks this size: walls, gaps and tanks, rounded up to whole barriers. */
    static int bundWidth(int size) {
        return even(TANKS_PER_BUND * size + (TANKS_PER_BUND + 1) * FIRE_GAP + 2);
    }

    /** The outer height of a bund of tanks this size. */
    static int bundHeight(int size) {
        return even(size + 2 * FIRE_GAP + 2);
    }

    private static int even(int n) { return n + (n & 1); }

    /**
     * The bunds on the box: a grid of {@link #BIG} tanks' bunds from its west edge, then {@link #SMALL} tanks' bunds
     * on the ground that grid leaves, east of it and beyond its rows.
     */
    static List<Bund> filled(int[] box) {
        int bw = bundWidth(BIG), bh = bundHeight(BIG);
        int boxW = box[2] - box[0] + 1, boxH = box[3] - box[1] + 1;
        int cols = (boxW + WALKWAY) / (bw + WALKWAY), rows = (boxH + WALKWAY) / (bh + WALKWAY);
        if (cols < 1 || rows < 1) return grid(box, SMALL, true);
        int usedX = box[0] + cols * (bw + WALKWAY), usedY = box[1] + rows * (bh + WALKWAY);
        List<Bund> out = grid(new int[]{box[0], box[1], usedX - WALKWAY - 1, usedY - WALKWAY - 1}, BIG, false);
        if (usedX <= box[2]) out.addAll(grid(new int[]{usedX, box[1], box[2], usedY - WALKWAY - 1}, SMALL, false));
        if (usedY <= box[3]) out.addAll(grid(new int[]{box[0], usedY, box[2], box[3]}, SMALL, false));
        return out;
    }

    /** As many bunds of tanks this size as fit the box in a grid, from its corner or centred in it. */
    static List<Bund> grid(int[] box, int size, boolean centred) {
        int bw = bundWidth(size), bh = bundHeight(size);
        int boxW = box[2] - box[0] + 1, boxH = box[3] - box[1] + 1;
        int cols = (boxW + WALKWAY) / (bw + WALKWAY), rows = (boxH + WALKWAY) / (bh + WALKWAY);
        List<Bund> out = new ArrayList<>();
        if (cols < 1 || rows < 1) return out;
        int ox = box[0] + (centred ? (boxW - (cols * bw + (cols - 1) * WALKWAY)) / 2 : 0);
        int oy = box[1] + (centred ? (boxH - (rows * bh + (rows - 1) * WALKWAY)) / 2 : 0);
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                int bx = ox + col * (bw + WALKWAY), by = oy + row * (bh + WALKWAY);
                out.add(bund(bx, by, bx + bw - 1, by + bh - 1, size));
            }
        }
        return out;
    }

    /** One bund in the rectangle: its tanks in a row along x, centred, and its wall with a stair gap per long side. */
    static Bund bund(int x0, int y0, int x1, int y1, int size) {
        List<Tank> tanks = new ArrayList<>();
        int inner = x1 - x0 - 1;
        int spare = inner - (TANKS_PER_BUND * size + (TANKS_PER_BUND - 1) * FIRE_GAP);
        int tx = x0 + 1 + spare / 2;
        int ty = y0 + 1 + (y1 - y0 - 1 - size) / 2;
        for (int k = 0; k < TANKS_PER_BUND; k++) tanks.add(new Tank(tx + k * (size + FIRE_GAP), ty, size));
        List<Wall> walls = new ArrayList<>();
        int mid = x0 + (x1 - x0 + 1) / 2 - 1;
        mid -= (mid - x0) & 1;
        for (int x = x0; x < x1; x += 2) {
            if (x == mid) continue;
            walls.add(new Wall(x, y0, true));
            walls.add(new Wall(x, y1, true));
        }
        for (int y = y0 + 1; y < y1 - 1; y += 2) {
            walls.add(new Wall(x0, y, false));
            walls.add(new Wall(x1, y, false));
        }
        return new Bund(x0, y0, x1, y1, tanks, walls);
    }

    /**
     * The largest rectangle of {@code free} cells inside the inclusive box, as {@code {x0, y0, x1, y1}}, or
     * {@code null} when the box has none. The histogram method, row by row.
     */
    public static int[] largestClear(int w, int x0, int y0, int x1, int y1, IntPredicate free) {
        int cols = x1 - x0 + 1;
        if (cols <= 0 || y1 < y0) return null;
        int[] height = new int[cols];
        int[] best = null;
        long bestArea = 0;
        for (int y = y0; y <= y1; y++) {
            for (int i = 0; i < cols; i++) height[i] = free.test(y * w + x0 + i) ? height[i] + 1 : 0;
            int[] stack = new int[cols + 1];
            int top = 0;
            for (int i = 0; i <= cols; i++) {
                int h = i == cols ? 0 : height[i];
                while (top > 0 && height[stack[top - 1]] >= h) {
                    int hh = height[stack[--top]];
                    int left = top == 0 ? 0 : stack[top - 1] + 1;
                    long area = (long) hh * (i - left);
                    if (hh > 0 && area > bestArea) {
                        bestArea = area;
                        best = new int[]{x0 + left, y - hh + 1, x0 + i - 1, y};
                    }
                }
                stack[top++] = i;
            }
        }
        return best;
    }
}
