package com.dillon.starsectormarines.battle.world.gen.nature;

import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.PatchField;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.forest.LandscapeAccess;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.function.BiPredicate;
import java.util.function.IntPredicate;

/**
 * A toxic world's wild ground ({@link SurfacePalette#bearsSludge}): pools of standing sludge, each ringed by crusted
 * sulphur, and broad sulphur flats across the stained rock.
 *
 * <p>Both are ordinary ground kinds drawn from the palette's own art. A pool is {@link GroundKind#WATER},
 * impassable and see-through as a brook is; a flat is {@link GroundKind#SAND}, open going. So nothing about movement,
 * cover or placement learns a new kind, and a pool closes ground exactly as water does.
 *
 * <p>A pool is all or nothing, like a mineral formation: every cell of it and of the ground within {@link #RING} of
 * it must be open wild ground, and closing it must leave the ground around it connected
 * ({@link LandscapeAccess#canClose}). So a pool never touches a building, a road, a reservation or another pool. Own
 * random substreams, so a world without sludge is the map it always was.
 */
public final class SludgeGround {
    private SludgeGround() {}

    private static final long POOL_SALT = 0x534c55444745L, FLAT_SALT = 0x53554c5048L;
    /** Map cells per pool asked for, and the spacing of the candidate lattice. */
    static final int CELLS_PER_POOL = 1800, LATTICE = 7;
    /** Radii of a pool's ellipse, in cells. */
    static final float MIN_RADIUS = 1.6f, MAX_RADIUS = 3.4f;
    /** How far the sulphur ring reaches past a pool's edge; it is also the open ground a pool needs around it. */
    static final int RING = 2;
    /** Patch noise above which stained rock is a sulphur flat, and the flats' feature size in cells. */
    static final float FLAT_THRESHOLD = .7f, FLAT_CELLS = 15f;

    /** Whether closing {@code closes} inside a box keeps the ground around it connected, given what closed earlier. */
    @FunctionalInterface
    public interface Closure {
        boolean allows(boolean[] closedEarlier, int left, int bottom, int right, int top,
                       BiPredicate<Integer, Integer> closes);
    }

    /** Lays the pools and flats on a world that bears sludge; any other world is left alone. */
    public static void paint(GenContext c, SurfacePalette surface, boolean[] reserved) {
        if (surface == null || !surface.bearsSludge()) return;
        int w = c.width, h = c.height;
        // A placed prop keeps its ground: a pool would drown it, or leave its sprite floating on the sludge.
        boolean[] propped = new boolean[w * h];
        for (Doodad d : c.doodads)
            for (int y = Math.max(0, d.cellY); y < Math.min(h, d.cellY + d.footprintCellsY); y++)
                for (int x = Math.max(0, d.cellX); x < Math.min(w, d.cellX + d.footprintCellsX); x++) propped[y * w + x] = true;
        IntPredicate open = i -> !propped[i] && c.topology.getGroundKind(i % w, i / w) == GroundKind.STONE
                && FormationBrush.eligible(c.grid, c.topology, reserved, i % w, i / w);
        boolean[] pools = pools(w, h, c.seed ^ POOL_SALT, open,
                (earlier, l, b, r, t, closes) -> LandscapeAccess.canClose(c.grid, earlier, l, b, r, t, closes));
        boolean[] sulphur = flats(w, h, c.seed ^ FLAT_SALT, pools);
        for (int i = 0; i < w * h; i++) {
            int x = i % w, y = i / w;
            if (pools[i]) {
                c.topology.setGroundKind(x, y, GroundKind.WATER);
                c.topology.setNatureOverlayIndex(x, y, -1);
                c.grid.setWalkable(x, y, false);
                c.grid.setSeeThrough(x, y, true);
                c.grid.setEdgeCoverSuppressed(x, y, true);
            } else if (sulphur[i] && c.topology.getGroundKind(x, y) == GroundKind.STONE
                    && c.topology.getSurface(x, y) == 0 && !c.topology.isWall(x, y)) {
                c.topology.setGroundKind(x, y, GroundKind.SAND);
            }
        }
    }

