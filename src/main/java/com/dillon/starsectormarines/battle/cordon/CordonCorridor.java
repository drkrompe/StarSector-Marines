package com.dillon.starsectormarines.battle.cordon;

import com.dillon.starsectormarines.battle.evacuation.CivilDefenceCompound;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.vehicle.VehicleClearance;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.dillon.starsectormarines.battle.world.gen.precinct.Precinct;
import com.dillon.starsectormarines.battle.world.gen.precinct.PrecinctPlan;
import com.dillon.starsectormarines.battle.world.model.CellTopology;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.PriorityQueue;

/**
 * The Road's corridor ({@code cordon-nouns.md}, Corridor): the route the
 * convoys drive, from where the country road leaves the map at the rear edge
 * to the street at the cut-off town's compound.
 *
 * <p>Found on the finished map, not carved: the cheapest way a truck can
 * drive, every cell of it open with room either side, road five times cheaper
 * than open ground, and a turn made where a truck has no room to turn dearer
 * still. So it keeps to the country road and the streets wherever
 * they go its way and crosses country only where the generator left no road
 * (it prunes links between places that carry nobody to a map edge). A map
 * with no drivable way at all is no Road and re-rolls.
 */
public final class CordonCorridor {

    /** How near the compound's seed the corridor must reach to be at its gate. */
    static final int AT_COMPOUND = 24;
    /** What a cell of open ground costs a truck against a cell of road. */
    static final int OFF_ROAD_COST = 5;
    /**
     * What turning on a cell with no {@link #TURNING_ROOM} costs on top. A truck (turn radius near three cells) drives a
     * lane three wide straight but cannot turn into one: on town Road seed 8 the corridor turned off a street through
     * a gap into an alley three wide, and every convoy's lead truck wedged at its mouth for the battle, with open
     * streets two cells on. Dearer, not forbidden, so a map whose only way turns there still has its corridor.
     */
    static final int TIGHT_TURN_COST = 40;
    /** Cells open either side of a cell, all round, for a truck to turn on it. */
    static final int TURNING_ROOM = 2;

    private CordonCorridor() {}

    /** The compound of a Road plan, or {@code null}. */
    public static Precinct compound(PrecinctPlan plan) {
        for (Precinct p : plan.precincts()) if (p.name().equals(CivilDefenceCompound.PLACE)) return p;
        return null;
    }

    /**
     * The corridor's cells in driving order, rear edge first, or {@code null}
     * when no truck can drive from the rear edge to the compound.
     */
    public static List<int[]> find(NavigationGrid grid, CellTopology topology, PrecinctPlan plan) {
        Precinct compound = compound(plan);
        if (compound == null) return null;
        int width = grid.getWidth(), height = grid.getHeight();
        int rearRow = CordonFront.rearRow(plan, height);
        // The country road where it leaves the map; any drivable rear cell if the map has none.
        boolean anyRoad = false;
        for (int x = 1; x < width - 1; x++) anyRoad |= drivable(grid, x, rearRow) && road(topology, x, rearRow);
        List<int[]> starts = new ArrayList<>();
        for (int x = 1; x < width - 1; x++) {
            if (drivable(grid, x, rearRow) && (!anyRoad || road(topology, x, rearRow))) starts.add(new int[]{x, rearRow});
        }
        return search(grid, (x, y) -> road(topology, x, y), starts,
                (x, y) -> Math.abs(x - compound.seedX()) <= AT_COMPOUND && Math.abs(y - compound.seedY()) <= AT_COMPOUND
                        && road(topology, x, y));
    }

    /** A test on a cell. */
    interface CellTest {
        boolean test(int x, int y);
    }

    /**
     * The cheapest drivable way from any start to the first cell {@code goal} accepts, in driving order, or
     * {@code null}: a step onto road costs 1, onto open ground {@link #OFF_ROAD_COST}, and a turn on a cell with no
     * room to turn {@link #TIGHT_TURN_COST} more. Searched over cell and heading, so a turn is seen where it is made.
     */
    static List<int[]> search(NavigationGrid grid, CellTest road, List<int[]> starts, CellTest goal) {
        int width = grid.getWidth(), height = grid.getHeight();
        int[][] steps = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        // A state is a cell and the heading the truck came onto it with; a start has every heading.
        int[] cost = new int[width * height * 4];
        int[] parent = new int[width * height * 4];
        Arrays.fill(cost, Integer.MAX_VALUE);
        Arrays.fill(parent, -1);
        PriorityQueue<long[]> open = new PriorityQueue<>(
                (a, b) -> a[0] != b[0] ? Long.compare(a[0], b[0]) : Long.compare(a[1], b[1]));
        for (int[] s : starts) {
            for (int h = 0; h < 4; h++) {
                int state = (s[1] * width + s[0]) * 4 + h;
                cost[state] = 0;
                open.add(new long[]{0, state});
            }
        }
        int found = -1;
        while (!open.isEmpty()) {
            long[] top = open.poll();
            int state = (int) top[1];
            if (top[0] > cost[state]) continue;
            int idx = state / 4, heading = state % 4;
            int x = idx % width, y = idx / width;
            if (goal.test(x, y)) {
                found = state;
                break;
            }
            boolean roomy = false, roomyRead = false;
            for (int h = 0; h < 4; h++) {
                int nx = x + steps[h][0], ny = y + steps[h][1];
                if (!drivable(grid, nx, ny)) continue;
                int c = cost[state] + (road.test(nx, ny) ? 1 : OFF_ROAD_COST);
                if (h != heading) {
                    if (!roomyRead) {
                        roomy = roomy(grid, x, y);
                        roomyRead = true;
                    }
                    if (!roomy) c += TIGHT_TURN_COST;
                }
                int n = (ny * width + nx) * 4 + h;
                if (c >= cost[n]) continue;
                cost[n] = c;
                parent[n] = state;
                open.add(new long[]{c, n});
            }
        }
        if (found < 0) return null;
        List<int[]> path = new ArrayList<>();
        for (int at = found; at >= 0; at = parent[at]) path.add(new int[]{(at / 4) % width, (at / 4) / width});
        Collections.reverse(path);
        return path;
    }

