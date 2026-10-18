package com.dillon.starsectormarines.ops.battleview;

import java.util.List;

/**
 * A follow camera for a fight between a few large bodies (warship duels and fleet actions): it frames the bounding
 * box of the bodies it is handed, plus a margin that keeps the space between them, and the rounds crossing it, in
 * frame.
 *
 * <p><b>Smoothing is damped, with hysteresis, and never at the cost of a body.</b> The framing it eases toward moves
 * only when the bodies leave a dead zone around the current target (pan) or when the box needs a clearly closer or
 * wider view (zoom), so a hull yawing in place does not shake the frame. Centre and width then follow that target on
 * a critically damped spring, which starts and stops gently and never overshoots. A spring lags, so after it the
 * view is widened or shifted just enough to hold every body: the frame may move abruptly when a hull outruns it, but
 * it never loses one.
 *
 * <p>The view is held between a closest width ({@code minAcross}), so hulls read large, and the width that shows the
 * whole map, so it never opens onto empty space beyond the edges; and it is kept inside the map. The centre is
 * snapped to whole pixels at the zoom the review renderer will draw it at (whole pixels per cell), so a slow pan does
 * not shimmer.
 *
 * <p>It is a pure function of the bodies it has been handed and the time between calls: no wall clock, no
 * randomness. Recorders call {@link #update} once per recorded frame.
 */
public final class DuelCamera {

    /** One framed body: its centre and radius, in cells. */
    public record Subject(float x, float y, float radius) {}

    /** Where to point a {@link BattleReviewFrameRenderer#refocus} close view. */
    public record View(float centerX, float centerY, float cellsAcross) {
        /** The view's height in cells for a viewport of {@code aspect} (width over height). */
        public float cellsDown(float aspect) {
            return cellsAcross / aspect;
        }
    }

    /** Share of the box's larger side added on each side: the room the rounds and the next manoeuvre need. */
    static final float PAD_SHARE = 0.12f;
    /** Cells added on each side whatever the box, so two hulls nose to nose are not cropped at the frame's edge. */
    static final float PAD_CELLS = 8f;
    /** The zoom target widens once the padded box outgrows it by this share (the padding absorbs less). */
    static final float ZOOM_OUT_BAND = 0.06f;
    /** It closes in only when it could be this much narrower. */
    static final float ZOOM_IN_BAND = 0.15f;
    /** Either way it settles this much wider than the tightest fit, so it does not sit on a band's edge. */
    static final float ZOOM_SLACK = 0.08f;
    /** The pan target moves when the box centre leaves a dead zone of this share of the view width. */
    static final float DEAD_ZONE_SHARE = 0.14f;
    /** Seconds the spring takes to cover most of a step (its time constant). */
    static final float RESPONSE_SECONDS = 0.8f;
    /** A body this close to the frame's edge (share of the view) is treated as leaving it. */
    static final float EDGE_SAFETY_SHARE = 0.04f;

    private final int widthPx;
    private final int heightPx;
    private final int mapWidth;
    private final int mapHeight;
    private final float minAcross;
    private final float maxAcross;
    private final float aspect;

    private boolean primed;
    private float targetX, targetY, targetAcross;
    private float x, y, across;
    private float vx, vy, vAcross;

    /**
     * @param widthPx    output image width
     * @param heightPx   output image height, including the caption band
     * @param mapWidth   grid width in cells
     * @param mapHeight  grid height in cells
     * @param minAcross  the closest the camera comes, in cells across
     */
    public DuelCamera(int widthPx, int heightPx, int mapWidth, int mapHeight, float minAcross) {
        this.widthPx = widthPx;
        this.heightPx = heightPx;
        this.mapWidth = mapWidth;
        this.mapHeight = mapHeight;
        this.aspect = (float) widthPx / (heightPx - BattleReviewFrameRenderer.HEADER_HEIGHT);
        this.maxAcross = Math.max(mapWidth, mapHeight * aspect);
        this.minAcross = Math.min(Math.max(1f, minAcross), maxAcross);
    }

    public float aspect() { return aspect; }

    public float maxAcross() { return maxAcross; }

