package com.dillon.starsectormarines.battle.warship;

import com.dillon.starsectormarines.battle.air.AirScale;

import java.util.ArrayList;
import java.util.List;

/**
 * The true scale a ship is drawn at while it sits in a berth, and the lift run that carries its body to the airborne
 * scale ({@code landed-ships-true-scale.md}).
 *
 * <p>The convention is the grounded Shepherd's: Morrow's 92-pixel hull blocks 24 cells of ground beside one-cell
 * infantry, so a landed hull is {@link #CELLS_PER_HULL_PX} cells per pixel of its vanilla sprite: a Hound is about
 * nineteen cells long and a Venture about fifty. In flight a hull is drawn at {@link WarshipScale#HULL_PRESENCE}
 * times the shared air density. The factor between the two is a ratio to whatever flight presence is in force.
 *
 * <p>A lifting hull does not change size where it sits. It flies a {@linkplain #liftRun lift run}: ahead off the
 * berth, round in a climbing turn toward where it is going, and out along that bearing. Its body, its shadow and its
 * mounts follow the share of that run it has flown, so the size change is carried by travel and reads as altitude
 * and distance rather than as a hull shrinking on the pad.
 */
public final class LandedScale {

    /** Cells per vanilla hull pixel for a landed ship: Morrow's 24 cells over her 92-pixel sprite. */
    public static final float CELLS_PER_HULL_PX = 24f / 92f;

    /** Run ahead off the berth before the turn, in landed hull lengths. */
    static final float AHEAD_LENGTHS = 0.4f;
    /** Radius of the climbing turn, in landed hull lengths. */
    static final float TURN_LENGTHS = 0.5f;
    /** Run out of the turn along the new bearing, in landed hull lengths. */
    static final float EXIT_LENGTHS = 0.5f;
    /** Smallest turn radius in cells, so a small hull still comes round visibly rather than pivoting. */
    static final float MIN_TURN_CELLS = 8f;
    /** Largest arc between two run waypoints, in degrees. */
    static final float ARC_STEP_DEGREES = 20f;
    /**
     * Seconds after which a lift counts as complete however far the hull has flown: a hull taken over, held or
     * blocked mid-run still reaches flight scale rather than staying at its berth size indefinitely.
     */
    public static final float LIFT_FALLBACK_SECONDS = 60f;

    private LandedScale() {}

    /** A landed hull's size as a multiple of the shared air density (about 5.8). */
    public static float berthPresence() {
        return CELLS_PER_HULL_PX * AirScale.METERS_PER_CELL / AirScale.METERS_PER_PX;
    }

    /** How many times larger a landed body is drawn than the same hull in flight. */
    public static float berthFactor() {
        return berthFactor(WarshipScale.HULL_PRESENCE);
    }

    /** {@link #berthFactor()} against a stated flight presence. */
    public static float berthFactor(float flightPresence) {
        if (!(flightPresence > 0f)) throw new IllegalArgumentException("Flight presence must be positive");
        return berthPresence() / flightPresence;
    }

    /**
     * The body factor of a hull that sat at {@code berthFactor} and has flown {@code progress} of its lift run: the
     * berth factor before it lifts (negative progress means it has not), easing by smoothstep to one at the end of
     * the run, and one thereafter.
     */
    public static float bodyFactor(float berthFactor, float progress) {
        if (progress < 0f) return berthFactor;
        float t = Math.min(1f, progress);
        float eased = t * t * (3f - 2f * t);
        return berthFactor + (1f - berthFactor) * eased;
    }

    /**
     * The lift run for a hull at {@code (x, y)} with its nose on {@code noseDegrees} (mathematical bearing, east zero,
     * counter-clockwise), turning toward {@code (towardX, towardY)}: a leg ahead, an arc to the bearing of the target
     * as seen from the end of that leg, and a leg out along it. Lengths scale with the hull's landed length. The
     * starting point is not included.
     */
    public static float[][] liftRun(float x, float y, float noseDegrees, float towardX, float towardY, float landedLength) {
        float length = Math.max(1f, landedLength);
        double nose = Math.toRadians(noseDegrees);
        float ahead = AHEAD_LENGTHS * length;
        float radius = Math.max(MIN_TURN_CELLS, TURN_LENGTHS * length);
        float exit = EXIT_LENGTHS * length;
        List<float[]> points = new ArrayList<>();
        float ax = x + (float) Math.cos(nose) * ahead;
        float ay = y + (float) Math.sin(nose) * ahead;
        points.add(new float[]{ax, ay});
        double want = Math.atan2(towardY - ay, towardX - ax);
        if (!Double.isFinite(want) || (towardX == ax && towardY == ay)) want = nose;
        double turn = Math.IEEEremainder(want - nose, 2 * Math.PI);
        double heading = nose;
        if (Math.abs(turn) > 1e-3) {
            double side = Math.signum(turn);
            // Centre of the turning circle, abeam the leg's end on the side the hull turns toward.
            double cx = ax + Math.cos(nose + side * Math.PI / 2) * radius;
            double cy = ay + Math.sin(nose + side * Math.PI / 2) * radius;
            int steps = Math.max(1, (int) Math.ceil(Math.toDegrees(Math.abs(turn)) / ARC_STEP_DEGREES));
            for (int i = 1; i <= steps; i++) {
                heading = nose + turn * i / steps;
                double fromCentre = heading - side * Math.PI / 2;
                points.add(new float[]{(float) (cx + Math.cos(fromCentre) * radius), (float) (cy + Math.sin(fromCentre) * radius)});
            }
        }
        float[] end = points.get(points.size() - 1);
        points.add(new float[]{end[0] + (float) Math.cos(heading) * exit, end[1] + (float) Math.sin(heading) * exit});
        return points.toArray(new float[0][]);
    }

    /**
     * The body factor of a hull falling out of the sky: {@code burstBody} (whatever it was drawn at when it burst,
     * flight scale or part way through a lift) easing by smoothstep to {@code berthFactor}, true landed scale, over
     * {@code fall} (nought at the burst, one at the strike). The lift run read backwards, keyed to the fall.
     */
    public static float fallFactor(float burstBody, float berthFactor, float fall) {
        float t = Math.max(0f, Math.min(1f, fall));
        float eased = t * t * (3f - 2f * t);
        return burstBody + (berthFactor - burstBody) * eased;
    }

    /** Length of a run flown from {@code (x, y)} through every waypoint in turn. */
    public static float runLength(float x, float y, float[][] run) {
        float total = 0f;
        float px = x, py = y;
        for (float[] p : run) {
            total += (float) Math.hypot(p[0] - px, p[1] - py);
            px = p[0];
            py = p[1];
        }
        return total;
    }

    /**
     * How much of {@code run} a hull at {@code (x, y)} heading for waypoint {@code index} has flown, from zero to
     * one: what is left is its distance to that waypoint plus the legs beyond it.
     */
    public static float runProgress(float x, float y, float[][] run, int index, float runLength) {
        if (run == null || index >= run.length) return 1f;
        float left = (float) Math.hypot(run[index][0] - x, run[index][1] - y);
        for (int i = index; i < run.length - 1; i++)
            left += (float) Math.hypot(run[i + 1][0] - run[i][0], run[i + 1][1] - run[i][1]);
        return Math.max(0f, Math.min(1f, 1f - left / Math.max(1e-3f, runLength)));
    }
}
