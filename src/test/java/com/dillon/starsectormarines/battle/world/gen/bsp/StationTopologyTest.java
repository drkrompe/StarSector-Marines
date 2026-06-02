package com.dillon.starsectormarines.battle.world.gen.bsp;

import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Structural-invariant gate for {@link StationGraph}'s topological roles
 * (computed by {@code StationTopologyStage}). The teeth here are an
 * <em>independent brute-force oracle</em>: a corridor is a bridge iff physically
 * removing it disconnects the graph, and a room is an articulation point iff
 * removing it (and its corridors) disconnects what remains. That oracle is
 * pure remove-and-flood — it shares no code with the stage's Tarjan DFS, so the
 * two agreeing is real cross-validation, not a tautology. Cheap at gen scale
 * (tens of rooms): O(corridors × (V+E)) + O(rooms × (V+E)) per seed.
 *
 * <p>Also asserts the depth-from-entry gradient is a valid BFS layering, the
 * on-loop flags are consistent with bridge-ness, and the whole role set is
 * deterministic from the seed.
 */
public class StationTopologyTest {

    private static final int W = 80;
    private static final int H = 80;
    private static final long[] SEEDS = { 1L, 42L, 100L, 777L, 1234L, 9999L };

    @Test
    void rolesMatchBruteForceOracleAndAreDeterministic() {
        BspCityGenerator gen = new BspCityGenerator();
        List<String> failures = new ArrayList<>();

        for (long seed : SEEDS) {
            gen.generateStation(W, H, seed);
            StationGraph g = gen.getLastStationGraph();
            if (g == null || !g.hasRoles()) {
                failures.add("seed " + seed + ": station graph missing or roles not applied");
                continue;
            }
            int n = g.roomCount();
            List<int[]> edges = edgesOf(g);

            // --- depth-from-entry is a valid BFS layering ---
            int entry = g.entryRoom();
            if (g.depthFromEntry(entry) != 0) {
                failures.add("seed " + seed + ": entry room " + entry + " depth != 0");
            }
            for (int r = 0; r < n; r++) {
                if (g.depthFromEntry(r) < 0) {
                    failures.add("seed " + seed + ": room " + r + " unreachable from entry (depth < 0)");
                }
            }
            for (int[] e : edges) {
                int dd = Math.abs(g.depthFromEntry(e[0]) - g.depthFromEntry(e[1]));
                if (dd > 1) {
                    failures.add("seed " + seed + ": corridor " + e[0] + "-" + e[1]
                            + " spans depth jump " + dd + " (> 1 — not a BFS layering)");
                }
            }

            // --- bridge oracle: remove each corridor, check disconnection ---
            for (int i = 0; i < edges.size(); i++) {
                boolean oracleBridge = !connected(n, edges, /*skipEdge*/ i, /*removedRoom*/ -1);
                if (g.isBridge(i) != oracleBridge) {
                    failures.add("seed " + seed + ": corridor " + i + " (" + edges.get(i)[0] + "-"
                            + edges.get(i)[1] + ") bridge=" + g.isBridge(i) + " but oracle=" + oracleBridge);
                }
            }

            // --- articulation oracle: remove each room, check disconnection of the rest ---
            for (int r = 0; r < n; r++) {
                boolean oracleArticulation = removingRoomDisconnects(n, edges, r);
                if (g.isArticulation(r) != oracleArticulation) {
                    failures.add("seed " + seed + ": room " + r + " articulation=" + g.isArticulation(r)
                            + " but oracle=" + oracleArticulation);
                }
            }

            // --- on-loop consistency: a room is on-loop iff it has a non-bridge corridor ---
            boolean[] expectOnLoop = new boolean[n];
            for (int i = 0; i < edges.size(); i++) {
                if (!g.isBridge(i)) {
                    expectOnLoop[edges.get(i)[0]] = true;
                    expectOnLoop[edges.get(i)[1]] = true;
                }
            }
            for (int r = 0; r < n; r++) {
                if (g.isOnLoop(r) != expectOnLoop[r]) {
                    failures.add("seed " + seed + ": room " + r + " onLoop=" + g.isOnLoop(r)
                            + " but expected " + expectOnLoop[r]);
                }
            }

            // --- determinism: regenerate and compare the whole role set ---
            BspCityGenerator gen2 = new BspCityGenerator();
            gen2.generateStation(W, H, seed);
            StationGraph g2 = gen2.getLastStationGraph();
            if (g2.roomCount() != n || g2.corridorCount() != g.corridorCount() || g2.entryRoom() != entry) {
                failures.add("seed " + seed + ": non-deterministic graph shape across regeneration");
            } else {
                for (int r = 0; r < n; r++) {
                    if (g2.depthFromEntry(r) != g.depthFromEntry(r)
                            || g2.isArticulation(r) != g.isArticulation(r)
                            || g2.isOnLoop(r) != g.isOnLoop(r)) {
                        failures.add("seed " + seed + ": non-deterministic role for room " + r);
                        break;
                    }
                }
                for (int i = 0; i < g.corridorCount(); i++) {
                    if (g2.isBridge(i) != g.isBridge(i)) {
                        failures.add("seed " + seed + ": non-deterministic bridge flag for corridor " + i);
                        break;
                    }
                }
            }

            System.out.printf("seed %d: %d rooms, %d corridors, %d articulation, %d bridges, maxDepth %d%n",
                    seed, n, edges.size(), articulationCount(g, n), bridgeCount(g), maxDepth(g, n));
        }

        if (!failures.isEmpty()) {
            fail("Station topology found " + failures.size() + " violation(s):\n  "
                    + String.join("\n  ", failures));
        }
    }

