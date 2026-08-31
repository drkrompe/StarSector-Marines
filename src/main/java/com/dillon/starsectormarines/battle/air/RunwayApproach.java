package com.dillon.starsectormarines.battle.air;

import com.dillon.starsectormarines.battle.vehicle.Pose;
import com.dillon.starsectormarines.battle.vehicle.ReedsShepp;
import com.dillon.starsectormarines.battle.world.gen.Runway;

import java.util.List;

/**
 * The path an aircraft flies to arrive on a runway threshold pointing down it.
 *
 * <p><b>A landing is flown, not captured.</b> It used to be neither: the craft
 * steered to a point out on the extended centreline, then at the threshold the
 * simulation put it on the strip pointing along it and drove the rollout from
 * there. That worked while every aircraft could turn inside seven cells. It
 * stopped working the moment the atmosphere calibration widened the circle to
 * twenty-odd, because a machine cannot turn onto a centreline it joins fourteen
 * cells short of the threshold — the geometry is impossible, and what the
 * takeover was hiding was a craft arriving crabbed and being snapped straight.
 *
 * <p>So the arrival is <em>solved</em> instead. Where a craft is and where it
 * has to be, both as poses, with a bound on how tightly it can turn, is exactly
 * the question {@link ReedsShepp} answers in closed form; the forward-only
 * subset of that is a Dubins path, and it exists for any two poses. The circuit
 * shape — the leg out, the turn onto final, the straight run in — falls out of
 * the geometry rather than being authored, and the touchdown is a real arrival
 * because the path ended pointing the right way.
 *
 * <p><b>The path ends through a final-approach fix, not straight at the
 * threshold.</b> The last stretch is deliberately a straight line down the
 * runway axis: it absorbs whatever tracking error the turn left, and — the
 * reason it is structural rather than cosmetic — it makes a go-around a re-plan
 * to the same fix instead of a degenerate solve. A craft denied the strip a
 * hundredth of a cell short of the threshold, asked for a path from there to
 * the threshold, would be told to fly straight ahead; asked for a path to the
 * fix behind it, it is told to fly a circuit, which is what a go-around is.
 *
 * <p><b>Planned at a wider circle than the craft can fly.</b> A path laid out at
 * the tightest arc a hull manages is one it can only just manage: the follower
 * has no margin for the tracking error it is there to correct, so it falls
 * behind the curve and never catches up. The plan radius carries slack for
 * exactly that.
 *
 * <p><b>And it runs on past the threshold, down the strip.</b> A follower
 * whose carrot has nowhere left to slide pins it to the last point and starts
 * chasing it, and a body with a turn radius chasing a point it is offset from
 * swings its nose further off the closer it gets — a shuttle measured at eight
 * degrees of crab a cell before the threshold arrived at thirty. The takeoff
 * roll already aims a carrot out beyond the far threshold for exactly this
 * reason; a landing has the same shape arriving from the other end. Where the
 * approach <em>ends</em> is still the threshold, which is what the arrival test
 * is asked about — the run-out is the carrot's business, not the landing's.
 *
 * <p>Obstacle-free and constant-radius, which is honest for something at
 * altitude and would not be for anything on the ground. The braking is not part
 * of the path — a Dubins solution is a path and not a trajectory, so how fast
 * the craft goes along it stays the caller's business.
 *
 * <p>See {@code air-nouns.md}.
 */
public final class RunwayApproach {

    /**
     * How much wider than the hull's own tightest circle the path is laid out.
     *
     * <p>The tightest circle is the one flown at full turn rate and full speed
     * at once, which leaves a follower nothing to correct with. A third again
     * is enough margin that the craft tracks the curve rather than falling
     * progressively outside it, and still narrow enough that the circuit reads
     * as this aircraft's circuit.
     */
    private static final float PLAN_RADIUS_SLACK = 1.35f;

    /** Floor under the plan radius, cells — a craft that turns on a sixpence still flies a legible hook. */
    private static final float MIN_PLAN_RADIUS_CELLS = 3f;

    /**
     * The straight final, as a multiple of the plan radius.
     *
     * <p>Derived rather than authored, because what it has to be long enough
     * for is the follower settling onto the axis, and that is measured in turn
     * radii. The constant it replaces was fourteen cells: right for a shuttle
     * that turns inside four and impossible for a fighter that turns inside
     * twenty.
     */
    private static final float FINAL_LEG_RADII = 1f;

    /**
     * How far up the path the follower aims, as a multiple of the plan radius.
     *
     * <p>Near enough to the radius that the commanded heading tracks the curve
     * rather than cutting across it, and short enough of it that the carrot
     * does not sit across a corner. Erring long on purpose: the chord a
     * look-ahead cuts off an arc is second order in it, while the crab it
     * leaves at the threshold is first order — measured on the shipped
     * airfield, lengthening it took a fighter from four and a half degrees of
     * crab at touchdown to two, and cost nothing anywhere else.
     */
    private static final float LOOKAHEAD_RADII = 0.85f;

