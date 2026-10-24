package com.dillon.starsectormarines.battle.world.gen.elevation;

import com.dillon.starsectormarines.battle.nav.ElevationMap;
import com.dillon.starsectormarines.battle.world.gen.elevation.ElevationPlan.Plateau;
import com.dillon.starsectormarines.battle.world.gen.elevation.ElevationPlan.Point;
import com.dillon.starsectormarines.battle.world.gen.elevation.ElevationPlan.Ramp;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntPredicate;

/**
 * Raises built ground onto a ridge's crest ({@code elevation-nouns.md}, Terraces): a
 * flat top over a place's whole footprint and a margin round it, its cliff
 * standing in open country beyond the margin, and a ramp wherever a road crosses
 * that cliff, so the place stands on one level and its roads still come up to
 * its gates. The Cordon's redoubts stand on their ridge so.
 *
 * <p>Generation only, and pure like {@link RidgePlanner}: it reads which cells
 * are road, open and free, and returns the terrace's plateau and ramps or why it
 * cannot stand. Built ground under the top is the point: the brush lets it stay
 * where the plan says it is top ground ({@link #built}), and refuses only a
 * face or a ramp on it.
 */
public final class TerracePlanner {

    private TerracePlanner() {}

    /**
     * Cells between a place's ground and the terrace's outline to begin with: enough that the outline's rounded
     * corners and its roughness leave every corner of the ground on the top. A side is pushed further out where
     * something stands under its cliff.
     */
    public static final int MARGIN = 4;
    /** Radius of the capsules the top is filled with: deep enough that the place's ground is top on every side. */
    static final double FILL_RADIUS = 6;
    /** Cells between the filling capsules' rows. */
    static final double ROW_STEP = 2;
    /** Cells of ramp either side of the road it carries, so the ramp's side faces stand clear of the road's margin. */
    static final int RAMP_SHOULDER = 3;
    /** The narrowest terrace ramp: a tank's, as the ridge's own ramps are. */
    public static final int MIN_RAMP_WIDTH = 9;
    /** Cells either side of the outline a road is looked for: road there crosses the cliff and is ramped. */
    static final int ROAD_SCAN = 1;

    /**
     * Built ground to stand on a crest: the inclusive box of a place's ground. A
     * required terrace that cannot stand fails its map; an optional one leaves
     * its place in a pass, as a ridge does any place.
     */
    public record Terrace(String name, int x0, int y0, int x1, int y1, boolean required) {
        public Terrace {
            if (name == null || name.isEmpty()) throw new IllegalArgumentException("a terrace needs a name");
            if (x1 < x0 || y1 < y0) throw new IllegalArgumentException("a terrace's box is inclusive and ordered");
        }
    }

    /**
     * A terrace as planned: its top, the ramps up to it, the cells the brush may raise whatever stands on them
     * ({@code built}: the outline's whole rectangle and the ramps), and the cells it touches with its apron
     * ({@code zone}). {@code refusal} is null when it stands, and every other field is then set.
     */
    public record Planned(Terrace terrace, List<Plateau> plateaus, List<Ramp> ramps, boolean[] built, boolean[] zone,
                          String refusal) {
        public boolean stands() { return refusal == null; }

        /** The cells of each ramp's foot, where its road meets the low ground. */
        public List<int[]> feet() {
            List<int[]> out = new ArrayList<>();
            for (Ramp ramp : ramps) out.add(new int[]{(int) Math.round(ramp.foot().x()), (int) Math.round(ramp.foot().y())});
            return out;
        }

        static Planned refused(Terrace terrace, String why) {
            return new Planned(terrace, List.of(), List.of(), null, null, why);
        }
    }

    /**
     * Plans one terrace on a map of this size.
     *
     * @param seed the seed of the plan the terrace will be stamped in: the outline's roughness is the plan's
     * @param road whether a cell is road (a ramp is cut wherever road crosses the outline)
     * @param open whether a cell is walkable open ground of any kind, road included: what a ramp may cover
     * @param free whether a cell is open country nothing reserves: the only ground a face may stand on
     * @param bare whether nothing is built on a cell (no building, no wall): what the cliff's drawn apron may lie on
     */
    public static Planned plan(int width, int height, long seed, Terrace terrace, IntPredicate road,
                               IntPredicate open, IntPredicate free, IntPredicate bare) {
        // West, east, south, north. A side whose outline meets a road running along it, or built ground under its
        // cliff, is pushed out past it, so a road beside the place runs on the top and is ramped where it leaves.
        int[] margins = {MARGIN, MARGIN, MARGIN, MARGIN};
        Attempt last = null;
        for (int tries = 0; tries < MAX_TRIES; tries++) {
            last = attempt(width, height, seed, terrace, margins, road, open, free, bare);
            if (last.planned().stands() || last.grow() < 0 || margins[last.grow()] >= MARGIN + MAX_GROWTH) break;
            margins[last.grow()]++;
        }
        return last.planned();
    }

