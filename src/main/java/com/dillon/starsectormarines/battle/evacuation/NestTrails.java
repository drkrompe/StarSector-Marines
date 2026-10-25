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
 *
 * <p>A root grows, and its growth rises where it ends (owner, 2026-10-25: thinner veins "dig out and construct"
 * from the bore, and the root is the lead-up to the growth, not its sequel): a thin vein digs out from the bore over
 * the first {@link #DIG_SHARE} of the growth's lead and thickens into rope over the rest ({@link #growth}), and only
 * then does the colony or outpost stand at its end. A mouth colony's lead is {@link SwarmBores#COLONY_LEAD_SECONDS};
 * an outpost's is its hold ({@link CreepOutposts#HOLD_SECONDS}). A standing growth, from setup or risen, has its root
 * fully grown.
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
    /** The share of a growth's lead its root spends digging out; it thickens into rope over the rest. */
    public static final float DIG_SHARE = 0.5f;
    /**
     * Half-width laid for a root still a vein, in cells: below what the field can hold as a rope, so the creep's
     * shader draws it as a band of thickened, lit veins instead. A thickening rope starts at {@link #ROPE_START}.
     */
    public static final float VEIN_HALF_WIDTH = 0.05f, ROPE_START = 0.4f;
    /** Cells per polyline step along a root. */
    static final float STEP = 0.75f;

    /**
     * One root: from the mouth at (x0, y0) to a growth at (x1, y1), its full half-widths at either end, its seed, how
     * far along it its vein has dug ({@code reach}, 0..1) and how far it has thickened into rope ({@code thick}, 0..1).
     */
    public record Trail(float x0, float y0, float x1, float y1, float halfWidthFrom, float halfWidthTo, long seed,
                        float reach, float thick) { }

    /**
     * How far a root has grown {@code progress} (0..1) through its growth's lead: its reach along its length and its
     * thickness, each 0..1, into {@code out}. It digs out over the first {@link #DIG_SHARE}, then thickens.
     */
    public static void growth(float progress, float[] out) {
        out[0] = Math.max(0f, Math.min(1f, progress / DIG_SHARE));
        out[1] = Math.max(0f, Math.min(1f, (progress - DIG_SHARE) / (1f - DIG_SHARE)));
    }

    private NestTrails() { }

    /**
     * Every living bore's roots, in a stable order: grown to its living colonies, outposts and spire, and growing to
     * the colonies on their way and the outpost whose cell it holds.
     */
    public static List<Trail> of(BattleSimulation sim) {
        List<Trail> out = new ArrayList<>();
        SwarmBores bores = sim.swarmBores();
        if (bores == null) return out;
        int tick = sim.getSimTickIndex();
        CreepOutposts outposts = sim.creepOutposts();
        for (SwarmBore bore : bores.bores()) {
            if (bore.collapsed() || !sim.world().isAlive(bore.entityId())) continue;
            for (long id : bore.colonies()) addTo(sim, out, bore, id, HALF_WIDTH_STUB, HALF_WIDTH_STUB * 0.75f);
            for (long id : bore.outposts()) addTo(sim, out, bore, id, HALF_WIDTH_ROOT, HALF_WIDTH_TIP);
            for (SwarmBore.Growing g : bore.growing()) {
                add(out, bore, g.x(), g.y(), HALF_WIDTH_STUB, HALF_WIDTH_STUB * 0.75f, g.progress(tick));
            }
            float held = outposts == null ? -1f : outposts.rootProgress(bore.entityId(), tick);
            if (held >= 0f) {
                int[] cell = outposts.pendingCell(bore.entityId());
                add(out, bore, cell[0], cell[1], HALF_WIDTH_ROOT, HALF_WIDTH_TIP, held);
            }
        }
        for (BroodSpires.Spire spire : sim.broodSpires().spires()) {
            if (spire.bore.collapsed()) continue;
            addTo(sim, out, spire.bore, spire.entityId, HALF_WIDTH_ROOT * 1.15f, HALF_WIDTH_TIP * 1.4f);
        }
        return out;
    }

    /** A standing growth's root, fully grown. */
    private static void addTo(BattleSimulation sim, List<Trail> out, SwarmBore bore, long id, float from, float to) {
        if (sim.resolveUnit(id) == 0L || !sim.world().isAlive(id)) return;
        add(out, bore, sim.world().cellX(id), sim.world().cellY(id), from, to, 1f);
    }

    /**
     * A root from {@code bore} to cell ({@code cx}, {@code cy}), {@code progress} through its growth's lead. Seeded off
     * the bore and the cell, so a root keeps its line as the growth rises at its end.
     */
    private static void add(List<Trail> out, SwarmBore bore, int cx, int cy, float from, float to, float progress) {
        float bx = bore.x() + 0.5f, by = bore.y() + 0.5f;
        float gx = cx + 0.5f, gy = cy + 0.5f;
        float dx = gx - bx, dy = gy - by, len = (float) Math.hypot(dx, dy);
        if (len < SwarmBores.MOUTH_RADIUS + 0.5f) return;
        // Leave from under the bore's own painting, so the root runs out of its rim rather than beside it.
        float sx = bx + dx / len * MOUTH_START, sy = by + dy / len * MOUTH_START;
        float[] grown = new float[2];
        growth(progress, grown);
        if (grown[0] <= 0f) return;
        long cell = cx * 73856093L ^ cy * 19349663L;
        out.add(new Trail(sx, sy, gx, gy, from, to, SwarmBores.mix(bore.number() * 0x9E3779B97F4A7C15L ^ cell),
                grown[0], grown[1]));
    }

    /** A cheap fingerprint of {@code trails}, so a drawer re-lays the field only when the nest has changed. */
    public static long signature(List<Trail> trails) {
        long h = trails.size();
        for (Trail t : trails) {
            h = h * 31 + Float.floatToIntBits(t.x1()) * 17L + Float.floatToIntBits(t.y1());
            h = h * 31 + t.seed();
            // Re-laid as a growing root moves on by about half a cell or a sixteenth of its thickening.
            h = h * 31 + Math.round(t.reach() * Math.hypot(t.x1() - t.x0(), t.y1() - t.y0()) * 2f) * 17L
                    + Math.round(t.thick() * 16f);
        }
        return h;
    }

    /** The half-width laid at {@code u} along {@code t}: a vein's until it thickens, then a rope growing to its own. */
    static float laidHalfWidth(Trail t, float u) {
        if (t.thick() <= 0f) return VEIN_HALF_WIDTH;
        float full = t.halfWidthFrom() + (t.halfWidthTo() - t.halfWidthFrom()) * u;
        float start = Math.min(ROPE_START, full);
        return start + (full - start) * t.thick();
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
                float ua = s / (float) segments, ub = (s + 1) / (float) segments;
                if (ua >= t.reach()) break;
                float ax = pts[s * 2], ay = pts[s * 2 + 1], bx = pts[s * 2 + 2], by = pts[s * 2 + 3];
                if (ub > t.reach()) {
                    // The vein's digging front: cut the last segment where it has got to.
                    float k = (t.reach() - ua) / (ub - ua);
                    bx = ax + (bx - ax) * k;
                    by = ay + (by - ay) * k;
                    ub = t.reach();
                }
                float hwA = laidHalfWidth(t, ua), hwB = laidHalfWidth(t, ub);
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
