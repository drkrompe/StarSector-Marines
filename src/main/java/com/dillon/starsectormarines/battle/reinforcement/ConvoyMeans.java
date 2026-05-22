package com.dillon.starsectormarines.battle.reinforcement;

import com.dillon.starsectormarines.battle.BattleSimulation;
import com.dillon.starsectormarines.battle.Faction;
import com.dillon.starsectormarines.battle.ground.ConvoyPlanner;
import com.dillon.starsectormarines.battle.ground.Vehicle;
import com.dillon.starsectormarines.battle.ground.VehicleType;
import com.dillon.starsectormarines.battle.mapgen.road.RoadGraph;
import com.fs.starfarer.api.Global;
import org.apache.log4j.Logger;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Convoy-vehicle delivery means. Spawns a {@link VehicleType#MILITIA_TRUCK}
 * at a perimeter road-graph entry that's reachable to the request's rally
 * point, drives it in, and lets the truck deboard once near the rally
 * (the existing ground-system docking logic from
 * {@code roadmap/convoy/v1-polish.md} handles arrival).
 *
 * <p>V1 ports the routing from the now-retired {@code maybeSpawnDebugConvoy}
 * path: sort perimeter nodes by distance to the rally, BFS-flood the
 * reachable component from each, pick the entry whose component contains
 * the best interior junction near the rally. Falls back gracefully across
 * disconnected components so a stub perimeter doesn't kill the spawn.
 */
public final class ConvoyMeans implements ReinforcementMeans {

    private static final Logger LOG = Global.getLogger(ConvoyMeans.class);

    /**
     * Sim-seconds before the truck emerges from off-map. Matches the value
     * the debug spawner used so existing playtest pacing carries over.
     */
    private static final float PENDING_SEC = 6f;
    /** Cells the off-map staging waypoint sits beyond the perimeter — the truck visibly drives onto the map rather than popping in at the edge. */
    private static final float OFFMAP_PAD = 6f;

    private final RoadGraph graph;

    public ConvoyMeans(RoadGraph graph) {
        this.graph = graph;
    }

    @Override
    public boolean canFulfill(BattleSimulation sim, ReinforcementRequest req) {
        if (graph == null || graph.nodes().isEmpty()) return false;
        if (req.side != Faction.DEFENDER) return false;
        if (!req.hasRally()) return false;
        return !graph.perimeterNodes().isEmpty();
    }

    @Override
    public void dispatch(BattleSimulation sim, ReinforcementRequest req) {
        int rx = req.rallyX;
        int ry = req.rallyY;
        int gw = sim.getGrid().getWidth();
        int gh = sim.getGrid().getHeight();

        List<RoadGraph.Node> perim = graph.perimeterNodes();
        List<RoadGraph.Node> perimByDist = sortedByDistance(perim, rx, ry);

        RoadGraph.Node entry = null;
        RoadGraph.Node dest = null;
        for (RoadGraph.Node candidate : perimByDist) {
            Set<RoadGraph.Node> reachable = reachableFrom(candidate);
            RoadGraph.Node candDest = bestInteriorJunctionWithin(reachable, rx, ry);
            if (candDest != null && candDest != candidate) {
                entry = candidate;
                dest = candDest;
                break;
            }
        }
        if (entry == null) {
            LOG.warn("ConvoyMeans: no entry/dest pair reachable for rally=(" + rx + "," + ry
                    + ") — " + perimByDist.size() + " perimeter candidates tried");
            return;
        }

        List<RoadGraph.Edge> path = ConvoyPlanner.planPath(graph, entry, dest);
        if (path == null || path.isEmpty()) {
            LOG.warn("ConvoyMeans: planPath failed entry=(" + entry.cellX + "," + entry.cellY
                    + ")→dest=(" + dest.cellX + "," + dest.cellY + ")");
            return;
        }

        float[][] inboundCells = ConvoyPlanner.expandToWaypoints(path, entry);

        float offX = entry.cellX + 0.5f;
        float offY = entry.cellY + 0.5f;
        if (entry.cellY == 0)            offY = -OFFMAP_PAD;
        else if (entry.cellY == gh - 1)  offY = gh + OFFMAP_PAD;
        else if (entry.cellX == 0)       offX = -OFFMAP_PAD;
        else if (entry.cellX == gw - 1)  offX = gw + OFFMAP_PAD;

        int len = inboundCells[0].length;
        float[] inX = new float[len + 1];
        float[] inY = new float[len + 1];
        inX[0] = offX;
        inY[0] = offY;
        System.arraycopy(inboundCells[0], 0, inX, 1, len);
        System.arraycopy(inboundCells[1], 0, inY, 1, len);
        float[][] outboundCells = ConvoyPlanner.reverse(inX, inY);

        Vehicle truck = new Vehicle(
                VehicleType.MILITIA_TRUCK, Faction.DEFENDER,
                inX, inY, outboundCells[0], outboundCells[1],
                PENDING_SEC);
        sim.addConvoyVehicle(truck);
        LOG.info("ConvoyMeans: dispatched MILITIA_TRUCK entry=(" + entry.cellX + "," + entry.cellY
                + ") rally=(" + rx + "," + ry + ") path=" + path.size() + "edges/" + inX.length + "wps");
    }

    /** Sort {@code nodes} by squared distance to ({@code x, y}), ascending. Defensive copy — input list is not mutated. */
    private static List<RoadGraph.Node> sortedByDistance(List<RoadGraph.Node> nodes, int x, int y) {
        List<RoadGraph.Node> out = new ArrayList<>(nodes);
        out.sort((a, b) -> {
            int adx = a.cellX - x, ady = a.cellY - y;
            int bdx = b.cellX - x, bdy = b.cellY - y;
            return Integer.compare(adx*adx + ady*ady, bdx*bdx + bdy*bdy);
        });
        return out;
    }

    /** BFS flood from {@code seed} over edges — returns the seed's connected component as a Set. */
    private static Set<RoadGraph.Node> reachableFrom(RoadGraph.Node seed) {
        Set<RoadGraph.Node> seen = new HashSet<>();
        Deque<RoadGraph.Node> q = new ArrayDeque<>();
        q.add(seed);
        seen.add(seed);
        while (!q.isEmpty()) {
            RoadGraph.Node n = q.poll();
            for (RoadGraph.Edge e : n.edges()) {
                RoadGraph.Node nxt = e.otherEnd(n);
                if (seen.add(nxt)) q.add(nxt);
            }
        }
        return seen;
    }

    /**
     * Best interior junction within a reachable set, near ({@code cx, cy}).
     * Walks degree thresholds from 3 down to 2 — a degree-2 interior node
     * is a worse drop-off (forced turn-around at arrival) but still better
     * than a failed dispatch, especially when a stub component has only
     * chain nodes.
     */
    private static RoadGraph.Node bestInteriorJunctionWithin(Set<RoadGraph.Node> reachable, int cx, int cy) {
        for (int minDegree = 3; minDegree >= 2; minDegree--) {
            RoadGraph.Node best = null;
            int bestD2 = Integer.MAX_VALUE;
            for (RoadGraph.Node n : reachable) {
                if (n.perimeter) continue;
                if (n.degree() < minDegree) continue;
                int dx = n.cellX - cx;
                int dy = n.cellY - cy;
                int d2 = dx * dx + dy * dy;
                if (d2 < bestD2) { bestD2 = d2; best = n; }
            }
            if (best != null) return best;
        }
        return null;
    }
}
