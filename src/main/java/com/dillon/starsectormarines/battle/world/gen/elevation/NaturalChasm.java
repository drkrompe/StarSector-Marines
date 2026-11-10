package com.dillon.starsectormarines.battle.world.gen.elevation;

import java.util.function.IntPredicate;

/**
 * A natural drop across open country ({@code voids.md}, placement in generated maps): a gorge, canyon, crevasse or
 * rift running edge to edge across a generated outdoor map, its look picked by the world's palette
 * ({@code NaturalVoidZoning}). GL-free; the landscape pass asks it once and stamps what it returns.
 *
 * <p>It runs from one edge of the map to the opposite one, wandering, its width swelling and narrowing. It takes only open ground
 * ({@code free}): the caller's reservations keep roads, buildings, doorways, fixtures, the landing and the routes
 * between required places out of it, so each of those that crosses its line is left standing across it as a
 * bridge. It is opened by {@link #OPEN_RADIUS} so no sliver of drop is left; then every {@link #CROSS_EVERY} cells
 * along its line a crossing is left standing, a strip of the ground it runs through, which the landscape decks as a
 * footbridge; then pieces smaller than {@link #MIN_PIECE} are dropped.
 *
 * <p>Then it is proved: every stretch of walkable ground connected before stays connected after, through the
 * crossings. A small pocket of open ground the drop would strand ({@link #MAX_POCKET} cells or fewer) is taken into
 * it; any other stranding refuses that line. Lines both ways across the map are tried, through its middle and
 * through the country to either side of a settlement, and the one cutting most is kept; a map none of them fits
 * keeps no drop.
 */
public final class NaturalChasm {

    /** The drop's half width at its narrowest and how far it swells past that, in cells. UNTUNED. */
    static final float HALF_WIDTH = 3.5f, SWELL = 2.5f;
    /** Cells along its line between natural crossings, and a crossing's width. UNTUNED. */
    public static final int CROSS_EVERY = 34, CROSS_WIDTH = 4;
    /** Cells the drop is opened by: no part of it is narrower than twice this plus one. */
    static final int OPEN_RADIUS = 2;
    /** The fewest cells a piece of drop keeps, and a map's whole drop. UNTUNED. */
    static final int MIN_PIECE = 60, MIN_CELLS = 300;
    /** The largest pocket of open ground the drop may swallow rather than strand. */
    static final int MAX_POCKET = 40;
    /** Where across the map its line is tried, as shares of the side it crosses. */
    static final float[] LINES = {.5f, .36f, .64f, .22f, .78f, .12f, .88f};
    /** How far its line wanders either side, as a share of the side it crosses. */
    static final float WANDER = .09f;

    /** Cells along its line a deck looks for the drop on either side of it. */
    static final int DECK_REACH = 6;

    private NaturalChasm() { }

    /**
     * A drop and the decks over it, row-major: the crossings' cells of open ground that stand with the drop on both
     * sides along the line, decked as footbridges.
     */
    public record Cut(boolean[] drop, boolean[] decks) { }

    /**
     * The drop's cells over a {@code w} by {@code h} map, row-major, or {@code null} when no line fits.
     *
     * @param free     cells the drop may take: open natural ground nothing is reserved on
     * @param walkable cells anything stands on now
     */
    public static Cut plan(int w, int h, IntPredicate free, IntPredicate walkable, int seed) {
        int[] before = OpenPit.label(w, h, walkable);
        boolean[] best = null, bestCrossings = null;
        boolean bestAlong = true;
        int bestCells = 0;
        for (int attempt = 0; attempt < 2 * LINES.length; attempt++) {
            boolean along = attempt % 2 == 0;
            boolean[] crossings = new boolean[w * h];
            boolean[] cut = line(w, h, free, walkable, along, LINES[attempt / 2], seed + 31 * attempt, crossings);
            if (cut == null) continue;
            cut = proved(w, h, cut, before, free, walkable);
            if (cut == null) continue;
            int cells = OpenPit.count(cut);
            if (cells > bestCells) { bestCells = cells; best = cut; bestCrossings = crossings; bestAlong = along; }
        }
        return best == null ? null : new Cut(best, decks(w, h, best, bestCrossings, bestAlong, free, walkable));
    }

    /** The crossings' open ground with the drop on both sides of it along the line. */
    static boolean[] decks(int w, int h, boolean[] drop, boolean[] crossings, boolean along, IntPredicate free,
                           IntPredicate walkable) {
        boolean[] out = new boolean[w * h];
        int dx = along ? 1 : 0, dy = along ? 0 : 1;
        for (int i = 0; i < out.length; i++) {
            if (!crossings[i] || drop[i] || !free.test(i) || !walkable.test(i)) continue;
            int x = i % w, y = i / w;
            out[i] = dropWithin(drop, w, h, x, y, dx, dy) && dropWithin(drop, w, h, x, y, -dx, -dy);
        }
        return out;
    }

