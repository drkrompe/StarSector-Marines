package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;

import java.util.ArrayList;
import java.util.List;

/**
 * What a nest leaves lying about on its creep (`colony-breach-nouns.md`, Nest): egg clutches, brood pods and shed
 * husks beside its roots and round its mouth. Drawn only: nothing walks round, hides behind or shoots them.
 *
 * <p>A root carries clutter only once it has thickened into rope, a piece every few cells along it, set off to one
 * side clear of the rope and never at either end, where the bore and the growth stand. A bore at bore tier or above
 * keeps a few pieces in the gaps round its mouth, past its rim and clear of its colonies and its roots. Pure over the roots and the bores, seeded by
 * each root's own seed and each bore's number: the same nest lies the same way.
 */
public final class NestClutter {

    /** The three kinds, each with its own paintings ({@code <name>-<n>.png}). */
    public enum Kind {
        CLUTCH("nest-clutch"), PODS("nest-pods"), HUSK("nest-husk");

        public final String name;

        Kind(String name) { this.name = name; }
    }

    /** One piece: its kind, its painting (1 to {@link #VARIANTS}) and where it lies, in cells. */
    public record Item(Kind kind, int variant, float x, float y) { }

    /** Paintings of each kind. */
    public static final int VARIANTS = 3;
    /** Cells between pieces along a root, and how far either way of that a piece may lie. */
    static final float SPACING = 5f, JITTER = 1.4f;
    /** The share of a root at either end left bare, where its bore and its growth stand. */
    static final float END_BARE = 0.15f;
    /** Cells a piece lies off a rope's edge, least and most. */
    static final float OFF_MIN = 0.5f, OFF_MAX = 1.1f;
    /** A root's thickness from which it carries clutter. */
    static final float THICK_ENOUGH = 0.5f;
    /** Cells round a mouth its pieces lie in, and how far clear of a colony each keeps. */
    static final float MOUTH_IN = 3.2f, MOUTH_OUT = 4.4f, COLONY_CLEAR = 1.6f;
    /** Cells a mouth piece keeps off a root's edge. */
    static final float ROOT_CLEAR = 0.8f;
    /** Pieces round the mouth of a bore at bore tier, and at maw. */
    static final int MOUTH_BORE = 2, MOUTH_MAW = 4;
    /** Tries for each mouth piece before it is given up. */
    static final int MOUTH_TRIES = 12;

    private NestClutter() { }

    /** Every piece the battle's nests have laid now. */
    public static List<Item> of(BattleSimulation sim) {
        List<Item> out = new ArrayList<>();
        List<NestTrails.Trail> roots = NestTrails.of(sim);
        for (NestTrails.Trail t : roots) along(t, out);
        SwarmBores bores = sim.swarmBores();
        if (bores == null) return out;
        for (SwarmBore bore : bores.bores()) {
            if (bore.collapsed() || !sim.world().isAlive(bore.entityId())) continue;
            int count = bore.tier() == BoreTier.MAW ? MOUTH_MAW : bore.tier() == BoreTier.BORE ? MOUTH_BORE : 0;
            if (count == 0) continue;
            List<float[]> colonies = new ArrayList<>();
            for (long id : bore.colonies()) {
                if (sim.resolveUnit(id) == 0L || !sim.world().isAlive(id)) continue;
                colonies.add(new float[]{sim.world().cellX(id) + 0.5f, sim.world().cellY(id) + 0.5f});
            }
            mouth(bore.x() + 0.5f, bore.y() + 0.5f, SwarmBores.mix(0xC1077E7L ^ bore.number()), count, colonies, roots,
                    out);
        }
        return out;
    }

