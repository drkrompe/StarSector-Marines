package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The creep's roots between a bore and the growths it feeds (`colony-breach-nouns.md`, Nest): a thick rope of
 * sinew raised out of the creep from the bore's mouth to each colony, outpost and brood spire it holds, so a bore and
 * everything it grew read as one organism. Drawn only; nothing walks, stops or fights by them.
 *
 * <p>A root bows to one side by a seeded amount and meanders a little along its length, so no two are straight or
 * alike, and tapers from the mouth to its growth. It is pure over its endpoints: the same nest gives the same roots.
 * {@link #rasterize} lays them into a per-cell distance field the creep's shader reads.
 */
public final class NestTrails {

    /** Half-width of a root where it leaves the mouth, and where it reaches a growth far out, in cells. */
    public static final float HALF_WIDTH_ROOT = 0.6f, HALF_WIDTH_TIP = 0.28f;
    /** A mouth colony's root, short and stout. */
    public static final float HALF_WIDTH_STUB = 0.45f;
    /** Cells from a root's edge over which the distance field is kept; beyond it reads as far. */
    public static final float RANGE = 3f;
    /** Largest bow, as a share of a root's length, and the meander's amplitude (cells) and wavelength (cells). */
    static final float BOW = 0.22f, MEANDER = 0.45f, MEANDER_WAVELENGTH = 6f;
    /** Cells from the bore's centre where a root starts, under the rim of its painting. */
    static final float MOUTH_START = 1f;
    /** Cells per polyline step along a root. */
    static final float STEP = 0.75f;

    /**
     * Temporary A/B switch for the roots' look, read while the owner picks: {@code rope} (a ringed rope raised out of
     * the creep, the default), {@code furrow} (a channel sunk into it) or {@code veins} (no relief, the creep's veins
     * thickened and lit along a wider path); the creep's shading reads it. Remove it with the losing styles once the
     * owner has picked.
     */
    public static final String STYLE_PROPERTY = "battle.render.nestRoots";
    /** How much wider a vein path is laid than a rope. */
    static final float VEIN_PATH_WIDTH = 1.6f;

    /** The roots' look ({@link #STYLE_PROPERTY}). */
    public static String style() { return System.getProperty(STYLE_PROPERTY, "rope"); }

    /** One root: from the mouth at (x0, y0) to a growth at (x1, y1). */
    public record Trail(float x0, float y0, float x1, float y1, float halfWidthFrom, float halfWidthTo, long seed) { }

    private NestTrails() { }

    /** Every living bore's roots to its living colonies, outposts and spire, in a stable order. */
    public static List<Trail> of(BattleSimulation sim) {
        List<Trail> out = new ArrayList<>();
        SwarmBores bores = sim.swarmBores();
        if (bores == null) return out;
        for (SwarmBore bore : bores.bores()) {
            if (bore.collapsed() || !sim.world().isAlive(bore.entityId())) continue;
            float bx = bore.x() + 0.5f, by = bore.y() + 0.5f;
            for (long id : bore.colonies()) add(sim, out, bore, bx, by, id, HALF_WIDTH_STUB, HALF_WIDTH_STUB * 0.75f);
            for (long id : bore.outposts()) add(sim, out, bore, bx, by, id, HALF_WIDTH_ROOT, HALF_WIDTH_TIP);
        }
        for (BroodSpires.Spire spire : sim.broodSpires().spires()) {
            if (spire.bore.collapsed()) continue;
            add(sim, out, spire.bore, spire.bore.x() + 0.5f, spire.bore.y() + 0.5f, spire.entityId,
                    HALF_WIDTH_ROOT * 1.15f, HALF_WIDTH_TIP * 1.4f);
        }
        return out;
    }

    private static void add(BattleSimulation sim, List<Trail> out, SwarmBore bore, float bx, float by, long id,
                            float from, float to) {
        if (sim.resolveUnit(id) == 0L || !sim.world().isAlive(id)) return;
        float gx = sim.world().cellX(id) + 0.5f, gy = sim.world().cellY(id) + 0.5f;
        float dx = gx - bx, dy = gy - by, len = (float) Math.hypot(dx, dy);
        if (len < SwarmBores.MOUTH_RADIUS + 0.5f) return;
        // Leave from under the bore's own painting, so the root runs out of its rim rather than beside it.
        float sx = bx + dx / len * MOUTH_START, sy = by + dy / len * MOUTH_START;
        float wide = "veins".equals(style()) ? VEIN_PATH_WIDTH : 1f;
        out.add(new Trail(sx, sy, gx, gy, from * wide, to * wide, SwarmBores.mix(bore.number() * 0x9E3779B97F4A7C15L ^ id)));
    }

    /** A cheap fingerprint of {@code trails}, so a drawer re-lays the field only when the nest has changed. */
    public static long signature(List<Trail> trails) {
        long h = trails.size();
        for (Trail t : trails) {
            h = h * 31 + Float.floatToIntBits(t.x1()) * 17L + Float.floatToIntBits(t.y1());
            h = h * 31 + t.seed();
        }
        return h;
    }

    /** The root's centreline, from mouth to growth, as x, y pairs. */
    public static float[] centreline(Trail t) {
        float dx = t.x1() - t.x0(), dy = t.y1() - t.y0(), len = (float) Math.hypot(dx, dy);
        float nx = -dy / len, ny = dx / len;
        float bow = (SwarmBores.unit(t.seed()) * 2f - 1f) * BOW * len;
        float phase = SwarmBores.unit(SwarmBores.mix(t.seed())) * (float) (Math.PI * 2);
        int steps = Math.max(2, (int) Math.ceil(len / STEP));
        float[] pts = new float[(steps + 1) * 2];
        for (int i = 0; i <= steps; i++) {
            float u = i / (float) steps;
            // Bow peaks mid-root; the meander fades out at both ends so the root meets the mouth and the growth.
            float side = bow * 4f * u * (1f - u)
                    + MEANDER * (float) Math.sin(u * len / MEANDER_WAVELENGTH * Math.PI * 2 + phase) * (float) Math.sin(u * Math.PI);
            pts[i * 2] = t.x0() + dx * u + nx * side;
            pts[i * 2 + 1] = t.y0() + dy * u + ny * side;
        }
        return pts;
    }

    /**
     * Lays {@code trails} into the per-cell RGBA {@code rgba} ({@code w} by {@code h}), clearing it first in the two
     * channels it owns. Channel {@code distChannel} holds the distance from the cell centre to the nearest root's edge,
     * {@code 0.5 - d / (2 * RANGE)} as 0..255 (above half is inside a root); {@code widthChannel} that root's half-width
     * there, as {@code hw / 2} in 0..255.
     */
    public static void rasterize(List<Trail> trails, int w, int h, byte[] rgba, int distChannel, int widthChannel) {
        int cells = w * h;
        float[] best = new float[cells];
        float[] width = new float[cells];
        Arrays.fill(best, RANGE);
        for (Trail t : trails) {
            float[] pts = centreline(t);
            int segments = pts.length / 2 - 1;
            for (int s = 0; s < segments; s++) {
                float ax = pts[s * 2], ay = pts[s * 2 + 1], bx = pts[s * 2 + 2], by = pts[s * 2 + 3];
                float ua = s / (float) segments, ub = (s + 1) / (float) segments;
                float hwA = t.halfWidthFrom() + (t.halfWidthTo() - t.halfWidthFrom()) * ua;
                float hwB = t.halfWidthFrom() + (t.halfWidthTo() - t.halfWidthFrom()) * ub;
                float reach = Math.max(hwA, hwB) + RANGE;
                int x0 = Math.max(0, (int) Math.floor(Math.min(ax, bx) - reach));
                int y0 = Math.max(0, (int) Math.floor(Math.min(ay, by) - reach));
                int x1 = Math.min(w - 1, (int) Math.ceil(Math.max(ax, bx) + reach));
                int y1 = Math.min(h - 1, (int) Math.ceil(Math.max(ay, by) + reach));
                float ex = bx - ax, ey = by - ay, ee = ex * ex + ey * ey;
                for (int y = y0; y <= y1; y++) {
                    for (int x = x0; x <= x1; x++) {
                        float px = x + 0.5f - ax, py = y + 0.5f - ay;
                        float k = ee > 0f ? Math.max(0f, Math.min(1f, (px * ex + py * ey) / ee)) : 0f;
                        float qx = px - ex * k, qy = py - ey * k;
                        float hw = hwA + (hwB - hwA) * k;
                        float d = (float) Math.sqrt(qx * qx + qy * qy) - hw;
                        int at = y * w + x;
                        if (d < best[at]) {
                            best[at] = d;
                            width[at] = hw;
                        }
                    }
                }
            }
        }
        for (int i = 0; i < cells; i++) {
            float v = 0.5f - Math.max(-RANGE, Math.min(RANGE, best[i])) / (2f * RANGE);
            rgba[i * 4 + distChannel] = (byte) Math.round(v * 255f);
            rgba[i * 4 + widthChannel] = (byte) Math.round(Math.min(1f, width[i] / 2f) * 255f);
        }
    }
}
