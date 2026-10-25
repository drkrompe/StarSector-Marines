package com.dillon.starsectormarines.battle.world.gen.elevation;

import com.dillon.starsectormarines.battle.world.gen.elevation.ElevationPlan.Plateau;
import com.dillon.starsectormarines.battle.world.gen.elevation.ElevationPlan.Point;
import com.dillon.starsectormarines.battle.world.gen.elevation.ElevationPlan.Ramp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
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
     * What a band's hill segments look like ({@code elevation-nouns.md}, Relief). {@link #NATURAL} ground swells,
     * narrows and wanders within the band's ground; a {@link #BERM} is an engineered bank graded along the path, the
     * band's own width the whole way, its faces straight and its ends cut square where the band breaks.
     */
    public enum Relief { NATURAL, BERM }

    /**
     * One authored ridge: a band {@code thickness} cells through, centred on a
     * path of cell points. Its ramps climb from the side {@code rampSide} names:
     * +1 is the left of the path's direction of travel (counter-clockwise from
     * it, in cell coordinates), -1 the right. {@code rampWidth} cells wide.
     */
    public record Band(List<Point> path, int thickness, int rampSide, int rampWidth, Relief relief) {
        public Band {
            path = List.copyOf(path);
            if (path.size() < 2) throw new IllegalArgumentException("a band runs along at least two points");
            if (thickness < 4 || thickness > 64) throw new IllegalArgumentException("thickness 4-64");
            if (rampSide != -1 && rampSide != 1) throw new IllegalArgumentException("a ramp climbs from one side");
            if (rampWidth < 1 || rampWidth > 16) throw new IllegalArgumentException("ramp width 1-16");
            if (relief == null) throw new IllegalArgumentException("a band has a relief");
        }

        /** A band of natural ground. */
        public Band(List<Point> path, int thickness, int rampSide, int rampWidth) {
            this(path, thickness, rampSide, rampWidth, Relief.NATURAL);
        }

        /** This band as an engineered bank. */
        public Band berm() {
            return new Band(path, thickness, rampSide, rampWidth, Relief.BERM);
        }

        /**
         * Cells natural ground may swell past the band's thickness either side of the path: a berm keeps to it. The
         * ground a band holds or seals reaches this much further ({@link #heldGround}, {@link #sideGround}).
         */
        public int spread() {
            return relief == Relief.NATURAL ? naturalSpread(thickness) : 0;
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

    /** How far natural ground may swell past its band's thickness either side, as a share of the thickness. */
    static final double SPREAD = 0.3;

    /** Cells natural ground {@code thickness} through may swell past it either side ({@link Band#spread}). */
    public static int naturalSpread(int thickness) {
        return (int) Math.round(thickness * SPREAD);
    }
    /**
     * The narrowest natural ground runs, as a radius: enough that a top stays one body however its tall south face
     * and roughening bite into it, so a waist never cuts a segment into a part with no ramp. Thick ground narrows to
     * {@link #WAIST_SHARE} of its radius, no less.
     */
    static final double NATURAL_WAIST = 4.5, WAIST_SHARE = 0.7;
    /** Radius a natural segment tapers to at its ends: a spur running out, not a capsule's round end. */
    static final double NATURAL_TIP = 2.5;
    /** Cells along the path between a natural segment's points. */
    static final int NATURAL_STRIDE = 2;
    /** Cells a berm's square ends stand inside its run, so its apron stays on the ground the run was proved over. */
    static final int BERM_END = 2;

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
        for (int b = 0; b < bands.size(); b++) {
            Band band = bands.get(b);
            List<Sample> samples = samples(band);
            for (int[] run : runs(band, samples, width, height, runFree)) {
                List<Sample> span = samples.subList(run[0], run[1] + 1);
                // Natural ground first, then kept to the band's own width, then the plain capsule where neither stands.
                List<Shape> shapes = band.relief() == Relief.BERM ? List.of(Shape.BERM)
                        : List.of(Shape.NATURAL, Shape.NARROW, Shape.PLAIN);
                for (Shape shape : shapes) {
                    List<Plateau> nextPlateaus = new ArrayList<>(plateaus);
                    List<Ramp> nextRamps = new ArrayList<>(ramps);
                    long salt = seed * 0x9E3779B97F4A7C15L + b * 0x632BE59BD9B4E019L + run[0];
                    if (!segment(band, shape, span, salt, width, height, nextPlateaus, nextRamps, rampClear)) continue;
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
                    break;
                }
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
            int reach = (int) Math.ceil(band.thickness() / 2.0) + band.spread() + apron + margin;
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

    /**
     * {@link #heldGround} without the rounded ends: the same ground across the band, but none past either end of its
     * path, so the gap a mission leaves between two bands (a pass) stays as wide as it was drawn whatever the margin.
     * What sealed ground holds ({@code elevation-nouns.md}, Sealed ground).
     */
    public static boolean[][] sideGround(List<Band> bands, int width, int height, int margin) {
        boolean[][] held = new boolean[width][height];
        int apron = PlateauPlanner.FACE_WIDTH + PlateauPlanner.SOUTH_FACE + 1;
        for (Band band : bands) {
            double reach = Math.ceil(band.thickness() / 2.0) + band.spread() + apron + margin;
            List<Point> path = band.path();
            for (int i = 0; i + 1 < path.size(); i++) {
                Point a = path.get(i), b = path.get(i + 1);
                double dx = b.x() - a.x(), dy = b.y() - a.y(), len = Math.hypot(dx, dy);
                if (len < 1e-9) continue;
                double nx = -dy / len, ny = dx / len;
                for (double t = 0; t <= len; t += 0.5) {
                    double px = a.x() + dx * t / len, py = a.y() + dy * t / len;
                    for (double d = -reach; d <= reach; d += 0.5) {
                        int x = (int) Math.round(px + nx * d), y = (int) Math.round(py + ny * d);
                        if (x >= 0 && y >= 0 && x < width && y < height) held[x][y] = true;
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

    /** How a segment is outlined: as its band's relief asks, or the plain capsule natural ground falls back to. */
    enum Shape {
        NATURAL, NARROW, PLAIN, BERM;

        boolean natural() { return this == NATURAL || this == NARROW; }
    }

    /**
     * Adds one segment's plateau and its ramp, at the run's middle or, where that is not clear, the nearest point
     * of the run that is; false when the run is too short to hold them or no point of it is clear.
     *
     * <p>{@link Shape#PLAIN} is a capsule chain of the band's radius whose round ends lie inside the run.
     * {@link Shape#NATURAL} swells and narrows along the run, from {@link #NATURAL_WAIST} to the band's radius plus
     * its {@link Band#spread}, its crest wandering from side to side in the room its width leaves, and tapers to a
     * spur at each end; seeded by {@code salt}, smooth over tens of cells. Its ramp climbs where it is at least the
     * band's own width, wherever it can. {@link Shape#NARROW} is the same within the band's own width.
     * {@link Shape#BERM} is the band's width the whole way and runs to {@link #BERM_END} cells from each end of the
     * run, cut square there.
     */
    private static boolean segment(Band band, Shape shape, List<Sample> run, long salt, int width, int height,
                                   List<Plateau> plateaus, List<Ramp> ramps, IntPredicate rampClear) {
        double radius = band.thickness() / 2.0;
        int trim = switch (shape) {
            // A capsule chain: its round ends lie inside the run, so the cliffs close round the segment's ends.
            case PLAIN -> (int) Math.ceil(radius);
            // A spur's tip runs out at the run's end, as far as a capsule's round end reaches.
            case NATURAL, NARROW -> (int) Math.floor(NATURAL_TIP);
            case BERM -> BERM_END;
        };
        int n = run.size();
        if (n <= 2 * trim) return false;
        // Each sample's radius and the crest's offset along its normal.
        double[] r = new double[n], offset = new double[n];
        double widest = radius;
        if (shape.natural()) {
            double waist = Math.min(Math.max(NATURAL_WAIST, radius * WAIST_SHARE), radius);
            double full = radius + (shape == Shape.NATURAL ? band.spread() : 0);
            widest = full;
            Random random = new Random(salt);
            Wave swell = new Wave(random), wander = new Wave(random);
            double taper = Math.max(2 * radius, 8), tip = Math.min(NATURAL_TIP, waist);
            for (int i = 0; i < n; i++) {
                double w = waist + (full - waist) * (.5 + .5 * swell.at(i));
                double end = Math.min(i - trim, n - 1 - trim - i);
                // Toward each end the spur narrows and comes back onto the path, so it runs out where the band does.
                double toEnd = end < taper ? smooth(Math.max(0, end) / taper) : 1;
                w = tip + (w - tip) * toEnd;
                r[i] = w;
                offset[i] = wander.at(i) * (full - w) * .9 * toEnd;
            }
        } else {
            Arrays.fill(r, radius);
        }
        List<Point> points = new ArrayList<>();
        List<Double> radii = new ArrayList<>();
        int stride = shape.natural() ? NATURAL_STRIDE : Math.max(1, (int) Math.floor(radius));
        int tail = n - trim - 1, i = trim;
        for (; i < tail; i += stride) {
            points.add(crest(run.get(i), offset[i]));
            radii.add(r[i]);
        }
        // The tail once: a repeated point is a capsule of no length, a round end on a berm cut square.
        points.add(crest(run.get(tail), offset[tail]));
        radii.add(r[tail]);
        // Outward from the middle, alternately each way, to the first point whose ramp stands clear.
        int middle = n / 2;
        for (int pass = 0; pass < 2; pass++) {
            for (int k = 0; k < 2 * n; k++) {
                int at = middle + (k % 2 == 0 ? k / 2 : -(k / 2 + 1));
                if (at < trim || at >= n - trim) continue;
                if (pass == 0 && shape.natural() && r[at] < Math.min(radius, widest - 1)) continue;
                Sample mid = run.get(at);
                Point c = crest(mid, offset[at]);
                double fx = mid.nx() * band.rampSide(), fy = mid.ny() * band.rampSide();
                // A ramp cut deeper where it climbs a tall south face, as the plateau planner cuts its own.
                double south = Math.max(0, -fy) * PlateauPlanner.SOUTH_FACE;
                Point foot = new Point(clamp(c.x() + fx * (r[at] + 3), width), clamp(c.y() + fy * (r[at] + 3), height));
                Point head = new Point(clamp(c.x() + fx * (r[at] - 4 - south), width),
                        clamp(c.y() + fy * (r[at] - 4 - south), height));
                if (!rampClear(foot, head, band.rampWidth(), width, height, rampClear)) continue;
                plateaus.add(switch (shape) {
                    case PLAIN -> new Plateau(1, radius, points);
                    case NATURAL, NARROW -> new Plateau(1, widest, points, radii, false);
                    case BERM -> new Plateau(1, radius, points, List.of(), true);
                });
                ramps.add(new Ramp(foot, head, band.rampWidth()));
                return true;
            }
            if (!shape.natural()) break;
        }
        return false;
    }

    private static Point crest(Sample s, double offset) {
        return new Point(s.x() + s.nx() * offset, s.y() + s.ny() * offset);
    }

    private static double smooth(double t) {
        double c = Math.max(0, Math.min(1, t));
        return c * c * (3 - 2 * c);
    }

    /**
     * A smooth seeded wave along a run, -1 to 1: three sines of incommensurate lengths between about twenty and
     * seventy cells, so a hill swells and narrows over a few tens of cells and never repeats along a ridge.
     */
    private record Wave(double l1, double l2, double l3, double p1, double p2, double p3) {
        Wave(Random random) {
            this(18 + random.nextDouble() * 10, 30 + random.nextDouble() * 14, 50 + random.nextDouble() * 20,
                    random.nextDouble() * 2 * Math.PI, random.nextDouble() * 2 * Math.PI, random.nextDouble() * 2 * Math.PI);
        }

        double at(double s) {
            double v = .5 * Math.sin(2 * Math.PI * s / l1 + p1) + .3 * Math.sin(2 * Math.PI * s / l2 + p2)
                    + .2 * Math.sin(2 * Math.PI * s / l3 + p3);
            return Math.max(-1, Math.min(1, v * 1.25));
        }
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
