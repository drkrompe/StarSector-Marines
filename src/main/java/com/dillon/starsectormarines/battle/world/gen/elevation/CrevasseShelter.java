package com.dillon.starsectormarines.battle.world.gen.elevation;

import java.util.function.IntPredicate;

/**
 * A town sheltered in a great crevasse on a frozen world ({@code voids.md}, the crevasse town): the battle is fought
 * on the crevasse's floor and the settlement stands on it, while the crevasse's walls rise along a pair of the map's
 * opposite edges as terraced ice banks ({@code NaturalRise.bank}), out of the gale that streams snow over their lips far
 * above. GL-free; the landscape pass asks it once and stamps what it returns.
 *
 * <p>Each wall runs in from its edge to a wandering line {@link #DEPTH} cells deep, give or take {@link #WANDER}. It
 * takes only open ground ({@code free}): the reservations keep the settlement, its roads, the landing and its
 * approach, the spawns and the routes between required places out of it, so where any of those reaches an edge the
 * wall is notched by a pass. It is opened by {@link #OPEN_RADIUS} so no sliver of wall stands alone, and pieces
 * smaller than {@link #MIN_PIECE} are dropped. Then it is proved as a drop is ({@link NaturalChasm#proved}): every
 * stretch of walkable ground connected before stays connected, a pocket of open ground of {@link #MAX_POCKET} cells
 * or fewer it would strand (dead ground behind the wall) is taken into the wall, and anything else refuses the
 * shelter. A map whose walls would stand along less than
 * {@link #MIN_RUN} of their edges keeps none: a town that fills its map to the edges has no crevasse to shelter
 * in.
 */
public final class CrevasseShelter {

    /** How deep each wall stands in from its edge, in cells, and how far its foot wanders either way. UNTUNED. */
    static final float DEPTH = 17f, WANDER = 4f;
    /** Cells the walls are opened by. */
    static final int OPEN_RADIUS = 2;
    /** The fewest cells a piece of wall keeps. */
    static final int MIN_PIECE = 80;
    /**
     * The largest pocket of open ground a wall may take in rather than strand: ground left between a wall and the
     * map's edge is dead ground behind the crevasse's wall, and goes into it. UNTUNED.
     */
    static final int MAX_POCKET = 600;
    /** The share of the long edges the walls must stand along for the map to be a crevasse at all. */
    static final float MIN_RUN = .5f;

    private CrevasseShelter() { }

    /**
     * The walls' cells over a {@code w} by {@code h} map, row-major, or {@code null} when the map has too little open
     * ground along its long edges or the walls would strand ground.
     *
     * @param along    whether the walls stand along the north and south edges, else the west and east
     * @param free     cells a wall may take: open ground nothing is reserved on
     * @param takeable cells a stranded pocket behind a wall may be taken in over: open ground, reserved or not
     * @param walkable cells anything stands on now
     */
    public static boolean[] plan(int w, int h, boolean along, IntPredicate free, IntPredicate takeable,
                                 IntPredicate walkable, int seed) {
        int length = along ? w : h, span = along ? h : w;
        float phase = (seed & 0xff) / 40f, phase2 = ((seed >> 8) & 0xff) / 40f;
        boolean[] wall = new boolean[w * h];
        for (int t = 0; t < length; t++) {
            for (int side = 0; side < 2; side++) {
                float p = side == 0 ? phase : phase2;
                float deep = DEPTH + WANDER * ((float) Math.sin(t / 23f + p) * .65f + (float) Math.sin(t / 9f + 2f * p) * .35f);
                for (int d = 0; d < Math.min(span / 2, (int) Math.ceil(deep)); d++) {
                    if (d + .5f > deep) continue;
                    int s = side == 0 ? d : span - 1 - d;
                    int i = along ? s * w + t : t * w + s;
                    if (free.test(i) && walkable.test(i)) wall[i] = true;
                }
            }
        }
        wall = and(OpenPit.grow(OpenPit.shrink(wall, w, h, OPEN_RADIUS), w, h, OPEN_RADIUS), wall);
        wall = withoutSmallPieces(wall, w, h);
        if (run(wall, w, h, along) < MIN_RUN) return null;
        return NaturalChasm.proved(w, h, wall, OpenPit.label(w, h, walkable), takeable, walkable, MAX_POCKET);
    }

    /** The share of the long edges, both together, along which a wall stands at the edge. */
    static float run(boolean[] wall, int w, int h, boolean along) {
        int length = along ? w : h, span = along ? h : w, stood = 0;
        for (int t = 0; t < length; t++) {
            for (int side = 0; side < 2; side++) {
                boolean any = false;
                for (int d = 0; d < 6 && !any; d++) {
                    int s = side == 0 ? d : span - 1 - d;
                    any = wall[along ? s * w + t : t * w + s];
                }
                if (any) stood++;
            }
        }
        return stood / (2f * length);
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
