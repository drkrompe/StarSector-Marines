package com.dillon.starsectormarines.battle.vehicle;

/**
 * Where an articulated hull's trailing modules are drawn ({@link Articulation}): presentation only, never physics. The
 * hull drives as one rigid {@link BicycleBody}, so planners and route proof keep their bounded curvature; the cab is
 * drawn on that body, and each module behind it trails its hinge as a towed trailer does.
 *
 * <p>Each module's axle rolls and never slides sideways: it moves only along the module's own heading, as far as
 * keeps it its drag length behind its hinge. That one rule covers both directions. Going forward the module trails
 * the hinge on a tractrix and settles into line. Backing up, the cab pushes the hinge back into the module, and a
 * module that already stands off the cab's line is pushed further round, as any articulated train is when nobody
 * counter-steers it, until its hinge rams bottom out at {@link Articulation#MAX_BEND_DEG} and it is shoved along at
 * that angle. The rule is driven by displacement, not time, so the bend depends only on the path the hinge took: the
 * same drive drawn at any frame rate, or collected twice for one frame, bends the same.
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
            float length = layout.trailerLength(s);
            float dirX = forwardX(heading[s]), dirY = forwardY(heading[s]);
            // The axle where it stood, rolled along its own heading until it is its length behind the hinge.
            float wx = nx - (hingeX[h] - dirX * length), wy = ny - (hingeY[h] - dirY * length);
            float wd = wx * dirX + wy * dirY;
            float off = wx * dirY - wy * dirX;
            float roll = wd - (float) Math.sqrt(Math.max(0f, length * length - off * off));
            float bend = wrap((float) Math.toDegrees(Math.atan2(-(wx - dirX * roll), wy - dirY * roll)) - parent);
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

    /**
     * Where the hinge section {@code h} is drawn at {@code localX} across the hull: its front edge, riding the plate
     * ahead, into {@code out[0], out[1]}, and its rear edge, riding the plate behind, into {@code out[2], out[3]}. The
     * section between is stretched or pressed to span them.
     */
    public float[] hingeEdges(float bodyX, float bodyY, int h, float localX, float[] out) {
        toWorld(bodyX, bodyY, h, localX, layout.gapFrontY(h), out);
        float fx = out[0], fy = out[1];
        toWorld(bodyX, bodyY, h + 1, localX, layout.gapRearY(h), out);
        out[2] = out[0];
        out[3] = out[1];
        out[0] = fx;
        out[1] = fy;
        return out;
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
