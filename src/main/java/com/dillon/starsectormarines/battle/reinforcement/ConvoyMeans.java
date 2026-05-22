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
    /** Minimum cell separation between a fresh dispatch's destination junction and any already-active convoy truck's LZ. Soft preference — exhausted before degrading to no-separation (see {@link #bestInteriorJunctionWithin}) so a clogged rally still resolves rather than failing. */
    private static final int MIN_DEST_SEPARATION = 4;

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
        List<int[]> reservedLz = activeConvoyDestinations(sim);

        RoadGraph.Node entry = null;
        RoadGraph.Node dest = null;
        for (RoadGraph.Node candidate : perimByDist) {
            Set<RoadGraph.Node> reachable = reachableFrom(candidate);
            RoadGraph.Node candDest = bestInteriorJunctionWithin(reachable, rx, ry, reservedLz);
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
     * Two priority axes: junction quality (degree ≥ 3 preferred, ≥ 2
     * tolerated) and separation from already-active convoy destinations
     * in {@code reserved}. Degree quality wins outright — a separated
     * degree-2 junction loses to an overlapping degree-3 — because a
     * degree-2 node forces the truck to back out the way it came at
     * arrival. Within each degree tier, separation is preferred but
     * not required: an overlapping high-quality junction beats no
     * junction at all.
     *
     * <p>{@code reserved} is the list of {@code (lzCellX, lzCellY)} for
     * every currently in-flight or landed convoy truck. Empty list →
     * separation never kicks in, behaviour matches the pre-separation
     * implementation.
     */
    private static RoadGraph.Node bestInteriorJunctionWithin(Set<RoadGraph.Node> reachable,
                                                             int cx, int cy,
                                                             List<int[]> reserved) {
        for (int minDegree = 3; minDegree >= 2; minDegree--) {
            for (boolean useSeparation : new boolean[]{true, false}) {
                RoadGraph.Node best = null;
                int bestD2 = Integer.MAX_VALUE;
                for (RoadGraph.Node n : reachable) {
                    if (n.perimeter) continue;
                    if (n.degree() < minDegree) continue;
                    if (useSeparation && nearAnyReserved(n.cellX, n.cellY, reserved)) continue;
                    int dx = n.cellX - cx;
                    int dy = n.cellY - cy;
                    int d2 = dx * dx + dy * dy;
                    if (d2 < bestD2) { bestD2 = d2; best = n; }
                }
                if (best != null) return best;
            }
        }
        return null;
    }

    /** {@code (lzCellX, lzCellY)} of every convoy vehicle that's still inbound or landed. DEPARTING / GONE trucks aren't holding the cell any more, so they're excluded. */
    private static List<int[]> activeConvoyDestinations(BattleSimulation sim) {
        List<int[]> out = new ArrayList<>();
        for (Vehicle v : sim.getConvoyVehicles()) {
            if (v.state == Vehicle.State.DEPARTING || v.state == Vehicle.State.GONE) continue;
            out.add(new int[]{(int) v.lzX, (int) v.lzY});
        }
        return out;
    }

    /** True iff {@code (x, y)} is within {@link #MIN_DEST_SEPARATION} cells of any reserved point. Squared-distance comparison so no sqrt. */
    private static boolean nearAnyReserved(int x, int y, List<int[]> reserved) {
        if (reserved.isEmpty()) return false;
        int sepSq = MIN_DEST_SEPARATION * MIN_DEST_SEPARATION;
        for (int[] r : reserved) {
            int dx = x - r[0];
            int dy = y - r[1];
            if (dx * dx + dy * dy < sepSq) return true;
        }
        return false;
    }
}
