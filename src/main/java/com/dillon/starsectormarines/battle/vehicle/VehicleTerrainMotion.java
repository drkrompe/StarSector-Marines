package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;

/**
 * Conservative continuous rectangle sweep. Ordinary integration interpolates
 * center and shortest yaw between tick poses; docking samples its actual RS
 * curve. Every accepted interval has a containing rectangle that clears the
 * current cells and reciprocal closed edges. Doubtful intervals are bisected
 * in time order; exhausting the bounded refinement stops at the proven prefix.
 * No number of clear point samples alone permits an interval through terrain.
 */
public final class VehicleTerrainMotion {
    private static final int MAX_INTERVALS = 256;
    private static final int MAX_DEPTH = 18;

    private VehicleTerrainMotion() {}

    public record Result(Pose pose, float fraction, boolean blocked) {}

    /** allowOffMap exempts only map bounds, never obstacles inside the map. */
    public static Result sweep(Pose from, Pose to, float length, float width,
                                NavigationGrid grid, boolean allowOffMap) {
        return new Sweep(from, to, length, width, grid, allowOffMap, null, 0f, 0f, 0f).run();
    }

    public static boolean isSweepFeasible(Pose from, Pose to, float length, float width,
                                          NavigationGrid grid) {
        return new Sweep(from, to, length, width, grid, false, null, 0f, 0f, 0f).isFeasible();
    }

    /** Actual curved center and yaw, including reverse/cusp segments. Distances are path arc lengths. */
    public static Result sweepReedsShepp(Pose origin, ReedsShepp.Path path, float turnRadius,
                                         float fromDistance, float toDistance, float length, float width,
                                         NavigationGrid grid) {
        if (!(turnRadius > 0f) || !Float.isFinite(turnRadius) || !Float.isFinite(fromDistance)
                || !Float.isFinite(toDistance) || fromDistance < 0f || toDistance < fromDistance) {
            throw new IllegalArgumentException("Finite forward path interval and positive turn radius required");
        }
        return new Sweep(origin, origin, length, width, grid, false,
                path, turnRadius, fromDistance, toDistance).run();
    }

    static boolean isReedsSheppFeasible(Pose origin, ReedsShepp.Path path, float turnRadius,
                                         float fromDistance, float toDistance, float length, float width,
                                         NavigationGrid grid) {
        return new Sweep(origin, origin, length, width, grid, false,
                path, turnRadius, fromDistance, toDistance).isFeasible();
    }

    private static final class Sweep {
        final Pose from, to;
        final double halfL, halfW, radius, yaw;
        final NavigationGrid grid;
        final boolean allowOffMap;
        final ReedsShepp.Path path;
        final float turnRadius, startDistance, endDistance;
        int intervals;

        Sweep(Pose from, Pose to, float length, float width, NavigationGrid grid, boolean allowOffMap,
              ReedsShepp.Path path, float turnRadius, float startDistance, float endDistance) {
            if (!finite(from) || !finite(to) || !Float.isFinite(length) || !Float.isFinite(width)
                    || length <= 0f || width <= 0f) throw new IllegalArgumentException("Finite poses and positive body dimensions required");
            this.from = from; this.to = to;
            halfL = length * .5d; halfW = width * .5d; radius = Math.hypot(halfL, halfW);
            yaw = ((to.facingDeg - (double) from.facingDeg) % 360d + 540d) % 360d - 180d;
            this.grid = grid; this.allowOffMap = allowOffMap;
            this.path = path; this.turnRadius = turnRadius;
            this.startDistance = startDistance; this.endDistance = endDistance;
        }

        Result run() {
            Pose start = pose(0d);
            if (!clear(start)) return new Result(start, 0f, true);
            double fraction = prefix(0d, 1d, 0);
            Pose result = pose(fraction);
            // Float publication may round a proven double boundary into terrain.
            // Retain the starting pose if that representation is not legal.
            if (!clear(result)) return new Result(start, 0f, true);
            return new Result(result, (float) fraction, fraction < 1d);
        }

        boolean isFeasible() {
            return clear(pose(0d)) && clear(pose(1d)) && feasible(0d, 1d, 0);
        }

        boolean feasible(double lo, double hi, int depth) {
            if (++intervals > MAX_INTERVALS) return false;
            if (enclosureClear(lo, hi)) return true;
            double mid = (lo + hi) * .5d;
            if (depth >= MAX_DEPTH || !clear(pose(mid))) return false;
            return feasible(lo, mid, depth + 1) && feasible(mid, hi, depth + 1);
        }

        double prefix(double lo, double hi, int depth) {
            if (++intervals > MAX_INTERVALS) return lo;
            if (enclosureClear(lo, hi)) return hi;
            if (depth >= MAX_DEPTH) return lo;
            double mid = (lo + hi) * .5d;
            double first = prefix(lo, mid, depth + 1);
            if (first < mid) return first;
            return prefix(mid, hi, depth + 1);
        }

        boolean enclosureClear(double lo, double hi) {
            double mid = (lo + hi) * .5d;
            // Keep linear enclosures in double precision: rounding their
            // center before proving an interval could understate one side.
            double cx, cy, facing;
            if (path == null) {
                cx = from.x + (to.x - (double) from.x) * mid;
                cy = from.y + (to.y - (double) from.y) * mid;
                facing = from.facingDeg + yaw * mid;
            } else {
                Pose center = pose(mid);
                cx = center.x; cy = center.y; facing = center.facingDeg;
            }
            double extraL, extraW, angle;
            if (path == null) {
                double rad = Math.toRadians(facing);
                double dx = (to.x - (double) from.x) * (hi - lo) * .5d;
                double dy = (to.y - (double) from.y) * (hi - lo) * .5d;
                extraL = Math.abs(-Math.sin(rad) * dx + Math.cos(rad) * dy);
                extraW = Math.abs(Math.cos(rad) * dx + Math.sin(rad) * dy);
                angle = Math.toRadians(Math.abs(yaw)) * (hi - lo) * .5d;
            } else {
                // Arc distance bounds displacement in every direction, and
                // curvature bounds yaw even across direction/steering cusps.
                double distance = (endDistance - (double) startDistance) * (hi - lo) * .5d;
                extraL = distance; extraW = distance;
                angle = distance / turnRadius;
            }
            double rotationalPad = 2d * radius * Math.sin(Math.min(Math.PI, angle) * .5d);
            if (path != null && endDistance > startDistance) {
                // RS publishes floats; include their rounding envelope in the
                // continuous proof instead of trusting a rounded midpoint.
                rotationalPad += Math.ulp((float) cx) + Math.ulp((float) cy)
                        + radius * Math.toRadians(Math.ulp((float) facing));
            }
            return VehicleFootprint.clears(cx, cy, facing,
                    halfL + extraL + rotationalPad, halfW + extraW + rotationalPad, grid, allowOffMap);
        }

        Pose pose(double t) {
            if (path != null) return ReedsShepp.sample(from, turnRadius, path,
                    (float) (startDistance + (endDistance - (double) startDistance) * t));
            return new Pose((float) (from.x + (to.x - (double) from.x) * t),
                    (float) (from.y + (to.y - (double) from.y) * t),
                    (float) (from.facingDeg + yaw * t));
        }

        boolean clear(Pose pose) {
            return VehicleFootprint.clears(pose.x, pose.y, pose.facingDeg, halfL, halfW, grid, allowOffMap);
        }

        static boolean finite(Pose pose) {
            return pose != null && Float.isFinite(pose.x) && Float.isFinite(pose.y) && Float.isFinite(pose.facingDeg);
        }
    }
}