    /**
     * Floor under the straight final, cells.
     *
     * <p>A radius-derived length is right for a fast hull and too short for a
     * nimble one, because what has to be washed out on the final is not
     * proportional to the turn radius at all — it is the sideways momentum the
     * craft carries out of the turn, which is its speed over its drift damping.
     * A shuttle turns inside four cells and still slides three, so a final leg
     * of six is most of a slide long. Measured over four dozen arrival poses,
     * lifting the floor took a shuttle from eight degrees of crab at touchdown
     * to under two.
     */
    private static final float MIN_FINAL_LEG_CELLS = 18f;

    /**
     * Floor under the look-ahead, cells.
     *
     * <p>The look-ahead <em>is</em> the crab at touchdown. A craft following a
     * carrot on the centreline from half a cell off it arrives pointed
     * {@code atan(offset / lookAhead)} away from the strip, so a short
     * look-ahead turns a small tracking error into a large heading one: at
     * three and a half cells a shuttle half a cell off the line touched down
     * eight degrees crooked. That matters at a specific number, because a
     * wheeled aircraft more than five degrees off where it is going stops and
     * swings its nose round standing still — which on a runway is the pirouette
     * a flown approach exists to remove. This floor puts both a shuttle and a
     * fighter around two.
     */
    private static final float MIN_LOOKAHEAD_CELLS = 9f;

    /** Spacing of the sampled polyline, cells. Fine enough that a chord across one step is nothing next to the radius. */
    private static final float SAMPLE_STEP_CELLS = 1f;

    /** Two points closer than this are one point, and the segment between them has no direction. */
    private static final float DEGENERATE_STEP_CELLS = 0.05f;

    /**
     * The polyline the craft is flown along: the solved hook, the threshold,
     * and a run-out down the strip past it for the carrot to slide along.
     */
    public final float[] xs;
    public final float[] ys;

    /**
     * Where the threshold sits in that polyline. The landing is over when the
     * follower has crossed the point before it, which is the final approach
     * fix; everything after is run-out.
     */
    public final int thresholdIdx;

    /** Length of the flown part, threshold included — what the descent is lerped against. */
    public final float lengthCells;

    /** Length of the run-out past the threshold, which is not part of the descent. */
    public final float runOutCells;

    /** How far up the path to aim, cells. */
    public final float lookAheadCells;

    /**
     * Whether a curvature-feasible path was found at all.
     *
     * <p>False means the craft is flying the straight-in fallback and will
     * arrive on whatever heading it managed — which is what every landing did
     * before this existed, and is still a landing, which is the requirement.
     * Worth counting rather than hiding.
     *
     * <p>Dubins has a solution for any two poses, so in practice this is the
     * guard rather than a case: measured over four dozen arrival poses onto a
     * strip, for a shuttle and for a fighter, it never once fired.
     */
    public final boolean solved;

    /**
     * Whether the circuit leaves the map by more than a circuit diameter.
     *
     * <p>Reported and not corrected, which is a deliberate position. A landing
     * approach is obstacle-free and the map edge is not a wall to something at
     * altitude — every off-map sortie in the game crosses it by construction —
     * so a bound here buys legibility rather than feasibility, and the only way
     * to buy it would be to arrive crabbed, which is the fault this class
     * exists to remove. If a fast hull spends too long out of sight on the way
     * home, the lever is the atmosphere calibration that set its circle, not
     * the landing that has to fly it.
     */
    public final boolean wide;

    /** Where the follower's cursor has reached. Carried between ticks. */
    public int leg;

    private RunwayApproach(float[] xs, float[] ys, int thresholdIdx,
                           float lookAheadCells, boolean solved, boolean wide) {
        this.xs = xs;
        this.ys = ys;
        this.thresholdIdx = thresholdIdx;
        this.lookAheadCells = lookAheadCells;
        this.solved = solved;
        this.wide = wide;
        float flown = 0f;
        float runOut = 0f;
        for (int i = 1; i < xs.length; i++) {
            float step = (float) Math.hypot(xs[i] - xs[i - 1], ys[i] - ys[i - 1]);
            if (i <= thresholdIdx) flown += step;
            else runOut += step;
        }
        this.lengthCells = Math.max(0.001f, flown);
        this.runOutCells = runOut;
    }

