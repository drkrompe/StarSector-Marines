package com.dillon.starsectormarines.battle.vehicle;

import java.util.ArrayList;
import java.util.List;

/**
 * Closed-form shortest forward+reverse path between two ({@code x, y, θ})
 * poses for a vehicle that can turn left or right at a fixed minimum turn
 * radius {@code R} or go straight. Reeds & Shepp (1990) proved the optimal
 * path is always a sequence of at most five segments, each one of
 * {Left arc, Right arc, Straight} traversed either forward or in reverse,
 * and that there are exactly 48 such path families (12 base "words" × 4
 * geometric transformations: identity, timeflip τ, reflect μ, τμ).
 *
 * <p>This implementation covers the <b>CSC + CCC subset</b>: 2 base CSC
 * families (LSL, LSR) and 1 base CCC family (LRL) under all 4 transforms
 * = 12 candidates. CSC paths (arc–straight–arc) dominate when the vehicle
 * approaches from a road; CCC paths (arc–arc–arc) handle tight U-turns
 * and large heading changes in close quarters where CSC is infeasible.
 * Longer families (CCSC, CCSCC) remain out of scope — extend by adding
 * more base-family functions to {@link #enumerateCandidates}.
 *
 * <p>Math frame convention: internal math uses the standard robotics
 * convention (θ=0 along +X, positive CCW). The public API uses the mod's
 * facing convention (0° = +Y, positive CCW). Conversion is +90° at the
 * input boundary and -90° at the output boundary.
 *
 * <p>References:
 * <ul>
 *   <li>Reeds, Shepp. <i>Optimal paths for a car that goes both forwards
 *       and backwards.</i> Pacific J. Math, 145(2):367–393, 1990.</li>
 *   <li>LaValle, <i>Planning Algorithms</i>, §15.3.</li>
 *   <li>OMPL ReedsSheppStateSpace — canonical C++ implementation.</li>
 * </ul>
 */
public final class ReedsShepp {

    private static final double EPS = 1e-6;
    private static final double TWO_PI = 2.0 * Math.PI;

    private ReedsShepp() {}

    // ---- Data types ----

    /** One segment of a Reeds-Shepp path. Length is in unit-radius space (radians for L/R, units for S). */
    public static final class Element {
        public final Type type;
        public final boolean forward;
        public final float length;

        public Element(Type type, boolean forward, float length) {
            this.type = type;
            this.forward = forward;
            this.length = length;
        }

        @Override
        public String toString() {
            return type + (forward ? "+" : "-") + "(" + length + ")";
        }
    }

    public enum Type { LEFT, RIGHT, STRAIGHT }

    /**
     * A full Reeds-Shepp path between two poses. Lengths are in unit-radius
     * space; multiply by {@code turnRadius} to get cell units.
     */
    public static final class Path {
        public final List<Element> elements;
        /** Total path length in unit-radius space — sum of segment lengths (arc length = arc angle for L/R). */
        public final float lengthUnits;

        public Path(List<Element> elements) {
            this.elements = elements;
            float total = 0f;
            for (Element e : elements) total += e.length;
            this.lengthUnits = total;
        }

        public float lengthCells(float turnRadius) { return lengthUnits * turnRadius; }
    }

    // ---- Public API ----

    /**
     * Compute the shortest CSC+CCC Reeds-Shepp path from {@code start} to
     * {@code goal} for a vehicle with minimum turn radius {@code turnRadius}.
     * Returns {@code null} if no candidate family is feasible — this should
     * not happen for the CSC subset on well-separated poses, but a degenerate
     * input (start == goal exactly) returns a zero-length path.
     */
    public static Path shortest(Pose start, Pose goal, float turnRadius) {
        double sTheta = Math.toRadians(start.facingDeg + 90.0);
        double gTheta = Math.toRadians(goal.facingDeg + 90.0);
        double dx = goal.x - start.x;
        double dy = goal.y - start.y;
        double cs = Math.cos(sTheta);
        double sn = Math.sin(sTheta);
        double x = (dx * cs + dy * sn) / turnRadius;
        double y = (-dx * sn + dy * cs) / turnRadius;
        double phi = mod2pi(gTheta - sTheta);

        Path best = null;
        float bestLen = Float.MAX_VALUE;
        for (Path p : enumerateCandidates(x, y, phi)) {
            if (p == null) continue;
            if (p.lengthUnits < bestLen) {
                bestLen = p.lengthUnits;
                best = p;
            }
        }
        return best;
    }

