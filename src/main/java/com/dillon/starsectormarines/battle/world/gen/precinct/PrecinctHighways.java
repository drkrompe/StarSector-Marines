package com.dillon.starsectormarines.battle.world.gen.precinct;

import com.dillon.starsectormarines.battle.world.gen.bsp.GrownTrunkPlan;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Keeps the road that leaves a place and removes the road that merely wanders.
 *
 * <p>Growth does not stop at a precinct's claim. Arms run their full length
 * whether or not the place they belong to holds the ground they cross, so an
 * installation with a modest claim still throws a street network across the
 * wilderness around it. Measured on a remote map, <b>two thirds of all road was
 * outside every precinct</b> — 3086 cells of it — fully connected, with no dead
 * ends, and serving nothing. A street grid in a field.
 *
 * <p>What open-country road is <em>for</em> is the reason to keep any of it: a
 * remote installation is supplied from somewhere, and the road out is how. So
 * the rule is that road beyond every claim survives only where it carries a
 * place to the edge of the map. What is left reads as a highway with country
 * either side rather than as the outskirts of a town that is not there.
 *
 * <p>Run before {@link PrecinctInterconnect}, which can then re-link anything
 * the pruning separated — pruning first and welding second is what keeps the
 * map whole without preserving the very roads this exists to remove.
 */
public final class PrecinctHighways {

    private PrecinctHighways() {}

    private static final int[][] STEPS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    /** What the pruning did, so a map that lost most of its road can say so. */
    public record Result(int openBefore, int keptAsHighway, int pruned, int exits,
                         boolean carved) { }

    /**
     * How many ways off the map are kept.
     *
     * <p>A few, not all of them. Growth reaching the border five times is five
     * roads out of a place that needs one or two, and keeping them all leaves
     * the wandering this exists to remove with an excuse. The shortest are kept
     * because a supply road takes the near way out.
     */
    public static final int MAX_HIGHWAYS = 3;

    /**
     * Half-width of a kept highway.
     *
     * <p>The traced route is one cell wide, because a breadth-first path is.
     * Kept as it is, a road that was five cells across becomes a footpath and
     * nothing drives the supply route — measured, 193 cells of highway across
     * five exits, which is a track. The route is widened back out over the road
     * that was already there, so a highway is as wide as the road it is made of
     * and never wider.
     */
    private static final int HIGHWAY_HALF_WIDTH = PrecinctBoundary.DRIVABLE_GATE_WIDTH / 2;

