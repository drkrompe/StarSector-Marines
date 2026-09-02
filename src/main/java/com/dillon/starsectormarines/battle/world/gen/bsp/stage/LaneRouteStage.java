package com.dillon.starsectormarines.battle.world.gen.bsp.stage;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.GenStage;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.gen.bsp.GrownTrunkPlan;
import com.dillon.starsectormarines.battle.world.gen.precinct.LaneResistance;
import com.dillon.starsectormarines.battle.world.gen.precinct.LaneRoute;
import com.dillon.starsectormarines.battle.world.gen.precinct.Precinct;
import com.dillon.starsectormarines.battle.world.gen.precinct.PrecinctPlan;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.PriorityQueue;

/**
 * Reads back the road each lane actually runs on, once the map is finished.
 *
 * <p>The plan states where a lane's places go; growth, welding and walling then
 * decide what the ground between them looks like, and nothing until now wrote
 * down the answer. A commander reading a lane as a chain of compounds has to
 * stage on the road to the next link, and a road it inferred for itself would be
 * a second answer to a question the generator had already settled — so the
 * generator settles it here, in cells, and {@code MapResult.lanes} carries it.
 *
 * <p><b>Cheapest along the road network, not shortest across the map.</b>
 * Off-road ground is walkable and costs {@link #OFF_ROAD} times as much, so a
 * route takes the street wherever a street goes roughly the right way and cuts
 * across a field only when the street would be a detour many times its length.
 * That is what makes the recorded route the one a convoy or a squad would
 * plausibly take rather than a ruled line through back gardens.
 *
 * <p>Runs after the front stage and before the closing anchor fit, which is the
 * first point at which every wall, gate and building on the map stands where it
 * will stand. Reading it earlier would record a route through a wall nobody had
 * stamped yet.
 */
public final class LaneRouteStage implements GenStage {

    /**
     * What a cell of open ground costs against a cell of road.
     *
     * <p>High enough that a route prefers a street that wanders to a straight
     * line across country, low enough that it will still cross a field rather
     * than go round the map. Eight is roughly "a detour of up to eight times the
     * distance is still worth taking on tarmac", which on a city map keeps a
     * route on the roads and on a remote one lets it cross the wilderness
     * between two posts that have none.
     */
    static final int OFF_ROAD = 8;

    private static final int ON_ROAD = 1;

    private static final int[][] STEPS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    @Override
    public void run(GenContext ctx) {
        PrecinctPlan plan = ctx.get(BspKeys.PRECINCTS);
        if (plan == null || plan.lanes() == null) return;
        int[][] claim = ctx.get(BspKeys.PRECINCT_CLAIM);
        int[][] road = ctx.get(BspKeys.PRECINCT_ROAD);
        if (claim == null || road == null) return;
        Precinct objective = plan.objective();
        if (objective == null) return;

        int[][] bounds = claimBounds(claim, plan.precincts().size(),
                ctx.width, ctx.height);
        List<LaneRoute> lanes = new ArrayList<>();
        for (int lane = 0; lane < plan.lanes().count(); lane++) {
            lanes.add(route(ctx, plan, claim, road, bounds, objective, lane));
        }
        ctx.put(BspKeys.LANES, List.copyOf(lanes));
    }