    private static List<int[]> edgesOf(StationGraph g) {
        List<int[]> edges = new ArrayList<>();
        for (StationGraph.Corridor c : g.corridors()) {
            edges.add(new int[]{ c.roomA, c.roomB });
        }
        return edges;
    }

    /**
     * Is the graph connected over all present rooms, using all edges except
     * {@code skipEdge} and any edge touching {@code removedRoom}? Rooms equal to
     * {@code removedRoom} are excluded from the "present" set. An empty/singleton
     * present set is trivially connected.
     */
    private static boolean connected(int n, List<int[]> edges, int skipEdge, int removedRoom) {
        List<List<Integer>> adj = new ArrayList<>();
        for (int i = 0; i < n; i++) adj.add(new ArrayList<>());
        for (int i = 0; i < edges.size(); i++) {
            if (i == skipEdge) continue;
            int a = edges.get(i)[0], b = edges.get(i)[1];
            if (a == removedRoom || b == removedRoom) continue;
            adj.get(a).add(b);
            adj.get(b).add(a);
        }
        int start = -1, present = 0;
        for (int r = 0; r < n; r++) {
            if (r == removedRoom) continue;
            present++;
            if (start == -1) start = r;
        }
        if (present <= 1) return true;

        boolean[] seen = new boolean[n];
        Deque<Integer> queue = new ArrayDeque<>();
        seen[start] = true;
        queue.add(start);
        int reached = 1;
        while (!queue.isEmpty()) {
            int u = queue.poll();
            for (int v : adj.get(u)) {
                if (!seen[v]) {
                    seen[v] = true;
                    reached++;
                    queue.add(v);
                }
            }
        }
        return reached == present;
    }

    /** A room is an articulation point iff removing it leaves the rest disconnected (needs ≥ 2 other rooms to mean anything). */
    private static boolean removingRoomDisconnects(int n, List<int[]> edges, int room) {
        if (n - 1 < 2) return false;
        return !connected(n, edges, -1, room);
    }

    private static int articulationCount(StationGraph g, int n) {
        int c = 0;
        for (int r = 0; r < n; r++) if (g.isArticulation(r)) c++;
        return c;
    }

    private static int bridgeCount(StationGraph g) {
        int c = 0;
        for (int i = 0; i < g.corridorCount(); i++) if (g.isBridge(i)) c++;
        return c;
    }

    private static int maxDepth(StationGraph g, int n) {
        int m = 0;
        for (int r = 0; r < n; r++) m = Math.max(m, g.depthFromEntry(r));
        return m;
    }
}
