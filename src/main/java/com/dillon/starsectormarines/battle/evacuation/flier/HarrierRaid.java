package com.dillon.starsectormarines.battle.evacuation.flier;

import java.util.List;

/**
 * A deep raid off a brood spire (`colony-breach-nouns.md`, Brood spire, Raids): a few of its roost sent past the line
 * to a soft target no anti-air covers, by a way bent round every flak umbrella, to dive on it and come home the same
 * way to land.
 *
 * <p>Pure: {@link #choose} picks the target from the pack's candidates and {@link #route} finds the way; the flight
 * itself is a {@link HarrierPatrol} with a strike leg ({@link #sortie}).
 */
public final class HarrierRaid {

    /** Nearest and furthest a raid's target lies from its spire, in cells: past the spire's own wake. */
    public static final float RANGE_MIN = 50f, RANGE_MAX = 160f;
    /** Cells a raid's way keeps outside every air gun's reach. */
    public static final float MARGIN = 6f;
    /** How fast a raid's anchor flies its way, in cells a second. */
    public static final float SPEED = 3.6f;
    /** Dives it makes on the target's ground before it turns for home. */
    public static final int DIVES = 2;
    /** Seconds over the target with nothing to dive on before it turns for home, and the most it stays at all. */
    public static final float STRIKE_IDLE_SECONDS = 8f, STRIKE_MAX_SECONDS = 45f;
    /** Cells round its target's ground a raid looks for what to dive on. */
    public static final float STRIKE_RADIUS = 16f;
    /** Cells either side of the direct way a detour point may lie, in steps. */
    static final float DETOUR_STEP = 10f;
    static final int DETOUR_STEPS = 9;
    static final float[] DETOUR_AT = {0.25f, 0.5f, 0.75f};

    private HarrierRaid() {}

    /**
     * Whether a candidate is worth a raid: the radio that calls the air in, an unarmed truck, a medic, a body on its
     * own, or a gun, and nothing that answers fliers reaches it.
     */
    static boolean soft(HarrierTargeting.Candidate c) {
        if (c.airCover() > 0) return false;
        return switch (c.prey()) {
            case RADIO, LOGISTICS, MEDIC, ISOLATED, GUN -> true;
            default -> false;
        };
    }

    /**
     * The raid's target from ({@code x}, {@code y}): the most valuable soft candidate between {@link #RANGE_MIN} and
     * {@link #RANGE_MAX}, the nearer on a tie of value, the lower id on a tie of both; 0 for none.
     */
    public static long choose(List<HarrierTargeting.Candidate> candidates, float x, float y) {
        long best = 0L;
        float bestValue = -1f, bestDistance = Float.MAX_VALUE;
        for (HarrierTargeting.Candidate c : candidates) {
            if (!soft(c)) continue;
            float d = (float) Math.hypot(c.x() - x, c.y() - y);
            if (d < RANGE_MIN || d > RANGE_MAX) continue;
            float v = c.prey().value;
            if (v > bestValue || v == bestValue && (d < bestDistance || d == bestDistance && c.id() < best)) {
                best = c.id();
                bestValue = v;
                bestDistance = d;
            }
        }
        return best;
    }

    /**
     * What a raid over its target dives on: the most valuable soft candidate within {@link #STRIKE_RADIUS} of where it
     * was sent ({@code sx}, {@code sy}), the nearer on a tie, keeping {@code current} while it is still one; 0 for
     * none. It never takes on what the guns cover, however rich.
     */
    public static long pickAt(List<HarrierTargeting.Candidate> candidates, float sx, float sy, long current) {
        long best = 0L;
        float bestValue = -1f, bestDistance = Float.MAX_VALUE;
        for (HarrierTargeting.Candidate c : candidates) {
            if (!soft(c)) continue;
            float d = (float) Math.hypot(c.x() - sx, c.y() - sy);
            if (d > STRIKE_RADIUS) continue;
            if (c.id() == current) return current;
            float v = c.prey().value;
            if (v > bestValue || v == bestValue && (d < bestDistance || d == bestDistance && c.id() < best)) {
                best = c.id();
                bestValue = v;
                bestDistance = d;
            }
        }
        return best;
    }