    /**
     * The extent of every precinct's claim, in one pass over the map.
     *
     * <p>A link records the ground its place holds so the commander can pair a
     * compound with the place it stands in rather than with the nearest anchor.
     * Measured here because this is the only layer that has the claim: it is
     * generation scratch, and nothing downstream of {@code MapResult} sees it.
     *
     * @return {@code {left, top, right, bottom}} per precinct index, or a row
     *         of {@code -1} for a precinct that claimed nothing
     */
    private static int[][] claimBounds(int[][] claim, int precincts,
                                       int width, int height) {
        int[][] bounds = new int[precincts][];
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                int who = claim[x][y];
                if (who < 0 || who >= precincts) continue;
                int[] box = bounds[who];
                if (box == null) {
                    bounds[who] = new int[]{x, y, x, y};
                    continue;
                }
                if (x < box[0]) box[0] = x;
                if (y < box[1]) box[1] = y;
                if (x > box[2]) box[2] = x;
                if (y > box[3]) box[3] = y;
            }
        }
        for (int i = 0; i < precincts; i++) {
            if (bounds[i] == null) bounds[i] = new int[]{-1, -1, -1, -1};
        }
        return bounds;
    }

    /** One lane's links and the polyline through them. */
    private static LaneRoute route(GenContext ctx, PrecinctPlan plan, int[][] claim,
                                   int[][] road, int[][] claimBounds,
                                   Precinct objective, int lane) {
        List<LaneRoute.Link> links = new ArrayList<>();
        List<LaneRoute.Cell> polyline = new ArrayList<>();
        List<int[]> anchors = new ArrayList<>();
        List<String> names = new ArrayList<>();
        List<Integer> bands = new ArrayList<>();
        List<int[]> grounds = new ArrayList<>();

        // Outermost rung first: a lane runs from the beachhead to the keep, and
        // the ladder counts the other way.
        for (int band = LaneResistance.OUTERMOST_BAND;
             band >= LaneResistance.INNERMOST_BAND; band--) {
            String name = "lane-" + (lane + 1) + "-band-" + band;
            int index = indexOf(plan, name);
            if (index < 0) continue;
            int[] anchor = anchor(ctx, claim, road, index,
                    plan.precincts().get(index));
            if (anchor == null) continue;
            anchors.add(anchor);
            names.add(name);
            bands.add(band);
            grounds.add(claimBounds[index]);
        }
        int objectiveIndex = plan.precincts().indexOf(objective);
        int[] end = anchor(ctx, claim, road, objectiveIndex, objective);
        if (end != null) {
            anchors.add(end);
            names.add(objective.name());
            bands.add(LaneRoute.OBJECTIVE_BAND);
            grounds.add(claimBounds[objectiveIndex]);
        }

        for (int i = 0; i < anchors.size(); i++) {
            int[] at = anchors.get(i);
            if (i > 0) {
                List<int[]> leg = walk(ctx.grid, road, anchors.get(i - 1), at,
                        ctx.width, ctx.height);
                // A leg that cannot be walked leaves the polyline short rather
                // than throwing: what could not be reached is evidence, and
                // ConquestOnPrecinctsTest is where the law is enforced.
                if (leg == null) break;
                for (int step = 1; step < leg.size(); step++) {
                    int[] cell = leg.get(step);
                    polyline.add(new LaneRoute.Cell(cell[0], cell[1]));
                }
            } else {
                polyline.add(new LaneRoute.Cell(at[0], at[1]));
            }
            int[] ground = grounds.get(i);
            links.add(new LaneRoute.Link(names.get(i), bands.get(i), at[0], at[1],
                    polyline.size() - 1, ground[0], ground[1], ground[2],
                    ground[3]));
        }
        return new LaneRoute(lane, links, polyline);
    }

    private static int indexOf(PrecinctPlan plan, String name) {
        for (int i = 0; i < plan.precincts().size(); i++) {
            if (plan.precincts().get(i).name().equals(name)) return i;
        }
        return -1;
    }

    /**
     * A cell of this place a route can actually reach.
     *
     * <p>Its own road first, because a precinct's road is its circulation and is
     * connected to the network by construction; then any walkable cell it holds;
     * then the nearest walkable cell to its seed anywhere. A seed itself is
     * usually not the answer — it is where the arms grew from, and by the time
     * the packer has finished a garrison's seed is as likely to be under a
     * barrack block as on open ground.
     */
    private static int[] anchor(GenContext ctx, int[][] claim, int[][] road, int who,
                                Precinct precinct) {
        NavigationGrid grid = ctx.grid;
        int[] onRoad = nearest(ctx, precinct, (x, y) ->
                claim[x][y] == who && road[x][y] == who && grid.isWalkable(x, y));
        if (onRoad != null) return onRoad;
        int[] inClaim = nearest(ctx, precinct, (x, y) ->
                claim[x][y] == who && grid.isWalkable(x, y));
        if (inClaim != null) return inClaim;
        return nearest(ctx, precinct, grid::isWalkable);
    }

    /** Whether a cell will do. */
    private interface CellTest {
        boolean matches(int x, int y);
    }

    /** The matching cell nearest the precinct's seed, by breadth first steps. */
    private static int[] nearest(GenContext ctx, Precinct precinct, CellTest test) {
        int width = ctx.width;
        int height = ctx.height;
        int seedX = Math.max(0, Math.min(width - 1, precinct.seedX()));
        int seedY = Math.max(0, Math.min(height - 1, precinct.seedY()));
        boolean[] seen = new boolean[width * height];
        Deque<int[]> queue = new ArrayDeque<>();
        queue.add(new int[]{seedX, seedY});
        seen[seedY * width + seedX] = true;
        while (!queue.isEmpty()) {
            int[] at = queue.poll();
            if (test.matches(at[0], at[1])) return at;
            for (int[] step : STEPS) {
                int nx = at[0] + step[0];
                int ny = at[1] + step[1];
                if (nx < 0 || ny < 0 || nx >= width || ny >= height) continue;
                if (seen[ny * width + nx]) continue;
                seen[ny * width + nx] = true;
                queue.add(new int[]{nx, ny});
            }
        }
        return null;
    }

    /**
     * The cheapest walkable way from one link to the next, road-first.
     *
     * <p>Ties are broken by cell index so two runs of the same map produce the
     * same polyline: a route that varied between replays would make an evidence
     * trace disagree with itself for no reason anybody could act on.
     *
     * @return every cell from {@code from} to {@code to} inclusive, or
     *         {@code null} when there is no walkable way at all
     */
    static List<int[]> walk(NavigationGrid grid, int[][] road, int[] from, int[] to,
                            int width, int height) {
        int start = from[1] * width + from[0];
        int goal = to[1] * width + to[0];
        if (!grid.isWalkable(from[0], from[1]) || !grid.isWalkable(to[0], to[1])) return null;
        int[] cost = new int[width * height];
        int[] cameFrom = new int[width * height];
        Arrays.fill(cost, Integer.MAX_VALUE);
        Arrays.fill(cameFrom, -1);
        cost[start] = 0;
        PriorityQueue<int[]> open = new PriorityQueue<>(
                Comparator.<int[]>comparingInt(entry -> entry[0])
                        .thenComparingInt(entry -> entry[1]));
        open.add(new int[]{0, start});
        while (!open.isEmpty()) {
            int[] head = open.poll();
            int at = head[1];
            if (head[0] > cost[at]) continue;
            if (at == goal) break;
            int x = at % width;
            int y = at / width;
            for (int[] step : STEPS) {
                int nx = x + step[0];
                int ny = y + step[1];
                if (nx < 0 || ny < 0 || nx >= width || ny >= height) continue;
                if (!grid.isWalkable(nx, ny)) continue;
                int next = ny * width + nx;
                int stepCost = road[nx][ny] != GrownTrunkPlan.UNOWNED ? ON_ROAD : OFF_ROAD;
                int through = cost[at] + stepCost;
                if (through >= cost[next]) continue;
                cost[next] = through;
                cameFrom[next] = at;
                open.add(new int[]{through, next});
            }
        }
        if (cost[goal] == Integer.MAX_VALUE) return null;
        List<int[]> out = new ArrayList<>();
        for (int at = goal; at != -1; at = cameFrom[at]) {
            out.add(new int[]{at % width, at / width});
            if (at == start) break;
        }
        Collections.reverse(out);
        return out;
    }
}