    /**
     * How much of the descent is left, given how much of the whole polyline is.
     *
     * <p>The run-out past the threshold is not part of the arrival, so it is
     * taken off before the ratio is formed: a craft that is on the threshold is
     * on the ground, whatever is left of the line the carrot is still sliding
     * along.
     */
    public float descentRemaining(float pathRemainingCells) {
        return Math.max(0f, pathRemainingCells - runOutCells) / lengthCells;
    }

    /**
     * Solve the approach from {@code from} onto {@code threshold}.
     *
     * @param from      where the aircraft is and which way it is pointed
     * @param strip     the runway, for the axis its far end defines
     * @param threshold the end being landed on, as {@code x, y}
     * @param minTurnRadiusCells the tightest circle this craft can fly
     * @param mapW      map width, cells — only the reach check uses it
     * @param mapH      map height, cells
     */
    public static RunwayApproach plan(Pose from, Runway strip, float[] threshold,
                                      float minTurnRadiusCells, int mapW, int mapH) {
        float[] far = strip.opposite(threshold);
        float axisX = far[0] - threshold[0];
        float axisY = far[1] - threshold[1];
        float axisLen = (float) Math.hypot(axisX, axisY);
        float runwayFacing = axisLen < 1e-6f
                ? from.facingDeg
                : AirBody.facingToward(axisX / axisLen, axisY / axisLen);

        float radius = Float.isFinite(minTurnRadiusCells) && minTurnRadiusCells > 0f
                ? Math.max(MIN_PLAN_RADIUS_CELLS, minTurnRadiusCells * PLAN_RADIUS_SLACK)
                : MIN_PLAN_RADIUS_CELLS;
        float lookAhead = Math.max(MIN_LOOKAHEAD_CELLS, radius * LOOKAHEAD_RADII);
        float finalLeg = Math.max(MIN_FINAL_LEG_CELLS, radius * FINAL_LEG_RADII);
        float[] fix = strip.approachPoint(threshold, finalLeg);

        ReedsShepp.Path hook = ReedsShepp.shortestForward(
                from, new Pose(fix[0], fix[1], runwayFacing), radius);
        if (hook != null) {
            List<Pose> poses = ReedsShepp.samplePath(from, radius, hook, SAMPLE_STEP_CELLS);
            float[] xs = new float[poses.size() + 2];
            float[] ys = new float[poses.size() + 2];
            int kept = 0;
            for (Pose pose : poses) {
                // A sampler that walks in fixed steps and then adds the end
                // point leaves a duplicate whenever the path length happens to
                // divide evenly. Two points a thousandth of a cell apart carry
                // no direction, so the segment between them reads as a
                // reversal to anything asking which way the path goes.
                if (kept > 0 && Math.hypot(pose.x - xs[kept - 1], pose.y - ys[kept - 1])
                        < DEGENERATE_STEP_CELLS) {
                    continue;
                }
                xs[kept] = pose.x;
                ys[kept] = pose.y;
                kept++;
            }
            xs[kept] = threshold[0];
            ys[kept] = threshold[1];
            xs[kept + 1] = far[0];
            ys[kept + 1] = far[1];
            return new RunwayApproach(trim(xs, kept + 2), trim(ys, kept + 2), kept,
                    lookAhead, /*solved*/ true,
                    !withinReachOfTheMap(poses, mapW, mapH, 2f * radius + finalLeg));
        }
        // Nothing curvature-feasible at all, which Dubins does not admit and a
        // degenerate handling profile might: fly straight at the fix and then
        // down the axis. The craft arrives on whatever heading it managed,
        // which is what a landing looked like before any of this — and is still
        // a landing, which is the requirement.
        return new RunwayApproach(
                new float[]{from.x, fix[0], threshold[0], far[0]},
                new float[]{from.y, fix[1], threshold[1], far[1]},
                2, lookAhead, /*solved*/ false, /*wide*/ false);
    }

    /** {@code source} cut down to its first {@code length} entries. */
    private static float[] trim(float[] source, int length) {
        if (length == source.length) return source;
        float[] out = new float[length];
        System.arraycopy(source, 0, out, 0, length);
        return out;
    }

    /**
     * Whether a circuit stays somewhere an aircraft may be.
     *
     * <p>Generous by a circuit diameter and a final leg outside the map,
     * because off the map is a legitimate place for an aircraft: every off-map
     * sortie enters and leaves across the boundary by construction, and the
     * final approach fix for a strip near an edge is off the map by
     * construction too. What this flags is a circuit that would take the craft
     * so far outside that it is gone rather than briefly wide.
     */
    private static boolean withinReachOfTheMap(List<Pose> poses, int mapW, int mapH,
                                               float marginCells) {
        for (Pose p : poses) {
            if (p.x < -marginCells || p.x > mapW + marginCells) return false;
            if (p.y < -marginCells || p.y > mapH + marginCells) return false;
        }
        return true;
    }
}
