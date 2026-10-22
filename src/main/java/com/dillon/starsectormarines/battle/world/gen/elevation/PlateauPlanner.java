package com.dillon.starsectormarines.battle.world.gen.elevation;

import com.dillon.starsectormarines.battle.world.gen.elevation.ElevationPlan.Plateau;
import com.dillon.starsectormarines.battle.world.gen.elevation.ElevationPlan.Point;
import com.dillon.starsectormarines.battle.world.gen.elevation.ElevationPlan.Ramp;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.IntPredicate;

/**
 * Places plateaus on a generated map's open ground, each with a ramp facing
 * the nearest ground the host keeps open (its roads, routes and places), so a
 * plateau overlooks what the battle is about and is climbed from it.
 *
 * <p>Generation only, and pure: it reads which cells it may use and returns a
 * plan; the caller stamps it. A candidate is kept only when the layout the
 * whole plan resolves to is valid (every plateau ramped, every ramp a tier
 * apart) and everything the new plateau touches, its drawn apron included,
 * lies on cells the caller allowed.
 */
public final class PlateauPlanner {

    /** Faces one cell wide, three where they look down the screen (the recommended look). */
    public static final int FACE_WIDTH = 1;
    public static final int SOUTH_FACE = 2;

    private PlateauPlanner() {}

    /** Smallest plateau radius worth placing, and the apron and gap kept around its faces. */
    private static final double MIN_RADIUS = 6, MAX_RADIUS = 11, APRON = 3;

    /**
     * @param free     cells a plateau, its faces and its apron may cover
     * @param kept     cells the host keeps open, which a ramp should face
     * @param closable whether closing these face cells keeps the host's ground connected
     */
    public static ElevationPlan plan(int width, int height, long seed, int count, Random random,
                                     IntPredicate free, IntPredicate kept, Closable closable) {
        double[] room = room(width, height, free);
        List<Integer> sites = new ArrayList<>();
        for (int i = 0; i < room.length; i++) if (room[i] >= MIN_RADIUS + APRON) sites.add(i);
        List<Plateau> plateaus = new ArrayList<>();
        List<Ramp> ramps = new ArrayList<>();
        boolean[] footprint = new boolean[width * height];
        for (int attempt = 0; attempt < count * 24 && plateaus.size() < count && !sites.isEmpty(); attempt++) {
            int site = sites.get(random.nextInt(sites.size()));
            int sx = site % width, sy = site / width;
            if (near(footprint, width, height, sx, sy, (int) (MIN_RADIUS + APRON))) continue;
            double space = Math.min(MAX_RADIUS, room[site] - APRON);
            double radius = MIN_RADIUS + random.nextDouble() * (space - MIN_RADIUS);
            double[] facing = towardKept(width, height, sx, sy, (int) (radius + 40), kept);
            if (facing == null) continue;
            double rampWidth = random.nextBoolean() ? 3 : 5;
            Plateau plateau = new Plateau(1, radius, List.of(new Point(sx, sy)));
            // A ramp cut deeper where it climbs a tall south face.
            double south = Math.max(0, -facing[1]) * SOUTH_FACE;
            Ramp ramp = new Ramp(
                    new Point(clamp(sx + facing[0] * (radius + 3), width), clamp(sy + facing[1] * (radius + 3), height)),
                    new Point(clamp(sx + facing[0] * (radius - 4 - south), width),
                            clamp(sy + facing[1] * (radius - 4 - south), height)),
                    rampWidth);
            List<Plateau> nextPlateaus = new ArrayList<>(plateaus);
            nextPlateaus.add(plateau);
            List<Ramp> nextRamps = new ArrayList<>(ramps);
            nextRamps.add(ramp);
            ElevationLayout layout;
            try {
                layout = ElevationLayout.resolve(new ElevationPlan(width, height, FACE_WIDTH, SOUTH_FACE, .6, seed,
                        nextPlateaus, nextRamps));
            } catch (IllegalArgumentException refused) {
                continue;
            }
            boolean[] next = footprint(layout);
            if (!fits(next, footprint, width, height, free) || !closable.allows(layout)) continue;
            // The ramp's foot opens onto ground a squad can stand on and reach.
            int footX = (int) Math.round(ramp.foot().x()), footY = (int) Math.round(ramp.foot().y());
            if (!free.test(footY * width + footX)) continue;
            plateaus = nextPlateaus;
            ramps = nextRamps;
            footprint = next;
        }
        return plateaus.isEmpty() ? null
                : new ElevationPlan(width, height, FACE_WIDTH, SOUTH_FACE, .6, seed, plateaus, ramps);
    }

