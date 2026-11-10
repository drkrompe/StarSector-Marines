package com.dillon.starsectormarines.battle.vehicle;

/**
 * Where an articulated hull's trailing modules are drawn ({@link Articulation}): presentation only, never physics. The
 * hull drives as one rigid {@link BicycleBody}, so planners and route proof keep their bounded curvature; the cab is
 * drawn on that body, and each module behind it trails its hinge as a towed trailer does.
 *
 * <p>Going forward, a module's axle is dragged along a tractrix: it moves straight toward where its hinge now is and
 * stays its drag length behind it. The rule is driven by displacement, not time, so the bend depends only on the path
 * the hinge took: the same drive drawn at any frame rate, or collected twice for one frame, bends the same. Backing up,
 * a towed trailer would jackknife; a carrier's hinge rams hold it, so a module eases back into line with the one ahead
 * over about its own drag length of travel. Either way a module never swings past {@link Articulation#MAX_BEND_DEG}.
 *
 * <p>A first update, or a jump too long to be a drive (a spawn, a teleport), lays the train out straight.
 */
public final class TrailerKinematic {

    /** A hinge moving farther than this in one update was placed, not driven: the train is laid out straight. */
    static final float RESET_CELLS = 1.5f;

    private final Articulation layout;
    /** World heading of each segment, degrees, 0 facing +Y; the cab's is the body's. */
    private final float[] heading;
    /** World position of each hinge after the last update. */
    private final float[] hingeX, hingeY;
    private boolean placed;

    public TrailerKinematic(Articulation layout) {
        this.layout = layout;
        this.heading = new float[layout.segments()];
        this.hingeX = new float[layout.hingeY().length];
        this.hingeY = new float[layout.hingeY().length];
    }

    /** Follows the hull to its body's pose: centre {@code x, y} cells, facing {@code facingDeg}. */
    public void update(float x, float y, float facingDeg) {
        float[] hy = layout.hingeY();
        heading[0] = facingDeg;
        // The first hinge rides on the cab, rigid with the body.
        float fx = forwardX(facingDeg), fy = forwardY(facingDeg);
        float nx = x + fx * hy[0], ny = y + fy * hy[0];
        if (!placed || Math.hypot(nx - hingeX[0], ny - hingeY[0]) > RESET_CELLS) {
            straighten(x, y, facingDeg);
            placed = true;
            return;
        }
        for (int s = 1; s < heading.length; s++) {
            int h = s - 1;
            float parent = heading[s - 1];
            float travelX = nx - hingeX[h], travelY = ny - hingeY[h];
            float along = travelX * forwardX(parent) + travelY * forwardY(parent);
            float length = layout.trailerLength(s);
            float bend;
            if (along >= 0f) {
                // The axle where it stood, dragged toward the hinge's new place until it is its length behind it.
                float ax = hingeX[h] - forwardX(heading[s]) * length;
                float ay = hingeY[h] - forwardY(heading[s]) * length;
                float dx = nx - ax, dy = ny - ay;
                float newHeading = (float) Math.toDegrees(Math.atan2(-dx, dy));
                bend = wrap(newHeading - parent);
            } else {
                float travelled = (float) Math.hypot(travelX, travelY);
                bend = wrap(heading[s] - parent) * (float) Math.exp(-travelled / length);
            }
            bend = Math.max(-Articulation.MAX_BEND_DEG, Math.min(Articulation.MAX_BEND_DEG, bend));
            heading[s] = parent + bend;
            hingeX[h] = nx;
            hingeY[h] = ny;
            if (s < heading.length - 1) {
                // The next hinge rides on this segment, its straight-frame distance behind this one's front hinge.
                float back = hy[s] - hy[s - 1];
                nx += forwardX(heading[s]) * back;
                ny += forwardY(heading[s]) * back;
            }
        }
    }

    private void straighten(float x, float y, float facingDeg) {
        float fx = forwardX(facingDeg), fy = forwardY(facingDeg);
        for (int s = 0; s < heading.length; s++) heading[s] = facingDeg;
        for (int h = 0; h < hingeX.length; h++) {
            hingeX[h] = x + fx * layout.hingeY()[h];
            hingeY[h] = y + fy * layout.hingeY()[h];
        }
    }

    /** Whether the train has been laid out by an update yet. */
    public boolean placed() {
        return placed;
    }

    /** Segment {@code s}'s world heading, degrees. */
    public float headingDeg(int s) {
        return heading[s];
    }

    /** How far segment {@code s} swings off the one ahead of it, degrees, signed as headings turn. */
    public float bendDeg(int s) {
        return wrap(heading[s] - heading[s - 1]);
    }

    /**
     * Where the point {@code localX, localY} of the straight hull (cells from its centre, +Y forward) is drawn, on the
     * segment it belongs to, into {@code out} as world x, y. {@code bodyX, bodyY} is the body's centre at the last
     * update.
     */
    public float[] toWorld(float bodyX, float bodyY, float localX, float localY, float[] out) {
        return toWorld(bodyX, bodyY, layout.segmentOf(localY), localX, localY, out);
    }

    /** As {@link #toWorld(float, float, float, float, float[])}, on segment {@code s} whatever the point's place. */
    public float[] toWorld(float bodyX, float bodyY, int s, float localX, float localY, float[] out) {
        float originX = bodyX, originY = bodyY, originLocalY = 0f;
        if (s > 0) {
            originX = hingeX[s - 1];
            originY = hingeY[s - 1];
            originLocalY = layout.hingeY()[s - 1];
        }
        float rad = (float) Math.toRadians(heading[s]);
        float c = (float) Math.cos(rad), sn = (float) Math.sin(rad);
        float ly = localY - originLocalY;
        out[0] = originX + localX * c - ly * sn;
        out[1] = originY + localX * sn + ly * c;
        return out;
    }

    private static float forwardX(float deg) {
        return -(float) Math.sin(Math.toRadians(deg));
    }

    private static float forwardY(float deg) {
        return (float) Math.cos(Math.toRadians(deg));
    }

    private static float wrap(float deg) {
        float d = (deg + 180f) % 360f;
        if (d < 0f) d += 360f;
        return d - 180f;
    }
}
