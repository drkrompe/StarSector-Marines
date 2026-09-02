package com.dillon.starsectormarines.battle.vision;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;

/**
 * Recursive shadowcasting (8-octant) for field-of-view computation on a
 * {@link NavigationGrid}. Pure static utility — no instance state.
 *
 * <p>Supports an {@code airLosRadius}: wall cells within that distance of the
 * source are treated as transparent so airborne observers see over nearby
 * rooftops.
 */
public final class Shadowcast {

    private Shadowcast() {}

    /**
     * Casts vision from {@code (sx, sy)} out to {@code range} cells, writing
     * the flat grid index of every visible cell into {@code out} starting at
     * {@code outOffset}. Returns the number of cells written.
     *
     * @param grid          the navigation grid (provides bounds + opacity)
     * @param sx            source cell x
     * @param sy            source cell y
     * @param range         maximum vision range in cells
     * @param airLosRadius  walls within this distance of the source are transparent (0 = none)
     * @param out           receives {@code grid.index(x, y)} of each visible cell
     * @param outOffset     write position in {@code out}
     * @return count of cells written (caller advances offset by this amount)
     */
    public static int castFrom(NavigationGrid grid, int sx, int sy, int range,
                                float airLosRadius, int[] out, int outOffset) {
        return castFrom(grid, sx, sy, range, airLosRadius, out, outOffset, false);
    }

    /**
     * Counterfactual presentation cast through transient smoke while retaining
     * every permanent wall and the observer's ordinary air-clearance rule.
     */
    public static int castFromIgnoringTransientOpacity(
            NavigationGrid grid, int sx, int sy, int range,
            float airLosRadius, int[] out, int outOffset) {
        return castFrom(grid, sx, sy, range, airLosRadius, out, outOffset, true);
    }

    private static int castFrom(NavigationGrid grid, int sx, int sy, int range,
                                float airLosRadius, int[] out, int outOffset,
                                boolean ignoreTransientOpacity) {
        int w = grid.getWidth();
        int h = grid.getHeight();
        float airR2 = airLosRadius * airLosRadius;
        int count = 0;

        if (sx >= 0 && sx < w && sy >= 0 && sy < h) {
            out[outOffset + count++] = grid.index(sx, sy);
        }

        for (int octant = 0; octant < 8; octant++) {
            count = scanOctant(grid, sx, sy, w, h, range, airR2,
                    octant, 1, 0.0f, 1.0f,
                    out, outOffset, count, ignoreTransientOpacity);
        }
        return count;
    }

    private static int scanOctant(NavigationGrid grid, int sx, int sy,
                                   int w, int h, int range, float airR2,
                                   int octant, int row,
                                   float startSlope, float endSlope,
                                   int[] out, int outOffset, int count,
                                   boolean ignoreTransientOpacity) {
        if (startSlope >= endSlope) return count;

        for (int r = row; r <= range; r++) {
            boolean blocked = false;
            float newStart = startSlope;

            int minCol = (int) Math.floor(r * startSlope + 0.5f);
            int maxCol = (int) Math.ceil(r * endSlope - 0.5f);

            for (int col = minCol; col <= maxCol; col++) {
                int dx = 0, dy = 0;
                switch (octant) {
                    case 0: dx =  col; dy = -r;   break; // NNE
                    case 1: dx =  r;   dy = -col; break; // ENE
                    case 2: dx =  r;   dy =  col; break; // ESE
                    case 3: dx =  col; dy =  r;   break; // SSE
                    case 4: dx = -col; dy =  r;   break; // SSW
                    case 5: dx = -r;   dy =  col; break; // WSW
                    case 6: dx = -r;   dy = -col; break; // WNW
                    case 7: dx = -col; dy = -r;   break; // NNW
                }

                int cx = sx + dx;
                int cy = sy + dy;

                if (cx < 0 || cx >= w || cy < 0 || cy >= h) {
                    blocked = false;
                    continue;
                }

                if (dx * dx + dy * dy > range * range) {
                    blocked = false;
                    continue;
                }

                float leftSlope  = (col - 0.5f) / r;
                float rightSlope = (col + 0.5f) / r;

                boolean opaque = isOpaque(grid, cx, cy, sx, sy, airR2,
                        ignoreTransientOpacity);

                if (!opaque) {
                    out[outOffset + count++] = grid.index(cx, cy);
                }

                if (blocked) {
                    if (opaque) {
                        newStart = rightSlope;
                    } else {
                        blocked = false;
                        startSlope = newStart;
                    }
                } else if (opaque) {
                    blocked = true;
                    count = scanOctant(grid, sx, sy, w, h, range, airR2,
                            octant, r + 1, startSlope, leftSlope,
                            out, outOffset, count, ignoreTransientOpacity);
                    newStart = rightSlope;
                }
            }

            if (blocked) break;
        }
        return count;
    }

    private static boolean isOpaque(NavigationGrid grid, int cx, int cy,
                                     int sx, int sy, float airR2,
                                     boolean ignoreTransientOpacity) {
        int idx = grid.index(cx, cy);
        if (!ignoreTransientOpacity && grid.hasTransientOpacityAt(idx)) return true;
        if (!grid.blocksStructuralLineOfSightAt(idx)) return false;
        if (airR2 <= 0f) return true;
        float dx = cx - sx;
        float dy = cy - sy;
        return (dx * dx + dy * dy) > airR2;
    }

    /**
     * Returns the maximum number of cells a single shadowcast can produce for
     * a given range. Use to pre-size the output array.
     *
     * <p>This is the bounding square of the disc rather than the disc's own
     * area, because a cast writes more cells than the disc holds. The eight
     * octants each scan their boundary rays inclusively, so the four axes and
     * the four diagonals are written twice — the diagonals out to
     * {@code range / sqrt(2)}, where they leave the disc. The area formula
     * accounted for the axes and not the diagonals, which left it about a
     * hundred and seventy short at the fog service's own 60-cell ceiling.
     * Nothing hit it while every long-sighted observer stood behind a wall; an
     * emplacement in open country sees the whole disc, and the overflow is an
     * {@code ArrayIndexOutOfBoundsException} thousands of ticks into a battle.
     * The square is 30% more memory at the ceiling — 58KB against 46KB, once
     * per fog service — and cannot be exceeded however the rays are counted.
     */
    public static int maxCells(int range) {
        int span = 2 * range + 1;
        return span * span + 1;
    }
}