    /**
     * The pool cells: ragged ellipses on a shuffled lattice, each kept only when it, the ground within
     * {@link #RING} of it and the ground it would close all pass. {@code open} says whether a cell is wild ground a
     * pool may take or border.
     */
    public static boolean[] pools(int w, int h, long seed, IntPredicate open, Closure closure) {
        boolean[] pools = new boolean[w * h], taken = new boolean[w * h];
        int wanted = Math.max(1, w * h / CELLS_PER_POOL);
        Random random = new Random(seed);
        PatchField rag = new PatchField(seed ^ 0x52414747L, 3);
        int reach = (int) Math.ceil(MAX_RADIUS);
        int edge = reach + RING + 1;
        List<int[]> candidates = new ArrayList<>();
        for (int y = edge; y < h - edge; y += LATTICE) for (int x = edge; x < w - edge; x += LATTICE)
            candidates.add(new int[]{x, y});
        Collections.shuffle(candidates, random);
        int laid = 0;
        for (int[] centre : candidates) {
            if (laid >= wanted) break;
            float rx = MIN_RADIUS + random.nextFloat() * (MAX_RADIUS - MIN_RADIUS);
            float ry = MIN_RADIUS + random.nextFloat() * (MAX_RADIUS - MIN_RADIUS);
            boolean[] pool = new boolean[w * h];
            List<Integer> cells = new ArrayList<>();
            int l = w, b = h, r = -1, t = -1;
            for (int dy = -reach; dy <= reach; dy++) for (int dx = -reach; dx <= reach; dx++) {
                int x = centre[0] + dx, y = centre[1] + dy;
                float d = (dx * dx) / (rx * rx) + (dy * dy) / (ry * ry);
                // The edge wanders with the rag noise, so a pool is a puddle rather than a stamped oval.
                if (d > .75f + .5f * rag.sample(x, y)) continue;
                pool[y * w + x] = true;
                cells.add(y * w + x);
                l = Math.min(l, x); r = Math.max(r, x); b = Math.min(b, y); t = Math.max(t, y);
            }
            if (cells.size() < 5 || !fits(w, h, pool, l, b, r, t, open, taken)) continue;
            if (!closure.allows(pools, l, b, r, t, (x, y) -> pool[y * w + x])) continue;
            for (int i : cells) pools[i] = true;
            // The ring stays open ground between pools: another pool may neither take nor border it.
            for (int y = Math.max(0, b - 2 * RING); y <= Math.min(h - 1, t + 2 * RING); y++)
                for (int x = Math.max(0, l - 2 * RING); x <= Math.min(w - 1, r + 2 * RING); x++) taken[y * w + x] = true;
            laid++;
        }
        return pools;
    }

    /** Whether the pool and every cell within {@link #RING} of it is open and unclaimed. */
    private static boolean fits(int w, int h, boolean[] pool, int l, int b, int r, int t,
                                IntPredicate open, boolean[] taken) {
        for (int y = b - RING; y <= t + RING; y++) for (int x = l - RING; x <= r + RING; x++) {
            if (x < 0 || y < 0 || x >= w || y >= h) return false;
            if (!near(w, h, pool, x, y)) continue;
            int i = y * w + x;
            if (taken[i] || !open.test(i)) return false;
        }
        return true;
    }

    private static boolean near(int w, int h, boolean[] pool, int x, int y) {
        for (int dy = -RING; dy <= RING; dy++) for (int dx = -RING; dx <= RING; dx++) {
            int nx = x + dx, ny = y + dy;
            if (nx >= 0 && ny >= 0 && nx < w && ny < h && pool[ny * w + nx]) return true;
        }
        return false;
    }

    /** Sulphur: the flats where the patch noise runs high, and a ring of crust around every pool. */
    static boolean[] flats(int w, int h, long seed, boolean[] pools) {
        boolean[] out = new boolean[w * h];
        PatchField patches = new PatchField(seed, FLAT_CELLS);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int i = y * w + x;
            if (pools[i]) continue;
            boolean ring = false;
            for (int dy = -RING; dy <= RING && !ring; dy++) for (int dx = -RING; dx <= RING && !ring; dx++) {
                int nx = x + dx, ny = y + dy;
                ring = dx * dx + dy * dy <= RING * RING && nx >= 0 && ny >= 0 && nx < w && ny < h
                        && pools[ny * w + nx];
            }
            out[i] = ring || patches.sample(x, y) > FLAT_THRESHOLD;
        }
        return out;
    }
}
