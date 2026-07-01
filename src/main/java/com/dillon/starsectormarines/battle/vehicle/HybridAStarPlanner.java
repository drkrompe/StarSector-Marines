package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.air.AirBody;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.PriorityQueue;

/**
 * Hybrid A* path planner for ground vehicles. Searches (x, y, heading)
 * configuration space using the bicycle model's kinematics for successor
 * generation, {@link VehicleFootprint} for obstacle checking, and
 * {@link ReedsShepp} for analytic shortcutting.
 *
 * <p>Slots between {@link ConvoyPlanner} (high-level road-graph route) and
 * {@link VehicleMission} (waypoint consumer). The output is a dense {@code float[][]}
 * of kinematically-feasible waypoints that {@link PurePursuit} can track
 * without wall collisions.
 */
public final class HybridAStarPlanner {

    private HybridAStarPlanner() {}

    static final int NUM_HEADING_BINS = 36;
    static final float BIN_WIDTH_DEG = 360f / NUM_HEADING_BINS;
    static final float STEP_CELLS = 2.0f;
    static final int NUM_STEER_SAMPLES = 5;
    static final float REVERSE_PENALTY = 1.5f;
    static final float STEER_CHANGE_PENALTY = 0.1f;
    static final int MAX_ITERATIONS = 15_000;
    static final int ANALYTIC_EXPANSION_INTERVAL = 30;
    static final float ANALYTIC_RANGE_FACTOR = 3f;
    static final float FOOTPRINT_SAMPLE_STEP = 0.5f;
    static final float WAYPOINT_SPACING = 1.0f;
    /** Extra clearance added to the vehicle footprint during planning. Accounts for PurePursuit tracking imprecision so planned paths don't graze walls. */
    static final float PLANNER_CLEARANCE = 0.6f;
    private static final float SQRT2 = (float) Math.sqrt(2.0);

    // -- Rolling-horizon local search --------------------------------------