    /**
     * Sample the path at the given distance from start. Returns the world
     * pose ({@code x, y, facingDeg}) the vehicle would occupy after
     * traveling {@code distanceCells} along the path starting at
     * {@code start}.
     */
    public static Pose sample(Pose start, float turnRadius, Path path, float distanceCells) {
        double remaining = Math.max(0.0, distanceCells / turnRadius);
        double lx = 0, ly = 0, lTheta = 0;
        for (Element e : path.elements) {
            if (e.length <= 0) continue;
            double take = Math.min(remaining, e.length);
            double[] end = traverse(lx, ly, lTheta, e.type, e.forward, take);
            lx = end[0]; ly = end[1]; lTheta = end[2];
            remaining -= take;
            if (remaining <= 0) break;
        }
        double sTheta = Math.toRadians(start.facingDeg + 90.0);
        double cs = Math.cos(sTheta);
        double sn = Math.sin(sTheta);
        float worldX = (float) (start.x + (lx * cs - ly * sn) * turnRadius);
        float worldY = (float) (start.y + (lx * sn + ly * cs) * turnRadius);
        float worldFacingDeg = (float) (Math.toDegrees(lTheta + sTheta) - 90.0);
        return new Pose(worldX, worldY, worldFacingDeg);
    }

    /**
     * Sample evenly-spaced poses along the path. The first pose is
     * {@code start}; subsequent poses are spaced {@code stepCells} apart;
     * the last pose is the goal (may be closer than {@code stepCells} to
     * the previous one).
     */
    public static List<Pose> samplePath(Pose start, float turnRadius, Path path, float stepCells) {
        List<Pose> poses = new ArrayList<>();
        float total = path.lengthCells(turnRadius);
        for (float d = 0; d < total; d += stepCells) {
            poses.add(sample(start, turnRadius, path, d));
        }
        poses.add(sample(start, turnRadius, path, total));
        return poses;
    }

    // ---- Base families (math in unit-radius frame, start at origin facing +X) ----

    /**
     * CSC family L+S+L+ — forward left arc, forward straight, forward left
     * arc. Derivation (origin facing +X, unit radius):
     * <pre>
     *   After L+(t):  pos = (sin t, 1 - cos t), heading t
     *   After S+(u):  pos += (u cos t, u sin t), heading t
     *   After L+(v):  heading = t + v = φ
     *
     *   Closed form (let ξ = x - sin φ, η = y - 1 + cos φ):
     *     u = √(ξ² + η²)
     *     t = atan2(η, ξ)
     *     v = mod2π(φ - t)
     *   Feasible iff t ≥ 0 and v ≥ 0.
     * </pre>
     */
    private static Path LpSpLp(double x, double y, double phi) {
        double xi = x - Math.sin(phi);
        double eta = y - 1.0 + Math.cos(phi);
        double u = Math.sqrt(xi * xi + eta * eta);
        double t = Math.atan2(eta, xi);
        double v = mod2pi(phi - t);
        if (t < -EPS || v < -EPS) return null;
        return path(elem(Type.LEFT, true, t), elem(Type.STRAIGHT, true, u), elem(Type.LEFT, true, v));
    }

    /**
     * CSC family L+S+R+ — forward left arc, forward straight, forward right
     * arc. Derivation (origin facing +X, unit radius):
     * <pre>
     *   After L+(t):  pos = (sin t, 1 - cos t), heading t
     *   After S+(u):  pos += (u cos t, u sin t), heading t
     *   After R+(v):  heading = t - v = φ
     *
     *   Let ξ = x + sin φ, η = y - 1 - cos φ.
     *   Algebra gives:
     *     ξ² + η² = 4 + u²
     *     ξ = 2 sin t + u cos t = √(4+u²) sin(t + atan2(u, 2))
     *     η = u sin t - 2 cos t = -√(4+u²) cos(t + atan2(u, 2))
     *   ⟹ u = √(ξ² + η² - 4)  (infeasible if ξ² + η² < 4)
     *     t = atan2(ξ, -η) - atan2(u, 2)
     *     v = mod2π(t - φ)
     * </pre>
     */
    private static Path LpSpRp(double x, double y, double phi) {
        double xi = x + Math.sin(phi);
        double eta = y - 1.0 - Math.cos(phi);
        double rho2 = xi * xi + eta * eta;
        if (rho2 < 4.0) return null;
        double u = Math.sqrt(rho2 - 4.0);
        double alpha = Math.atan2(u, 2.0);
        double t = mod2pi(Math.atan2(xi, -eta) - alpha);
        double v = mod2pi(t - phi);
        if (t < -EPS || v < -EPS) return null;
        return path(elem(Type.LEFT, true, t), elem(Type.STRAIGHT, true, u), elem(Type.RIGHT, true, v));
    }

    // ---- CCC base family ----

