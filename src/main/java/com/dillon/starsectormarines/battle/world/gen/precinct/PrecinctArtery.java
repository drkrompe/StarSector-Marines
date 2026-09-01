package com.dillon.starsectormarines.battle.world.gen.precinct;

import com.dillon.starsectormarines.battle.world.gen.bsp.GrownTrunkPlan;

import java.util.List;

/**
 * A place you can drive out of.
 *
 * <p>Growth usually provides this by itself — an arm that leaves the precinct
 * crosses its boundary and that crossing is a gate. But it does not always, and
 * the way it fails is silent: if a precinct's claim is large enough relative to
 * how far its arms reach, the claim swallows its own road network entirely, no
 * arm crosses anything, and the result is a walled installation with no way out
 * at all. Measured on a grown garrison, seed 3 of three produced exactly that —
 * two seeds with usable gates and one with none, which is the shape of defect
 * that ships.
 *
 * <p>So a walled precinct <b>owes</b> at least one drivable way out, and this
 * supplies one when its growth did not. That is the same obligation the shipped
 * map already has for the vehicle corridor — a defender's armour with no route
 * off its own base is scenery — arrived at from the precinct's side.
 *
 * <p>The carved artery is a last resort rather than the normal case, and it
 * looks like one: it runs straight where a grown arm would wander. When this
 * fires often on a profile, the profile is wrong — its arms are too short for
 * the ground it claims — and the fix belongs there rather than here.
 */
public final class PrecinctArtery {

    private PrecinctArtery() {}

    private static final int[][] STEPS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    /** Half-width either side of the centreline, giving a drivable odd width. */
    private static final int HALF = PrecinctBoundary.DRIVABLE_GATE_WIDTH / 2;

    /**
     * How far past its own boundary the artery runs before it stops.
     *
     * <p>Its job is to make a gate, not to reach anywhere in particular. Two
     * precincts that have grown into each other share a border with no
     * unclaimed ground between them, so an artery heading for a neighbour's
     * road has to enter that neighbour's claim — a road between two places is
     * in both of them — and left to run the whole way it would bulldoze a lane
     * across somebody else's ground to reach a road that was already adjacent.
     * Clearing the boundary is enough; the neighbour's own network is right
     * there.
     */
    private static final int EXIT_RUN = PrecinctBoundary.DRIVABLE_GATE_WIDTH + 1;

    /**
     * Ensures the precinct has a drivable way out, carving one if it has none.
     *
     * <p>Mutates {@code owner} in place, claiming road cells for this precinct
     * where nothing else has them. Returns whether anything was carved, so a
     * caller can report how often growth needed rescuing rather than having it
     * happen invisibly.
     */
    public static boolean ensure(int[][] claim, int[][] owner, int who,
                                 int seedX, int seedY, int width, int height) {
        List<PrecinctBoundary.Gate> gates =
                PrecinctBoundary.gates(claim, owner, who, width, height);
        if (gates.stream().anyMatch(PrecinctBoundary.Gate::drivable)) return false;

        int[] centre = centroid(claim, who, width, height);
        int[] target = nearestWayOut(claim, who, centre, width, height);
        carve(owner, claim, who, centre, target, width, height);
        return true;
    }