    /** Whether closing the faces a layout adds keeps the host's ground connected. */
    @FunctionalInterface
    public interface Closable {
        boolean allows(ElevationLayout layout);
    }

    /**
     * Distance in cells from each free cell to the nearest cell that is not
     * free (or the map edge): how big a plateau could stand there.
     */
    static double[] room(int w, int h, IntPredicate free) {
        double[] d = new double[w * h];
        double big = w + h;
        for (int i = 0; i < d.length; i++) {
            int x = i % w, y = i / w;
            d[i] = !free.test(i) || x == 0 || y == 0 || x == w - 1 || y == h - 1 ? 0 : big;
        }
        double diagonal = Math.sqrt(2);
        for (int y = 1; y < h - 1; y++) for (int x = 1; x < w - 1; x++) {
            int i = y * w + x;
            d[i] = Math.min(d[i], Math.min(d[i - 1] + 1, d[i - w] + 1));
            d[i] = Math.min(d[i], Math.min(d[i - w - 1], d[i - w + 1]) + diagonal);
        }
        for (int y = h - 2; y > 0; y--) for (int x = w - 2; x > 0; x--) {
            int i = y * w + x;
            d[i] = Math.min(d[i], Math.min(d[i + 1] + 1, d[i + w] + 1));
            d[i] = Math.min(d[i], Math.min(d[i + w + 1], d[i + w - 1]) + diagonal);
        }
        return d;
    }

    /** Every cell a layout touches or draws an apron on. */
    private static boolean[] footprint(ElevationLayout layout) {
        int w = layout.width(), h = layout.height();
        double[] art = layout.artCorners(1);
        boolean[] out = new boolean[w * h];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            double near = Math.max(Math.max(art[y * (w + 1) + x], art[y * (w + 1) + x + 1]),
                    Math.max(art[(y + 1) * (w + 1) + x], art[(y + 1) * (w + 1) + x + 1]));
            out[y * w + x] = layout.touched(x, y) || near >= -1.5;
        }
        return out;
    }

    /**
     * What the new plateau adds lies on free cells, at least two cells from
     * every plateau already placed, so plateaus never merge.
     */
    private static boolean fits(boolean[] next, boolean[] before, int w, int h, IntPredicate free) {
        for (int i = 0; i < next.length; i++) {
            if (!next[i] || before[i]) continue;
            if (!free.test(i) || near(before, w, h, i % w, i / w, 2)) return false;
        }
        return true;
    }

    private static boolean near(boolean[] cells, int w, int h, int x, int y, int r) {
        for (int yy = Math.max(0, y - r); yy <= Math.min(h - 1, y + r); yy++)
            for (int xx = Math.max(0, x - r); xx <= Math.min(w - 1, x + r); xx++)
                if (cells[yy * w + xx]) return true;
        return false;
    }

    /** Unit direction from a plateau's center to the nearest kept cell within reach, or null. */
    private static double[] towardKept(int w, int h, int cx, int cy, int reach, IntPredicate kept) {
        for (int r = 1; r <= reach; r++) {
            double bestX = 0, bestY = 0;
            int found = 0;
            for (int dy = -r; dy <= r; dy++) {
                for (int dx = -r; dx <= r; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) != r) continue;
                    int x = cx + dx, y = cy + dy;
                    if (x < 0 || y < 0 || x >= w || y >= h || !kept.test(y * w + x)) continue;
                    bestX += dx;
                    bestY += dy;
                    found++;
                }
            }
            if (found > 0) {
                double length = Math.hypot(bestX, bestY);
                if (length < 1e-6) return new double[]{0, -1};
                return new double[]{bestX / length, bestY / length};
            }
        }
        return null;
    }

    private static double clamp(double v, int size) {
        return Math.max(1, Math.min(size - 2, v));
    }
}
