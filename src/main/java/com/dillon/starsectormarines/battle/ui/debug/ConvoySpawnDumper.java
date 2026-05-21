package com.dillon.starsectormarines.battle.ui.debug;

import com.dillon.starsectormarines.StarsectorMarinesModPlugin;
import com.dillon.starsectormarines.battle.mapgen.road.RoadGraph;
import com.fs.starfarer.api.Global;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/**
 * Diagnostic dumper for convoy spawn failures. Writes a single JSON file to
 * {@code saves/common/starsector_marines/debug/convoy_spawn_fail.json},
 * overwriting on every call — by design: the user wanted a "what went wrong
 * the LAST time" snapshot, not a growing log.
 *
 * <p>Dump includes the failure reason, road-graph stats, a
 * connected-component analysis (the load-bearing signal for the
 * disconnected-graph failure mode — entry and dest in different components
 * means BFS can never connect them), and the candidate entry / destination
 * nodes (when known).
 *
 * <p>Uses the same SettingsAPI {@code writeJSONToCommon} channel as
 * {@link SquadStateDumper} — {@link java.nio.file} / {@link java.io.File}
 * are blocked by the Starsector script sandbox.
 */
public final class ConvoySpawnDumper {

    private static final Logger LOG = Logger.getLogger(ConvoySpawnDumper.class);
    private static final int SCHEMA_VERSION = 1;
    private static final String PATH =
            StarsectorMarinesModPlugin.MOD_ID + "/debug/convoy_spawn_fail.json";

    private ConvoySpawnDumper() {}

    /**
     * Write the failure diagnostic. Any of {@code graph}, {@code entry},
     * {@code dest} may be {@code null} — the dump omits the corresponding
     * sections. Exceptions are caught and logged (a diagnostic that throws
     * is worse than no diagnostic).
     */
    public static void dump(String reason,
                            RoadGraph graph,
                            RoadGraph.Node entry,
                            RoadGraph.Node dest,
                            int gridW, int gridH,
                            int defenderSpawnX, int defenderSpawnY) {
        try {
            JSONObject root = new JSONObject();
            root.put("schemaVersion", SCHEMA_VERSION);
            root.put("reason", reason);
            root.put("gridW", gridW);
            root.put("gridH", gridH);
            root.put("defenderSpawnX", defenderSpawnX);
            root.put("defenderSpawnY", defenderSpawnY);

            if (graph == null) {
                root.put("roadGraph", JSONObject.NULL);
            } else {
                root.put("roadGraph", buildGraphJson(graph));
            }
            root.put("entry", entry == null ? JSONObject.NULL : nodeJson(entry));
            root.put("dest",  dest  == null ? JSONObject.NULL : nodeJson(dest));

            if (graph != null && entry != null) {
                root.put("entryComponentId", componentIdOf(graph, entry));
            }
            if (graph != null && dest != null) {
                root.put("destComponentId", componentIdOf(graph, dest));
            }

            Global.getSettings().writeJSONToCommon(PATH, root, true);
            LOG.info("ConvoySpawnDumper: wrote saves/common/" + PATH + " (reason=" + reason + ")");
        } catch (Exception ex) {
            LOG.warn("ConvoySpawnDumper: dump failed for reason=" + reason, ex);
        }
    }

    private static JSONObject buildGraphJson(RoadGraph graph) throws Exception {
        JSONObject o = new JSONObject();
        o.put("nodeCount", graph.nodes().size());
        o.put("edgeCount", graph.edges().size());
        o.put("perimeterNodeCount", graph.perimeterNodes().size());

        // Components — the key diagnostic for the disconnected-graph case.
        // Flood-label every node, then group node IDs by component id so the
        // user can see at a glance "entry is in component 0 of size 3,
        // dest is in component 1 of size 12".
        Map<RoadGraph.Node, Integer> compId = labelComponents(graph);
        int compCount = 0;
        for (Integer id : compId.values()) compCount = Math.max(compCount, id + 1);
        JSONArray components = new JSONArray();
        for (int c = 0; c < compCount; c++) {
            JSONArray nodeList = new JSONArray();
            int perimCount = 0;
            for (Map.Entry<RoadGraph.Node, Integer> e : compId.entrySet()) {
                if (e.getValue() != c) continue;
                RoadGraph.Node n = e.getKey();
                JSONObject no = new JSONObject();
                no.put("id", n.id);
                no.put("x", n.cellX);
                no.put("y", n.cellY);
                no.put("perim", n.perimeter);
                no.put("degree", n.degree());
                nodeList.put(no);
                if (n.perimeter) perimCount++;
            }
            JSONObject cj = new JSONObject();
            cj.put("id", c);
            cj.put("nodeCount", nodeList.length());
            cj.put("perimeterCount", perimCount);
            cj.put("nodes", nodeList);
            components.put(cj);
        }
        o.put("componentCount", compCount);
        o.put("components", components);
        return o;
    }

    private static JSONObject nodeJson(RoadGraph.Node n) throws Exception {
        JSONObject o = new JSONObject();
        o.put("id", n.id);
        o.put("x", n.cellX);
        o.put("y", n.cellY);
        o.put("perim", n.perimeter);
        o.put("degree", n.degree());
        return o;
    }

    private static int componentIdOf(RoadGraph graph, RoadGraph.Node n) {
        Map<RoadGraph.Node, Integer> compId = labelComponents(graph);
        Integer id = compId.get(n);
        return id == null ? -1 : id;
    }

    private static Map<RoadGraph.Node, Integer> labelComponents(RoadGraph graph) {
        Map<RoadGraph.Node, Integer> compId = new HashMap<>();
        int next = 0;
        for (RoadGraph.Node seed : graph.nodes()) {
            if (compId.containsKey(seed)) continue;
            int id = next++;
            Deque<RoadGraph.Node> q = new ArrayDeque<>();
            q.add(seed);
            compId.put(seed, id);
            while (!q.isEmpty()) {
                RoadGraph.Node cur = q.poll();
                for (RoadGraph.Edge e : cur.edges()) {
                    RoadGraph.Node nxt = e.otherEnd(cur);
                    if (compId.containsKey(nxt)) continue;
                    compId.put(nxt, id);
                    q.add(nxt);
                }
            }
        }
        return compId;
    }
}
