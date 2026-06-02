package com.dillon.starsectormarines.battle.world.gen.bsp.stage;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.BlockLeaf;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.GenStage;
import com.dillon.starsectormarines.battle.world.gen.bsp.Bsp;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.gen.bsp.LeafAdjacency;
import com.dillon.starsectormarines.battle.world.gen.bsp.StationGraph;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The station's connective spine — carve corridors that join the carved rooms
 * into one walkable whole. Rooms are vertices of the {@link LeafAdjacency}
 * graph; corridors are a chosen subset of its edges:
 *
 * <ol>
 *   <li><b>Spanning tree</b> — deterministic BFS from room 0; one corridor per
 *       tree edge guarantees every room is reachable (no islands). Any leaf the
 *       adjacency graph leaves unreached (degenerate geometry) is connected to
 *       the nearest reached room by center distance so the guarantee holds.</li>
 *   <li><b>Sparse loops</b> — a small fixed budget of extra (non-tree) adjacency
 *       edges, giving alternate / flanking routes instead of a pure funnel.
 *       Budget scales with room count ({@link #LOOP_FRACTION_DENOM}).</li>
 * </ol>
 *
 * <p>Each chosen edge is carved as a {@value #CORRIDOR_WIDTH}-wide Manhattan L
 * between the two room centers, but <em>only cells that are currently solid
 * (non-walkable) are converted</em> to walkable {@link RoomPurpose#CORRIDOR}.
 * Cells already inside a room are left untouched, so the corridor shows up only
 * in the wall gap and the doorway lands exactly where the path crosses a room
 * boundary — the stage owns both endpoints, which dissolves the
 * doorway-coordination problem. Straight runs fall out as degenerate Ls.
 *
 * <p>Publishes the room/corridor {@link StationGraph} under
 * {@link BspKeys#STATION_GRAPH} — the structure passes downstream consume,
 * not just the carved cells. Reads {@link BspKeys#PARTITION}.
 */
public final class CorridorStage implements GenStage {

    /** Transit-corridor width. 2 is the floor that flows (gives the pathfinder's occupancy penalty a parallel lane); see the corridors-first-class width policy. */
    private static final int CORRIDOR_WIDTH = 2;

    /** Loop budget = roomCount / this. One extra (non-tree) edge per ~this-many rooms, so the spine stays mostly tree with a few alternate routes. */
    private static final int LOOP_FRACTION_DENOM = 10;

    @Override
    public void run(GenContext ctx) {
        Bsp.Partition partition = ctx.get(BspKeys.PARTITION);
        List<BlockLeaf> leaves = partition.leaves;

        // Room id == index into the leaf list (deterministic BSP order).
        List<StationGraph.Room> rooms = new ArrayList<>(leaves.size());
        Map<BlockLeaf, Integer> idOf = new IdentityHashMap<>(leaves.size() * 2);
        for (int i = 0; i < leaves.size(); i++) {
            rooms.add(new StationGraph.Room(i, leaves.get(i)));
            idOf.put(leaves.get(i), i);
        }
        StationGraph graph = new StationGraph(rooms);

        if (leaves.isEmpty()) {
            ctx.put(BspKeys.STATION_GRAPH, graph);
            return;
        }

        Map<BlockLeaf, List<BlockLeaf>> adjacency =
                LeafAdjacency.compute(leaves, ctx.width, ctx.height);

        // --- Spanning tree (BFS from room 0) ---
        Set<Long> chosen = new LinkedHashSet<>();   // undirected edge keys, in carve order
        boolean[] reached = new boolean[leaves.size()];
        Deque<Integer> queue = new ArrayDeque<>();
        reached[0] = true;
        queue.add(0);
        while (!queue.isEmpty()) {
            int id = queue.poll();
            for (BlockLeaf nbr : adjacency.get(leaves.get(id))) {
                int nid = idOf.get(nbr);
                if (!reached[nid]) {
                    reached[nid] = true;
                    chosen.add(edgeKey(id, nid, leaves.size()));
                    queue.add(nid);
                }
            }
        }

        // --- Connect any adjacency-orphaned rooms to the nearest reached one ---
        for (int id = 0; id < leaves.size(); id++) {
            if (reached[id]) continue;
            int nearest = nearestReached(rooms, reached, id);
            reached[id] = true;
            chosen.add(edgeKey(id, nearest, leaves.size()));
        }

        // --- Sparse loops: add a few non-tree adjacency edges ---
        List<long[]> spare = new ArrayList<>();     // [key, a, b]
        for (int id = 0; id < leaves.size(); id++) {
            for (BlockLeaf nbr : adjacency.get(leaves.get(id))) {
                int nid = idOf.get(nbr);
                if (id >= nid) continue;            // undirected: count each pair once
                long key = edgeKey(id, nid, leaves.size());
                if (!chosen.contains(key)) {
                    spare.add(new long[]{key, id, nid});
                }
            }
        }
        Collections.shuffle(spare, ctx.rng);
        int loopBudget = leaves.size() / LOOP_FRACTION_DENOM;
        for (int i = 0; i < loopBudget && i < spare.size(); i++) {
            chosen.add(spare.get(i)[0]);
        }

        // --- Carve every chosen edge + record it in the graph ---
        for (long key : chosen) {
            int a = (int) (key / leaves.size());
            int b = (int) (key % leaves.size());
            carveCorridor(ctx, rooms.get(a), rooms.get(b));
            graph.addCorridor(a, b);
        }

        ctx.put(BspKeys.STATION_GRAPH, graph);
    }

    /** Stable undirected edge key: {@code min*N + max} with {@code N == roomCount}. */
    private static long edgeKey(int a, int b, int n) {
        int lo = Math.min(a, b);
        int hi = Math.max(a, b);
        return (long) lo * n + hi;
    }

    private static int nearestReached(List<StationGraph.Room> rooms, boolean[] reached, int from) {
        StationGraph.Room f = rooms.get(from);
        int best = -1;
        long bestD = Long.MAX_VALUE;
        for (int id = 0; id < rooms.size(); id++) {
            if (!reached[id]) continue;
            StationGraph.Room r = rooms.get(id);
            long dx = r.centerX - f.centerX;
            long dy = r.centerY - f.centerY;
            long d = dx * dx + dy * dy;
            if (d < bestD) {
                bestD = d;
                best = id;
            }
        }
        return best;
    }

    /**
     * Carve a {@value #CORRIDOR_WIDTH}-wide Manhattan L from room {@code a}'s
     * center to room {@code b}'s center: horizontal run along {@code a}'s row,
     * then vertical run along {@code b}'s column (meeting at the corner). Only
     * solid cells are converted, so the carve materializes solely in the wall
     * gaps the L crosses.
     */
    private static void carveCorridor(GenContext ctx, StationGraph.Room a, StationGraph.Room b) {
        int ax = a.centerX, ay = a.centerY;
        int bx = b.centerX, by = b.centerY;
        carveHRun(ctx, ax, bx, ay);   // horizontal leg at y = ay
        carveVRun(ctx, ay, by, bx);   // vertical leg at x = bx (closes the L at the corner)
    }

    /** Carve a 2-wide horizontal run between x0 and x1 at row y (+ the parallel lane below, or above at the grid's bottom edge). */
    private static void carveHRun(GenContext ctx, int x0, int x1, int y) {
        int lo = Math.min(x0, x1);
        int hi = Math.max(x0, x1);
        int y2 = laneOffset(y, ctx.height);
        for (int x = lo; x <= hi; x++) {
            carveCell(ctx, x, y);
            carveCell(ctx, x, y2);
        }
    }

    /** Carve a 2-wide vertical run between y0 and y1 at column x (+ the parallel lane to the right, or left at the grid's right edge). */
    private static void carveVRun(GenContext ctx, int y0, int y1, int x) {
        int lo = Math.min(y0, y1);
        int hi = Math.max(y0, y1);
        int x2 = laneOffset(x, ctx.width);
        for (int y = lo; y <= hi; y++) {
            carveCell(ctx, x, y);
            carveCell(ctx, x2, y);
        }
    }

    /** The parallel-lane coordinate: {@code c+1}, or {@code c-1} when {@code c+1} would leave the {@code [0, limit)} axis. */
    private static int laneOffset(int c, int limit) {
        return (c + 1 < limit) ? c + 1 : c - 1;
    }

    /**
     * Convert one cell to walkable {@link RoomPurpose#CORRIDOR} — but only if it
     * is currently solid. Cells already inside a room stay room cells (the
     * corridor is the carved wall gap, not a relabeling of room floor).
     */
    private static void carveCell(GenContext ctx, int x, int y) {
        NavigationGrid grid = ctx.grid;
        if (!grid.inBounds(x, y)) return;
        if (grid.isWalkable(x, y)) return;
        grid.setWalkableFloor(x, y);
        CellTopology topo = ctx.topology;
        topo.setGroundKind(x, y, GroundKind.STRIPED);
        topo.setRoomPurpose(x, y, RoomPurpose.CORRIDOR);
    }
}