    /**
     * Rolling-horizon local search: plan a short kinematically-feasible
     * trajectory from {@code start} toward a soft goal region (radius
     * {@code goalRadiusCells}) around {@code goal}, with both the heuristic
     * flood and successor expansion clamped to the cell window
     * {@code [winMinX..winMaxX] × [winMinY..winMaxY]}.
     *
     * <p>Reuses the same bicycle successors, {@link VehicleFootprint} check,
     * grid-distance heuristic and Reeds-Shepp analytic expansion as the
     * full-path planner. Two differences make it cheap and high-success where the
     * one-shot full-path refine fails: (a) the window bounds the search so it
     * can't flood the map, and (b) acceptance is a goal <em>radius</em> rather
     * than an exact cell + heading — the corridor is advisory, so reaching
     * near the rolling goal with a feasible heading is enough.
     *
     * <p>Pure: ({@code start}, {@code goal}, window, grid) in → feasible
     * {@code float[3][]} ([0]=X, [1]=Y, [2]=headingDeg) or {@code null} out.
     * No {@link VehicleMission} / {@link GroundSystem} coupling. {@code null} means
     * "no forward trajectory in the window within {@code maxIterations}" — the
     * caller (and, in slice 3, the recovery ladder) escalates.
     */
    public static float[][] planLocal(Pose start, Pose goal, float goalRadiusCells,
                                      int winMinX, int winMinY, int winMaxX, int winMaxY,
                                      int maxIterations, VehicleType type, NavigationGrid grid) {
        GroundBody body = type.createBody();
        if (!(body instanceof BicycleBody)) return null;
        BicycleBody bicycle = (BicycleBody) body;
        float turnRadius = bicycle.minTurnRadiusCells();
        float wheelbase = bicycle.getWheelbaseCells();
        float maxSteerRad = bicycle.getMaxSteeringRad();
        float vLen = type.visualLengthCells + PLANNER_CLEARANCE;
        float vWid = type.visualWidthCells + PLANNER_CLEARANCE;
        int gridW = grid.getWidth(), gridH = grid.getHeight();

        int minX = Math.max(0, winMinX), minY = Math.max(0, winMinY);
        int maxX = Math.min(gridW - 1, winMaxX), maxY = Math.min(gridH - 1, winMaxY);
        if (minX > maxX || minY > maxY) return null;

        int goalCellX = (int) Math.floor(goal.x);
        int goalCellY = (int) Math.floor(goal.y);
        // planLocal is public and reused; a caller passing a window that excludes
        // the goal cell would otherwise get an all-MAX_VALUE grid-distance field
        // (heuristic-less, degraded to uniform-cost search to the iteration cap).
        // Fail fast instead -- a goal outside the search window is "no trajectory".
        if (goalCellX < minX || goalCellX > maxX || goalCellY < minY || goalCellY > maxY) {
            return null;
        }
        float[] gridDist = computeGridDistance(goalCellX, goalCellY, grid, gridW, gridH,
                minX, minY, maxX, maxY);

        float[] steerAngles = new float[NUM_STEER_SAMPLES];
        for (int i = 0; i < NUM_STEER_SAMPLES; i++) {
            steerAngles[i] = -maxSteerRad + (2f * maxSteerRad * i / (NUM_STEER_SAMPLES - 1));
        }

        PriorityQueue<Node> open = new PriorityQueue<>(256, Comparator.comparingDouble(n -> n.fCost));
        HashMap<Integer, Node> best = new HashMap<>(1024);
        HashSet<Integer> closed = new HashSet<>(1024);

        int startBin = headingBinFor(start.facingDeg);
        int startCellX = (int) Math.floor(start.x);
        int startCellY = (int) Math.floor(start.y);
        int startKey = stateIndex(startCellX, startCellY, startBin, gridW);
        Node startNode = new Node(start.x, start.y, start.facingDeg, startKey);
        startNode.gCost = 0f;
        startNode.fCost = heuristic(start.x, start.y, start.facingDeg,
                goal, turnRadius, gridDist, gridW);
        open.add(startNode);
        best.put(startKey, startNode);

        float goalRadiusSq = goalRadiusCells * goalRadiusCells;
        Node goalNode = null;
        ReedsShepp.Path analyticPath = null;
        Node analyticFrom = null;

        int iterations = 0;
        while (!open.isEmpty() && iterations < maxIterations) {
            Node current = open.poll();
            if (closed.contains(current.stateKey)) continue;
            Node bestForState = best.get(current.stateKey);
            if (bestForState != null && current.gCost > bestForState.gCost + 1e-4f) continue;
            closed.add(current.stateKey);
            iterations++;

            float gdx = goal.x - current.x, gdy = goal.y - current.y;
            float distSqToGoal = gdx * gdx + gdy * gdy;
            if (distSqToGoal <= goalRadiusSq) {
                goalNode = current;
                break;
            }

            if (iterations % ANALYTIC_EXPANSION_INTERVAL == 0) {
                float distToGoal = (float) Math.sqrt(distSqToGoal);
                if (distToGoal < ANALYTIC_RANGE_FACTOR * turnRadius) {
                    Pose curPose = new Pose(current.x, current.y, current.headingDeg);
                    ReedsShepp.Path rsPath = tryAnalyticExpansion(
                            curPose, goal, turnRadius, vLen, vWid, grid);
                    if (rsPath != null) {
                        analyticPath = rsPath;
                        analyticFrom = current;
                        break;
                    }
                }
            }

            for (int si = 0; si < NUM_STEER_SAMPLES; si++) {
                for (int dir = 0; dir < 2; dir++) {
                    float dirSign = (dir == 0) ? 1f : -1f;
                    float steer = steerAngles[si];

                    float dTheta = (STEP_CELLS / wheelbase) * (float) Math.tan(steer) * dirSign;
                    float midHeadingRad = (float) Math.toRadians(current.headingDeg) + dTheta * 0.5f;
                    float newHeadingRad = (float) Math.toRadians(current.headingDeg) + dTheta;

                    float nx = current.x + STEP_CELLS * dirSign * (-(float) Math.sin(midHeadingRad));
                    float ny = current.y + STEP_CELLS * dirSign * ((float) Math.cos(midHeadingRad));
                    float newHeadingDeg = (float) Math.toDegrees(newHeadingRad);
                    newHeadingDeg = ((newHeadingDeg % 360f) + 360f) % 360f;

                    int ncx = (int) Math.floor(nx);
                    int ncy = (int) Math.floor(ny);
                    if (ncx < minX || ncx > maxX || ncy < minY || ncy > maxY) continue;

                    float midX = current.x + STEP_CELLS * 0.5f * dirSign * (-(float) Math.sin(midHeadingRad));
                    float midY = current.y + STEP_CELLS * 0.5f * dirSign * ((float) Math.cos(midHeadingRad));
                    float midHeadingDeg = (float) Math.toDegrees(midHeadingRad);
                    midHeadingDeg = ((midHeadingDeg % 360f) + 360f) % 360f;

                    if (!VehicleFootprint.isPoseFeasible(midX, midY, midHeadingDeg, vLen, vWid, grid)) continue;
                    if (!VehicleFootprint.isPoseFeasible(nx, ny, newHeadingDeg, vLen, vWid, grid)) continue;

                    int nhb = headingBinFor(newHeadingDeg);
                    int nKey = stateIndex(ncx, ncy, nhb, gridW);
                    if (closed.contains(nKey)) continue;

                    float edgeCost = STEP_CELLS * (dir == 1 ? REVERSE_PENALTY : 1f)
                            + STEER_CHANGE_PENALTY * Math.abs(steer);
                    float ng = current.gCost + edgeCost;

                    Node existing = best.get(nKey);
                    if (existing != null && ng >= existing.gCost - 1e-4f) continue;

                    Node succ = new Node(nx, ny, newHeadingDeg, nKey);
                    succ.gCost = ng;
                    succ.fCost = ng + heuristic(nx, ny, newHeadingDeg,
                            goal, turnRadius, gridDist, gridW);
                    succ.parentKey = current.stateKey;
                    open.add(succ);
                    best.put(nKey, succ);
                }
            }
        }

        if (goalNode == null && analyticPath == null) return null;
        return extractLocal(goalNode, analyticFrom, analyticPath, best, goal, turnRadius);
    }

