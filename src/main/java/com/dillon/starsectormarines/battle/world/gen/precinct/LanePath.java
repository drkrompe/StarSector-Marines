package com.dillon.starsectormarines.battle.world.gen.precinct;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Where one lane goes, as an ordered list of waypoints from the attacker's
 * region to the objective's.
 *
 * <p>A lane used to be a ribbon: a strip of the map a third across, with a
 * ladder of places seeded at fixed fractions up the middle of it. That laid the
 * layers a Conquest was missing and it cannot say <em>where the route goes</em>.
 * A lane that has to bend round a lake, a ridge or a claimed town has no way to
 * be stated in a strip, and a forward fraction cannot describe a bend. So a lane
 * is a path, its places stand on its waypoints one rung per waypoint in path
 * order, and the chain of compounds a track has to take <em>is</em> the route.
 *
 * <p><b>Waypoints are fractions of the map</b>, the shape {@link MapPlacement}
 * already has and for the same reason: the same brief lays out at 200x140 and at
 * 560x336. A cell would pin a mission's path to one map size.
 *
 * <p><b>Every derived lane begins at the beachhead and ends at the keep.</b> The
 * two ends are shared and the lanes differ in the middle, which is the shape the
 * mission was named for — one base, three routes, one fortress. A lane used to
 * start from the middle of its own lateral third of the attacker's region, which
 * put two ladders in three two hundred cells sideways from the only ground the
 * force stands on; measured on {@code reinforced-south}, those two lanes' fronts
 * sat at their outermost rung for the whole battle because nobody ever went near
 * them.
 *
 * <p>Two derivations, and the meandering one is the default. {@link #fanned} is
 * the ruled form of the fan — the shared axis with each lane pushed out by the
 * {@linkplain #spreadAt spread envelope} and nothing else. {@link #meandering}
 * adds a bounded drift on top of that envelope, so two lanes on one map are not
 * parallel curves.
 *
 * <p><b>A waypoint that cannot stand where it is slides along its own path.</b>
 * {@link #fitted} moves it forward, then backward, until it finds ground the
 * caller allows, and reports the move. What "allows" means is the caller's
 * business: {@link PrecinctPlan} asks whether the cell is clear of every place
 * already seeded, and a caller with a finished map to consult asks whether the
 * ground can be walked. Stating it as a predicate is what lets water and rock be
 * refused the day {@code world-surface-palette.md} puts them on the ground,
 * without this class learning about either. A waypoint that finds nowhere at all
 * keeps its place and the seeding drops the rung — dropping a waypoint would
 * shorten the path rather than bend it.
 */
public record LanePath(List<Waypoint> waypoints) {

    public LanePath {
        if (waypoints == null || waypoints.isEmpty()) {
            throw new IllegalArgumentException("a lane with no waypoints on it is not a path");
        }
        waypoints = List.copyOf(waypoints);
    }

    /**
     * One point on a lane, as a fraction of the map on each axis.
     *
     * <p>Y is up, the convention {@link MapPlacement} states and the rest of the
     * world model keeps.
     */
    public record Waypoint(float x, float y) {

        public Waypoint {
            if (!Float.isFinite(x) || !Float.isFinite(y)
                    || x < 0f || x > 1f || y < 0f || y > 1f) {
                throw new IllegalArgumentException(
                        "a waypoint is a fraction of the map: " + x + "," + y);
            }
        }

        /** Which column this waypoint names on a map this wide. */
        public int cellX(int width) {
            return cell(x, width);
        }

        /** Which row this waypoint names on a map this tall. */
        public int cellY(int height) {
            return cell(y, height);
        }

        private static int cell(float fraction, int extent) {
            if (extent <= 1) return 0;
            return Math.max(0, Math.min(extent - 1, Math.round(fraction * (extent - 1))));
        }
    }

    /**
     * Whether a waypoint may stand on a cell.
     *
     * <p>The refusal rule, stated by whoever knows what the map holds. At plan
     * time that is "clear of every place already seeded"; on a finished map it
     * is "walkable", which today means not a building and not a wall and will
     * mean not water and not rock as soon as there is any.
     */
    @FunctionalInterface
    public interface Ground {

        /** Nothing is refused. What a caller with no map to consult passes. */
        Ground ANYWHERE = (x, y) -> true;

        boolean allows(int x, int y);
    }

    /** One waypoint that had to move, and how far along its path it went. */
    public record Move(int index, int fromX, int fromY, int toX, int toY) {

        /** Cells between where it was asked to stand and where it stands. */
        public int cells() {
            return Math.max(Math.abs(toX - fromX), Math.abs(toY - fromY));
        }
    }

    /** A fitted path and what fitting it cost. */
    public record Fit(LanePath path, List<Move> moved) {

        public Fit {
            moved = moved == null ? List.of() : List.copyOf(moved);
        }
    }

    /**
     * The frame a derived path is drawn in.
     *
     * <p>Which axis runs from the attacker to the objective, the two cells every
     * lane on this map shares as its ends, and the lane's own lateral strip. It
     * is the geometry {@code PrecinctPlan.seedLanes} already works out from the
     * landing place, the objective and {@link LaneGeometry}, named so a
     * derivation can be asked for it directly rather than through a whole plan.
     *
     * <p><b>The start is the beachhead, not the attacker's region.</b> The
     * region is a third of the map and its centre is nowhere in particular; the
     * landing place is the one cell every marine on the map actually stands on
     * at tick zero, and a lane that does not begin there begins somewhere the
     * force has no reason to go. A plan with no landing place — every mission
     * but Conquest — passes the region's centre and gets the old anchor.
     *
     * @param forwardIsX    whether the attacker-to-objective axis is x
     * @param startForward  the beachhead's own coordinate on that axis
     * @param startLateral  and across it
     * @param endForward    the objective's coordinate on the forward axis
     * @param endLateral    and across it
     * @param laneStart     first cell of this lane's own lateral strip
     * @param laneEnd       last cell of it, inclusive
     */
    public record Frame(boolean forwardIsX,
                        int startForward, int startLateral,
                        int endForward, int endLateral,
                        int laneStart, int laneEnd, int width, int height) {

        public Frame {
            if (width <= 0 || height <= 0) {
                throw new IllegalArgumentException("a frame on a " + width + "x" + height
                        + " map has no map to be on");
            }
            if (laneEnd < laneStart) {
                throw new IllegalArgumentException("a lane from " + laneStart + " to "
                        + laneEnd + " is not a strip");
            }
        }

        /** How far the map runs along the attacker-to-objective axis. */
        public int forwardExtent() {
            return forwardIsX ? width : height;
        }

        /** How far the map runs across it. */
        public int lateralExtent() {
            return forwardIsX ? height : width;
        }

        /** The middle of this lane's strip. */
        public int lateralCentre() {
            return (laneStart + laneEnd) / 2;
        }

        /**
         * How far off the shared axis this lane stands at its widest: its own
         * third's offset from the middle of the map.
         *
         * <p><b>An offset rather than a destination, so the fan is symmetric
         * about the ground the force is on.</b> Pushing each lane out to the
         * absolute middle of its own third instead makes the fan lopsided
         * whenever the beachhead is not itself in the middle of the map, and it
         * usually is not: a landing place is resolved against the approach
         * region and the standoff, not against the lateral midpoint. Three
         * routes out of one beachhead should leave it in three directions, not
         * two and a half.
         */
        public int laneOffset() {
            return lateralCentre() - (lateralExtent() - 1) / 2;
        }

        /** The waypoint standing at these forward and lateral coordinates. */
        public Waypoint at(int forward, int lateral) {
            int x = forwardIsX ? forward : lateral;
            int y = forwardIsX ? lateral : forward;
            return new Waypoint(fraction(x, width), fraction(y, height));
        }

        /** Which cell on the forward axis this fraction of the way along names. */
        public int forwardAt(float fraction, int margin) {
            int at = Math.round(startForward + fraction * (endForward - startForward));
            return Math.max(margin, Math.min(forwardExtent() - 1 - margin, at));
        }

        /**
         * The shared axis's own lateral at this fraction: the line from the
         * beachhead to the keep, before any lane is pushed off it.
         */
        public int axisLateralAt(float fraction) {
            return Math.round(startLateral + fraction * (endLateral - startLateral));
        }

        /**
         * Where this lane stands laterally at this fraction of its path — the
         * shared axis pushed out by its own {@link #laneOffset} times the
         * {@linkplain LanePath#spreadAt spread envelope}.
         *
         * @param peak the fraction at which the fan is at its widest
         */
        public int fanLateralAt(float fraction, float peak, int margin) {
            int at = Math.round(axisLateralAt(fraction)
                    + spreadAt(fraction, peak) * laneOffset());
            return Math.max(margin, Math.min(lateralExtent() - 1 - margin, at));
        }

        private static float fraction(int cell, int extent) {
            if (extent <= 1) return 0f;
            return Math.max(0f, Math.min(1f, cell / (float) (extent - 1)));
        }
    }

    /**
     * How wide the fan stands at a fraction along the path, as a share of the
     * lane's own {@linkplain Frame#laneOffset offset} from the shared axis.
     *
     * <p>Zero at the beachhead, one at {@code peak}, zero again at the keep,
     * straight between. The two ends are the law — every lane leaves the same
     * ground and arrives at the same ground — and the middle is where three
     * routes have to be three routes rather than one. A share of one puts a lane
     * a whole third of the map off the shared axis; see {@code Frame.laneOffset}.
     *
     * <p><b>A tent rather than a curve, and the peak is the ladder's own middle
     * rung rather than the midpoint of the path.</b> The rungs are what the fan
     * exists to place, so a shape whose widest point is not one of them would be
     * widest where nothing stands. With the shipped fractions that is 0.55, and
     * the outer and inner rungs come out at a little over half the full offset —
     * the outermost close enough to the beachhead to be walked to out of the
     * landing zone, the innermost far enough off the axis to stand clear of the
     * fortress's own claim.
     */
    static float spreadAt(float fraction, float peak) {
        float at = Math.max(0f, Math.min(1f, fraction));
        if (peak <= 0f) return 1f - at;
        if (peak >= 1f) return at;
        return at <= peak ? at / peak : (1f - at) / (1f - peak);
    }

    /** Where the fan is widest: the middle rung of the ladder standing on it. */
    static float peakOf(float[] fractions) {
        return fractions[fractions.length / 2];
    }

    /**
     * How far a meandering path may wander off its own fan line between one band
     * and the next.
     *
     * <p>Enough that a lane reads as a route rather than a ruled line; not so
     * much that two consecutive rungs end up in opposite corners of the strip
     * and the road between them runs sideways. A sixth of a lane's width is
     * about eighteen cells at Conquest's scale, against rungs fifty to ninety
     * cells apart on the forward axis — a bend rather than a zig-zag.
     *
     * <p><b>The drift is bounded against the fan line, not against the strip.</b>
     * A free walk inside a 112-cell strip can wander the same way three times
     * running and leave the deepest rung fifty cells off its lane's middle,
     * which is not a bend but a diagonal — and it carries the rung into the next
     * front band. Measured on {@code reinforced-south}, that took the deepest
     * rung of two lanes in three out of front band 1 and broke the layering the
     * ladder exists for. The strip itself cannot be the bound any more, because
     * near the beachhead every lane is on the shared axis and two of the three
     * are outside their own thirds by construction.
     */
    private static final int DRIFT_SHARE = 6;

    /** A path stated cell by cell against one map, for a test or a fixture. */
    public static LanePath ofCells(int width, int height, int... xy) {
        if (xy == null || xy.length < 2 || xy.length % 2 != 0) {
            throw new IllegalArgumentException("a path is stated as x,y pairs");
        }
        List<Waypoint> out = new ArrayList<>(xy.length / 2);
        for (int i = 0; i < xy.length; i += 2) {
            out.add(new Frame(true, 0, 0, width - 1, height - 1,
                    0, height - 1, width, height).at(xy[i], xy[i + 1]));
        }
        return new LanePath(out);
    }

    /**
     * The fan, ruled: every waypoint on its lane's own fan line.
     *
     * <p>The simplest derivation, and the one a test asks about the envelope
     * through: one waypoint per stated fraction of the way from the beachhead to
     * the keep, pushed off the shared axis by {@link #spreadAt}.
     *
     * @param fractions how far along the lane each waypoint stands, in path
     *                  order — the beachhead's end first
     * @param margin    cells to keep clear of the map's own edges
     */
    public static LanePath fanned(Frame frame, float[] fractions, int margin) {
        float peak = peakOf(fractions);
        List<Waypoint> out = new ArrayList<>(fractions.length);
        for (float fraction : fractions) {
            out.add(frame.at(frame.forwardAt(fraction, margin),
                    frame.fanLateralAt(fraction, peak, margin)));
        }
        return new LanePath(out);
    }

    /**
     * The fan walked band by band with a bounded lateral drift.
     *
     * <p>The default derivation. What wanders is the waypoint's <em>offset from
     * the fan line</em>, not its lateral coordinate: the offset walks by up to
     * {@link #DRIFT_SHARE} of the lane's width per band and is clamped to one
     * drift either side of zero, so a lane bends without ever losing the ends it
     * shares with the other two.
     *
     * <p><b>A lane wanders as much as it is wide.</b> The drift is scaled by the
     * same envelope the fan is, so it is nothing at the beachhead and everything
     * at the middle band. Unscaled it is a third of the closure the fan buys at
     * the outer rung on the narrow axis, and a lane that closes on the beachhead
     * and then wanders a hundred cells off it has not closed on anything.
     *
     * <p><b>Walking the coordinate instead saturates the clamp and stops being
     * random at all.</b> The fan line moves sixty cells between two rungs and the
     * drift band is eighteen wide, so a walk that started from the previous
     * waypoint was outside the next band before the draw was made and every seed
     * produced the identical path — a meander that had quietly become a ruled
     * line, and one no assertion about "it drifted" could have caught.
     */
    public static LanePath meandering(Frame frame, float[] fractions, int margin,
                                      Random rng) {
        float peak = peakOf(fractions);
        int drift = driftFor(frame.laneEnd() - frame.laneStart() + 1);
        int offset = 0;
        List<Waypoint> out = new ArrayList<>(fractions.length);
        for (float fraction : fractions) {
            int bound = driftAt(drift, fraction, peak);
            offset += rng.nextInt(2 * bound + 1) - bound;
            offset = Math.max(-bound, Math.min(bound, offset));
            int lateral = Math.max(margin, Math.min(frame.lateralExtent() - 1 - margin,
                    frame.fanLateralAt(fraction, peak, margin) + offset));
            out.add(frame.at(frame.forwardAt(fraction, margin), lateral));
        }
        return new LanePath(out);
    }

    /**
     * How far a waypoint at this fraction may wander off its fan line: the
     * lane's own drift, narrowed by the same envelope the fan is.
     */
    static int driftAt(int drift, float fraction, float peak) {
        return Math.max(0, Math.round(drift * spreadAt(fraction, peak)));
    }

    /** The drift a lane of this lateral span is entitled to at its widest. */
    static int driftFor(int laneSpan) {
        return Math.max(1, laneSpan / DRIFT_SHARE);
    }

    /** The cells this path names on a map this size, in path order. */
    public List<int[]> cells(int width, int height) {
        List<int[]> out = new ArrayList<>(waypoints.size());
        for (Waypoint waypoint : waypoints) {
            out.add(new int[]{waypoint.cellX(width), waypoint.cellY(height)});
        }
        return out;
    }

    /**
     * The same path with every refused waypoint slid along it until it finds
     * ground the caller allows.
     *
     * <p>Forward first, because a lane runs toward the objective and a rung that
     * has to give way should give way to the front rather than back into the
     * force behind it. <b>The deepest waypoint gives way backward first</b>: the
     * ground ahead of it is the objective's own claim, and once lanes fan the
     * heading of its last leg is the curve closing on the keep rather than a
     * line at it — measured, the middle lane's innermost rung slid seventy cells
     * "forward" and came to rest forty cells <em>behind</em> the fortress.
     *
     * <p>A waypoint that finds nothing either way keeps its place: the path
     * still has the shape it was stated in, and the seeding drops the rung it
     * cannot seat and names it.
     *
     * @param slide how many cells a waypoint may travel along its path
     */
    public Fit fitted(int width, int height, Ground ground, int slide) {
        List<int[]> cells = cells(width, height);
        List<Waypoint> out = new ArrayList<>(waypoints.size());
        List<Move> moved = new ArrayList<>();
        for (int i = 0; i < cells.size(); i++) {
            int[] at = cells.get(i);
            if (ground.allows(at[0], at[1])) {
                out.add(waypoints.get(i));
                continue;
            }
            int[] heading = heading(cells, i);
            boolean deepest = i == cells.size() - 1;
            int[] found = slideAlong(at, heading, ground, slide, width, height, deepest);
            if (found == null) {
                out.add(waypoints.get(i));
                continue;
            }
            moved.add(new Move(i, at[0], at[1], found[0], found[1]));
            out.add(new Waypoint(Frame.fraction(found[0], width),
                    Frame.fraction(found[1], height)));
        }
        return new Fit(new LanePath(out), moved);
    }

    /**
     * Which way along the path a waypoint gives way: toward the next waypoint
     * where there is one, and away from the previous one at the end of the path.
     * A one-waypoint path has no direction at all and cannot slide.
     */
    private static int[] heading(List<int[]> cells, int i) {
        int[] from = i + 1 < cells.size() ? cells.get(i) : cells.get(Math.max(0, i - 1));
        int[] to = i + 1 < cells.size() ? cells.get(i + 1) : cells.get(i);
        int dx = to[0] - from[0];
        int dy = to[1] - from[1];
        if (dx == 0 && dy == 0) return null;
        float length = (float) Math.sqrt((double) dx * dx + (double) dy * dy);
        return new int[]{Math.round(dx / length * 1000f), Math.round(dy / length * 1000f)};
    }

    /** The first allowed cell along the heading, ahead of the waypoint then behind it. */
    private static int[] slideAlong(int[] at, int[] heading, Ground ground, int slide,
                                    int width, int height, boolean backwardFirst) {
        if (heading == null || slide <= 0) return null;
        for (int sign : backwardFirst ? new int[]{-1, 1} : new int[]{1, -1}) {
            for (int step = 1; step <= slide; step++) {
                int x = at[0] + Math.round(heading[0] * step * sign / 1000f);
                int y = at[1] + Math.round(heading[1] * step * sign / 1000f);
                if (x < 0 || y < 0 || x >= width || y >= height) break;
                if (ground.allows(x, y)) return new int[]{x, y};
            }
        }
        return null;
    }
}