    /**
     * Advances the camera by {@code seconds} toward framing {@code subjects} and returns the view to draw. An empty
     * list holds the last view (the whole map before anything has been framed).
     */
    public View update(List<Subject> subjects, float seconds) {
        if (subjects.isEmpty()) {
            if (!primed) return clampToMap(mapWidth * 0.5f, mapHeight * 0.5f, maxAcross);
            return current();
        }
        float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (Subject s : subjects) {
            minX = Math.min(minX, s.x() - s.radius());
            maxX = Math.max(maxX, s.x() + s.radius());
            minY = Math.min(minY, s.y() - s.radius());
            maxY = Math.max(maxY, s.y() + s.radius());
        }
        float boxX = (minX + maxX) * 0.5f;
        float boxY = (minY + maxY) * 0.5f;
        float fit = fitAcross(maxX - minX, maxY - minY);

        if (!primed) {
            primed = true;
            targetX = x = boxX;
            targetY = y = boxY;
            targetAcross = across = fit;
        } else {
            // Zoom hysteresis: out once the box has outgrown the target, in only when it could be clearly closer. The
            // containment below holds the hulls in between, so the band costs padding, never a hull.
            if (fit > targetAcross * (1f + ZOOM_OUT_BAND) || fit < targetAcross * (1f - ZOOM_IN_BAND)) {
                targetAcross = clampAcross(fit * (1f + ZOOM_SLACK));
            }
            // Pan hysteresis: the target follows the box only once its centre leaves the dead zone.
            float dead = DEAD_ZONE_SHARE * targetAcross;
            targetX = follow(targetX, boxX, dead);
            targetY = follow(targetY, boxY, dead / aspect);
            float[] sx = spring(x, vx, targetX, seconds);
            x = sx[0]; vx = sx[1];
            float[] sy = spring(y, vy, targetY, seconds);
            y = sy[0]; vy = sy[1];
            float[] sa = spring(across, vAcross, targetAcross, seconds);
            across = sa[0]; vAcross = sa[1];
        }
        contain(minX, maxX, minY, maxY);
        View v = clampToMap(x, y, across);
        x = v.centerX();
        y = v.centerY();
        across = v.cellsAcross();
        return snap(v);
    }

    /** The width that fits a box of {@code w} by {@code h} cells with its padding, within the camera's limits. */
    float fitAcross(float w, float h) {
        float side = Math.max(w, h * aspect);
        float padded = side * (1f + 2f * PAD_SHARE) + 2f * PAD_CELLS;
        // Padding never widens the view past the map's own width, where it would only add the black beyond the edge;
        // a box too tall for that width still gets the width its height needs.
        float cap = Math.max(mapWidth, (h + 2f * PAD_CELLS) * aspect);
        return clampAcross(Math.min(padded, cap));
    }

    private float clampAcross(float a) {
        return Math.max(minAcross, Math.min(maxAcross, a));
    }

    private static float follow(float target, float actual, float dead) {
        if (actual > target + dead) return actual - dead;
        if (actual < target - dead) return actual + dead;
        return target;
    }

    /** One exact step of a critically damped spring from ({@code p}, {@code v}) toward {@code goal}. */
    private static float[] spring(float p, float v, float goal, float seconds) {
        if (!(seconds > 0f)) return new float[]{p, v};
        double omega = 1.0 / RESPONSE_SECONDS;
        double e = Math.exp(-omega * seconds);
        double d = p - goal;
        double c = v + omega * d;
        double np = goal + (d + c * seconds) * e;
        double nv = (v - omega * c * seconds) * e;
        return new float[]{(float) np, (float) nv};
    }

    /** Widens or shifts the eased view just enough that every body is inside it, clear of the very edge. */
    private void contain(float minX, float maxX, float minY, float maxY) {
        float w = maxX - minX;
        float h = maxY - minY;
        float need = clampAcross(Math.max(w, h * aspect) / (1f - 2f * EDGE_SAFETY_SHARE));
        if (need > across) {
            across = need;
            vAcross = Math.max(0f, vAcross);
        }
        float halfX = across * 0.5f * (1f - 2f * EDGE_SAFETY_SHARE);
        float halfY = across / aspect * 0.5f * (1f - 2f * EDGE_SAFETY_SHARE);
        if (w <= 2f * halfX) {
            if (minX < x - halfX) x = minX + halfX;
            if (maxX > x + halfX) x = maxX - halfX;
        }
        if (h <= 2f * halfY) {
            if (minY < y - halfY) y = minY + halfY;
            if (maxY > y + halfY) y = maxY - halfY;
        }
    }

    /** Keeps the view inside the map, as the live camera's pan clamp does, and centres it on a map it covers. */
    private View clampToMap(float cx, float cy, float a) {
        float halfX = a * 0.5f;
        float halfY = a / aspect * 0.5f;
        float nx = halfX * 2f >= mapWidth ? mapWidth * 0.5f : Math.max(halfX, Math.min(mapWidth - halfX, cx));
        float ny = halfY * 2f >= mapHeight ? mapHeight * 0.5f : Math.max(halfY, Math.min(mapHeight - halfY, cy));
        return new View(nx, ny, a);
    }

    /** The centre snapped to whole pixels at the zoom the renderer will draw (whole pixels per cell). */
    private View snap(View v) {
        float cellPx = (float) Math.floor(widthPx / v.cellsAcross());
        if (cellPx < 1f) return v;
        return new View(Math.round(v.centerX() * cellPx) / cellPx, Math.round(v.centerY() * cellPx) / cellPx,
                v.cellsAcross());
    }

    private View current() {
        return snap(clampToMap(x, y, across));
    }

    /** The output frame's height in pixels (with its caption band); for callers laying out an inset. */
    public int heightPx() { return heightPx; }
}
