package com.dillon.starsectormarines.battle.world.gen.elevation;

import com.dillon.starsectormarines.battle.world.gen.elevation.ElevationPlan.Plateau;
import com.dillon.starsectormarines.battle.world.gen.elevation.ElevationPlan.Point;
import com.dillon.starsectormarines.battle.world.gen.elevation.ElevationPlan.Ramp;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntPredicate;

/**
 * Raises a mission's authored ridges ({@code elevation-nouns.md}, Ridge): a
 * band of ground the mission names, broken into hill segments wherever the
 * host's ground crosses it, each segment a plateau with one ramp up from the
 * side the mission names.
 *
 * <p>The mission says where the high ground is; the map says where it is
 * broken. A road, a lane, a building or a landing crossing the band is a pass
 * between two segments, never a cliff across it, because elevation never
 * touches protected ground. Generation only, and pure like
 * {@link PlateauPlanner}: it reads which cells it may use and returns a plan,
 * and a segment whose layout would not fit or would cut the host's ground
 * apart is left out.
 */
public final class RidgePlanner {

    private RidgePlanner() {}

    /**
     * One authored ridge: a rectangle of cells, inclusive, whose long axis runs
     * along x when {@code alongX}; its ramps climb from the side of the short
     * axis that {@code rampSide} points to (-1 toward the lower coordinate,
     * +1 toward the higher), {@code rampWidth} cells wide.
     */
    public record Band(int x0, int y0, int x1, int y1, boolean alongX, int rampSide, int rampWidth) {
        public Band {
            if (x1 < x0 || y1 < y0) throw new IllegalArgumentException("a band is a rectangle");
            if (rampSide != -1 && rampSide != 1) throw new IllegalArgumentException("a ramp climbs from one side");
            if (rampWidth < 1 || rampWidth > 16) throw new IllegalArgumentException("ramp width 1-16");
        }

        int length() { return alongX ? x1 - x0 + 1 : y1 - y0 + 1; }
        int thickness() { return alongX ? y1 - y0 + 1 : x1 - x0 + 1; }
    }

    /** Cells a segment must run to be worth raising, as a multiple of the band's thickness. */
    static final double MIN_RUN = 2.0;
    /** Cells kept between a segment's end and the ground that broke the band. */
    static final int END_GAP = 2;

    /**
     * @param free     cells a ridge, its faces and its apron may cover
     * @param closable whether closing these face cells keeps the host's ground connected
     * @return the ridges' plan, or {@code null} when no segment fits
     */
    public static ElevationPlan plan(int width, int height, long seed, List<Band> bands, IntPredicate free,
                                     PlateauPlanner.Closable closable) {
        List<Plateau> plateaus = new ArrayList<>();
        List<Ramp> ramps = new ArrayList<>();
        boolean[] footprint = new boolean[width * height];
        for (Band band : bands) {
            for (int[] run : runs(band, width, height, free)) {
                List<Plateau> nextPlateaus = new ArrayList<>(plateaus);
                List<Ramp> nextRamps = new ArrayList<>(ramps);
                if (!segment(band, run[0], run[1], width, height, nextPlateaus, nextRamps)) continue;
                ElevationLayout layout;
                try {
                    layout = ElevationLayout.resolve(new ElevationPlan(width, height, PlateauPlanner.FACE_WIDTH,
                            PlateauPlanner.SOUTH_FACE, .6, seed, nextPlateaus, nextRamps));
                } catch (IllegalArgumentException refused) {
                    continue;
                }
                boolean[] next = PlateauPlanner.footprint(layout);
                if (!PlateauPlanner.fits(next, footprint, width, height, free) || !closable.allows(layout)) continue;
                Ramp ramp = nextRamps.get(nextRamps.size() - 1);
                int footX = (int) Math.round(ramp.foot().x()), footY = (int) Math.round(ramp.foot().y());
                if (!free.test(footY * width + footX)) continue;
                plateaus = nextPlateaus;
                ramps = nextRamps;
                footprint = next;
            }
        }
        return plateaus.isEmpty() ? null
                : new ElevationPlan(width, height, PlateauPlanner.FACE_WIDTH, PlateauPlanner.SOUTH_FACE, .6, seed,
                plateaus, ramps);
    }

    /**
     * The band's unbroken runs along its long axis, each as {@code [from, to]}
     * inclusive: a position is broken when any cell across the band there, or
     * within the apron either side of it, is not free.
     */
    static List<int[]> runs(Band band, int width, int height, IntPredicate free) {
        int apron = (int) Math.ceil(PlateauPlanner.FACE_WIDTH + PlateauPlanner.SOUTH_FACE + 1);
        int from = band.alongX ? band.x0 : band.y0, to = band.alongX ? band.x1 : band.y1;
        int lo = (band.alongX ? band.y0 : band.x0) - apron, hi = (band.alongX ? band.y1 : band.x1) + apron;
        boolean[] open = new boolean[to - from + 1];
        for (int t = from; t <= to; t++) {
            boolean ok = true;
            for (int s = lo; s <= hi && ok; s++) {
                int x = band.alongX ? t : s, y = band.alongX ? s : t;
                if (x < 1 || y < 1 || x >= width - 1 || y >= height - 1 || !free.test(y * width + x)) ok = false;
            }
            open[t - from] = ok;
        }
        List<int[]> out = new ArrayList<>();
        int minRun = (int) Math.ceil(MIN_RUN * band.thickness());
        int start = -1;
        for (int i = 0; i <= open.length; i++) {
            boolean ok = i < open.length && open[i];
            if (ok && start < 0) start = i;
            if (!ok && start >= 0) {
                int a = from + start + END_GAP, b = from + i - 1 - END_GAP;
                if (b - a + 1 >= minRun) out.add(new int[]{a, b});
                start = -1;
            }
        }
        return out;
    }

    /** Adds one segment's plateau and its ramp, at the run's middle; false when the run is too short to hold them. */
    private static boolean segment(Band band, int a, int b, int width, int height,
                                   List<Plateau> plateaus, List<Ramp> ramps) {
        double radius = band.thickness() / 2.0;
        if (radius < 2) return false;
        double centre = band.alongX ? (band.y0 + band.y1) / 2.0 : (band.x0 + band.x1) / 2.0;
        // A capsule chain: its round ends lie inside the run, so the cliffs close round the segment's ends.
        double first = a + radius, last = b - radius;
        if (last < first) return false;
        List<Point> points = new ArrayList<>();
        int steps = Math.max(1, (int) Math.ceil((last - first) / radius));
        for (int i = 0; i <= steps; i++) {
            double t = first + (last - first) * i / steps;
            points.add(band.alongX ? new Point(t, centre) : new Point(centre, t));
        }
        plateaus.add(new Plateau(1, radius, points));
        double mid = (a + b) / 2.0;
        // A ramp cut deeper where it climbs a tall south face, as the plateau planner cuts its own.
        boolean climbsSouthFace = !band.alongX ? false : band.rampSide < 0;
        double south = climbsSouthFace ? PlateauPlanner.SOUTH_FACE : 0;
        double footAcross = clamp(centre + band.rampSide * (radius + 3), band.alongX ? height : width);
        double headAcross = clamp(centre + band.rampSide * (radius - 4 - south), band.alongX ? height : width);
        Point foot = band.alongX ? new Point(mid, footAcross) : new Point(footAcross, mid);
        Point head = band.alongX ? new Point(mid, headAcross) : new Point(headAcross, mid);
        ramps.add(new Ramp(foot, head, band.rampWidth));
        return true;
    }

    private static double clamp(double v, int size) {
        return Math.max(0, Math.min(size - 1, v));
    }
}
