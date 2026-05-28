package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.air.AirBody;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.fs.starfarer.api.Global;
import org.apache.log4j.Logger;

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
 * {@link Vehicle} (waypoint consumer). The output is a dense {@code float[][]}
 * of kinematically-feasible waypoints that {@link PurePursuit} can track
 * without wall collisions.
 */
public final class HybridAStarPlanner {

    private static final Logger LOG = Global.getLogger(HybridAStarPlanner.class);

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
    private static final int GOAL_HEADING_TOLERANCE = 1;

    /**
     * Refine a coarse cell-center polyline into a kinematically-feasible
     * dense waypoint sequence. Returns {@code null} if no feasible path is
     * found within the iteration budget — caller should fall back to the
     * coarse polyline.
     *
     * @param guideX coarse waypoint X coords (cell-center)
     * @param guideY coarse waypoint Y coords (cell-center)
     * @param startFacingDeg heading at the first guide waypoint
     * @param goalFacingDeg desired heading at the last guide waypoint
     * @param type vehicle type (provides kinematics and footprint dims)
     * @param grid navigation grid for walkability checks
     * @return {@code float[3][]} where [0]=X, [1]=Y, [2]=headingDeg, or null on failure
     */
    public static float[][] refine(float[] guideX, float[] guideY,
                                   float startFacingDeg, float goalFacingDeg,
                                   VehicleType type, NavigationGrid grid) {
        if (guideX.length < 2) return null;

        GroundBody body = type.createBody();
        if (!(body instanceof BicycleBody)) return null;
        BicycleBody bicycle = (BicycleBody) body;
        float turnRadius = bicycle.minTurnRadiusCells();
        float wheelbase = bicycle.getWheelbaseCells();
        float maxSteerRad = bicycle.getMaxSteeringRad();
        float vLen = type.visualLengthCells + PLANNER_CLEARANCE;
        float vWid = type.visualWidthCells + PLANNER_CLEARANCE;

        int gridW = grid.getWidth(), gridH = grid.getHeight();

        float realLen = type.visualLengthCells;
        float realWid = type.visualWidthCells;
        int startIdx = 0;
        float curFacing = startFacingDeg;
        for (int i = 0; i < guideX.length - 1; i++) {
            if (VehicleFootprint.isPoseFeasible(guideX[i], guideY[i], curFacing, realLen, realWid, grid)) {
                startIdx = i;
                break;
            }
            if (i + 1 < guideX.length) {
                curFacing = com.dillon.starsectormarines.battle.air.AirBody.facingToward(
                        guideX[i + 1] - guideX[i], guideY[i + 1] - guideY[i]);
            }
            startIdx = i + 1;
        }
        if (startIdx >= guideX.length - 1) return null;

        float startX = guideX[startIdx];
        float startY = guideY[startIdx];
        if (startIdx > 0) {
            startFacingDeg = com.dillon.starsectormarines.battle.air.AirBody.facingToward(
                    guideX[startIdx] - guideX[startIdx - 1],
                    guideY[startIdx] - guideY[startIdx - 1]);
        }
        float goalX = guideX[guideX.length - 1], goalY = guideY[guideY.length - 1];

        int goalCellX = (int) Math.floor(goalX);
        int goalCellY = (int) Math.floor(goalY);
        int goalBin = headingBinFor(goalFacingDeg);
        Pose goalPose = new Pose(goalX, goalY, goalFacingDeg);

        float[] gridDist = computeGridDistance(goalCellX, goalCellY, grid, gridW, gridH);

        float[] steerAngles = new float[NUM_STEER_SAMPLES];
        for (int i = 0; i < NUM_STEER_SAMPLES; i++) {
            steerAngles[i] = -maxSteerRad + (2f * maxSteerRad * i / (NUM_STEER_SAMPLES - 1));
        }

        PriorityQueue<Node> open = new PriorityQueue<>(256, Comparator.comparingDouble(n -> n.fCost));
        HashMap<Integer, Node> best = new HashMap<>(1024);
        HashSet<Integer> closed = new HashSet<>(1024);

        int startBin = headingBinFor(startFacingDeg);
        int startCellX = (int) Math.floor(startX);
        int startCellY = (int) Math.floor(startY);
        int startKey = stateIndex(startCellX, startCellY, startBin, gridW);
        Node startNode = new Node(startX, startY, startFacingDeg, startKey);
        startNode.gCost = 0f;
        startNode.fCost = heuristic(startX, startY, startFacingDeg,
                goalPose, turnRadius, gridDist, gridW);
        open.add(startNode);
        best.put(startKey, startNode);

        Node goalNode = null;
        ReedsShepp.Path analyticPath = null;
        Node analyticFrom = null;

        int iterations = 0;
        while (!open.isEmpty() && iterations < MAX_ITERATIONS) {
            Node current = open.poll();
            if (closed.contains(current.stateKey)) continue;
            Node bestForState = best.get(current.stateKey);
            if (bestForState != null && current.gCost > bestForState.gCost + 1e-4f) continue;
            closed.add(current.stateKey);
            iterations++;

            int cx = cellXFromKey(current.stateKey, gridW);
            int cy = cellYFromKey(current.stateKey, gridW);
            int hb = headingBinFromKey(current.stateKey);
            if (cx == goalCellX && cy == goalCellY
                    && headingBinDist(hb, goalBin) <= GOAL_HEADING_TOLERANCE) {
                goalNode = current;
                break;
            }

            if (iterations % ANALYTIC_EXPANSION_INTERVAL == 0) {
                float dx = goalX - current.x, dy = goalY - current.y;
                float distToGoal = (float) Math.sqrt(dx * dx + dy * dy);
                if (distToGoal < ANALYTIC_RANGE_FACTOR * turnRadius) {
                    Pose curPose = new Pose(current.x, current.y, current.headingDeg);
                    ReedsShepp.Path rsPath = tryAnalyticExpansion(
                            curPose, goalPose, turnRadius, vLen, vWid, grid);
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
                    if (ncx < 0 || ncx >= gridW || ncy < 0 || ncy >= gridH) continue;

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
                            goalPose, turnRadius, gridDist, gridW);
                    succ.parentKey = current.stateKey;
                    open.add(succ);
                    best.put(nKey, succ);
                }
            }
        }

