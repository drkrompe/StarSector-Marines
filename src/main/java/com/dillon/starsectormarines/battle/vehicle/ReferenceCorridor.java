package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.air.AirBody;

/**
 * Advisory coarse route for one ground vehicle: the cell-center polyline from
 * {@link ConvoyPlanner} plus a progress cursor. Owns the carrot-picking and
 * remaining-length bookkeeping that previously lived as a loose
 * {@code waypointIndex} on {@link Vehicle}.
 *
 * <p>The corridor is a <em>suggestion</em> the controller tracks, not a rail
 * the body is snapped to — see {@code navigation-rework/overview.md}. The
 * planner-facing queries ({@link #targetAhead}, {@link #offCorridorDistance})
 * exist so slices 1–2 have a stable seam to plug into; slice-0 motion only
 * uses {@link #carrot}, {@link #remainingLength} and {@link #atEnd}.
 *
 * <p>Slice-0 scope: pure relocation of the {@link PurePursuit} math behind a
 * cursor-owning type. No new path algorithm.
 */
public final class ReferenceCorridor {

    private final float[] xs;
    private final float[] ys;
    /** Index of the next un-consumed waypoint; mirrors the old {@code Vehicle.waypointIndex}. */
    private int cursor;

    public ReferenceCorridor(float[] xs, float[] ys) {
        this(xs, ys, 1);
    }

    public ReferenceCorridor(float[] xs, float[] ys, int cursor) {
        this.xs = xs;
        this.ys = ys;
        this.cursor = cursor;
    }

    public int cursor() { return cursor; }
    public int lastIndex() { return xs.length - 1; }
    public float endX() { return xs[xs.length - 1]; }
    public float endY() { return ys[ys.length - 1]; }

    /**
     * Carrot {@code lookAhead} cells ahead of the pose along the polyline.
     * Advances the cursor (feeds the picker's {@code nextIdx} back in so the
     * scan doesn't restart from the top each tick). Wraps
     * {@link PurePursuit#pick}.
     */
    public PurePursuit.Carrot carrot(float poseX, float poseY, float lookAhead) {
        PurePursuit.Carrot c = PurePursuit.pick(poseX, poseY, xs, ys, cursor, lookAhead);
        cursor = c.nextIdx;
        return c;
    }

    /**
     * Advance the cursor past any waypoint the pose has crossed, without
     * picking a carrot. The controller calls this once per tick so
     * {@link #remainingLength} and {@link #targetAhead} measure from the
     * current segment as the vehicle tracks the (advisory) corridor — otherwise
     * {@code remainingLength} would keep summing already-passed segments and
     * leave the brake taper too lenient. {@code lookAhead 0} makes
     * {@link PurePursuit#pick} return immediately after its crossed-waypoint
     * advance, so we just keep its {@code nextIdx}.
     */
    public void advance(float poseX, float poseY) {
        cursor = PurePursuit.pick(poseX, poseY, xs, ys, cursor, 0f).nextIdx;
    }

    /** Remaining polyline length from the pose to the final waypoint. */
    public float remainingLength(float poseX, float poseY) {
        return PurePursuit.remainingPathLength(poseX, poseY, xs, ys, cursor);
    }

    /**
     * Soft goal a horizon down the corridor: the carrot point {@code horizon}
     * cells ahead, facing along the corridor at that point. This is the target
     * the slice-1 local planner aims at; it does <em>not</em> advance the
     * stored cursor (it's a read-only query, the controller advances the cursor
     * by tracking).
     */
    public Pose targetAhead(float poseX, float poseY, float horizon) {
        PurePursuit.Carrot c = PurePursuit.pick(poseX, poseY, xs, ys, cursor, horizon);
        float dx = c.x - poseX, dy = c.y - poseY;
        // Near the corridor end the carrot pins to the final vertex; once the
        // pose sits on it, pose->carrot is a zero vector and facingToward would
        // collapse to an arbitrary 0deg. Fall back to the final segment's
        // direction so the goal heading stays meaningful (the planner's RS tail
        // and turn-cost heuristic key off it).
        float facing = (dx * dx + dy * dy > 1e-6f)
                ? AirBody.facingToward(dx, dy)
                : finalSegmentHeading();
        return new Pose(c.x, c.y, facing);
    }

    /**
     * Heading along the last non-degenerate corridor segment (final vertex
     * minus the nearest preceding distinct vertex), so a trailing duplicate
     * vertex doesn't yield a zero direction. Defaults to 0deg for a corridor
     * with no extent.
     */
    private float finalSegmentHeading() {
        int last = xs.length - 1;
        for (int i = last - 1; i >= 0; i--) {
            float dx = xs[last] - xs[i], dy = ys[last] - ys[i];
            if (dx * dx + dy * dy > 1e-6f) return AirBody.facingToward(dx, dy);
        }
        return 0f;
    }

    /**
     * Perpendicular distance from the pose to the nearest corridor segment —
     * the deviation monitor slices 2–3 escalate on. Scans the whole polyline so
     * a vehicle that has drifted backward off a segment still measures sanely.
     */
    public float offCorridorDistance(float poseX, float poseY) {
        float best = Float.MAX_VALUE;
        for (int i = 0; i < xs.length - 1; i++) {
            float d = pointToSegment(poseX, poseY, xs[i], ys[i], xs[i + 1], ys[i + 1]);
            if (d < best) best = d;
        }
        return best;
    }

    /** True when the pose is within {@code threshold} cells of the final waypoint. */
    public boolean atEnd(float poseX, float poseY, float threshold) {
        float dx = poseX - endX();
        float dy = poseY - endY();
        return dx * dx + dy * dy < threshold * threshold;
    }

    private static float pointToSegment(float px, float py,
                                        float ax, float ay, float bx, float by) {
        float dx = bx - ax, dy = by - ay;
        float lenSq = dx * dx + dy * dy;
        float t = (lenSq > 1e-9f) ? ((px - ax) * dx + (py - ay) * dy) / lenSq : 0f;
        if (t < 0f) t = 0f; else if (t > 1f) t = 1f;
        float cx = ax + t * dx, cy = ay + t * dy;
        float ex = px - cx, ey = py - cy;
        return (float) Math.sqrt(ex * ex + ey * ey);
    }
}
