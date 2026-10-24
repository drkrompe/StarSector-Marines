package com.dillon.starsectormarines.battle.world.gen.elevation;

import com.dillon.starsectormarines.battle.world.gen.elevation.ElevationPlan.Plateau;
import com.dillon.starsectormarines.battle.world.gen.elevation.ElevationPlan.Point;
import com.dillon.starsectormarines.battle.world.gen.elevation.ElevationPlan.Ramp;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntPredicate;

/**
 * Raises a mission's authored ridges ({@code elevation-nouns.md}, Ridges): a
 * band of ground the mission names along a path, broken into hill segments
 * wherever the host's ground crosses it, each segment a plateau with one ramp
 * up from the side the mission names.
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
     * One authored ridge: a band {@code thickness} cells through, centred on a
     * path of cell points. Its ramps climb from the side {@code rampSide} names:
     * +1 is the left of the path's direction of travel (counter-clockwise from
     * it, in cell coordinates), -1 the right. {@code rampWidth} cells wide.
     */
    public record Band(List<Point> path, int thickness, int rampSide, int rampWidth) {
        public Band {
            path = List.copyOf(path);
            if (path.size() < 2) throw new IllegalArgumentException("a band runs along at least two points");
            if (thickness < 4 || thickness > 64) throw new IllegalArgumentException("thickness 4-64");
            if (rampSide != -1 && rampSide != 1) throw new IllegalArgumentException("a ramp climbs from one side");
            if (rampWidth < 1 || rampWidth > 16) throw new IllegalArgumentException("ramp width 1-16");
        }

        /**
         * A straight band filling a rectangle of cells, inclusive, along its long
         * axis ({@code alongX}); {@code toward} names the short-axis side its ramps
         * climb from, -1 the lower coordinate and +1 the higher.
         */
        public static Band rect(int x0, int y0, int x1, int y1, boolean alongX, int toward, int rampWidth) {
            if (x1 < x0 || y1 < y0) throw new IllegalArgumentException("a band is a rectangle");
            if (alongX) {
                double y = (y0 + y1) / 2.0;
                // Travelling +x, the left (counter-clockwise) side is +y.
                return new Band(List.of(new Point(x0, y), new Point(x1, y)), y1 - y0 + 1, toward, rampWidth);
            }
            double x = (x0 + x1) / 2.0;
            // Travelling +y, the left side is -x.
            return new Band(List.of(new Point(x, y0), new Point(x, y1)), x1 - x0 + 1, -toward, rampWidth);
        }
    }

    /** Cells a segment must run to be worth raising, as a multiple of the band's thickness. */
    static final double MIN_RUN = 2.0;
    /** Cells kept between a segment's end and the ground that broke the band. */
    static final int END_GAP = 2;

    /**
     * @param free     cells a ridge, its faces and its apron may cover
     * @param closable whether closing these face cells keeps the host's ground connected; asked
     *                 last for each segment, so a layout it allows is the one kept
     * @return the ridges' plan, or {@code null} when no segment fits
     */
    public static ElevationPlan plan(int width, int height, long seed, List<Band> bands, IntPredicate free,
                                     PlateauPlanner.Closable closable) {
        return plan(width, height, seed, bands, free, closable, List.of());
    }

    /**
     * The ridges with {@code terraces} already standing ({@link TerracePlanner}): each terrace's top and ramps go
     * into the plan first, and a band runs on through a terrace's zone and merges with its top rather than breaking
     * into a pass there, so built ground stands on the crest. A segment's ramp is never cut into a terrace.
     *
     * @param terraces planned terraces that stand, already proved to fit and to keep the host's ground connected
     */
    public static ElevationPlan plan(int width, int height, long seed, List<Band> bands, IntPredicate free,
                                     PlateauPlanner.Closable closable, List<TerracePlanner.Planned> terraces) {
        List<Plateau> plateaus = new ArrayList<>();
        List<Ramp> ramps = new ArrayList<>();
        boolean[] footprint = new boolean[width * height];
        boolean[] zone = null, terraced = null, terraceFaces = null;
        if (!terraces.isEmpty()) {
            zone = new boolean[width * height];
            for (TerracePlanner.Planned terrace : terraces) {
                plateaus.addAll(terrace.plateaus());
                ramps.addAll(terrace.ramps());
                for (int i = 0; i < zone.length; i++) zone[i] |= terrace.zone()[i];
            }
            // What the terraces cover is already theirs: a segment is asked only about what it adds.
            ElevationLayout standing = ElevationLayout.resolve(new ElevationPlan(width, height,
                    PlateauPlanner.FACE_WIDTH, PlateauPlanner.SOUTH_FACE, .6, seed, plateaus, ramps));
            footprint = PlateauPlanner.footprint(standing);
            terraced = footprint.clone();
            terraceFaces = new boolean[width * height];
            for (int i = 0; i < terraceFaces.length; i++) terraceFaces[i] = standing.isFace(i % width, i / width);
        }
        // Segments may not merge with each other; with a terrace is what they are let through it for.
        boolean[] segments = new boolean[width * height];
        boolean[] through = zone;
        IntPredicate runFree = through == null ? free : i -> free.test(i) || through[i];
        IntPredicate rampClear = through == null ? i -> true : i -> !near(through, width, height, i % width, i / width, 3);
        for (Band band : bands) {
            List<Sample> samples = samples(band);
            for (int[] run : runs(band, samples, width, height, runFree)) {
                List<Plateau> nextPlateaus = new ArrayList<>(plateaus);
                List<Ramp> nextRamps = new ArrayList<>(ramps);
                if (!segment(band, samples.subList(run[0], run[1] + 1), width, height, nextPlateaus, nextRamps,
                        rampClear)) {
                    continue;
                }
                ElevationLayout layout;
                try {
                    layout = ElevationLayout.resolve(new ElevationPlan(width, height, PlateauPlanner.FACE_WIDTH,
                            PlateauPlanner.SOUTH_FACE, .6, seed, nextPlateaus, nextRamps));
                } catch (IllegalArgumentException refused) {
                    continue;
                }
                boolean[] next = PlateauPlanner.footprint(layout);
                if (through == null ? !PlateauPlanner.fits(next, footprint, width, height, free)
                        : !fitsThrough(layout, next, footprint, segments, through, terraceFaces, width, height, free)) continue;
                Ramp ramp = nextRamps.get(nextRamps.size() - 1);
                int footX = (int) Math.round(ramp.foot().x()), footY = (int) Math.round(ramp.foot().y());
                if (!free.test(footY * width + footX)) continue;
                // Asked last, so a closable that allows a layout knows it is the one kept.
                if (!closable.allows(layout)) continue;
                plateaus = nextPlateaus;
                ramps = nextRamps;
                if (through != null) {
                    for (int i = 0; i < next.length; i++) segments[i] |= next[i] && !through[i] && !terraced[i];
                }
                footprint = next;
            }
        }
        return plateaus.isEmpty() ? null
                : new ElevationPlan(width, height, PlateauPlanner.FACE_WIDTH, PlateauPlanner.SOUTH_FACE, .6, seed,
                plateaus, ramps);
    }

    /**
     * {@link PlateauPlanner#fits} with terraces standing: what a segment adds outside them lies on free cells and
     * at least two cells from every other segment, but it may run into a terrace and merge with it.
     */
    private static boolean fitsThrough(ElevationLayout layout, boolean[] next, boolean[] before, boolean[] segments,
                                       boolean[] through, boolean[] terraceFaces, int w, int h, IntPredicate free) {
        for (int i = 0; i < next.length; i++) {
            if (!next[i]) continue;
            // A face the merge makes anywhere, a terrace's included, stands on free ground like any other.
            if (layout.isFace(i % w, i / w) && !terraceFaces[i] && !free.test(i)) return false;
            if (before[i] || through[i]) continue;
            if (!free.test(i) || near(segments, w, h, i % w, i / w, 2)) return false;
        }
        return true;
    }

    private static boolean near(boolean[] cells, int w, int h, int x, int y, int r) {
        for (int yy = Math.max(0, y - r); yy <= Math.min(h - 1, y + r); yy++)
            for (int xx = Math.max(0, x - r); xx <= Math.min(w - 1, x + r); xx++)
                if (cells[yy * w + xx]) return true;
        return false;
    }

    /**
     * The ground these bands need held open before the places grow: every cell
     * a segment's plateau, faces or apron could cover, plus {@code margin}
     * cells either side for the reservations the landscape pass keeps round
     * whatever was built beside it. Held ground left open is what lets a band
     * fit among built places (`elevation-nouns.md`, Held ground).
     */
    public static boolean[][] heldGround(List<Band> bands, int width, int height, int margin) {
        boolean[][] held = new boolean[width][height];
        int apron = PlateauPlanner.FACE_WIDTH + PlateauPlanner.SOUTH_FACE + 1;
        for (Band band : bands) {
            int reach = (int) Math.ceil(band.thickness() / 2.0) + apron + margin;
            for (Sample s : samples(band)) {
                int cx = (int) Math.round(s.x()), cy = (int) Math.round(s.y());
                for (int x = Math.max(0, cx - reach); x <= Math.min(width - 1, cx + reach); x++) {
                    for (int y = Math.max(0, cy - reach); y <= Math.min(height - 1, cy + reach); y++) {
                        if ((x - cx) * (x - cx) + (y - cy) * (y - cy) <= reach * reach) held[x][y] = true;
                    }
                }
            }
        }
        return held;
    }

    /** One step along a band's path: where it is and the unit normal to its left. */
    record Sample(double x, double y, double nx, double ny) { }

    /** The band's path walked a cell at a time. */
    static List<Sample> samples(Band band) {
        List<Sample> out = new ArrayList<>();
        List<Point> path = band.path();
        for (int i = 0; i + 1 < path.size(); i++) {
            Point a = path.get(i), b = path.get(i + 1);
            double dx = b.x() - a.x(), dy = b.y() - a.y(), len = Math.hypot(dx, dy);
            if (len < 1e-9) continue;
            double ux = dx / len, uy = dy / len;
            int steps = (int) Math.ceil(len);
            for (int s = 0; s < steps; s++) {
                double t = s / (double) steps;
                out.add(new Sample(a.x() + dx * t, a.y() + dy * t, -uy, ux));
            }
        }
        Point last = path.get(path.size() - 1);
        Sample end = out.get(out.size() - 1);
        out.add(new Sample(last.x(), last.y(), end.nx(), end.ny()));
        return out;
    }

    /** {@link #runs(Band, List, int, int, IntPredicate)} over the band's own samples. */
    static List<int[]> runs(Band band, int width, int height, IntPredicate free) {
        return runs(band, samples(band), width, height, free);
    }

    /**
     * The band's unbroken runs, each as {@code [from, to]} sample indices,
     * inclusive: a sample is broken when any cell across the band there, or
     * within the apron either side of it, is not free.
     */
    static List<int[]> runs(Band band, List<Sample> samples, int width, int height, IntPredicate free) {
        int apron = PlateauPlanner.FACE_WIDTH + PlateauPlanner.SOUTH_FACE + 1;
        double reach = band.thickness() / 2.0 + apron;
        boolean[] open = new boolean[samples.size()];
        for (int i = 0; i < samples.size(); i++) {
            Sample s = samples.get(i);
            boolean ok = true;
            for (double d = -reach; d <= reach && ok; d += 0.5) {
                int x = (int) Math.round(s.x() + s.nx() * d), y = (int) Math.round(s.y() + s.ny() * d);
                if (x < 1 || y < 1 || x >= width - 1 || y >= height - 1 || !free.test(y * width + x)) ok = false;
            }
            open[i] = ok;
        }
        List<int[]> out = new ArrayList<>();
        int minRun = (int) Math.ceil(MIN_RUN * band.thickness());
        int start = -1;
        for (int i = 0; i <= open.length; i++) {
            boolean ok = i < open.length && open[i];
            if (ok && start < 0) start = i;
            if (!ok && start >= 0) {
                int a = start + END_GAP, b = i - 1 - END_GAP;
                if (b - a + 1 >= minRun) out.add(new int[]{a, b});
                start = -1;
            }
        }
        return out;
    }

    /**
     * Adds one segment's plateau and its ramp, at the run's middle or, where that is not clear, the nearest point
     * of the run that is; false when the run is too short to hold them or no point of it is clear.
     */
    private static boolean segment(Band band, List<Sample> run, int width, int height,
                                   List<Plateau> plateaus, List<Ramp> ramps, IntPredicate rampClear) {
        double radius = band.thickness() / 2.0;
        // A capsule chain: its round ends lie inside the run, so the cliffs close round the segment's ends.
        int trim = (int) Math.ceil(radius);
        if (run.size() <= 2 * trim) return false;
        List<Sample> body = run.subList(trim, run.size() - trim);
        List<Point> points = new ArrayList<>();
        int stride = Math.max(1, (int) Math.floor(radius));
        for (int i = 0; i < body.size(); i += stride) points.add(new Point(body.get(i).x(), body.get(i).y()));
        Sample tail = body.get(body.size() - 1);
        points.add(new Point(tail.x(), tail.y()));
        // Outward from the middle, alternately each way, to the first point whose ramp stands clear.
        int middle = run.size() / 2;
        for (int k = 0; k < 2 * run.size(); k++) {
            int at = middle + (k % 2 == 0 ? k / 2 : -(k / 2 + 1));
            if (at < trim || at >= run.size() - trim) continue;
            Sample mid = run.get(at);
            double fx = mid.nx() * band.rampSide(), fy = mid.ny() * band.rampSide();
            // A ramp cut deeper where it climbs a tall south face, as the plateau planner cuts its own.
            double south = Math.max(0, -fy) * PlateauPlanner.SOUTH_FACE;
            Point foot = new Point(clamp(mid.x() + fx * (radius + 3), width), clamp(mid.y() + fy * (radius + 3), height));
            Point head = new Point(clamp(mid.x() + fx * (radius - 4 - south), width),
                    clamp(mid.y() + fy * (radius - 4 - south), height));
            if (!rampClear(foot, head, band.rampWidth(), width, height, rampClear)) continue;
            plateaus.add(new Plateau(1, radius, points));
            ramps.add(new Ramp(foot, head, band.rampWidth()));
            return true;
        }
        return false;
    }

    /** Whether the ramp's ends and the corners of its corridor are all cells {@code clear} allows. */
    private static boolean rampClear(Point foot, Point head, double rampWidth, int width, int height,
                                     IntPredicate clear) {
        double ax = head.x() - foot.x(), ay = head.y() - foot.y(), length = Math.hypot(ax, ay);
        double nx = -ay / length * rampWidth / 2.0, ny = ax / length * rampWidth / 2.0;
        for (Point p : List.of(foot, head)) {
            for (int s = -1; s <= 1; s++) {
                int x = (int) Math.round(p.x() + nx * s), y = (int) Math.round(p.y() + ny * s);
                if (x < 0 || y < 0 || x >= width || y >= height) continue;
                if (!clear.test(y * width + x)) return false;
            }
        }
        return true;
    }

    private static double clamp(double v, int size) {
        return Math.max(0, Math.min(size - 1, v));
    }
}
