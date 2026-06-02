package com.dillon.starsectormarines.battle.world.gen.bsp.stage;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.GenStage;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.gen.bsp.StationGraph;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;

/**
 * Station spawn anchors — place the marine and defender spawns at the two ends
 * of the room/corridor graph's <em>diameter</em> (the longest shortest-path
 * between any two rooms), so the assault crosses the whole station rather than
 * starting in adjacent rooms. The endpoints fall out of a double BFS over the
 * {@link StationGraph}: BFS from room 0 to its farthest room {@code u}, then BFS
 * from {@code u} to its farthest room {@code v}; {@code (u, v)} is a diameter.
 *
 * <p>Binds {@link BspKeys#MARINE_SPAWN} / {@link BspKeys#DEFENDER_SPAWN} to a
 * walkable cell in each endpoint room.
 */
public final class StationSpawnStage implements GenStage {

    @Override
    public void run(GenContext ctx) {
        StationGraph graph = ctx.get(BspKeys.STATION_GRAPH);
        if (graph == null || graph.roomCount() == 0) {
            // Degenerate map — fall back to center for both so MapResult stays valid.
            int[] c = { ctx.width / 2, ctx.height / 2 };
            ctx.put(BspKeys.MARINE_SPAWN, c);
            ctx.put(BspKeys.DEFENDER_SPAWN, c.clone());
            return;
        }

        int u = farthestFrom(graph, 0);
        int v = farthestFrom(graph, u);

        ctx.put(BspKeys.MARINE_SPAWN, roomSpawnCell(ctx.grid, graph.room(u)));
        ctx.put(BspKeys.DEFENDER_SPAWN, roomSpawnCell(ctx.grid, graph.room(v)));
    }

    /** BFS over corridor edges; returns the room id with the greatest hop distance from {@code start} (lowest id breaks ties — BFS order). */
    private static int farthestFrom(StationGraph graph, int start) {
        int[] dist = new int[graph.roomCount()];
        Arrays.fill(dist, -1);
        dist[start] = 0;
        Deque<Integer> queue = new ArrayDeque<>();
        queue.add(start);
        int farthest = start;
        while (!queue.isEmpty()) {
            int id = queue.poll();
            for (int nbr : graph.neighbors(id)) {
                if (dist[nbr] == -1) {
                    dist[nbr] = dist[id] + 1;
                    if (dist[nbr] > dist[farthest]) farthest = nbr;
                    queue.add(nbr);
                }
            }
        }
        return farthest;
    }

    /** The room's center if walkable, else the first walkable cell scanned in its rect, else the center anyway. */
    private static int[] roomSpawnCell(NavigationGrid grid, StationGraph.Room room) {
        if (grid.isWalkable(room.centerX, room.centerY)) {
            return new int[]{ room.centerX, room.centerY };
        }
        for (int y = room.top; y <= room.bottom; y++) {
            for (int x = room.left; x <= room.right; x++) {
                if (grid.isWalkable(x, y)) return new int[]{ x, y };
            }
        }
        return new int[]{ room.centerX, room.centerY };
    }
}
