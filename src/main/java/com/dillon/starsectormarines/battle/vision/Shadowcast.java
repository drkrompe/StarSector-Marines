package com.dillon.starsectormarines.battle.vision;

import com.dillon.starsectormarines.battle.nav.ElevationMap;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;

/**
 * Recursive shadowcasting (8-octant) for field-of-view computation on a
 * {@link NavigationGrid}. Pure static utility — no instance state.
 *
 * <p>Supports an {@code airLosRadius}: wall cells within that distance of the
 * source are treated as transparent so airborne observers see over nearby
 * rooftops.
 *
 * <p>On a map with {@linkplain ElevationMap elevation} the cast sees from the
 * observer's tier: down and across freely, up only as far as the skyline rule
 * allows (the face, then the rim). An observer with an air radius is airborne
 * and sees every tier; a ground observer that reads through walls (a sensor
 * sweep, {@link #castFromGround}) reads past higher ground only within its
 * read radius. See {@code elevation-nouns.md}.
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
        return castFrom(grid, sx, sy, range, airLosRadius, out, outOffset, false, airLosRadius > 0f);
    }

    /**
     * A cast from a body standing on the ground that reads through walls
     * within {@code readRadius} (a mission spotter): the same wall rule as
     * {@link #castFrom}, but the eye stays at the source's own tier, so higher
     * ground beyond the read radius is seen as any ground observer sees it.
     */
    public static int castFromGround(NavigationGrid grid, int sx, int sy, int range,
                                     float readRadius, int[] out, int outOffset) {
        return castFrom(grid, sx, sy, range, readRadius, out, outOffset, false, false);
    }

    /**
     * Counterfactual presentation cast through transient smoke while retaining
     * every permanent wall and the observer's ordinary air-clearance rule.
     */
    public static int castFromIgnoringTransientOpacity(
            NavigationGrid grid, int sx, int sy, int range,
            float airLosRadius, int[] out, int outOffset) {
        return castFrom(grid, sx, sy, range, airLosRadius, out, outOffset, true, airLosRadius > 0f);
    }

    private static int castFrom(NavigationGrid grid, int sx, int sy, int range,
                                float airLosRadius, int[] out, int outOffset,
                                boolean ignoreTransientOpacity, boolean airborne) {
        int w = grid.getWidth();
        int h = grid.getHeight();
        float airR2 = airLosRadius * airLosRadius;
        ElevationMap elevation = grid.elevation();
        int eye = elevation == null ? 0
                : airborne ? ElevationMap.MAX_TIER : grid.eyeTierAt(sx, sy);
        int count = 0;

        if (sx >= 0 && sx < w && sy >= 0 && sy < h) {
            out[outOffset + count++] = grid.index(sx, sy);
        }

        for (int octant = 0; octant < 8; octant++) {
            count = scanOctant(grid, elevation, eye, sx, sy, w, h, range, airR2,
                    octant, 1, 0.0f, 1.0f,
                    out, outOffset, count, ignoreTransientOpacity);
        }
        return count;
    }

    private static int scanOctant(NavigationGrid grid, ElevationMap elevation, int eye,
                                   int sx, int sy,
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

                // A cell off the map or outside the disc says nothing about what
                // is in shadow, so the blocked run carries across it. Clearing
                // the flag here instead was the defect: a row whose tail falls
                // outside the disc - which is most rows, since the column bound
                // comes from the slope and not from the range - ended unblocked
                // however solid its visible part was, so the octant never broke
                // and re-scanned, from its original start slope, the wedge the
                // recursion had already covered. That is vision through a wall
                // and, compounding row on row, thousands of duplicate writes
                // into a buffer sized for a disc.
                if (cx < 0 || cx >= w || cy < 0 || cy >= h) continue;

                if (dx * dx + dy * dy > range * range) continue;

                float leftSlope  = (col - 0.5f) / r;
                float rightSlope = (col + 0.5f) / r;

                int idx = grid.index(cx, cy);
                boolean opaque;
                boolean surface;
                if (elevation != null && elevation.above(idx, eye) && dx * dx + dy * dy <= airR2) {
                    // Within a ground observer's read radius higher ground is
                    // read through, as a wall there is.
                    opaque = false;
                    surface = true;
                } else if (elevation != null && elevation.above(idx, eye)) {
                    // Higher ground: a face is the skyline the eye looks past
                    // (a wall under the strict rule); any top cell stops the
                    // cast, and is itself seen only if it is the rim.
                    opaque = !elevation.isFaceAt(idx) || elevation.stopsSight(idx, eye, false);
                    surface = elevation.visibleFrom(idx, eye);
                } else {
                    opaque = isOpaque(grid, cx, cy, sx, sy, airR2,
                            ignoreTransientOpacity);
                    // The first solid surface is visible; only the cells beyond
                    // it are shadowed. Smoke still conceals occupants of its own cell.
                    surface = !opaque || (grid.blocksStructuralLineOfSightAt(idx)
                            && (ignoreTransientOpacity || !grid.hasTransientOpacityAt(idx)));
                    if (opaque && elevation != null && elevation.isFaceAt(idx)) {
                        // A face at or below the eye stands under it: transparent.
                        opaque = !ignoreTransientOpacity && grid.hasTransientOpacityAt(idx);
                        surface = true;
                    }
                }
                if (surface) {
                    out[outOffset + count++] = idx;
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
                    count = scanOctant(grid, elevation, eye, sx, sy, w, h, range, airR2,
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