        if (goalNode == null && analyticPath == null) {
            LOG.info("HybridA*: no path found after " + iterations + " iterations");
            return null;
        }

        return extractPath(goalNode, analyticFrom, analyticPath,
                best, goalPose, turnRadius,
                guideX, guideY, startIdx);
    }

    // -- Grid distance heuristic (backward Dijkstra) -----------------------

    private static float[] computeGridDistance(int goalX, int goalY,
                                               NavigationGrid grid, int w, int h) {
        int size = w * h;
        float[] dist = new float[size];
        java.util.Arrays.fill(dist, Float.MAX_VALUE);

        if (!grid.inBounds(goalX, goalY)) return dist;

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
                if (nx < 0 || nx >= w || ny < 0 || ny >= h) continue;
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

    // -- Path extraction ----------------------------------------------------

    private static float[][] extractPath(Node goalNode, Node analyticFrom,
                                         ReedsShepp.Path analyticPath,
                                         HashMap<Integer, Node> best,
                                         Pose goalPose, float turnRadius,
                                         float[] guideX, float[] guideY,
                                         int refinedStartIdx) {
        List<float[]> poses = new ArrayList<>();

        for (int i = 0; i < refinedStartIdx; i++) {
            float heading = AirBody.facingToward(
                    guideX[i + 1] - guideX[i], guideY[i + 1] - guideY[i]);
            poses.add(new float[]{guideX[i], guideY[i], heading});
        }

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
        }

        poses.add(new float[]{goalPose.x, goalPose.y, goalPose.facingDeg});

        if (poses.size() < 2) return null;

        float[] outX = new float[poses.size()];
        float[] outY = new float[poses.size()];
        float[] outH = new float[poses.size()];
        for (int i = 0; i < poses.size(); i++) {
            outX[i] = poses.get(i)[0];
            outY[i] = poses.get(i)[1];
            outH[i] = poses.get(i)[2];
        }
        return new float[][]{outX, outY, outH};
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

    private static int cellXFromKey(int key, int gridW) {
        return (key / NUM_HEADING_BINS) % gridW;
    }

    private static int cellYFromKey(int key, int gridW) {
        return (key / NUM_HEADING_BINS) / gridW;
    }

    private static int headingBinFromKey(int key) {
        return key % NUM_HEADING_BINS;
    }

    static int headingBinDist(int a, int b) {
        int d = Math.abs(a - b);
        return Math.min(d, NUM_HEADING_BINS - d);
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
