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
 */
public final class TurnAwareCorridor {

    private static final float STRAIGHT_ANGLE_EPS_DEG = 3f;
    private static final float SAMPLE_CELLS = 0.25f;
    private static final float FOOTPRINT_SLACK_CELLS = 0.6f;
    private static final float LENGTH_EPS = 1e-3f;

    /** Refined points, or {@code null} plus the route location that failed. */
    public record Result(float[][] points, float failedX, float failedY) {
        static Result success(float[][] points) { return new Result(points, Float.NaN, Float.NaN); }
        static Result failure(float x, float y) { return new Result(null, x, y); }
    }

    private TurnAwareCorridor() {}

    public static Result refine(float[][] route, VehicleType type, NavigationGrid grid) {
        float[] xs = route[0], ys = route[1];
        int count = Math.min(xs.length, ys.length);
        if (count < 2) return Result.failure(count == 0 ? 0f : xs[0], count == 0 ? 0f : ys[0]);

        GroundBody body = type.createBody();
        if (!(body instanceof BicycleBody bicycle)) return Result.success(route);
        float radius = bicycle.minTurnRadiusCells();

        Corner[] corners = new Corner[count];
        for (int i = 1; i < count - 1; i++) {
            Corner corner = cornerAt(xs, ys, i, radius);
            if (corner == Corner.INVALID) return Result.failure(xs[i], ys[i]);
            corners[i] = corner;
        }

        // Adjacent fillets must not consume more than the straight segment
        // between their vertices. If they overlap, no forward min-radius path
        // follows this route ordering.
        for (int i = 1; i < count - 2; i++) {
            float used = tangent(corners[i]) + tangent(corners[i + 1]);
            float available = distance(xs[i], ys[i], xs[i + 1], ys[i + 1]);
            if (used > available - LENGTH_EPS) {
                return Result.failure((xs[i] + xs[i + 1]) * 0.5f,
                        (ys[i] + ys[i + 1]) * 0.5f);
            }
        }

        List<Float> outX = new ArrayList<>();
        List<Float> outY = new ArrayList<>();
        addDistinct(outX, outY, xs[0], ys[0]);
        for (int i = 1; i < count - 1; i++) {
            Corner corner = corners[i];
            if (corner == null) {
                addDistinct(outX, outY, xs[i], ys[i]);
                continue;
            }
            addDistinct(outX, outY, corner.entryX, corner.entryY);
            int samples = Math.max(1, (int) Math.ceil(radius * corner.sweepRadians / SAMPLE_CELLS));
            for (int sample = 1; sample <= samples; sample++) {
                float t = sample / (float) samples;
                double angle = corner.startAngle + corner.signedSweepRadians * t;
                addDistinct(outX, outY,
                        corner.centerX + radius * (float) Math.cos(angle),
                        corner.centerY + radius * (float) Math.sin(angle));
            }
        }
        addDistinct(outX, outY, xs[count - 1], ys[count - 1]);

        float[][] refined = arrays(outX, outY);
        float length = type.visualLengthCells + FOOTPRINT_SLACK_CELLS;
        float width = type.visualWidthCells + FOOTPRINT_SLACK_CELLS;
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
                    return Result.failure(x, y);
                }
            }
        }
        return Result.success(refined);
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

    private static float tangent(Corner corner) {
        return corner == null ? 0f : corner.tangent;
    }

    private static void addDistinct(List<Float> xs, List<Float> ys, float x, float y) {
        if (!xs.isEmpty()) {
            float dx = x - xs.get(xs.size() - 1), dy = y - ys.get(ys.size() - 1);
            if (dx * dx + dy * dy < 1e-8f) return;
        }
        xs.add(x);
        ys.add(y);
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
