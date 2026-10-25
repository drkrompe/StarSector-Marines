package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.air.AirBody;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;

import java.util.ArrayList;
import java.util.List;

/**
 * Converts a sparse clearance-valid route into a forward-drivable corridor.
 * Every material vertex is replaced by a circular fillet at the vehicle's
 * minimum turn radius, then the full result is footprint-sampled. A failure
 * identifies the bend the macro router must avoid and search around rather
 * than handing an instantaneous heading change to the controller.
 *
 * <p>A hull that turns on the spot as a matter of course ({@link NeutralPivot#onRoutes}) keeps a bend no
 * fillet fits as a sharp vertex instead, provided its footprint clears the whole
 * rotation there: the controller pivots at it. Every bend a fillet fits is still
 * filleted, so a pivot is only ever where the turning circle could not go.
 */
public final class TurnAwareCorridor {

    private static final float STRAIGHT_ANGLE_EPS_DEG = 3f;
    private static final float SAMPLE_CELLS = 0.25f;
    private static final float FOOTPRINT_SLACK_CELLS = 0.6f;
    private static final float LENGTH_EPS = 1e-3f;
    /** Sharpest bend (degrees) route proof keeps as a pivot; a sharper one is the route doubling back. */
    private static final float MAX_PIVOT_BEND_DEG = 135f;

    /** Refined points, or {@code null} plus the route location that failed. */
    public record Result(float[][] points, float failedX, float failedY) {
        static Result success(float[][] points) { return new Result(points, Float.NaN, Float.NaN); }
        static Result failure(float x, float y) { return new Result(null, x, y); }
    }

    private TurnAwareCorridor() {}

    public static Result refine(float[][] route, VehicleType type, NavigationGrid grid) {
        int given = Math.min(route[0].length, route[1].length);
        if (given < 2) return Result.failure(given == 0 ? 0f : route[0][0], given == 0 ? 0f : route[1][0]);

        GroundBody body = type.createBody();
        if (!(body instanceof BicycleBody bicycle)) return Result.success(route);
        float radius = bicycle.minTurnRadiusCells();
        float[][] bends = withoutStraightVertices(route[0], route[1], given);
        float[] xs = bends[0], ys = bends[1];
        int count = xs.length;

        boolean pivots = NeutralPivot.onRoutes(type);
        float length = type.visualLengthCells + FOOTPRINT_SLACK_CELLS;
        float width = type.visualWidthCells + FOOTPRINT_SLACK_CELLS;
        Corner[] corners = new Corner[count];
        for (int i = 1; i < count - 1; i++) {
            Corner corner = cornerAt(xs, ys, i, radius);
            if (corner == Corner.INVALID) {
                if (!pivots || !pivotFits(xs, ys, i, length, width, grid)) return Result.failure(xs[i], ys[i]);
                corner = Corner.PIVOT;
            }
            corners[i] = corner;
        }

        // Adjacent fillets must not consume more than the straight segment
        // between their vertices. If they overlap, no forward min-radius path
        // follows this route ordering — unless the hull pivots at one of the
        // two, the wider first, which gives its whole share back.
        for (int i = 1; i < count - 2; i++) {
            float available = distance(xs[i], ys[i], xs[i + 1], ys[i + 1]);
            if (tangent(corners[i]) + tangent(corners[i + 1]) <= available - LENGTH_EPS) continue;
            int wider = tangent(corners[i]) >= tangent(corners[i + 1]) ? i : i + 1;
            int other = wider == i ? i + 1 : i;
            if (pivots && pivotFits(xs, ys, wider, length, width, grid)) corners[wider] = Corner.PIVOT;
            else if (pivots && pivotFits(xs, ys, other, length, width, grid)) corners[other] = Corner.PIVOT;
            if (tangent(corners[i]) + tangent(corners[i + 1]) > available - LENGTH_EPS) {
                return Result.failure((xs[i] + xs[i + 1]) * 0.5f,
                        (ys[i] + ys[i + 1]) * 0.5f);
            }
        }

        // A fillet whose arc clips the ground is a bend the turning circle cannot drive either; a hull that turns on
        // the spot pivots there instead, and the corridor is laid again. Each pass pivots one more bend, so this ends.
        while (true) {
            List<Float> outX = new ArrayList<>();
            List<Float> outY = new ArrayList<>();
            List<Integer> owner = new ArrayList<>();
            addDistinct(outX, outY, owner, -1, xs[0], ys[0]);
            for (int i = 1; i < count - 1; i++) {
                Corner corner = corners[i];
                if (corner == null || corner == Corner.PIVOT) {
                    addDistinct(outX, outY, owner, -1, xs[i], ys[i]);
                    continue;
                }
                addDistinct(outX, outY, owner, -1, corner.entryX, corner.entryY);
                int samples = Math.max(1, (int) Math.ceil(radius * corner.sweepRadians / SAMPLE_CELLS));
                for (int sample = 1; sample <= samples; sample++) {
                    float t = sample / (float) samples;
                    double angle = corner.startAngle + corner.signedSweepRadians * t;
                    addDistinct(outX, outY, owner, i,
                            corner.centerX + radius * (float) Math.cos(angle),
                            corner.centerY + radius * (float) Math.sin(angle));
                }
            }
            addDistinct(outX, outY, owner, -1, xs[count - 1], ys[count - 1]);

            float[][] refined = arrays(outX, outY);
            float[] failed = firstInfeasible(refined, length, width, grid);
            if (failed == null) return Result.success(refined);
            int bend = owner.get((int) failed[2]);
            if (!pivots || bend < 0 || !pivotFits(xs, ys, bend, length, width, grid)) {
                return Result.failure(failed[0], failed[1]);
            }
            corners[bend] = Corner.PIVOT;
        }
    }

