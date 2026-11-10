package com.dillon.starsectormarines.battle.derelict;

import com.dillon.starsectormarines.battle.world.gen.ruins.RuinsDelve;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The security bulkheads (`derelict-watch.md`, Waking): the service passages the network keeps shut, each a
 * {@link #WIDTH}-cell way through a plant hall's wall to floor that is a long walk round from the hall. Until the
 * hunting stage a bulkhead's cells are wall, so the delve holds without them; at hunting they open for both sides. Each
 * is a leaf door ({@link RuinsDelve.LeafDoor}, answering) across its face on the hall's side. A pure rule over the
 * ground.
 */
public final class Bulkheads {

    /** Cells across a bulkhead: two, a corridor's width. */
    public static final int WIDTH = 2;
    /** The thickest wall a bulkhead is cut through, cells: the hull ring round a plant hall is three or four. */
    public static final int MAX_THICK = 4;
    /** The walk round a bulkhead must save, cells, to be worth the network keeping one. UNTUNED. */
    public static final int MIN_SAVING = 20;
    /** How far the walk round is followed, cells; floor beyond it is not counted as reached. */
    static final int WALK_LIMIT = 400;

    /** The ground a bulkhead is cut through. */
    public interface Ground {
        int width();

        int height();

        boolean floor(int x, int y);

        boolean wall(int x, int y);
    }

    /**
     * One bulkhead: its leaf door across the face on the hall's side, the wall cells it opens, and a floor cell
     * beside it whose ground the opened cells take.
     */
    public record Bulkhead(RuinsDelve.LeafDoor door, int[][] cells, int likeX, int likeY) {
    }

    private Bulkheads() {
    }

    /**
     * Lays at most one bulkhead a hall: through a wall up to {@link #MAX_THICK} thick on one of its faces, to floor
     * outside it the hall reaches only by a walk round of at least {@link #MIN_SAVING} cells, the one saving most.
     * Floor the hall cannot reach at all (a sealed store, the vault) is never opened into. A bulkhead never stands
     * beside one of {@code doors} nor shares a cell with another.
     */
    public static List<Bulkhead> lay(Ground ground, List<RuinsDelve.Area> halls, List<RuinsDelve.LeafDoor> doors) {
        List<Bulkhead> out = new ArrayList<>();
        Set<Long> taken = new HashSet<>();
        for (RuinsDelve.Area hall : halls) {
            int[] walk = walk(ground, hall);
            Bulkhead best = null;
            int bestSaving = MIN_SAVING - 1;
            // Each face: the outward step, and the run of cells along it.
            int[][] faces = {
                    {0, -1, hall.left(), hall.right(), hall.bottom(), 1},
                    {0, 1, hall.left(), hall.right(), hall.top(), 1},
                    {-1, 0, hall.bottom(), hall.top(), hall.left(), 0},
                    {1, 0, hall.bottom(), hall.top(), hall.right(), 0}};
            for (int[] face : faces) {
                int ox = face[0], oy = face[1];
                boolean alongX = face[5] == 1;
                for (int a = face[2]; a + WIDTH - 1 <= face[3]; a++) {
                    for (int thick = 1; thick <= MAX_THICK; thick++) {
                        int saving = Integer.MAX_VALUE;
                        boolean ok = true;
                        for (int k = 0; k < WIDTH && ok; k++) {
                            int ix = alongX ? a + k : face[4], iy = alongX ? face[4] : a + k;
                            if (!ground.floor(ix, iy)) ok = false;
                            for (int t = 1; t <= thick && ok; t++) {
                                if (!ground.wall(ix + ox * t, iy + oy * t)) ok = false;
                            }
                            int bx = ix + ox * (thick + 1), by = iy + oy * (thick + 1);
                            if (!ok || !inside(ground, bx, by) || !ground.floor(bx, by) || hall.contains(bx, by)) {
                                ok = false;
                            } else {
                                int d = walk[by * ground.width() + bx];
                                if (d < 0) ok = false;
                                else saving = Math.min(saving, d - (thick + 1));
                            }
                        }
                        if (!ok || saving <= bestSaving) continue;
                        Bulkhead candidate = bulkhead(alongX, a, face[4], ox, oy, thick);
                        if (besideDoor(candidate, doors) || clashes(candidate, taken)) continue;
                        best = candidate;
                        bestSaving = saving;
                    }
                }
            }
            if (best == null) continue;
            out.add(best);
            for (int[] c : best.cells()) taken.add(key(c[0], c[1]));
        }
        return out;
    }

    private static Bulkhead bulkhead(boolean alongX, int a, int line, int ox, int oy, int thick) {
        int[][] cells = new int[WIDTH * thick][];
        int n = 0;
        for (int t = 1; t <= thick; t++) {
            for (int k = 0; k < WIDTH; k++) {
                int ix = alongX ? a + k : line, iy = alongX ? line : a + k;
                cells[n++] = new int[] {ix + ox * t, iy + oy * t};
            }
        }
        int fx = alongX ? a : line + ox, fy = alongX ? line + oy : a;
        RuinsDelve.Area span = alongX ? new RuinsDelve.Area(fx, fy, fx + WIDTH - 1, fy)
                : new RuinsDelve.Area(fx, fy, fx, fy + WIDTH - 1);
        RuinsDelve.LeafDoor door = new RuinsDelve.LeafDoor(span, alongX, false, RuinsDelve.DoorStance.ANSWERS);
        int likeX = alongX ? a : line, likeY = alongX ? line : a;
        return new Bulkhead(door, cells, likeX, likeY);
    }

    private static boolean besideDoor(Bulkhead b, List<RuinsDelve.LeafDoor> doors) {
        for (RuinsDelve.LeafDoor door : doors) {
            RuinsDelve.Area s = door.span();
            for (int[] c : b.cells()) {
                if (c[0] >= s.left() - 1 && c[0] <= s.right() + 1 && c[1] >= s.bottom() - 1 && c[1] <= s.top() + 1) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean clashes(Bulkhead b, Set<Long> taken) {
        for (int[] c : b.cells()) if (taken.contains(key(c[0], c[1]))) return true;
        return false;
    }

    /** The walk, in cells, from the hall to each floor cell over floor, -1 where it does not reach. */
    static int[] walk(Ground ground, RuinsDelve.Area hall) {
        int w = ground.width();
        int[] dist = new int[w * ground.height()];
        Arrays.fill(dist, -1);
        ArrayDeque<int[]> open = new ArrayDeque<>();
        for (int y = hall.bottom(); y <= hall.top(); y++) {
            for (int x = hall.left(); x <= hall.right(); x++) {
                if (!inside(ground, x, y) || !ground.floor(x, y)) continue;
                dist[y * w + x] = 0;
                open.add(new int[] {x, y});
            }
        }
        int[][] steps = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!open.isEmpty()) {
            int[] c = open.poll();
            int d = dist[c[1] * w + c[0]];
            if (d >= WALK_LIMIT) continue;
            for (int[] s : steps) {
                int x = c[0] + s[0], y = c[1] + s[1];
                if (!inside(ground, x, y) || dist[y * w + x] >= 0 || !ground.floor(x, y)) continue;
                dist[y * w + x] = d + 1;
                open.add(new int[] {x, y});
            }
        }
        return dist;
    }

    private static boolean inside(Ground ground, int x, int y) {
        return x >= 0 && y >= 0 && x < ground.width() && y < ground.height();
    }

    private static long key(int x, int y) {
        return ((long) x << 32) ^ (y & 0xffffffffL);
    }
}