    /** The pieces beside one root, appended to {@code out}. */
    static void along(NestTrails.Trail t, List<Item> out) {
        if (t.thick() < THICK_ENOUGH || t.reach() < 1f) return;
        float[] pts = NestTrails.centreline(t);
        int n = pts.length / 2 - 1;
        float len = (float) Math.hypot(t.x1() - t.x0(), t.y1() - t.y0());
        long h = t.seed();
        for (float at = SPACING * 0.5f; at < len; at += SPACING) {
            h = SwarmBores.mix(h + 1);
            float u = (at + (SwarmBores.unit(h) * 2f - 1f) * JITTER) / len;
            if (u < END_BARE || u > 1f - END_BARE) continue;
            int i = Math.min(n - 1, (int) (u * n));
            float ax = pts[i * 2], ay = pts[i * 2 + 1], bx = pts[i * 2 + 2], by = pts[i * 2 + 3];
            float k = u * n - i;
            float cx = ax + (bx - ax) * k, cy = ay + (by - ay) * k;
            float dx = bx - ax, dy = by - ay, dl = (float) Math.hypot(dx, dy);
            if (dl < 1e-4f) continue;
            float side = SwarmBores.unit(SwarmBores.mix(h ^ 0x51DEL)) < 0.5f ? -1f : 1f;
            float off = NestTrails.laidHalfWidth(t, u) + OFF_MIN
                    + (OFF_MAX - OFF_MIN) * SwarmBores.unit(SwarmBores.mix(h ^ 0x0FFL));
            out.add(new Item(kind(SwarmBores.mix(h ^ 0x6B1DL)), variant(SwarmBores.mix(h ^ 0x7A21L)),
                    cx - dy / dl * off * side, cy + dx / dl * off * side));
        }
    }

    /**
     * Up to {@code count} pieces round a mouth at (bx, by), clear of {@code colonies} and {@code roots}, appended to
     * {@code out}.
     */
    static void mouth(float bx, float by, long seed, int count, List<float[]> colonies, List<NestTrails.Trail> roots,
                      List<Item> out) {
        long h = seed;
        int laid = 0;
        for (int tries = 0; laid < count && tries < count * MOUTH_TRIES; tries++) {
            h = SwarmBores.mix(h + 1);
            double a = SwarmBores.unit(h) * Math.PI * 2;
            float r = MOUTH_IN + (MOUTH_OUT - MOUTH_IN) * SwarmBores.unit(SwarmBores.mix(h ^ 0xA5L));
            float x = bx + (float) Math.cos(a) * r, y = by + (float) Math.sin(a) * r;
            if (near(x, y, colonies, COLONY_CLEAR) || nearItems(x, y, out, laid, COLONY_CLEAR)
                    || onRoot(x, y, roots)) continue;
            out.add(new Item(kind(SwarmBores.mix(h ^ 0x6B1DL)), variant(SwarmBores.mix(h ^ 0x7A21L)), x, y));
            laid++;
        }
    }

    /** Whether (x, y) lies within {@link #ROOT_CLEAR} of the edge of any of {@code roots} as far as each has dug. */
    static boolean onRoot(float x, float y, List<NestTrails.Trail> roots) {
        for (NestTrails.Trail t : roots) {
            float[] pts = NestTrails.centreline(t);
            int n = pts.length / 2 - 1;
            for (int i = 0; i <= n && i <= t.reach() * n; i++) {
                float hw = NestTrails.laidHalfWidth(t, i / (float) n);
                if (Math.hypot(pts[i * 2] - x, pts[i * 2 + 1] - y) < hw + ROOT_CLEAR + NestTrails.STEP) return true;
            }
        }
        return false;
    }

    /** Clutches most, then pods, then husks. */
    static Kind kind(long hash) {
        float u = SwarmBores.unit(hash);
        return u < 0.4f ? Kind.CLUTCH : u < 0.75f ? Kind.PODS : Kind.HUSK;
    }

    private static int variant(long hash) {
        return 1 + (int) (SwarmBores.unit(hash) * VARIANTS);
    }

    private static boolean near(float x, float y, List<float[]> points, float d) {
        for (float[] p : points) if (Math.hypot(p[0] - x, p[1] - y) < d) return true;
        return false;
    }

    /** Whether one of the last {@code recent} pieces in {@code out} lies within {@code d} of (x, y). */
    private static boolean nearItems(float x, float y, List<Item> out, int recent, float d) {
        for (int i = out.size() - recent; i < out.size(); i++) {
            Item it = out.get(i);
            if (Math.hypot(it.x() - x, it.y() - y) < d) return true;
        }
        return false;
    }
}
