package com.dillon.starsectormarines.battle.nav;

/**
 * Sweeps a circular ground body through the current navigation topology.
 * The caller supplies an already speed-limited displacement in cell space;
 * this class neither normalizes input nor mutates the grid or an actor.
 * Solid cell squares, reciprocal closed cardinal edges, and map bounds are
 * obstacles. Walkable cover is deliberately not an obstacle.
 *
 * <p>After the earliest contact the unused displacement is projected onto the
 * contact tangent and swept again. A bounded number of contacts may consume a
 * step; any unconsumed motion is discarded, never stored for a later tick.
 * Starting inside terrain returns the original position rather than inventing
 * a depenetration teleport. The caller retains ownership of placement/recovery.
 */
public final class ManualTerrainMotion {
    private static final double SKIN = 0.0001;
    private static final double EPSILON = 1e-10;
    private static final int MAX_CONTACTS = 8;

    private ManualTerrainMotion() {}

    /** Final position and the displacement actually applied, all in cells. */
    public record Result(float x, float y, float dx, float dy) {}

    /** Whether an existing body clears current terrain without any placement correction. */
    public static boolean canStand(NavigationGrid grid, float x, float y, float radius) {
        return Float.isFinite(x) && Float.isFinite(y)
                && Float.isFinite(radius) && radius > 0f
                && clear(grid, x, y, radius);
    }

    /**
     * Whether one complete straight displacement is clear, without sliding.
     * Uses the same contacts as {@link #move}; a contact at the endpoint is
     * conservatively rejected because move stops just short of that contact.
     */
    public static boolean canSweepStraight(NavigationGrid grid, float x, float y,
                                            float dx, float dy, float radius) {
        if (!Float.isFinite(dx) || !Float.isFinite(dy)
                || !canStand(grid, x, y, radius)
                || !canStand(grid, x + dx, y + dy, radius)) return false;
        if (dx == 0f && dy == 0f) return true;
        return sweep(grid, x, y, dx, dy, radius).time > 1d;
    }

    public static Result move(NavigationGrid grid, float x, float y,
                              float dx, float dy, float radius) {
        if (!Float.isFinite(x) || !Float.isFinite(y)
                || !Float.isFinite(dx) || !Float.isFinite(dy)
                || !Float.isFinite(radius) || radius <= 0f) {
            throw new IllegalArgumentException("Finite motion and a positive radius are required");
        }
        if (!clear(grid, x, y, radius)) return new Result(x, y, 0f, 0f);
        double px = x;
        double py = y;
        double rx = dx;
        double ry = dy;
        for (int contact = 0; contact < MAX_CONTACTS; contact++) {
            double length = Math.hypot(rx, ry);
            if (length <= EPSILON) break;
            Hit hit = sweep(grid, px, py, rx, ry, radius);
            if (hit.time > 1d) {
                px += rx;
                py += ry;
                break;
            }
            double safeTime = Math.max(0d, hit.time - SKIN / length);
            px += rx * safeTime;
            py += ry * safeTime;
            rx *= 1d - safeTime;
            ry *= 1d - safeTime;
            double inward = rx * hit.nx + ry * hit.ny;
            if (inward >= -EPSILON) break;
            rx -= inward * hit.nx;
            ry -= inward * hit.ny;
        }
        float resultX = (float) px;
        float resultY = (float) py;
        return new Result(resultX, resultY, resultX - x, resultY - y);
    }

    private static Hit sweep(NavigationGrid grid, double x, double y,
                             double dx, double dy, double radius) {
        Hit hit = new Hit();
        if (dx < 0d) hit.offer((radius - x) / dx, 1d, 0d, dx, dy);
        if (dx > 0d) hit.offer((grid.getWidth() - radius - x) / dx, -1d, 0d, dx, dy);
        if (dy < 0d) hit.offer((radius - y) / dy, 0d, 1d, dx, dy);
        if (dy > 0d) hit.offer((grid.getHeight() - radius - y) / dy, 0d, -1d, dx, dy);
        int minX = Math.max(0, (int) Math.floor(Math.min(x, x + dx) - radius));
        int minY = Math.max(0, (int) Math.floor(Math.min(y, y + dy) - radius));
        int maxX = Math.min(grid.getWidth() - 1, (int) Math.floor(Math.max(x, x + dx) + radius));
        int maxY = Math.min(grid.getHeight() - 1, (int) Math.floor(Math.max(y, y + dy) + radius));
        for (int cy = minY; cy <= maxY; cy++) {
            for (int cx = minX; cx <= maxX; cx++) {
                if (!grid.isWalkable(cx, cy)) {
                    segment(hit, x, y, dx, dy, radius, cx, cy, cx + 1d, cy);
                    segment(hit, x, y, dx, dy, radius, cx, cy + 1d, cx + 1d, cy + 1d);
                    segment(hit, x, y, dx, dy, radius, cx, cy, cx, cy + 1d);
                    segment(hit, x, y, dx, dy, radius, cx + 1d, cy, cx + 1d, cy + 1d);
                }
                if (cx + 1 < grid.getWidth()
                        && !grid.canTraverseCellStep(cx, cy, Direction.E)) {
                    segment(hit, x, y, dx, dy, radius, cx + 1d, cy, cx + 1d, cy + 1d);
                }
                if (cy + 1 < grid.getHeight()
                        && !grid.canTraverseCellStep(cx, cy, Direction.N)) {
                    segment(hit, x, y, dx, dy, radius, cx, cy + 1d, cx + 1d, cy + 1d);
                }
            }
        }
        // Cardinal segment geometry handles ordinary corners. A cell may also
        // explicitly close its diagonal bit while leaving both cardinals open.
        // Check exact vertex crossings through the same rule A* consumes.
        diagonalCrossings(grid, hit, x, y, dx, dy);
        return hit;
    }

