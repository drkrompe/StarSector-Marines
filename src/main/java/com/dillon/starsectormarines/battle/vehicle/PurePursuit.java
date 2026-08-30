package com.dillon.starsectormarines.battle.vehicle;

/**
 * Carrot-picker for path-following ground vehicles. Standalone so the carrot
 * selection is shared by every {@link GroundBody} kinematic model: each model
 * differs only in <em>how</em> it steers toward the carrot, not in how the
 * carrot is picked from the waypoint polyline.
 *
 * <p>Algorithm: starting from {@code startIdx}, advance the cursor past any
 * waypoint the body has already crossed (direction of travel through the
 * waypoint onto body-relative-to-waypoint has positive dot). Then walk forward
 * from the body along the polyline, accumulating segment lengths until
 * {@code lookAhead} cells have been covered — the point at that distance is
 * the carrot. If the path runs out first, the carrot is pinned to the final
 * vertex.
 *
 * <p><b>{@code startIdx 0} is legal, and means the first waypoint has not been
 * consumed yet.</b> The direction of travel through an interior waypoint is the
 * segment leading into it; the first waypoint has none, so it uses the segment
 * leading out of it instead. Measuring waypoint 0 against the <em>body's</em>
 * approach to it — which this once did — is a vector dotted with its own
 * negation and therefore negative for any body not standing exactly on the
 * waypoint, so a cursor left at zero could never advance: the carrot walk kept
 * re-entering the path at a point already behind the body, spent the look-ahead
 * getting back there, and converged onto the body's own nose. Callers worked
 * around it by starting at 1, which is still the right value when waypoint 0 is
 * known to be the cell the body is standing on — but it is now a statement
 * about the path rather than a workaround.
 *
 * <p>Why pure pursuit fixes the orbit-around-waypoint bug: the carrot is
 * always on the <em>path</em>, not at a fixed point. As the body approaches,
 * the carrot keeps sliding forward along the next segment, so the body never
 * tries to circle a stationary target.
 */
public final class PurePursuit {

    private PurePursuit() {}

    /** Carrot point + progress cursor + the segment containing the carrot + an end-of-path flag. */
    public static final class Carrot {
        public final float x;
        public final float y;
        public final int nextIdx;
        /** Index of the waypoint at the end of the path segment containing this carrot. */
        public final int segmentEndIdx;
        public final boolean atEnd;
        Carrot(float x, float y, int nextIdx, int segmentEndIdx, boolean atEnd) {
            this.x = x;
            this.y = y;
            this.nextIdx = nextIdx;
            this.segmentEndIdx = segmentEndIdx;
            this.atEnd = atEnd;
        }
    }

    /**
     * Pick a carrot at {@code lookAhead} cells ahead of the body along the
     * polyline ({@code xs}, {@code ys}), starting the search at index
     * {@code startIdx}. The returned {@link Carrot#nextIdx} should be fed back
     * as {@code startIdx} on the next tick so the picker doesn't rescan the
     * whole path.
     */
    public static Carrot pick(float bodyX, float bodyY,
                              float[] xs, float[] ys,
                              int startIdx,
                              float lookAhead) {
        int n = xs.length;
        if (n == 0) return new Carrot(bodyX, bodyY, 0, 0, true);
        if (n == 1) return new Carrot(xs[0], ys[0], 0, 0, true);

        // Advance startIdx past any waypoint the body has crossed (body is
        // past the perpendicular through that waypoint, measured against the
        // direction of travel through it).
        int idx = Math.max(0, Math.min(startIdx, n - 1));
        while (idx < n - 1) {
            float bx = xs[idx];
            float by = ys[idx];
            float segDx = (idx == 0) ? xs[1] - bx : bx - xs[idx - 1];
            float segDy = (idx == 0) ? ys[1] - by : by - ys[idx - 1];
            float toBodyDx = bodyX - bx;
            float toBodyDy = bodyY - by;
            if (segDx * toBodyDx + segDy * toBodyDy >= 0f) {
                idx++;
            } else {
                break;
            }
        }

        // Walk forward from body along the remaining polyline, accumulating
        // until we cover lookAhead cells. The carrot is on the segment where
        // accumulation overshoots.
        float remaining = lookAhead;
        float cx = bodyX, cy = bodyY;
        int cursor = idx;
        while (cursor < n) {
            float tx = xs[cursor], ty = ys[cursor];
            float dx = tx - cx, dy = ty - cy;
            float d = (float) Math.sqrt(dx * dx + dy * dy);
            if (d >= remaining) {
                float t = (d > 1e-6f) ? (remaining / d) : 0f;
                return new Carrot(cx + t * dx, cy + t * dy, idx, cursor, false);
            }
            remaining -= d;
            cx = tx; cy = ty;
            cursor++;
        }
        // Exhausted the path — pin carrot to the final waypoint.
        return new Carrot(xs[n - 1], ys[n - 1], idx, n - 1, true);
    }

