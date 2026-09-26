package com.dillon.starsectormarines.battle.nav;

import java.util.List;

/**
 * Immutable physical waypoints and requested-goal identity. The integer
 * projection has exactly the same number of points and is only a compatibility
 * view for occupancy and legacy active-path readers, never a clearance proof.
 */
public final class ContinuousRoute {
    private final int requestedCellX;
    private final int requestedCellY;
    private final float radius;
    private final long topologyRevision;
    private final float[] xs;
    private final float[] ys;
    private final int[] projection;
    private final boolean completed;

    public ContinuousRoute(int requestedCellX, int requestedCellY, float radius,
                           long topologyRevision, List<ClearanceRoutePlanner.Point> points) {
        if (points.isEmpty()) throw new IllegalArgumentException("A route needs an endpoint");
        this.requestedCellX = requestedCellX;
        this.requestedCellY = requestedCellY;
        this.radius = radius;
        this.topologyRevision = topologyRevision;
        this.completed = false;
        xs = new float[points.size()];
        ys = new float[points.size()];
        projection = new int[points.size() * 2];
        for (int i = 0; i < points.size(); i++) {
            xs[i] = points.get(i).x();
            ys[i] = points.get(i).y();
            projection[i * 2] = (int) Math.floor(xs[i]);
            projection[i * 2 + 1] = (int) Math.floor(ys[i]);
        }
    }

    private ContinuousRoute(ContinuousRoute source) {
        requestedCellX = source.requestedCellX;
        requestedCellY = source.requestedCellY;
        radius = source.radius;
        topologyRevision = source.topologyRevision;
        xs = source.xs;
        ys = source.ys;
        projection = source.projection;
        completed = true;
    }

    public int requestedCellX() { return requestedCellX; }
    public int requestedCellY() { return requestedCellY; }
    public float radius() { return radius; }
    public long topologyRevision() { return topologyRevision; }
    public int pointCount() { return xs.length; }
    public float x(int index) { return xs[index]; }
    public float y(int index) { return ys[index]; }
    public float endX() { return xs[xs.length - 1]; }
    public float endY() { return ys[ys.length - 1]; }
    public int[] projection() { return projection.clone(); }
    public boolean completed() { return completed; }
    public ContinuousRoute withCompleted() { return completed ? this : new ContinuousRoute(this); }
}
