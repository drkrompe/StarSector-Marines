package com.dillon.starsectormarines.battle.cordon;

import com.dillon.starsectormarines.battle.evacuation.CivilDefenceCompound;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.precinct.Precinct;
import com.dillon.starsectormarines.battle.world.gen.precinct.PrecinctPlan;
import com.dillon.starsectormarines.battle.world.model.CellTopology;

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
 * than open ground. So it keeps to the country road and the streets wherever
 * they go its way and crosses country only where the generator left no road
 * (it prunes links between places that carry nobody to a map edge). A map
 * with no drivable way at all is no Road and re-rolls.
 */
public final class CordonCorridor {

    /** How near the compound's seed the corridor must reach to be at its gate. */
    static final int AT_COMPOUND = 24;
    /** What a cell of open ground costs a truck against a cell of road. */
    static final int OFF_ROAD_COST = 5;

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
        int rearRow = CordonFront.frontward(plan) > 0 ? 1 : height - 2;
        int[] cost = new int[width * height];
        int[] parent = new int[width * height];
        Arrays.fill(cost, Integer.MAX_VALUE);
        Arrays.fill(parent, -1);
        PriorityQueue<long[]> open = new PriorityQueue<>(
                (a, b) -> a[0] != b[0] ? Long.compare(a[0], b[0]) : Long.compare(a[1], b[1]));
        // The country road where it leaves the map; any drivable rear cell if the map has none.
        boolean anyRoad = false;
        for (int x = 1; x < width - 1; x++) anyRoad |= drivable(grid, x, rearRow) && road(topology, x, rearRow);
        for (int x = 1; x < width - 1; x++) {
            if (!drivable(grid, x, rearRow) || (anyRoad && !road(topology, x, rearRow))) continue;
            int idx = rearRow * width + x;
            cost[idx] = 0;
            open.add(new long[]{0, idx});
        }
        int goal = -1;
        int[][] steps = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!open.isEmpty()) {
            long[] top = open.poll();
            int idx = (int) top[1];
            if (top[0] > cost[idx]) continue;
            int x = idx % width, y = idx / width;
            if (Math.abs(x - compound.seedX()) <= AT_COMPOUND && Math.abs(y - compound.seedY()) <= AT_COMPOUND
                    && road(topology, x, y)) {
                goal = idx;
                break;
            }
            for (int[] d : steps) {
                int nx = x + d[0], ny = y + d[1];
                if (!drivable(grid, nx, ny)) continue;
                int n = ny * width + nx;
                int c = cost[idx] + (road(topology, nx, ny) ? 1 : OFF_ROAD_COST);
                if (c >= cost[n]) continue;
                cost[n] = c;
                parent[n] = idx;
                open.add(new long[]{c, n});
            }
        }
        if (goal < 0) return null;
        List<int[]> path = new ArrayList<>();
        for (int at = goal; at >= 0; at = parent[at]) path.add(new int[]{at % width, at / width});
        Collections.reverse(path);
        return path;
    }

    /** The share of a corridor that runs on road. */
    public static float pavedShare(CellTopology topology, List<int[]> corridor) {
        if (corridor == null || corridor.isEmpty()) return 0f;
        int paved = 0;
        for (int[] c : corridor) if (road(topology, c[0], c[1])) paved++;
        return paved / (float) corridor.size();
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