    /**
     * {@link #pick(float, float, float[], float[], int, float)} over a flat
     * {@code int[]} cell path ({@code (path[2i], path[2i+1])} = cell {@code i},
     * the {@code GridPathfinder} output shape), with each waypoint at the cell
     * center ({@code +0.5}). Same algorithm, no per-tick {@code float[]}
     * conversion — this is the infantry mover's carrot source.
     */
    public static Carrot pick(float bodyX, float bodyY,
                              int[] flatCells,
                              int startIdx,
                              float lookAhead) {
        int n = flatCells.length / 2;
        if (n == 0) return new Carrot(bodyX, bodyY, 0, 0, true);
        if (n == 1) return new Carrot(flatCells[0] + 0.5f, flatCells[1] + 0.5f, 0, 0, true);

        int idx = Math.max(0, Math.min(startIdx, n - 1));
        while (idx < n - 1) {
            float bx = flatCells[idx * 2] + 0.5f;
            float by = flatCells[idx * 2 + 1] + 0.5f;
            float segDx = (idx == 0) ? flatCells[2] + 0.5f - bx : bx - (flatCells[(idx - 1) * 2] + 0.5f);
            float segDy = (idx == 0) ? flatCells[3] + 0.5f - by : by - (flatCells[(idx - 1) * 2 + 1] + 0.5f);
            float toBodyDx = bodyX - bx;
            float toBodyDy = bodyY - by;
            if (segDx * toBodyDx + segDy * toBodyDy >= 0f) {
                idx++;
            } else {
                break;
            }
        }

        float remaining = lookAhead;
        float cx = bodyX, cy = bodyY;
        int cursor = idx;
        while (cursor < n) {
            float tx = flatCells[cursor * 2] + 0.5f, ty = flatCells[cursor * 2 + 1] + 0.5f;
            float dx = tx - cx, dy = ty - cy;
            float d = (float) Math.sqrt(dx * dx + dy * dy);
            if (d >= remaining) {
                float t = (d > 1e-6f) ? (remaining / d) : 0f;
                return new Carrot(cx + t * dx, cy + t * dy, idx, cursor, false);
            }
            remaining -= d;
            cx = tx; cy = ty;
            cursor++;
        }
        return new Carrot(flatCells[(n - 1) * 2] + 0.5f, flatCells[(n - 1) * 2 + 1] + 0.5f,
                idx, n - 1, true);
    }

    /**
     * Sum of remaining segment lengths from the body's position to the final
     * waypoint, walking through {@code xs[startIdx]} first. Used by callers
     * to size the brake taper into the last waypoint — target speed gets
     * clamped to {@code sqrt(2·brake·remaining)} so the vehicle comes to a
     * clean stop at the LZ regardless of intermediate corner kinematics.
     */
    public static float remainingPathLength(float bodyX, float bodyY,
                                            float[] xs, float[] ys,
                                            int startIdx) {
        int n = xs.length;
        if (n == 0) return 0f;
        int idx = Math.max(0, Math.min(startIdx, n - 1));
        float total = 0f;
        float cx = bodyX, cy = bodyY;
        for (int i = idx; i < n; i++) {
            float dx = xs[i] - cx, dy = ys[i] - cy;
            total += (float) Math.sqrt(dx * dx + dy * dy);
            cx = xs[i]; cy = ys[i];
        }
        return total;
    }
}