    /**
     * Path extraction for {@link #planLocal}: walk the lattice back from the
     * terminal node, then append the Reeds-Shepp analytic tail (and the exact
     * goal pose) when the search closed via analytic expansion. There is no
     * coarse-guide prefix to prepend — the local search starts from the
     * vehicle's live pose. When the search closed
     * by reaching the goal radius (no analytic tail) the trajectory simply ends
     * at the in-radius lattice node, with no spurious jump to the exact goal.
     */
    private static float[][] extractLocal(Node goalNode, Node analyticFrom,
                                          ReedsShepp.Path analyticPath,
                                          HashMap<Integer, Node> best,
                                          Pose goalPose, float turnRadius) {
        List<float[]> poses = new ArrayList<>();
        Node terminal = (analyticFrom != null) ? analyticFrom : goalNode;
        List<float[]> lattice = new ArrayList<>();
        Node n = terminal;
        while (n != null) {
            lattice.add(new float[]{n.x, n.y, n.headingDeg});
            if (n.parentKey < 0) break;
            n = best.get(n.parentKey);
        }
        for (int i = lattice.size() - 1; i >= 0; i--) {
            poses.add(lattice.get(i));
        }

        if (analyticPath != null && analyticFrom != null) {
            Pose from = new Pose(analyticFrom.x, analyticFrom.y, analyticFrom.headingDeg);
            float total = analyticPath.lengthCells(turnRadius);
            for (float d = WAYPOINT_SPACING; d < total; d += WAYPOINT_SPACING) {
                Pose p = ReedsShepp.sample(from, turnRadius, analyticPath, d);
                poses.add(new float[]{p.x, p.y, p.facingDeg});
            }
            poses.add(new float[]{goalPose.x, goalPose.y, goalPose.facingDeg});
        }

        // Drop consecutive coincident poses so the Trajectory's cumulative arc
        // length is strictly increasing (the RS sampling step plus the appended
        // exact goal can emit a near-duplicate final pair). Keep the later pose's
        // heading on a collision so the terminal goal facing isn't lost.
        List<float[]> deduped = new ArrayList<>(poses.size());
        for (float[] p : poses) {
            if (deduped.isEmpty()) { deduped.add(p); continue; }
            float[] prev = deduped.get(deduped.size() - 1);
            float dx = p[0] - prev[0], dy = p[1] - prev[1];
            if (dx * dx + dy * dy > 1e-6f) {
                deduped.add(p);
            } else {
                prev[2] = p[2];
            }
        }
        if (deduped.size() < 2) return null;

        float[] outX = new float[deduped.size()];
        float[] outY = new float[deduped.size()];
        float[] outH = new float[deduped.size()];
        for (int i = 0; i < deduped.size(); i++) {
            outX[i] = deduped.get(i)[0];
            outY[i] = deduped.get(i)[1];
            outH[i] = deduped.get(i)[2];
        }
        return new float[][]{outX, outY, outH};
    }

    // -- Grid distance heuristic (backward Dijkstra) -----------------------