    /** How far past {@link #MARGIN} a side may be pushed out. */
    static final int MAX_GROWTH = 14;
    /** Outlines tried before a terrace is given up on. */
    static final int MAX_TRIES = 40;

    /** One outline tried: the terrace, or why not and which side to push out ({@code -1}: none would help). */
    private record Attempt(Planned planned, int grow) { }

    private static Attempt attempt(int width, int height, long seed, Terrace terrace, int[] margins,
                                   IntPredicate road, IntPredicate open, IntPredicate free, IntPredicate bare) {
        int rx0 = terrace.x0() - margins[0], rx1 = terrace.x1() + margins[1];
        int ry0 = terrace.y0() - margins[2], ry1 = terrace.y1() + margins[3];
        int reach = 5;
        if (rx0 - reach < 1 || ry0 - reach < 1 || rx1 + reach > width - 2 || ry1 + reach > height - 2) {
            return new Attempt(Planned.refused(terrace, "too near the map's edge"), -1);
        }
        List<Plateau> plateaus = fill(rx0, ry0, rx1, ry1, terrace);
        List<Ramp> ramps = new ArrayList<>();
        int[][] sides = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
        for (int side = 0; side < sides.length; side++) {
            int[] out = sides[side];
            boolean alongY = out[0] != 0;
            int edge = out[0] < 0 ? rx0 : out[0] > 0 ? rx1 : out[1] < 0 ? ry0 : ry1;
            // The outline is straight between its rounded corners.
            int from = (alongY ? ry0 : rx0) + (int) Math.ceil(FILL_RADIUS);
            int to = (alongY ? ry1 : rx1) - (int) Math.ceil(FILL_RADIUS);
            int start = Integer.MIN_VALUE;
            for (int t = from; t <= to + 1; t++) {
                boolean crossed = t <= to && crossed(edge, t, alongY, width, road);
                if (crossed && start == Integer.MIN_VALUE) start = t;
                if (crossed || start == Integer.MIN_VALUE) continue;
                int span = t - start;
                double rampWidth = Math.max(MIN_RAMP_WIDTH, span + 2 * RAMP_SHOULDER);
                if (rampWidth > 16) {
                    return new Attempt(Planned.refused(terrace, "a road " + span + " cells wide runs along its edge"),
                            side);
                }
                double middle = (start + t - 1) / 2.0;
                // A face looking down the screen is drawn deeper, so a ramp climbing it is cut deeper too.
                double south = Math.max(0, -out[1]) * PlateauPlanner.SOUTH_FACE;
                double foot = edge + (alongY ? out[0] : out[1]) * 3.0;
                // Its head on the margin, short of the place's own ground.
                double head = edge - (alongY ? out[0] : out[1]) * Math.min(4.0 + south, margins[side] - 1);
                ramps.add(alongY
                        ? new Ramp(new Point(foot, middle), new Point(head, middle), rampWidth)
                        : new Ramp(new Point(middle, foot), new Point(middle, head), rampWidth));
                start = Integer.MIN_VALUE;
            }
        }
        if (ramps.isEmpty()) return new Attempt(Planned.refused(terrace, "no road crosses its edge to be ramped"), -1);
        ElevationLayout layout;
        try {
            layout = ElevationLayout.resolve(new ElevationPlan(width, height, PlateauPlanner.FACE_WIDTH,
                    PlateauPlanner.SOUTH_FACE, .6, seed, plateaus, ramps));
        } catch (IllegalArgumentException refused) {
            return new Attempt(Planned.refused(terrace, "its ramps do not fit: " + refused.getMessage()), -1);
        }
        for (int y = terrace.y0(); y <= terrace.y1(); y++) {
            for (int x = terrace.x0(); x <= terrace.x1(); x++) {
                if (layout.tier(x, y) != 1 || layout.kind(x, y) != ElevationMap.GROUND) {
                    return new Attempt(Planned.refused(terrace, "its own ground is not all on the top at " + x + ","
                            + y), -1);
                }
            }
        }
        boolean[] footprint = PlateauPlanner.footprint(layout);
        boolean[] built = new boolean[width * height];
        for (int y = ry0; y <= ry1; y++) for (int x = rx0; x <= rx1; x++) built[y * width + x] = true;
        int[] box = {rx0, ry0, rx1, ry1};
        // Only round the outline: inside it is the top, which takes whatever stands there.
        int x0 = Math.max(0, rx0 - reach), x1 = Math.min(width - 1, rx1 + reach);
        int y0 = Math.max(0, ry0 - reach), y1 = Math.min(height - 1, ry1 + reach);
        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) {
                int i = y * width + x;
                String fault = null;
                if (layout.isFace(x, y)) {
                    if (!free.test(i)) fault = "a cliff would stand on built ground at ";
                } else if (layout.isRamp(x, y)) {
                    if (!open.test(i)) fault = "a ramp would climb over built ground at ";
                    built[i] = true;
                } else if (footprint[i] && !layout.touched(x, y) && !bare.test(i)) {
                    fault = "its apron would lie over built ground at ";
                }
                if (fault != null) return new Attempt(Planned.refused(terrace, fault + x + "," + y), sideOf(box, x, y));
            }
        }
        for (Ramp ramp : ramps) {
            int fx = (int) Math.round(ramp.foot().x()), fy = (int) Math.round(ramp.foot().y());
            if (!open.test(fy * width + fx)) {
                return new Attempt(Planned.refused(terrace, "a ramp's foot is closed at " + fx + "," + fy), -1);
            }
        }
        // A ridge may run on through the terrace and merge with its top, but never over a ramp or the road below it.
        boolean[] zone = footprint.clone();
        for (Ramp ramp : ramps) {
            double fx = ramp.foot().x(), fy = ramp.foot().y();
            double ax = ramp.head().x() - fx, ay = ramp.head().y() - fy, length = Math.hypot(ax, ay);
            double ux = ax / length, uy = ay / length, half = ramp.width() / 2.0 + RAMP_CLEAR;
            for (int y = y0; y <= y1; y++) {
                for (int x = x0; x <= x1; x++) {
                    double along = (x - fx) * ux + (y - fy) * uy, across = Math.abs(-(x - fx) * uy + (y - fy) * ux);
                    if (along >= -RAMP_CLEAR - 2 && along <= length + RAMP_CLEAR && across <= half) zone[y * width + x] = false;
                }
            }
        }
        return new Attempt(new Planned(terrace, plateaus, List.copyOf(ramps), built, zone, null), -1);
    }

    /**
     * The side to push out for a fault at {@code (x, y)}: the nearest edge, unless the cell is in that edge's
     * rounded corner, where pushing out the side beside it is what straightens the outline over the cell.
     */
    static int sideOf(int[] box, int x, int y) {
        int[] distance = {Math.abs(x - box[0]), Math.abs(x - box[2]), Math.abs(y - box[1]), Math.abs(y - box[3])};
        int nearest = 0;
        for (int side = 1; side < 4; side++) if (distance[side] < distance[nearest]) nearest = side;
        int corner = (int) Math.ceil(FILL_RADIUS);
        if (nearest >= 2) {
            if (x < box[0] + corner) return 0;
            if (x > box[2] - corner) return 1;
        } else {
            if (y < box[1] + corner) return 2;
            if (y > box[3] - corner) return 3;
        }
        return nearest;
    }

    /** Cells round a ramp, and below its foot, that no ridge running through the terrace may raise. */
    static final int RAMP_CLEAR = 2;

    /** Whether road crosses the outline at {@code t} along it: any road cell across the face band there. */
    private static boolean crossed(int edge, int t, boolean alongY, int width, IntPredicate road) {
        for (int d = -ROAD_SCAN; d <= ROAD_SCAN; d++) {
            int x = alongY ? edge + d : t, y = alongY ? t : edge + d;
            if (road.test(y * width + x)) return true;
        }
        return false;
    }

    /**
     * The top as one plateau: a serpentine of capsules {@link #FILL_RADIUS} wide filling the rectangle, so its
     * outline runs along the rectangle's edges (rounded at the corners) and the field deepens inward from every
     * side, which a single capsule along a square would not.
     */
    static List<Plateau> fill(int rx0, int ry0, int rx1, int ry1, Terrace terrace) {
        double radius = Math.min(FILL_RADIUS, Math.min(rx1 - rx0, ry1 - ry0) / 2.0);
        double left = rx0 + radius, right = rx1 - radius, bottom = ry0 + radius, top = ry1 - radius;
        List<Point> points = new ArrayList<>();
        boolean east = true;
        for (double y = bottom; ; y = Math.min(top, y + ROW_STEP)) {
            points.add(new Point(east ? left : right, y));
            points.add(new Point(east ? right : left, y));
            east = !east;
            if (y >= top) break;
        }
        List<Plateau> out = new ArrayList<>();
        out.add(new Plateau(1, radius, points));
        // A disc on each corner of the place's ground squares the outline's rounded corner over it, so the corner
        // stands as deep in the top as the sides do.
        for (int[] corner : new int[][]{{terrace.x0(), terrace.y0()}, {terrace.x1(), terrace.y0()},
                {terrace.x0(), terrace.y1()}, {terrace.x1(), terrace.y1()}}) {
            out.add(new Plateau(1, MARGIN, List.of(new Point(corner[0], corner[1]))));
        }
        return out;
    }
}
