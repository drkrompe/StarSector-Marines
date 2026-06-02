package com.dillon.starsectormarines.battle.world.gen.bsp.stage;

import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.GenStage;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.gen.bsp.StationGraph;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Derive the {@link StationGraph}'s topological roles — the foundation later
 * placement rules query. Pure analysis over the already-carved room/corridor
 * graph (no grid mutation, no {@code rng}), so the map stays byte-identical;
 * runs after {@link StationSpawnStage} because depth-from-entry is measured from
 * the marine (attacker) spawn room.
 *
 * <p>Computes, in one pass each:
 * <ul>
 *   <li><b>Depth from entry</b> — BFS hops from the entry room. The indoor
 *       analogue of the conquest assault gradient: 0 at the breach, rising
 *       toward the defender's deep end.</li>
 *   <li><b>Articulation rooms + bridge corridors</b> — one Tarjan DFS. An
 *       articulation room is must-pass (its removal splits the station); a bridge
 *       corridor is the sole link to a subtree. These are the defender's natural
 *       fortify points.</li>
 *   <li><b>On-spine vs on-loop</b> — a corridor is on-spine iff it's a bridge; a
 *       room is on-loop iff it has any non-bridge (cycle) corridor. Main line vs
 *       flank.</li>
 * </ul>
 *
 * <p>Reads {@link BspKeys#STATION_GRAPH} + {@link BspKeys#MARINE_SPAWN}; installs
 * the roles back onto the graph via {@link StationGraph#applyRoles}.
 */
public final class StationTopologyStage implements GenStage {

    @Override
    public void run(GenContext ctx) {
        StationGraph graph = ctx.get(BspKeys.STATION_GRAPH);
        if (graph == null || graph.roomCount() == 0) return;
        int n = graph.roomCount();

        int entry = entryRoomOf(graph, ctx.get(BspKeys.MARINE_SPAWN));

        int[] depth = bfsDepth(graph, entry);

        boolean[] articulation = new boolean[n];
        Set<Long> bridges = new HashSet<>();
        new Tarjan(graph, n, articulation, bridges).run();

        // Map bridge edges back to corridor indices; a room is on-loop iff any
        // of its corridors is a non-bridge (lies on a cycle).
        List<StationGraph.Corridor> corridors = graph.corridors();
        boolean[] bridgeCorridor = new boolean[corridors.size()];
        boolean[] onLoop = new boolean[n];
        for (int i = 0; i < corridors.size(); i++) {
            StationGraph.Corridor c = corridors.get(i);
            boolean isBridge = bridges.contains(edgeKey(c.roomA, c.roomB, n));
            bridgeCorridor[i] = isBridge;
            if (!isBridge) {
                onLoop[c.roomA] = true;
                onLoop[c.roomB] = true;
            }
        }

        graph.applyRoles(entry, depth, articulation, onLoop, bridgeCorridor);
    }

    /** The room whose rect contains the marine spawn cell; room 0 as a defensive fallback. */
    private static int entryRoomOf(StationGraph graph, int[] marineSpawn) {
        if (marineSpawn != null) {
            int sx = marineSpawn[0];
            int sy = marineSpawn[1];
            for (StationGraph.Room r : graph.rooms()) {
                if (sx >= r.left && sx <= r.right && sy >= r.top && sy <= r.bottom) {
                    return r.id;
                }
            }
        }
        return 0;
    }

    /** Hop distance from {@code entry} over corridor edges; -1 for any room the graph can't reach. */
    private static int[] bfsDepth(StationGraph graph, int entry) {
        int[] depth = new int[graph.roomCount()];
        Arrays.fill(depth, -1);
        depth[entry] = 0;
        Deque<Integer> queue = new ArrayDeque<>();
        queue.add(entry);
        while (!queue.isEmpty()) {
            int id = queue.poll();
            for (int nbr : graph.neighbors(id)) {
                if (depth[nbr] == -1) {
                    depth[nbr] = depth[id] + 1;
                    queue.add(nbr);
                }
            }
        }
        return depth;
    }

    /** Stable undirected edge key {@code min*N + max}, matching the corridor-index map. */
    private static long edgeKey(int a, int b, int n) {
        int lo = Math.min(a, b);
        int hi = Math.max(a, b);
        return (long) lo * n + hi;
    }

    /**
     * Tarjan's low-link DFS — finds articulation points and bridges of an
     * undirected graph in one traversal. No parallel edges in a
     * {@link StationGraph} (corridors dedupe each undirected pair), so the
     * single-parent skip is sufficient. The recursion depth is bounded by the
     * room count (tens, occasionally low hundreds), well within the JVM stack.
     */
    private static final class Tarjan {
        private final StationGraph graph;
        private final int n;
        private final boolean[] articulation;
        private final Set<Long> bridges;
        private final int[] disc;
        private final int[] low;
        private int time;

        Tarjan(StationGraph graph, int n, boolean[] articulation, Set<Long> bridges) {
            this.graph = graph;
            this.n = n;
            this.articulation = articulation;
            this.bridges = bridges;
            this.disc = new int[n];
            this.low = new int[n];
            Arrays.fill(disc, -1);
        }

        void run() {
            for (int i = 0; i < n; i++) {
                if (disc[i] == -1) dfs(i, -1);
            }
        }

        private void dfs(int u, int parent) {
            disc[u] = low[u] = time++;
            int children = 0;
            for (int v : graph.neighbors(u)) {
                if (v == parent) continue;
                if (disc[v] == -1) {
                    children++;
                    dfs(v, u);
                    low[u] = Math.min(low[u], low[v]);
                    if (low[v] > disc[u]) {
                        bridges.add(edgeKey(u, v, n));
                    }
                    if (parent != -1 && low[v] >= disc[u]) {
                        articulation[u] = true;
                    }
                } else {
                    low[u] = Math.min(low[u], disc[v]);
                }
            }
            if (parent == -1 && children > 1) {
                articulation[u] = true;
            }
        }
    }
}