    private static boolean dropWithin(boolean[] drop, int w, int h, int x, int y, int dx, int dy) {
        for (int s = 1; s <= DECK_REACH; s++) {
            int px = x + dx * s, py = y + dy * s;
            if (px < 0 || py < 0 || px >= w || py >= h) return false;
            if (drop[py * w + px]) return true;
        }
        return false;
    }

    /**
     * One line's cut before it is proved, or {@code null} when too little of it is open ground.
     *
     * @param along     whether it runs west to east, else north to south
     * @param crossings filled with the crossings' strips across its band
     */
    static boolean[] line(int w, int h, IntPredicate free, IntPredicate walkable, boolean along, float across,
                          int seed, boolean[] crossings) {
        int length = along ? w : h, span = along ? h : w;
        float phase = (seed & 0xff) / 40f, phase2 = ((seed >> 8) & 0xff) / 40f;
        int crossOffset = Math.floorMod(seed >> 4, CROSS_EVERY);
        float mid = across * span, wander = WANDER * span;
        boolean[] cut = new boolean[w * h];
        for (int t = 0; t < length; t++) {
            // A crossing: a strip of the country left standing across the drop, decked over.
            boolean crossing = Math.floorMod(t - crossOffset, CROSS_EVERY) < CROSS_WIDTH;
            float centre = mid + wander * ((float) Math.sin(t / 29f + phase) * .7f
                    + (float) Math.sin(t / 11f + phase2) * .3f);
            float half = HALF_WIDTH + SWELL * (.5f + .5f * (float) Math.sin(t / 17f + phase2 * 2f));
            // A crossing's strip reaches past the banks, so its deck runs from ground to ground.
            float reach = crossing ? half + 1.5f : half;
            int lo = (int) Math.floor(centre - reach), hi = (int) Math.ceil(centre + reach);
            for (int s = Math.max(0, lo); s <= Math.min(span - 1, hi); s++) {
                if (Math.abs(s + .5f - centre) > reach) continue;
                int i = along ? s * w + t : t * w + s;
                if (crossing) crossings[i] = true;
                if (free.test(i) && walkable.test(i)) cut[i] = true;
            }
        }
        // Opened before the crossings are taken out, so the drop meets each deck square and runs on under it.
        cut = and(OpenPit.grow(OpenPit.shrink(cut, w, h, OPEN_RADIUS), w, h, OPEN_RADIUS), cut);
        for (int i = 0; i < cut.length; i++) if (crossings[i]) cut[i] = false;
        cut = withoutSmallPieces(cut, w, h);
        return OpenPit.count(cut) < MIN_CELLS ? null : cut;
    }

    /**
     * The cut with every small pocket of open ground it strands taken in, or {@code null} when it strands any other
     * ground: a piece of a connected stretch of walkable ground, other than the piece holding most of it, that is
     * larger than {@link #MAX_POCKET} or not wholly open ground.
     */
    static boolean[] proved(int w, int h, boolean[] cut, int[] before, IntPredicate free, IntPredicate walkable) {
        return proved(w, h, cut, before, free, walkable, MAX_POCKET);
    }

    /** As {@link #proved(int, int, boolean[], int[], IntPredicate, IntPredicate)}, taking pockets up to {@code maxPocket}. */
    static boolean[] proved(int w, int h, boolean[] cut, int[] before, IntPredicate free, IntPredicate walkable,
                            int maxPocket) {
        boolean[] out = cut.clone();
        int[] after = OpenPit.label(w, h, i -> walkable.test(i) && !cut[i]);
        int pieces = 0, stretches = 0;
        for (int v : after) pieces = Math.max(pieces, v);
        for (int v : before) stretches = Math.max(stretches, v);
        int[] size = new int[pieces + 1];
        for (int v : after) if (v > 0) size[v]++;
        int[] main = new int[stretches + 1];
        for (int i = 0; i < after.length; i++) {
            if (after[i] == 0) continue;
            int s = before[i];
            if (main[s] == 0 || size[after[i]] > size[main[s]]) main[s] = after[i];
        }
        boolean[] stranded = new boolean[pieces + 1];
        for (int i = 0; i < after.length; i++) {
            int p = after[i];
            if (p == 0 || p == main[before[i]]) continue;
            if (size[p] > maxPocket || !free.test(i)) return null;
            stranded[p] = true;
        }
        for (int i = 0; i < after.length; i++) if (after[i] > 0 && stranded[after[i]]) out[i] = true;
        return out;
    }

    private static boolean[] withoutSmallPieces(boolean[] m, int w, int h) {
        int[] label = OpenPit.label(w, h, i -> m[i]);
        int pieces = 0;
        for (int v : label) pieces = Math.max(pieces, v);
        int[] size = new int[pieces + 1];
        for (int v : label) if (v > 0) size[v]++;
        boolean[] out = new boolean[m.length];
        for (int i = 0; i < m.length; i++) out[i] = label[i] > 0 && size[label[i]] >= MIN_PIECE;
        return out;
    }

    private static boolean[] and(boolean[] a, boolean[] b) {
        boolean[] out = new boolean[a.length];
        for (int i = 0; i < a.length; i++) out[i] = a[i] && b[i];
        return out;
    }
}
