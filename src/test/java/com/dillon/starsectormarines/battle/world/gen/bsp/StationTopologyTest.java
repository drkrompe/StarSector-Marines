package com.dillon.starsectormarines.battle.world.gen.bsp;

import com.dillon.starsectormarines.battle.world.gen.MapResult;
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

    /**
     * Concentric station structure — the "besieged core" invariants on top of the
     * reused Tarjan/oracle machinery: the core is a single-entrance dead-end whose
     * gate is a real bridge, and depth-from-entry rises monotonically inward across
     * every radial gate (the assault gradient). The bridge oracle is re-run on the
     * concentric graph (same independent remove-and-flood as the BSP test).
     */
    @Test
    void concentricStationStructureInvariants() {
        BspCityGenerator gen = new BspCityGenerator();
        List<String> failures = new ArrayList<>();

        for (long seed : SEEDS) {
            gen.generateConcentricStation(W, H, seed);
            StationGraph g = gen.getLastStationGraph();
            if (g == null || !g.hasRings() || !g.hasRoles()) {
                failures.add("seed " + seed + ": concentric graph missing rings/roles");
                continue;
            }
            int n = g.roomCount();
            int core = g.coreRoom();
            List<int[]> edges = edgesOf(g);

            if (core < 0 || g.ringOf(core) != 0) {
                failures.add("seed " + seed + ": core room " + core + " ring=" + g.ringOf(core) + " (expected 0)");
            }
            // Besieged core: single entrance, and that entrance is a bridge.
            if (g.degree(core) != 1) {
                failures.add("seed " + seed + ": core degree " + g.degree(core) + " (expected 1 — single entrance)");
            }
            for (int i = 0; i < edges.size(); i++) {
                if ((edges.get(i)[0] == core || edges.get(i)[1] == core) && !g.isBridge(i)) {
                    failures.add("seed " + seed + ": core gate (corridor " + i + ") is not a bridge");
                }
            }
            // Bridge oracle on the concentric graph (independent of Tarjan).
            for (int i = 0; i < edges.size(); i++) {
                boolean oracle = !connected(n, edges, i, -1);
                if (g.isBridge(i) != oracle) {
                    failures.add("seed " + seed + ": corridor " + i + " bridge=" + g.isBridge(i) + " oracle=" + oracle);
                }
            }
            // Radial gates: depth rises inward (toward the lower ring index / core).
            for (int[] e : edges) {
                int ra = g.ringOf(e[0]), rb = g.ringOf(e[1]);
                if (Math.abs(ra - rb) != 1) continue;
                int outer = ra > rb ? e[0] : e[1];
                int inner = ra > rb ? e[1] : e[0];
                if (g.depthFromEntry(inner) < g.depthFromEntry(outer)) {
                    failures.add("seed " + seed + ": radial gate " + outer + "->" + inner
                            + " depth drops inward (" + g.depthFromEntry(outer) + " -> " + g.depthFromEntry(inner) + ")");
                }
            }

            System.out.printf("seed %d (concentric): %d rooms, maxRing %d, core depth %d, maxDepth %d, bridges %d%n",
                    seed, n, maxRing(g, n), g.depthFromEntry(core), maxDepth(g, n), bridgeCount(g));
        }

        if (!failures.isEmpty()) {
            fail("Concentric structure found " + failures.size() + " violation(s):\n  "
                    + String.join("\n  ", failures));
        }
    }

    /**
     * Diamond station structure — the cardinal-ports-converging-inward invariants:
     * every port is an isolated degree-1 entry whose spoke is a bridge, the outer
     * shell is all bridges (no on-loop rooms) while a connective ring loops, the
     * core is a besieged degree-1 bridge room, depth rises inward, and the 4 map
     * corners are dead (the diamond footprint). Bridge oracle re-run independently.
     */
    @Test
    void diamondStationStructureInvariants() {
        BspCityGenerator gen = new BspCityGenerator();
        List<String> failures = new ArrayList<>();

        for (long seed : SEEDS) {
            MapResult map = gen.generateDiamondStation(W, H, seed);
            StationGraph g = gen.getLastStationGraph();
            if (g == null || !g.hasRings() || !g.hasRoles() || g.ports().length == 0) {
                failures.add("seed " + seed + ": diamond graph missing rings/roles/ports");
                continue;
            }
            int n = g.roomCount();
            int core = g.coreRoom();
            List<int[]> edges = edgesOf(g);

            // Core + ports are besieged degree-1 entries whose corridors are bridges.
            checkDegreeOneBridge(g, edges, core, "core", seed, failures);
            for (int p : g.ports()) {
                if (g.ringOf(p) > 1) {   // an isolated outer port (ring 1 = connective)
                    checkDegreeOneBridge(g, edges, p, "port", seed, failures);
                }
            }

            // Outer-shell rooms (ring ≥ 2) are never on a loop; a connective ring does loop.
            boolean anyLoop = false;
            for (int r = 0; r < n; r++) {
                if (g.ringOf(r) >= 2 && g.isOnLoop(r)) {
                    failures.add("seed " + seed + ": outer room " + r + " (ring " + g.ringOf(r) + ") is on a loop");
                }
                anyLoop |= g.isOnLoop(r);
            }
            if (!anyLoop) failures.add("seed " + seed + ": no connective loop ring (everything is a bridge)");

            // Bridge oracle (independent of Tarjan).
            for (int i = 0; i < edges.size(); i++) {
                boolean oracle = !connected(n, edges, i, -1);
                if (g.isBridge(i) != oracle) {
                    failures.add("seed " + seed + ": corridor " + i + " bridge=" + g.isBridge(i) + " oracle=" + oracle);
                }
            }

            // Valid BFS layering from the entry port: every corridor spans ≤ 1 depth
            // step. (Depth is radial from the ONE entry port, not concentric — the
            // other 3 spokes are dead-end branches whose ports are the deep ends, so
            // "monotone inward" holds only on the entry spoke, not globally.)
            for (int[] e : edges) {
                if (Math.abs(g.depthFromEntry(e[0]) - g.depthFromEntry(e[1])) > 1) {
                    failures.add("seed " + seed + ": corridor " + e[0] + "-" + e[1] + " spans > 1 depth step");
                }
            }

            // Dead corners — the diamond footprint: the 4 map-corner regions are solid.
            int c = 6;   // inside the outer ring's corner region (hull margin + ~half a band)
            for (int[] xy : new int[][]{ {c, c}, {W - 1 - c, c}, {c, H - 1 - c}, {W - 1 - c, H - 1 - c} }) {
                if (map.grid.isWalkable(xy[0], xy[1])) {
                    failures.add("seed " + seed + ": map corner (" + xy[0] + "," + xy[1] + ") is walkable (not a dead corner)");
                }
            }

            System.out.printf("seed %d (diamond): %d rooms, %d ports, %d bridges, core depth %d, maxDepth %d%n",
                    seed, n, g.ports().length, bridgeCount(g), g.depthFromEntry(core), maxDepth(g, n));
        }

        if (!failures.isEmpty()) {
            fail("Diamond structure found " + failures.size() + " violation(s):\n  "
                    + String.join("\n  ", failures));
        }
    }

    /** Assert a room has exactly one corridor and that corridor is a bridge (a besieged degree-1 terminal). */
    private static void checkDegreeOneBridge(StationGraph g, List<int[]> edges, int room,
                                             String label, long seed, List<String> failures) {
        if (g.degree(room) != 1) {
            failures.add("seed " + seed + ": " + label + " " + room + " degree " + g.degree(room) + " (expected 1)");
        }
        for (int i = 0; i < edges.size(); i++) {
            if ((edges.get(i)[0] == room || edges.get(i)[1] == room) && !g.isBridge(i)) {
                failures.add("seed " + seed + ": " + label + " " + room + " corridor " + i + " is not a bridge");
            }
        }
    }

    private static int maxRing(StationGraph g, int n) {
        int m = 0;
        for (int r = 0; r < n; r++) m = Math.max(m, g.ringOf(r));
        return m;
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