    /** A cell with {@link #TURNING_ROOM} open all round it: room for a truck to turn on it, not only to pass. */
    static boolean roomy(NavigationGrid grid, int x, int y) {
        for (int dy = -TURNING_ROOM; dy <= TURNING_ROOM; dy++) {
            for (int dx = -TURNING_ROOM; dx <= TURNING_ROOM; dx++) {
                if (!grid.inBounds(x + dx, y + dy) || !grid.isWalkable(x + dx, y + dy)) return false;
            }
        }
        return true;
    }

    /** The share of a corridor that runs on road. */
    public static float pavedShare(CellTopology topology, List<int[]> corridor) {
        if (corridor == null || corridor.isEmpty()) return 0f;
        int paved = 0;
        for (int[] c : corridor) if (road(topology, c[0], c[1])) paved++;
        return paved / (float) corridor.size();
    }

    /** How far from a corridor's end a truck's mask looks for ground to stand on. */
    private static final int END_REACH = 3;

    /**
     * Whether a supply truck can drive the corridor end to end on the mask its
     * convoy lays routes on, which keeps clear of walls along cell edges too
     * (a fence beside the road stops a hull the 3x3 test lets through). A gate
     * on the map, not the route: the corridor's cells are unchanged.
     */
    public static boolean truckReaches(NavigationGrid grid, List<int[]> corridor) {
        if (corridor == null || corridor.isEmpty()) return false;
        VehicleClearance mask = VehicleClearance.erode(grid,
                VehicleClearance.radiusForWidth(VehicleType.SUPPLY_TRUCK.visualWidthCells));
        int[] from = standing(mask, corridor.get(0)), to = standing(mask, corridor.get(corridor.size() - 1));
        if (from == null || to == null) return false;
        int w = mask.getWidth(), h = mask.getHeight();
        boolean[] seen = new boolean[w * h];
        ArrayDeque<Integer> open = new ArrayDeque<>();
        seen[from[1] * w + from[0]] = true;
        open.add(from[1] * w + from[0]);
        int goal = to[1] * w + to[0];
        int[] dx = {1, -1, 0, 0}, dy = {0, 0, 1, -1};
        while (!open.isEmpty()) {
            int at = open.poll();
            if (at == goal) return true;
            int x = at % w, y = at / w;
            for (int d = 0; d < 4; d++) {
                int nx = x + dx[d], ny = y + dy[d];
                if (nx < 0 || ny < 0 || nx >= w || ny >= h || seen[ny * w + nx] || !mask.isPassable(nx, ny)) continue;
                seen[ny * w + nx] = true;
                open.add(ny * w + nx);
            }
        }
        return false;
    }

    private static int[] standing(VehicleClearance mask, int[] cell) {
        for (int r = 0; r <= END_REACH; r++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dx = -r; dx <= r; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) != r) continue;
                    if (mask.isPassable(cell[0] + dx, cell[1] + dy)) return new int[]{cell[0] + dx, cell[1] + dy};
                }
            }
        }
        return null;
    }

    /** A cell a truck can stand on: open, with open ground all round it. */
    static boolean drivable(NavigationGrid grid, int x, int y) {
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (!grid.inBounds(x + dx, y + dy) || !grid.isWalkable(x + dx, y + dy)) return false;
            }
        }
        return true;
    }

    /** Road under a truck: street, pavement or a crossing. */
    static boolean road(CellTopology topology, int x, int y) {
        CellTopology.GroundKind kind = topology.getGroundKind(x, y);
        return kind == CellTopology.GroundKind.STREET || kind == CellTopology.GroundKind.SIDEWALK
                || topology.isCrosswalk(x, y);
    }
}