    /**
     * Backward Dijkstra grid-distance field, but expansion is clamped to the
     * cell window {@code [minX..maxX] × [minY..maxY]}. Cells outside the window
     * keep {@code Float.MAX_VALUE} — the windowed local search never visits
     * them, so their heuristic is irrelevant, and bounding the flood is what
     * keeps {@link #planLocal} cheap.
     *
     * <p><b>Windowed distances are a heuristic estimate, not admissible:</b> a
     * cell whose true shortest grid path to the goal detours outside the window
     * gets an over-estimate (up to {@code MAX_VALUE}). {@link #planLocal} is an
     * advisory soft-goal planner, so a sub-optimal expansion is acceptable —
     * callers must not assume the returned trajectory is cost-optimal.
     */
    private static float[] computeGridDistance(int goalX, int goalY,
                                               NavigationGrid grid, int w, int h,
                                               int minX, int minY, int maxX, int maxY) {
        int size = w * h;
        float[] dist = new float[size];
        java.util.Arrays.fill(dist, Float.MAX_VALUE);

        if (!grid.inBounds(goalX, goalY)) return dist;
        if (goalX < minX || goalX > maxX || goalY < minY || goalY > maxY) return dist;

        PriorityQueue<long[]> pq = new PriorityQueue<>(256,
                Comparator.comparingDouble(a -> Float.intBitsToFloat((int) a[1])));

        int goalIdx = goalY * w + goalX;
        dist[goalIdx] = 0f;
        pq.add(new long[]{goalIdx, Float.floatToIntBits(0f)});

        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1},
                {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
        float[] costs = {1f, 1f, 1f, 1f, SQRT2, SQRT2, SQRT2, SQRT2};

        while (!pq.isEmpty()) {
            long[] entry = pq.poll();
            int idx = (int) entry[0];
            float d = Float.intBitsToFloat((int) entry[1]);
            if (d > dist[idx]) continue;

            int cx = idx % w, cy = idx / w;
            for (int di = 0; di < dirs.length; di++) {
                int nx = cx + dirs[di][0], ny = cy + dirs[di][1];
                if (nx < minX || nx > maxX || ny < minY || ny > maxY) continue;
                if (!grid.isWalkable(nx, ny)) continue;
                if (di >= 4) {
                    if (!grid.isWalkable(cx + dirs[di][0], cy)
                            || !grid.isWalkable(cx, cy + dirs[di][1])) continue;
                }
                int nIdx = ny * w + nx;
                float nd = d + costs[di];
                if (nd < dist[nIdx]) {
                    dist[nIdx] = nd;
                    pq.add(new long[]{nIdx, Float.floatToIntBits(nd)});
                }
            }
        }
        return dist;
    }

    // -- Heuristic ----------------------------------------------------------

    private static float heuristic(float x, float y, float headingDeg,
                                   Pose goal, float turnRadius,
                                   float[] gridDist, int gridW) {
        int cx = (int) Math.floor(x), cy = (int) Math.floor(y);
        int idx = cy * gridW + cx;
        float gd = (idx >= 0 && idx < gridDist.length) ? gridDist[idx] : Float.MAX_VALUE;

        float bearingDeg = AirBody.facingToward(goal.x - x, goal.y - y);
        float errDeg = ((bearingDeg - headingDeg + 540f) % 360f) - 180f;
        float turnCost = turnRadius * Math.abs((float) Math.toRadians(errDeg)) * 0.5f;

        return Math.max(gd, turnCost);
    }

    // -- Analytic expansion -------------------------------------------------

    private static ReedsShepp.Path tryAnalyticExpansion(Pose from, Pose goal,
                                                        float turnRadius,
                                                        float vLen, float vWid,
                                                        NavigationGrid grid) {
        ReedsShepp.Path path = ReedsShepp.shortest(from, goal, turnRadius);
        if (path == null) return null;
        if (!isRsPathFeasible(from, path, turnRadius, vLen, vWid, grid)) return null;
        return path;
    }

    private static boolean isRsPathFeasible(Pose start, ReedsShepp.Path path,
                                            float turnRadius,
                                            float vLen, float vWid,
                                            NavigationGrid grid) {
        float total = path.lengthCells(turnRadius);
        for (float d = 0; d <= total; d += FOOTPRINT_SAMPLE_STEP) {
            Pose p = ReedsShepp.sample(start, turnRadius, path, d);
            if (!VehicleFootprint.isPoseFeasible(p.x, p.y, p.facingDeg, vLen, vWid, grid)) {
                return false;
            }
        }
        Pose end = ReedsShepp.sample(start, turnRadius, path, total);
        return VehicleFootprint.isPoseFeasible(end.x, end.y, end.facingDeg, vLen, vWid, grid);
    }

    // -- Helpers ------------------------------------------------------------

    static int headingBinFor(float facingDeg) {
        float norm = ((facingDeg % 360f) + 360f) % 360f;
        int bin = Math.round(norm / BIN_WIDTH_DEG);
        return (bin >= NUM_HEADING_BINS) ? 0 : bin;
    }

    static int stateIndex(int cx, int cy, int hb, int gridW) {
        return (cy * gridW + cx) * NUM_HEADING_BINS + hb;
    }

    // -- Node ---------------------------------------------------------------

    private static final class Node {
        final float x, y, headingDeg;
        final int stateKey;
        float gCost;
        float fCost;
        int parentKey = -1;

        Node(float x, float y, float headingDeg, int stateKey) {
            this.x = x;
            this.y = y;
            this.headingDeg = headingDeg;
            this.stateKey = stateKey;
        }
    }
}