    private static void diagonalCrossings(NavigationGrid grid, Hit hit,
                                           double x, double y, double dx, double dy) {
        if (dx == 0d || dy == 0d) return;
        int stepX = dx > 0d ? 1 : -1;
        int stepY = dy > 0d ? 1 : -1;
        int first = dx > 0d ? (int) Math.floor(x) + 1 : (int) Math.ceil(x) - 1;
        int end = dx > 0d ? (int) Math.floor(x + dx) : (int) Math.ceil(x + dx);
        for (int boundary = first; stepX > 0 ? boundary <= end : boundary >= end; boundary += stepX) {
            double time = (boundary - x) / dx;
            if (time > hit.time || time > 1d) break;
            double crossingY = y + dy * time;
            double vertexY = Math.rint(crossingY);
            if (Math.abs(crossingY - vertexY) > EPSILON) continue;
            int fromX = stepX > 0 ? boundary - 1 : boundary;
            int fromY = stepY > 0 ? (int) vertexY - 1 : (int) vertexY;
            if (!grid.canTraverseCellStep(fromX, fromY, fromX + stepX, fromY + stepY)) {
                double length = Math.hypot(dx, dy);
                hit.offer(time, -dx / length, -dy / length, dx, dy);
            }
        }
    }

    /** A moving point against a segment expanded by the body's radius. */
    private static void segment(Hit hit, double x, double y, double dx, double dy,
                                double radius, double ax, double ay, double bx, double by) {
        if (ax == bx && dx != 0d) {
            double nx = dx > 0d ? -1d : 1d;
            double time = (ax + nx * radius - x) / dx;
            double atY = y + dy * time;
            if (atY >= ay && atY <= by) hit.offer(time, nx, 0d, dx, dy);
        } else if (ay == by && dy != 0d) {
            double ny = dy > 0d ? -1d : 1d;
            double time = (ay + ny * radius - y) / dy;
            double atX = x + dx * time;
            if (atX >= ax && atX <= bx) hit.offer(time, 0d, ny, dx, dy);
        }
        endpoint(hit, x, y, dx, dy, radius, ax, ay);
        endpoint(hit, x, y, dx, dy, radius, bx, by);
    }

    private static void endpoint(Hit hit, double x, double y, double dx, double dy,
                                 double radius, double cx, double cy) {
        double ox = x - cx;
        double oy = y - cy;
        double a = dx * dx + dy * dy;
        double b = ox * dx + oy * dy;
        double c = ox * ox + oy * oy - radius * radius;
        double discriminant = b * b - a * c;
        if (discriminant < 0d) return;
        double time = (-b - Math.sqrt(discriminant)) / a;
        double nx = x + dx * time - cx;
        double ny = y + dy * time - cy;
        double length = Math.hypot(nx, ny);
        if (length > EPSILON) hit.offer(time, nx / length, ny / length, dx, dy);
    }

    private static boolean clear(NavigationGrid grid, double x, double y, double radius) {
        if (!grid.isWalkable((int) Math.floor(x), (int) Math.floor(y))) return false;
        // A legal float position at a tangent may be a few ulps inside when
        // promoted to double. Accept that contact tolerance; the sweep treats
        // an inward hit within the same skin as an immediate contact.
        double clearance = Math.max(0d, radius - SKIN);
        if (x < clearance || y < clearance || x > grid.getWidth() - clearance
                || y > grid.getHeight() - clearance) return false;
        double minimumDistanceSquared = clearance * clearance;
        for (int cy = Math.max(0, (int) Math.floor(y - radius));
             cy <= Math.min(grid.getHeight() - 1, (int) Math.floor(y + radius)); cy++) {
            for (int cx = Math.max(0, (int) Math.floor(x - radius));
                 cx <= Math.min(grid.getWidth() - 1, (int) Math.floor(x + radius)); cx++) {
                if (!grid.isWalkable(cx, cy)
                        && distanceSquared(x, y, cx, cy, cx + 1d, cy + 1d) < minimumDistanceSquared) return false;
                if (cx + 1 < grid.getWidth() && !grid.canTraverseCellStep(cx, cy, Direction.E)
                        && distanceSquared(x, y, cx + 1d, cy, cx + 1d, cy + 1d) < minimumDistanceSquared) return false;
                if (cy + 1 < grid.getHeight() && !grid.canTraverseCellStep(cx, cy, Direction.N)
                        && distanceSquared(x, y, cx, cy + 1d, cx + 1d, cy + 1d) < minimumDistanceSquared) return false;
            }
        }
        return true;
    }

    private static double distanceSquared(double x, double y, double minX, double minY,
                                           double maxX, double maxY) {
        double dx = x - Math.max(minX, Math.min(maxX, x));
        double dy = y - Math.max(minY, Math.min(maxY, y));
        return dx * dx + dy * dy;
    }

    private static final class Hit {
        double time = Double.POSITIVE_INFINITY;
        double nx;
        double ny;

        void offer(double candidate, double normalX, double normalY, double dx, double dy) {
            if (candidate < -SKIN / Math.hypot(dx, dy) || candidate > 1d || candidate >= time
                    || dx * normalX + dy * normalY >= -EPSILON) return;
            time = Math.max(0d, candidate);
            nx = normalX;
            ny = normalY;
        }
    }
}