    /**
     * CCC family L+R−L+ — forward left arc, reverse right arc, forward left
     * arc. Handles tight U-turns and large heading changes in close quarters
     * where CSC is infeasible.
     * <pre>
     *   After L+(t):  pos = (sin t, 1 − cos t), heading t
     *   After R−(u):  heading = t + u  (reverse right = +heading)
     *   After L+(v):  heading = t + u + v = φ  ⟹  v = φ − t − u
     *
     *   Position equations give (via sum-to-product):
     *     ξ = x − sin φ = −4 cos(t + u/2) sin(u/2)
     *     η = y − 1 + cos φ = −4 sin(t + u/2) sin(u/2)
     *
     *   Closed form:
     *     sin(u/2) = √(ξ² + η²) / 4   — feasible iff ξ² + η² ≤ 16
     *     u = 2 asin(sin(u/2))
     *     t = mod2π(atan2(η, ξ) − u/2 − π)
     *     v = mod2π(φ − t − u)
     * </pre>
     */
    private static Path LpRmLp(double x, double y, double phi) {
        double xi = x - Math.sin(phi);
        double eta = y - 1.0 + Math.cos(phi);
        double d2 = xi * xi + eta * eta;
        if (d2 > 16.0) return null;
        double sinHalfU = Math.sqrt(d2) / 4.0;
        if (sinHalfU > 1.0 + EPS) return null;
        if (sinHalfU > 1.0) sinHalfU = 1.0;
        double u = 2.0 * Math.asin(sinHalfU);
        double t = mod2pi(Math.atan2(eta, xi) - 0.5 * u - Math.PI);
        double v = mod2pi(phi - t - u);
        if (t < -EPS || u < -EPS || v < -EPS) return null;
        return path(
                elem(Type.LEFT, true, t),
                elem(Type.RIGHT, false, u),
                elem(Type.LEFT, true, v));
    }

    // ---- Transform composition ----

    /**
     * Enumerate all candidate paths for the input ({@code x, y, φ}) under
     * the CSC + CCC subset. Each base family is solved on the four
     * transformed inputs (identity, μ reflect, τ timeflip, τμ both) and
     * the resulting path is un-transformed.
     */
    private static Path[] enumerateCandidates(double x, double y, double phi) {
        return new Path[] {
                // --- CSC families ---
                // identity
                LpSpLp(x, y, phi),
                LpSpRp(x, y, phi),
                // reflect μ: (x, y, φ) → (x, -y, -φ); path: L↔R
                reflect(LpSpLp(x, -y, mod2pi(-phi))),
                reflect(LpSpRp(x, -y, mod2pi(-phi))),
                // timeflip τ: (x, y, φ) → (-x, y, -φ); path: forward↔reverse
                timeflip(LpSpLp(-x, y, mod2pi(-phi))),
                timeflip(LpSpRp(-x, y, mod2pi(-phi))),
                // τμ: (x, y, φ) → (-x, -y, φ); path: L↔R AND forward↔reverse
                timeflip(reflect(LpSpLp(-x, -y, phi))),
                timeflip(reflect(LpSpRp(-x, -y, phi))),
                // --- CCC family ---
                // identity: L+R−L+
                LpRmLp(x, y, phi),
                // reflect μ: R+L−R+
                reflect(LpRmLp(x, -y, mod2pi(-phi))),
                // timeflip τ: L−R+L−
                timeflip(LpRmLp(-x, y, mod2pi(-phi))),
                // τμ: R−L+R−
                timeflip(reflect(LpRmLp(-x, -y, phi)))
        };
    }

    private static Path reflect(Path p) {
        if (p == null) return null;
        List<Element> out = new ArrayList<>(p.elements.size());
        for (Element e : p.elements) {
            Type t = e.type == Type.LEFT ? Type.RIGHT
                    : e.type == Type.RIGHT ? Type.LEFT
                    : Type.STRAIGHT;
            out.add(new Element(t, e.forward, e.length));
        }
        return new Path(out);
    }

    private static Path timeflip(Path p) {
        if (p == null) return null;
        List<Element> out = new ArrayList<>(p.elements.size());
        for (Element e : p.elements) {
            out.add(new Element(e.type, !e.forward, e.length));
        }
        return new Path(out);
    }

    // ---- Path traversal (used by sample) ----

    /**
     * Integrate one segment forward by {@code length} units (in
     * unit-radius space), starting at local pose ({@code x, y, θ}).
     */
    private static double[] traverse(double x, double y, double theta,
                                     Type type, boolean forward, double length) {
        double dir = forward ? 1.0 : -1.0;
        if (type == Type.STRAIGHT) {
            return new double[] {
                    x + dir * length * Math.cos(theta),
                    y + dir * length * Math.sin(theta),
                    theta
            };
        }
        double sign = (type == Type.LEFT) ? 1.0 : -1.0;
        // Arc center is perpendicular to facing at unit distance: left perp
        // of (cos θ, sin θ) is (-sin θ, cos θ); right perp is (sin θ, -cos θ).
        double cx = x - sign * Math.sin(theta);
        double cy = y + sign * Math.cos(theta);
        // Heading change: L forward = +length, R forward = -length, reverse flips both.
        double dtheta = sign * dir * length;
        double newTheta = theta + dtheta;
        double newX = cx + sign * Math.sin(newTheta);
        double newY = cy - sign * Math.cos(newTheta);
        return new double[] { newX, newY, newTheta };
    }

    // ---- Helpers ----

    private static Element elem(Type type, boolean forward, double length) {
        return new Element(type, forward, (float) Math.max(0.0, length));
    }

    private static Path path(Element... elements) {
        List<Element> list = new ArrayList<>(elements.length);
        for (Element e : elements) list.add(e);
        return new Path(list);
    }

    /** Normalize an angle to {@code [0, 2π)}. */
    private static double mod2pi(double a) {
        double r = a % TWO_PI;
        if (r < 0) r += TWO_PI;
        return r;
    }
}