    /**
     * The first pose along {@code refined} whose slackened footprint does not clear the ground, as
     * {@code {x, y, index}} with the index of the point that ends its segment; {@code null} when every pose clears.
     */
    private static float[] firstInfeasible(float[][] refined, float length, float width, NavigationGrid grid) {
        for (int i = 1; i < refined[0].length; i++) {
            float ax = refined[0][i - 1], ay = refined[1][i - 1];
            float bx = refined[0][i], by = refined[1][i];
            float dx = bx - ax, dy = by - ay;
            float segmentLength = (float) Math.hypot(dx, dy);
            if (segmentLength < LENGTH_EPS) continue;
            float facing = AirBody.facingToward(dx, dy);
            int samples = Math.max(1, (int) Math.ceil(segmentLength / SAMPLE_CELLS));
            for (int sample = 0; sample <= samples; sample++) {
                float t = sample / (float) samples;
                float x = ax + dx * t, y = ay + dy * t;
                if (!VehicleFootprint.isPoseFeasible(x, y, facing, length, width, grid)) {
                    return new float[]{x, y, i};
                }
            }
        }
        return null;
    }

    /**
     * Whether a route can set out from {@code (x, y)} whichever way its first leg points: the footprint, with the
     * slack {@link #refine} samples it at, clears the ground at every one of eight headings. A hull set down where
     * this is false may have every route refused at its very first pose, the leg's heading swinging its tail into
     * the trees beside it.
     */
    public static boolean canSetOutFrom(float x, float y, VehicleType type, NavigationGrid grid) {
        float length = type.visualLengthCells + FOOTPRINT_SLACK_CELLS;
        float width = type.visualWidthCells + FOOTPRINT_SLACK_CELLS;
        for (int heading = 0; heading < 360; heading += 45) {
            if (!VehicleFootprint.isPoseFeasible(x, y, heading, length, width, grid)) return false;
        }
        return true;
    }

    /**
     * The route with every vertex it runs straight through removed (one whose bend is under
     * {@link #STRAIGHT_ANGLE_EPS_DEG}, or that repeats its neighbour), so a corner's legs are measured to the
     * corners either side of it. A string-pull can leave a vertex in the middle of a straight run; counted as a
     * corner's end it cut the leg to a cell, too short for any fillet, and a plain bend at the end of a long
     * diagonal was refused.
     */
    static float[][] withoutStraightVertices(float[] xs, float[] ys, int count) {
        List<Float> outX = new ArrayList<>();
        List<Float> outY = new ArrayList<>();
        outX.add(xs[0]);
        outY.add(ys[0]);
        for (int i = 1; i < count - 1; i++) {
            float px = outX.get(outX.size() - 1), py = outY.get(outY.size() - 1);
            float inX = xs[i] - px, inY = ys[i] - py, outLegX = xs[i + 1] - xs[i], outLegY = ys[i + 1] - ys[i];
            float inLength = (float) Math.hypot(inX, inY), outLength = (float) Math.hypot(outLegX, outLegY);
            if (inLength < LENGTH_EPS || outLength < LENGTH_EPS) continue;
            float dot = clamp((inX * outLegX + inY * outLegY) / (inLength * outLength), -1f, 1f);
            if (Math.toDegrees(Math.acos(dot)) < STRAIGHT_ANGLE_EPS_DEG) continue;
            outX.add(xs[i]);
            outY.add(ys[i]);
        }
        outX.add(xs[count - 1]);
        outY.add(ys[count - 1]);
        return arrays(outX, outY);
    }