    /** The middle of the claim, which is where a ray outward has to start. */
    private static int[] centroid(int[][] claim, int who, int width, int height) {
        long cx = 0;
        long cy = 0;
        int cells = 0;
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                if (claim[x][y] != who) continue;
                cx += x;
                cy += y;
                cells++;
            }
        }
        return cells == 0 ? new int[]{width / 2, height / 2}
                : new int[]{(int) (cx / cells), (int) (cy / cells)};
    }

    /**
     * Where the artery heads: the nearest ground this precinct does not hold.
     *
     * <p>Aiming at a neighbour's road was tried first and is self-defeating.
     * The rule that an artery may not overwrite another precinct's road is
     * right — joining its network is the point, bulldozing it is not — but it
     * means a ray aimed at that road cannot paint the very cells it needs, and
     * the carve stops one cell outside its own boundary. Measured: a garrison
     * aimed at town road 56 cells away got two cells of new road and kept the
     * one-cell gate it started with.
     *
     * <p>The nearest map edge was the answer to that, and it is not enough
     * either, for the same shape of reason: <b>a claim that already touches the
     * edge it is aimed at is never left</b>. The ray runs off the map still
     * inside the claim, {@link #carve} returns at the map boundary, and no gate
     * is made — silently, which is the exact failure this class exists to
     * prevent. Measured on the raid evidence fixture, whose garrison claims
     * about 40% of the map and reaches its edge.
     *
     * <p>So the target is the first cell outside the claim along whichever of
     * the four axes leaves it soonest. That is still an edge-ward heading where
     * the claim is small, and it is a heading that actually crosses the outline
     * where the claim is large. Beyond it the ray runs on as before, so a
     * neighbour whose own network lies across the route is met on the way
     * rather than aimed at, and a road that carries on off the map is what
     * {@code SettlementLink.ROAD} means.
     *
     * <p>Where no axis leaves the claim on the map at all, the nearest edge is
     * kept as the target. That is a precinct spanning the map from side to
     * side, which has no outside to reach and nothing this can do for it.
     */
    private static int[] nearestWayOut(int[][] claim, int who, int[] centre,
                                       int width, int height) {
        int[] nearest = null;
        int nearestRun = Integer.MAX_VALUE;
        for (int[] step : STEPS) {
            int x = centre[0];
            int y = centre[1];
            int run = 0;
            boolean left = false;
            while (true) {
                x += step[0];
                y += step[1];
                run++;
                if (x < 0 || x >= width || y < 0 || y >= height) break;
                if (claim[x][y] != who) {
                    left = true;
                    break;
                }
            }
            if (!left || run >= nearestRun) continue;
            nearestRun = run;
            nearest = new int[]{x, y};
        }
        return nearest != null ? nearest : nearestEdge(centre, width, height);
    }

    /** The fallback target: the map edge the centroid is closest to. */
    private static int[] nearestEdge(int[] centre, int width, int height) {
        int cx = centre[0];
        int cy = centre[1];
        int left = cx;
        int right = width - 1 - cx;
        int down = cy;
        int up = height - 1 - cy;
        int min = Math.min(Math.min(left, right), Math.min(down, up));
        if (min == left) return new int[]{0, cy};
        if (min == right) return new int[]{width - 1, cy};
        if (min == down) return new int[]{cx, 0};
        return new int[]{cx, height - 1};
    }

    /**
     * A ray from the middle of the claim outward, painted at drivable width and
     * stopped once it is clear of the boundary.
     *
     * <p>Radial rather than an L to the target. An L is not guaranteed to leave
     * at all: its legs run along the axes, and a target that is nearer the
     * centre than the boundary is in some direction leaves the whole path
     * inside the claim. Measured, that is exactly what happened — a carved
     * artery that never crossed its own outline and left the precinct with the
     * one-cell gate it started with. A ray from the centroid through the target
     * exits by construction, because the claim is bounded and the ray is not.
     *
     * <p>Straight rather than routed: this exists to guarantee a way out, not to
     * be a good road.
     */
    private static void carve(int[][] owner, int[][] claim, int who,
                              int[] from, int[] to, int width, int height) {
        int dx = to[0] - from[0];
        int dy = to[1] - from[1];
        int steps = Math.max(Math.abs(dx), Math.abs(dy));
        if (steps == 0) return;
        // Long enough that it must leave: the ray is extended past the target
        // by the map's own span rather than stopping at it.
        int reach = steps + width + height;
        int beyond = 0;
        for (int i = 0; i <= reach; i++) {
            int x = from[0] + (int) Math.round((double) dx * i / steps);
            int y = from[1] + (int) Math.round((double) dy * i / steps);
            if (x < 0 || x >= width || y < 0 || y >= height) return;
            paint(owner, claim, who, x, y, width, height);
            if (claim[x][y] != who && ++beyond >= EXIT_RUN) return;
        }
    }

    /** One cell of centreline, plus its flanks, claimed where nothing else holds them. */
    private static void paint(int[][] owner, int[][] claim, int who,
                              int x, int y, int width, int height) {
        for (int dx = -HALF; dx <= HALF; dx++) {
            for (int dy = -HALF; dy <= HALF; dy++) {
                int nx = x + dx;
                int ny = y + dy;
                if (nx < 0 || nx >= width || ny < 0 || ny >= height) continue;
                // Never take road another precinct grew: joining its network is
                // the point, overwriting it is not. Its *ground* is fair game —
                // a road between two places runs through both, and two precincts
                // grown into each other have no unclaimed ground between them
                // for a road to run down instead.
                if (owner[nx][ny] != GrownTrunkPlan.UNOWNED && owner[nx][ny] != who) continue;
                owner[nx][ny] = who;
            }
        }
    }
}
