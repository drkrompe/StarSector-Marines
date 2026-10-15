package com.dillon.starsectormarines.battle.squad;

/**
 * A line the player drew for a squad to hold: a segment of ground between two cells, and the
 * side of it that faces the enemy.
 *
 * <p>The squad spreads along the segment rather than around a point. Everything within
 * {@link #BAND} cells of it is the ground the order covers, which is where trench, sandbag and
 * ordinary cover are looked for. The front is fixed when the order is given — toward the threat
 * the squad believes in, else away from where the squad stood — so the player can see which way
 * the line faces before letting go of the drag.
 *
 * @param frontX unit vector pointing from the line toward the enemy
 * @param frontY unit vector pointing from the line toward the enemy
 */
public record FrontageLine(int x0, int y0, int x1, int y1, float frontX, float frontY) {

    /** How far either side of the drawn segment, and past its ends, the order reaches. */
    public static final int BAND = 3;

    /** A drag shorter than this, in cells, is a click. */
    public static final float MIN_LENGTH = 2f;

    /**
     * The line from ({@code x0},{@code y0}) to ({@code x1},{@code y1}), facing the believed threat
     * when there is one and otherwise away from ({@code fromX},{@code fromY}), where the squad
     * stands. A squad standing on the line itself with nothing to face takes the left-hand side
     * of the drag.
     *
     * @param threatX the believed threat's cell, or -1 for none
     */
    public static FrontageLine facing(int x0, int y0, int x1, int y1, float fromX, float fromY,
                                      int threatX, int threatY) {
        float dx = x1 - x0;
        float dy = y1 - y0;
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        float nx = length < 1e-3f ? 0f : -dy / length;
        float ny = length < 1e-3f ? 1f : dx / length;
        float mx = (x0 + x1) * 0.5f;
        float my = (y0 + y1) * 0.5f;
        float side;
        if (threatX >= 0 && threatY >= 0) {
            side = (threatX - mx) * nx + (threatY - my) * ny;
        } else {
            side = -((fromX - mx) * nx + (fromY - my) * ny);
        }
        if (side < 0f) {
            nx = -nx;
            ny = -ny;
        }
        return new FrontageLine(x0, y0, x1, y1, nx, ny);
    }

    public float length() {
        float dx = x1 - x0;
        float dy = y1 - y0;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    /** Where the cell projects onto the segment, from 0 at the start to 1 at the end, clamped. */
    public float along(float x, float y) {
        float dx = x1 - x0;
        float dy = y1 - y0;
        float lengthSq = dx * dx + dy * dy;
        if (lengthSq < 1e-6f) return 0f;
        float t = ((x - x0) * dx + (y - y0) * dy) / lengthSq;
        return Math.max(0f, Math.min(1f, t));
    }

    /** Distance in cells from the cell to the nearest point of the segment. */
    public float distance(float x, float y) {
        float t = along(x, y);
        float px = x0 + (x1 - x0) * t;
        float py = y0 + (y1 - y0) * t;
        float ex = x - px;
        float ey = y - py;
        return (float) Math.sqrt(ex * ex + ey * ey);
    }

    /** Whether the cell lies in the ground the order covers. */
    public boolean covers(int x, int y) {
        return distance(x, y) <= BAND;
    }

    /** How far the cell stands in front of the line (negative: behind it). */
    public float ahead(float x, float y) {
        return (x - x0) * frontX + (y - y0) * frontY;
    }

    /** The point at fraction {@code t} along the segment, moved {@code ahead} cells toward the front. */
    public float[] pointAt(float t, float ahead) {
        return new float[]{x0 + (x1 - x0) * t + frontX * ahead, y0 + (y1 - y0) * t + frontY * ahead};
    }

    /** A circle that holds the whole band, for consumers that reason in circles: centre x, y and radius. */
    public int[] enclosingCircle() {
        int cx = Math.round((x0 + x1) * 0.5f);
        int cy = Math.round((y0 + y1) * 0.5f);
        return new int[]{cx, cy, (int) Math.ceil(length() * 0.5f) + BAND + 1};
    }
}
