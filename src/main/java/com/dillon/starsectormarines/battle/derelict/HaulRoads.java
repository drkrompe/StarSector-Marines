package com.dillon.starsectormarines.battle.derelict;

import java.util.ArrayList;
import java.util.List;

/**
 * The haul roads (`fallen-hull-nouns.md`, Hauler): the ways the ground lays from the hull outward, along which haulers
 * and stake trains walk. A walk whose ends both lie within {@link #REACH_CELLS} of one road joins it at the cell
 * nearest its start, follows it to the cell nearest its end, and leaves it there; any other walk goes straight. The
 * pathfinder walks each leg; the road only says where the legs are.
 */
public final class HaulRoads {

    /** How far from a road, in cells (Chebyshev), a walk's end may lie and still take it. */
    public static final int REACH_CELLS = 8;
    /** Road cells between waypoints. */
    public static final int STEP_CELLS = 6;
    /** How near a waypoint, in cells (either axis), a walker counts as there. */
    public static final float ARRIVE_CELLS = 2f;

    private final List<int[]> roads;

    /** @param roads each road a polyline of cell pairs */
    public HaulRoads(List<int[]> roads) {
        this.roads = List.copyOf(roads);
    }

    /** The waypoints from {@code (fromX, fromY)} to {@code (toX, toY)}, ending at the destination. */
    public List<int[]> route(int fromX, int fromY, int toX, int toY) {
        int[] best = null;
        int bestRoad = -1;
        int bestCost = Integer.MAX_VALUE;
        for (int r = 0; r < roads.size(); r++) {
            int[] road = roads.get(r);
            int in = nearest(road, fromX, fromY);
            int out = nearest(road, toX, toY);
            int dIn = cheb(road, in, fromX, fromY);
            int dOut = cheb(road, out, toX, toY);
            if (in == out || dIn > REACH_CELLS || dOut > REACH_CELLS) continue;
            int cost = dIn + dOut;
            if (cost < bestCost) {
                bestCost = cost;
                best = new int[] {in, out};
                bestRoad = r;
            }
        }
        List<int[]> out = new ArrayList<>();
        if (best != null) {
            int[] road = roads.get(bestRoad);
            int step = best[1] > best[0] ? 1 : -1;
            for (int i = best[0]; i != best[1]; i += step) {
                if (Math.abs(i - best[0]) % STEP_CELLS == 0) out.add(new int[] {road[i * 2], road[i * 2 + 1]});
            }
            out.add(new int[] {road[best[1] * 2], road[best[1] * 2 + 1]});
        }
        out.add(new int[] {toX, toY});
        return out;
    }

    private static int nearest(int[] road, int x, int y) {
        int best = 0;
        int bestD = Integer.MAX_VALUE;
        for (int i = 0; i < road.length / 2; i++) {
            int d = cheb(road, i, x, y);
            if (d < bestD) {
                best = i;
                bestD = d;
            }
        }
        return best;
    }

    private static int cheb(int[] road, int i, int x, int y) {
        return Math.max(Math.abs(road[i * 2] - x), Math.abs(road[i * 2 + 1] - y));
    }

    /** A walk along a route: its current waypoint, moved on as the walker arrives. */
    public static final class Leg {
        private final List<int[]> waypoints;
        private int at;

        public Leg(List<int[]> waypoints) {
            if (waypoints.isEmpty()) throw new IllegalArgumentException("A leg goes somewhere");
            this.waypoints = List.copyOf(waypoints);
        }

        /** The waypoint to walk to from {@code (x, y)}: the next one not yet reached, the last once there. */
        public int[] toward(float x, float y) {
            while (at < waypoints.size() - 1) {
                int[] w = waypoints.get(at);
                if (Math.max(Math.abs(x - (w[0] + 0.5f)), Math.abs(y - (w[1] + 0.5f))) > ARRIVE_CELLS) break;
                at++;
            }
            return waypoints.get(at);
        }

        /** The walk's destination. */
        public int[] end() {
            return waypoints.get(waypoints.size() - 1);
        }
    }
}