    /**
     * Removes open-country road that is not carrying anybody off the map.
     *
     * <p>Mutates {@code owner}, clearing pruned cells. Road inside any claim is
     * never touched: what a place does with its own ground is its business.
     */
    public static Result prune(int[][] owner, int[][] claim, int width, int height) {
        boolean[][] open = new boolean[width][height];
        int openBefore = 0;
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                if (owner[x][y] == GrownTrunkPlan.UNOWNED) continue;
                if (claim[x][y] != GrownTrunkPlan.UNOWNED) continue;
                open[x][y] = true;
                openBefore++;
            }
        }

        // Walk inward from every road cell on the map edge, over open road only,
        // until the walk reaches a claim. The route it took is a highway; every
        // other open cell is a street in a field.
        List<List<int[]>> routes = new ArrayList<>();
        for (int[] edge : edgeExits(open, width, height)) {
            List<int[]> route = traceToAPlace(open, claim, edge, width, height);
            if (route != null) routes.add(route);
        }
        routes.sort((a, b) -> Integer.compare(a.size(), b.size()));

        boolean[][] keep = new boolean[width][height];
        int exits = Math.min(MAX_HIGHWAYS, routes.size());
        for (int i = 0; i < exits; i++) {
            widen(keep, open, routes.get(i), width, height);
        }

        // A place with no way off the map is a place nothing supplies. Growth
        // does not always provide one - measured, one remote map in three had
        // no arm reaching the border at all - so one is cut when none was
        // grown, the same obligation a walled precinct has to a drivable gate.
        //
        // The question is whether ANY road reaches the border, not whether an
        // open-country route does. A dense city's own streets run to the edge
        // and it has no open country at all, so asking only about open routes
        // cut a supply road across every city on the map.
        boolean carved = false;
        if (!roadReachesTheEdge(owner, width, height)) {
            carved = carveSupplyRoad(owner, claim, keep, width, height);
        }

        int kept = 0;
        int pruned = 0;
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                if (!open[x][y]) continue;
                if (keep[x][y]) {
                    kept++;
                } else {
                    owner[x][y] = GrownTrunkPlan.UNOWNED;
                    pruned++;
                }
            }
        }
        return new Result(openBefore, kept, pruned, exits, carved);
    }

    /** Whether any road at all, claimed or open, touches the border. */
    private static boolean roadReachesTheEdge(int[][] owner, int width, int height) {
        for (int x = 0; x < width; x++) {
            if (owner[x][0] != GrownTrunkPlan.UNOWNED) return true;
            if (owner[x][height - 1] != GrownTrunkPlan.UNOWNED) return true;
        }
        for (int y = 0; y < height; y++) {
            if (owner[0][y] != GrownTrunkPlan.UNOWNED) return true;
            if (owner[width - 1][y] != GrownTrunkPlan.UNOWNED) return true;
        }
        return false;
    }

    /** Fattens a traced route back out over the road it was traced through. */
    private static void widen(boolean[][] keep, boolean[][] open, List<int[]> route,
                              int width, int height) {
        for (int[] cell : route) {
            for (int dx = -HIGHWAY_HALF_WIDTH; dx <= HIGHWAY_HALF_WIDTH; dx++) {
                for (int dy = -HIGHWAY_HALF_WIDTH; dy <= HIGHWAY_HALF_WIDTH; dy++) {
                    int nx = cell[0] + dx;
                    int ny = cell[1] + dy;
                    if (nx < 0 || nx >= width || ny < 0 || ny >= height) continue;
                    if (open[nx][ny]) keep[nx][ny] = true;
                }
            }
        }
    }

    /**
     * Cuts one supply road from the nearest place to the nearest edge.
     *
     * <p>Straight, and it looks it — which is the same trade {@link
     * PrecinctArtery} makes: this exists so a place is supplied from somewhere,
     * not to be a good road, and a wandering rescue road would be
     * indistinguishable from a grown one in evidence.
     */
    private static boolean carveSupplyRoad(int[][] owner, int[][] claim, boolean[][] keep,
                                           int width, int height) {
        int bestX = -1;
        int bestY = -1;
        int bestDist = Integer.MAX_VALUE;
        int[] toward = null;
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                if (claim[x][y] == GrownTrunkPlan.UNOWNED) continue;
                int left = x;
                int right = width - 1 - x;
                int down = y;
                int up = height - 1 - y;
                int near = Math.min(Math.min(left, right), Math.min(down, up));
                if (near >= bestDist) continue;
                bestDist = near;
                bestX = x;
                bestY = y;
                toward = near == left ? new int[]{-1, 0}
                        : near == right ? new int[]{1, 0}
                        : near == down ? new int[]{0, -1} : new int[]{0, 1};
            }
        }
        if (toward == null) return false;

        int x = bestX;
        int y = bestY;
        while (x >= 0 && x < width && y >= 0 && y < height) {
            for (int dx = -HIGHWAY_HALF_WIDTH; dx <= HIGHWAY_HALF_WIDTH; dx++) {
                for (int dy = -HIGHWAY_HALF_WIDTH; dy <= HIGHWAY_HALF_WIDTH; dy++) {
                    int nx = x + dx;
                    int ny = y + dy;
                    if (nx < 0 || nx >= width || ny < 0 || ny >= height) continue;
                    if (claim[nx][ny] != GrownTrunkPlan.UNOWNED) continue;
                    owner[nx][ny] = ownerNear(owner, claim, bestX, bestY);
                    keep[nx][ny] = true;
                }
            }
            x += toward[0];
            y += toward[1];
        }
        return true;
    }

    /** Whose road this is: the precinct the supply road leaves from. */
    private static int ownerNear(int[][] owner, int[][] claim, int x, int y) {
        int who = claim[x][y];
        return who == GrownTrunkPlan.UNOWNED ? 0 : who;
    }

    /**
     * One cell per way off the map, not one per border cell.
     *
     * <p>A three-cell-wide road meeting the border is one road out. Traced from
     * each of its cells it comes back as three routes, and a map with a single
     * supply road reports three exits — which then eats the whole budget of
     * highways kept.
     */
    private static List<int[]> edgeExits(boolean[][] open, int width, int height) {
        List<int[]> out = new ArrayList<>();
        collectRun(out, open, width, height, true, 0);
        collectRun(out, open, width, height, true, height - 1);
        collectRun(out, open, width, height, false, 0);
        collectRun(out, open, width, height, false, width - 1);
        return out;
    }

    /** Walks one border line, emitting the middle cell of each contiguous run. */
    private static void collectRun(List<int[]> out, boolean[][] open, int width, int height,
                                   boolean horizontal, int fixed) {
        int span = horizontal ? width : height;
        int runStart = -1;
        for (int i = 0; i <= span; i++) {
            boolean road = i < span
                    && (horizontal ? open[i][fixed] : open[fixed][i]);
            if (road && runStart < 0) {
                runStart = i;
            } else if (!road && runStart >= 0) {
                int mid = (runStart + i - 1) / 2;
                out.add(horizontal ? new int[]{mid, fixed} : new int[]{fixed, mid});
                runStart = -1;
            }
        }
    }

    /**
     * Breadth-first from one edge cell over open road until a claim is touched,
     * then walks the parent chain back and marks it kept.
     *
     * <p>Breadth-first so the route kept is the shortest one — a highway that
     * meanders is the wandering this removes, wearing a different name. Returns
     * whether this exit reaches a place at all: one that does not is a road from
     * the edge of the map to nowhere, and is pruned entirely.
     */
    private static List<int[]> traceToAPlace(boolean[][] open, int[][] claim,
                                             int[] from, int width, int height) {
        int[][] cameFromX = new int[width][height];
        int[][] cameFromY = new int[width][height];
        boolean[][] seen = new boolean[width][height];
        Deque<int[]> queue = new ArrayDeque<>();
        queue.add(from);
        seen[from[0]][from[1]] = true;
        cameFromX[from[0]][from[1]] = -1;

        while (!queue.isEmpty()) {
            int[] at = queue.poll();
            for (int[] step : STEPS) {
                int nx = at[0] + step[0];
                int ny = at[1] + step[1];
                if (nx < 0 || nx >= width || ny < 0 || ny >= height) continue;
                if (seen[nx][ny]) continue;

                // Reached a place: keep the route that got here and stop.
                if (claim[nx][ny] != GrownTrunkPlan.UNOWNED) {
                    return walkBack(cameFromX, cameFromY, at);
                }
                if (!open[nx][ny]) continue;
                seen[nx][ny] = true;
                cameFromX[nx][ny] = at[0];
                cameFromY[nx][ny] = at[1];
                queue.add(new int[]{nx, ny});
            }
        }
        return null;
    }

    private static List<int[]> walkBack(int[][] cameFromX, int[][] cameFromY, int[] at) {
        List<int[]> route = new ArrayList<>();
        int x = at[0];
        int y = at[1];
        while (x >= 0) {
            route.add(new int[]{x, y});
            int px = cameFromX[x][y];
            int py = cameFromY[x][y];
            if (px < 0) break;
            x = px;
            y = py;
        }
        return route;
    }
}
