package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.world.gen.precinct.LaneRoute;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;

/**
 * Which lane a cell belongs to, by the recorded route that passes nearest it.
 *
 * <p>Conquest's lateral thirds were the fence for as long as a lane was a
 * ribbon: three parallel strips across the traversal axis, and a body's lane
 * was whichever strip it stood in. Lanes fan now — every one of them leaves the
 * beachhead and arrives at the keep, and stands in its own third only at its
 * widest band — so a line drawn across the axis can no longer say which of
 * three routes a squad at the landing zone belongs to. It says all of them
 * belong to the middle one, which is how two lanes in three came to have fronts
 * the force never went near.
 *
 * <p><b>The routes themselves are the fence.</b> Every cell takes the lane of
 * the route nearest it, by a breadth-first sweep out from every route at once —
 * a Voronoi partition of the map between three roads. Near the shared ends the
 * three routes are close together and the partition is decided by which one
 * bends the right way, which is exactly the question "whose lane is this squad
 * on" asks there.
 *
 * <p><b>Distance is steps across the map, not steps a marine could take.</b>
 * Walls are ignored deliberately: this is a fence, not a path, and a squad
 * standing in a walled compound belongs to the lane outside its wall rather
 * than to whichever route happens to share its room. Reachability is the
 * staging layer's question and it asks it separately.
 *
 * <p>Built once per battle from {@code MapResult.lanes}; a map that recorded no
 * routes has no fence and the caller falls back to
 * {@link ConquestTrackLayout}'s thirds, which is every mission but Conquest and
 * every Conquest on an ungrown map.
 */
public final class LaneFence {

    /** A cell no route reached: only possible on a map with no routes at all. */
    public static final int NO_LANE = -1;

    private final int width;
    private final int height;
    private final int[] lane;

    private LaneFence(int width, int height, int[] lane) {
        this.width = width;
        this.height = height;
        this.lane = lane;
    }

    /**
     * The fence these routes draw, or {@code null} when none of them recorded a
     * cell — there is nothing to partition the map between.
     */
    /** A cell more than one route runs through: it seeds nothing. */
    private static final int SHARED = -2;

    public static LaneFence of(List<LaneRoute> routes, int width, int height) {
        if (routes == null || routes.isEmpty() || width <= 0 || height <= 0) return null;
        int[] lane = new int[width * height];
        Arrays.fill(lane, NO_LANE);
        for (LaneRoute route : routes) {
            for (LaneRoute.Cell cell : route.route()) {
                if (cell.x() < 0 || cell.y() < 0
                        || cell.x() >= width || cell.y() >= height) continue;
                int at = cell.y() * width + cell.x();
                if (lane[at] == NO_LANE) lane[at] = route.lane();
                else if (lane[at] != route.lane()) lane[at] = SHARED;
            }
        }
        // Ground two lanes both run over decides nothing and is decided by the
        // sweep like any other cell. Seeding it would hand every shared trunk
        // road to whichever lane happened to be read first, and on
        // full-strength-west that gave the middle lane not one compound of its
        // own — its route runs down the same streets as its neighbour's for
        // most of its length, and only the stretches near its own rungs are
        // actually its.
        Deque<Integer> queue = new ArrayDeque<>();
        for (int at = 0; at < lane.length; at++) {
            if (lane[at] == SHARED) lane[at] = NO_LANE;
            else if (lane[at] != NO_LANE) queue.add(at);
        }
        if (queue.isEmpty()) return null;
        while (!queue.isEmpty()) {
            int at = queue.poll();
            int x = at % width;
            int y = at / width;
            spread(lane, queue, width, height, x - 1, y, lane[at]);
            spread(lane, queue, width, height, x + 1, y, lane[at]);
            spread(lane, queue, width, height, x, y - 1, lane[at]);
            spread(lane, queue, width, height, x, y + 1, lane[at]);
        }
        return new LaneFence(width, height, lane);
    }

    private static void spread(int[] lane, Deque<Integer> queue, int width, int height,
                               int x, int y, int from) {
        if (x < 0 || y < 0 || x >= width || y >= height) return;
        int at = y * width + x;
        if (lane[at] != NO_LANE) return;
        lane[at] = from;
        queue.add(at);
    }

    /** Which lane this cell belongs to, or {@link #NO_LANE} off the map. */
    public int laneAt(int x, int y) {
        if (x < 0 || y < 0 || x >= width || y >= height) return NO_LANE;
        return lane[y * width + x];
    }

    /** The same, for a continuous position. */
    public int laneAt(float x, float y) {
        return laneAt((int) Math.floor(x), (int) Math.floor(y));
    }
}
