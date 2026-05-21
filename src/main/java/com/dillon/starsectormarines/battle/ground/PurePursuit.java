package com.dillon.starsectormarines.battle.ground;

/**
 * Carrot-picker for path-following ground vehicles. Standalone so the carrot
 * selection is shared by every {@link GroundBody} kinematic model: each model
 * differs only in <em>how</em> it steers toward the carrot, not in how the
 * carrot is picked from the waypoint polyline.
 *
 * <p>Algorithm: starting from {@code startIdx}, advance the cursor past any
 * waypoint the body has already crossed (segment direction onto
 * body-relative-to-waypoint has positive dot). Then walk forward from the
 * body along the polyline, accumulating segment lengths until
 * {@code lookAhead} cells have been covered — the point at that distance is
 * the carrot. If the path runs out first, the carrot is pinned to the final
 * vertex.
 *
 * <p>Why pure pursuit fixes the orbit-around-waypoint bug: the carrot is
 * always on the <em>path</em>, not at a fixed point. As the body approaches,
 * the carrot keeps sliding forward along the next segment, so the body never
 * tries to circle a stationary target.
 */
public final class PurePursuit {

    private PurePursuit() {}

    /** Carrot point + the index of the next un-consumed waypoint + an end-of-path flag. */
    public static final class Carrot {
        public final float x;
        public final float y;
        public final int nextIdx;
        public final boolean atEnd;
        Carrot(float x, float y, int nextIdx, boolean atEnd) {
            this.x = x; this.y = y; this.nextIdx = nextIdx; this.atEnd = atEnd;
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
        if (n == 0) return new Carrot(bodyX, bodyY, 0, true);
        if (n == 1) return new Carrot(xs[0], ys[0], 0, true);

        // Advance startIdx past any waypoint the body has crossed (body is
        // past the perpendicular through that waypoint, measured against the
        // segment leading into it).
        int idx = Math.max(0, Math.min(startIdx, n - 1));
        while (idx < n - 1) {
            float ax = (idx == 0) ? bodyX : xs[idx - 1];
            float ay = (idx == 0) ? bodyY : ys[idx - 1];
            float bx = xs[idx];
            float by = ys[idx];
            float segDx = bx - ax;
            float segDy = by - ay;
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
                return new Carrot(cx + t * dx, cy + t * dy, idx, false);
            }
            remaining -= d;
            cx = tx; cy = ty;
            cursor++;
        }
        // Exhausted the path — pin carrot to the final waypoint.
        return new Carrot(xs[n - 1], ys[n - 1], idx, true);
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