    private static Corner cornerAt(float[] xs, float[] ys, int i, float radius) {
        float inX = xs[i] - xs[i - 1], inY = ys[i] - ys[i - 1];
        float outX = xs[i + 1] - xs[i], outY = ys[i + 1] - ys[i];
        float inLength = (float) Math.hypot(inX, inY);
        float outLength = (float) Math.hypot(outX, outY);
        if (inLength < LENGTH_EPS || outLength < LENGTH_EPS) return null;
        inX /= inLength;
        inY /= inLength;
        outX /= outLength;
        outY /= outLength;

        float dot = clamp(inX * outX + inY * outY, -1f, 1f);
        float angle = (float) Math.acos(dot);
        if (Math.toDegrees(angle) < STRAIGHT_ANGLE_EPS_DEG) return null;
        float cross = inX * outY - inY * outX;
        if (Math.abs(cross) < 1e-5f) return Corner.INVALID;

        float tangent = radius * (float) Math.tan(angle * 0.5f);
        if (!Float.isFinite(tangent)
                || tangent > inLength - LENGTH_EPS
                || tangent > outLength - LENGTH_EPS) {
            return Corner.INVALID;
        }

        float entryX = xs[i] - inX * tangent;
        float entryY = ys[i] - inY * tangent;
        float normalX = cross > 0f ? -inY : inY;
        float normalY = cross > 0f ? inX : -inX;
        float centerX = entryX + normalX * radius;
        float centerY = entryY + normalY * radius;
        double startAngle = Math.atan2(entryY - centerY, entryX - centerX);
        float signedSweep = cross > 0f ? angle : -angle;
        return new Corner(tangent, entryX, entryY,
                centerX, centerY, startAngle, signedSweep, angle);
    }

    /**
     * Whether the slackened footprint can turn on the spot at vertex {@code i} from its in-leg's heading to its
     * out-leg's. A bend sharper than {@link #MAX_PIVOT_BEND_DEG} is a route doubling back on itself, an artefact of
     * where its ends snapped rather than a bend in the ground, and is refused as before so the search looks
     * elsewhere; turning a hull right round is the controller's, where it starts or leaves facing the wrong way.
     */
    private static boolean pivotFits(float[] xs, float[] ys, int i, float length, float width, NavigationGrid grid) {
        float in = AirBody.facingToward(xs[i] - xs[i - 1], ys[i] - ys[i - 1]);
        float out = AirBody.facingToward(xs[i + 1] - xs[i], ys[i + 1] - ys[i]);
        if (Math.abs(NeutralPivot.signedDegrees(out - in)) > MAX_PIVOT_BEND_DEG) return false;
        return NeutralPivot.clearDirection(xs[i], ys[i], in, out, length, width, grid) != 0;
    }

    private static float tangent(Corner corner) {
        return corner == null ? 0f : corner.tangent;
    }

    /** Appends a point unless it repeats the last, recording which bend's arc it lies on ({@code -1} for none). */
    private static void addDistinct(List<Float> xs, List<Float> ys, List<Integer> owner, int bend, float x, float y) {
        if (!xs.isEmpty()) {
            float dx = x - xs.get(xs.size() - 1), dy = y - ys.get(ys.size() - 1);
            if (dx * dx + dy * dy < 1e-8f) return;
        }
        xs.add(x);
        ys.add(y);
        owner.add(bend);
    }

    private static float[][] arrays(List<Float> xs, List<Float> ys) {
        float[] outX = new float[xs.size()];
        float[] outY = new float[ys.size()];
        for (int i = 0; i < xs.size(); i++) {
            outX[i] = xs.get(i);
            outY[i] = ys.get(i);
        }
        return new float[][]{outX, outY};
    }

    private static float distance(float ax, float ay, float bx, float by) {
        return (float) Math.hypot(bx - ax, by - ay);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static final class Corner {
        static final Corner INVALID = new Corner();
        /** A bend kept sharp because the hull turns on the spot there; it uses none of its legs. */
        static final Corner PIVOT = new Corner();

        final float tangent;
        final float entryX, entryY;
        final float centerX, centerY;
        final double startAngle;
        final float signedSweepRadians;
        final float sweepRadians;

        private Corner() {
            tangent = entryX = entryY = centerX = centerY = 0f;
            startAngle = signedSweepRadians = sweepRadians = 0f;
        }

        private Corner(float tangent, float entryX, float entryY,
                       float centerX, float centerY,
                       double startAngle, float signedSweepRadians, float sweepRadians) {
            this.tangent = tangent;
            this.entryX = entryX;
            this.entryY = entryY;
            this.centerX = centerX;
            this.centerY = centerY;
            this.startAngle = startAngle;
            this.signedSweepRadians = signedSweepRadians;
            this.sweepRadians = sweepRadians;
        }
    }
}
