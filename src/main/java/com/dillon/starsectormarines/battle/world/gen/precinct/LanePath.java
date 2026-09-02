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
 * <p>Two derivations, and the meandering one is the default. {@link #straight}
 * is the ladder up the middle of the lane's own strip — what shipped before this
 * — and stays available. {@link #meandering} walks the strip band by band with a
 * bounded lateral drift, so two lanes on one map are not parallel lines.
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
     * <p>Which axis runs from the attacker to the objective, where the two ends
     * of it are, and the lane's own lateral strip. It is the geometry
     * {@code PrecinctPlan.seedLanes} already works out from the two placements
     * and {@link LaneGeometry}, named so a derivation can be asked for it
     * directly rather than through a whole plan.
     *
     * @param forwardIsX      whether the attacker-to-objective axis is x
     * @param attackerForward where the attacker's region sits on that axis
     * @param objectiveForward where the objective sits on that axis
     * @param laneStart       first cell of this lane's own lateral strip
     * @param laneEnd         last cell of it, inclusive
     */
    public record Frame(boolean forwardIsX, int attackerForward, int objectiveForward,
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

        /** The middle of this lane's strip — where a straight path runs. */
        public int lateralCentre() {
            return (laneStart + laneEnd) / 2;
        }

        /** The waypoint standing at these forward and lateral coordinates. */
        public Waypoint at(int forward, int lateral) {
            int x = forwardIsX ? forward : lateral;
            int y = forwardIsX ? lateral : forward;
            return new Waypoint(fraction(x, width), fraction(y, height));
        }

        /** Which cell on the forward axis this fraction of the way along names. */
        public int forwardAt(float fraction, int margin) {
            int at = Math.round(attackerForward
                    + fraction * (objectiveForward - attackerForward));
            return Math.max(margin, Math.min(forwardExtent() - 1 - margin, at));
        }

        private static float fraction(int cell, int extent) {
            if (extent <= 1) return 0f;
            return Math.max(0f, Math.min(1f, cell / (float) (extent - 1)));
        }
    }

    /**
     * How much of its own strip a meandering path may wander across between one
     * band and the next.
     *
     * <p>Enough that a lane reads as a route rather than a ruled line; not so
     * much that two consecutive rungs end up in opposite corners of the strip
     * and the road between them runs sideways. A sixth of a lane's width is
     * about eighteen cells at Conquest's scale, against rungs fifty to ninety
     * cells apart on the forward axis — a bend rather than a zig-zag.
     *
     * <p><b>The drift is bounded against the centreline, not only against the
     * strip.</b> A free walk inside a 112-cell strip can wander the same way
     * three times running and leave the deepest rung fifty cells off its lane's
     * middle, which is not a bend but a diagonal — and it carries the rung into
     * the next front band. Measured on {@code reinforced-south}, that took the
     * deepest rung of two lanes in three out of front band 1 and broke the
     * layering the ladder exists for. So a waypoint may stand up to one drift
     * either side of the centreline and the walk clamps there.
     */
    private static final int DRIFT_SHARE = 6;

    /** A path stated cell by cell against one map, for a test or a fixture. */
    public static LanePath ofCells(int width, int height, int... xy) {
        if (xy == null || xy.length < 2 || xy.length % 2 != 0) {
            throw new IllegalArgumentException("a path is stated as x,y pairs");
        }
        List<Waypoint> out = new ArrayList<>(xy.length / 2);
        for (int i = 0; i < xy.length; i += 2) {
            out.add(new Frame(true, 0, width - 1, 0, height - 1, width, height)
                    .at(xy[i], xy[i + 1]));
        }
        return new LanePath(out);
    }

    /**
     * The ladder up the middle of the lane's own strip.
     *
     * <p>What every lane was before a path existed, kept as the simplest
     * derivation: one waypoint per stated fraction of the way from the
     * attacker's region to the objective's, all of them on the lane's
     * centreline.
     *
     * @param fractions how far along the lane each waypoint stands, in path
     *                  order — the attacker's end first
     * @param margin    cells to keep clear of the map's own edges
     */
    public static LanePath straight(Frame frame, float[] fractions, int margin) {
        List<Waypoint> out = new ArrayList<>(fractions.length);
        for (float fraction : fractions) {
            out.add(frame.at(frame.forwardAt(fraction, margin), frame.lateralCentre()));
        }
        return new LanePath(out);
    }

    /**
     * The strip walked band by band with a bounded lateral drift.
     *
     * <p>The default derivation. Each waypoint starts from where the last one
     * stood and wanders up to {@link #DRIFT_SHARE} of the strip's width either
     * way, clamped inside the strip — so a lane bends without ever leaving the
     * ground the commanders classify into its own track.
     */
    public static LanePath meandering(Frame frame, float[] fractions, int margin,
                                      Random rng) {
        int span = frame.laneEnd() - frame.laneStart() + 1;
        int drift = Math.max(1, span / DRIFT_SHARE);
        int centre = frame.lateralCentre();
        int low = Math.max(frame.laneStart(), centre - drift);
        int high = Math.min(frame.laneEnd(), centre + drift);
        int lateral = centre;
        List<Waypoint> out = new ArrayList<>(fractions.length);
        for (float fraction : fractions) {
            lateral += rng.nextInt(2 * drift + 1) - drift;
            lateral = Math.max(low, Math.min(high, lateral));
            out.add(frame.at(frame.forwardAt(fraction, margin), lateral));
        }
        return new LanePath(out);
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
     * force behind it; backward second, because the deepest rung's forward
     * ground is the objective's own claim and there is nowhere ahead of it to
     * go. A waypoint that finds nothing either way keeps its place: the path
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
            int[] found = slideAlong(at, heading, ground, slide, width, height);
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
                                    int width, int height) {
        if (heading == null || slide <= 0) return null;
        for (int sign : new int[]{1, -1}) {
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