    /**
     * The way from ({@code fx}, {@code fy}) to ({@code tx}, {@code ty}) clear of every gun in {@code guns}
     * ({@code {x, y, reach}}) by {@link #MARGIN}, as its turning points with the target last: straight when that is
     * clear, else the shortest way by one detour point, else by two, each off the direct way at a quarter, half or
     * three quarters along and kept on a {@code w} by {@code h} map. {@code null} when there is none.
     */
    public static float[] route(float fx, float fy, float tx, float ty, List<float[]> guns, int w, int h) {
        if (clear(fx, fy, tx, ty, guns)) return new float[]{tx, ty};
        float dx = tx - fx, dy = ty - fy, len = (float) Math.hypot(dx, dy);
        if (len < 1e-3f) return null;
        float nx = -dy / len, ny = dx / len;
        int n = DETOUR_AT.length * DETOUR_STEPS * 2;
        float[] px = new float[n], py = new float[n];
        int k = 0;
        for (float at : DETOUR_AT) {
            for (int s = 1; s <= DETOUR_STEPS; s++) {
                for (int side = -1; side <= 1; side += 2) {
                    px[k] = Math.max(2f, Math.min(w - 2f, fx + dx * at + nx * side * s * DETOUR_STEP));
                    py[k] = Math.max(2f, Math.min(h - 2f, fy + dy * at + ny * side * s * DETOUR_STEP));
                    k++;
                }
            }
        }
        float[] best = null;
        float bestLength = Float.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            if (!clear(fx, fy, px[i], py[i], guns) || !clear(px[i], py[i], tx, ty, guns)) continue;
            float l = dist(fx, fy, px[i], py[i]) + dist(px[i], py[i], tx, ty);
            if (l < bestLength) {
                bestLength = l;
                best = new float[]{px[i], py[i], tx, ty};
            }
        }
        if (best != null) return best;
        for (int i = 0; i < n; i++) {
            if (!clear(fx, fy, px[i], py[i], guns)) continue;
            for (int j = 0; j < n; j++) {
                if (i == j || !clear(px[i], py[i], px[j], py[j], guns) || !clear(px[j], py[j], tx, ty, guns)) continue;
                float l = dist(fx, fy, px[i], py[i]) + dist(px[i], py[i], px[j], py[j]) + dist(px[j], py[j], tx, ty);
                if (l < bestLength) {
                    bestLength = l;
                    best = new float[]{px[i], py[i], px[j], py[j], tx, ty};
                }
            }
        }
        return best;
    }

    /**
     * The whole flight of a raid from a spire at ({@code hx}, {@code hy}) along {@code way} ({@link #route}): out by
     * its turning points to the target, back by them in reverse, and the spire last; the target is its strike leg.
     */
    public static HarrierPatrol sortie(float[] way, float hx, float hy, int size) {
        int out = way.length / 2;
        float[] route = new float[(out + (out - 1) + 1) * 2];
        System.arraycopy(way, 0, route, 0, way.length);
        int k = way.length;
        for (int i = out - 2; i >= 0; i--) {
            route[k++] = way[i * 2];
            route[k++] = way[i * 2 + 1];
        }
        route[k++] = hx;
        route[k] = hy;
        return new HarrierPatrol(route, hx, hy, size, SPEED, out - 1);
    }

    /** Whether the segment keeps {@link #MARGIN} outside every gun's reach. */
    static boolean clear(float ax, float ay, float bx, float by, List<float[]> guns) {
        for (float[] g : guns) {
            float r = g[2] + MARGIN;
            if (segmentDistance(g[0], g[1], ax, ay, bx, by) < r) return false;
        }
        return true;
    }

    static float segmentDistance(float px, float py, float ax, float ay, float bx, float by) {
        float dx = bx - ax, dy = by - ay;
        float l2 = dx * dx + dy * dy;
        float t = l2 < 1e-6f ? 0f : Math.max(0f, Math.min(1f, ((px - ax) * dx + (py - ay) * dy) / l2));
        return dist(px, py, ax + dx * t, ay + dy * t);
    }

    private static float dist(float ax, float ay, float bx, float by) {
        return (float) Math.hypot(bx - ax, by - ay);
    }
}
