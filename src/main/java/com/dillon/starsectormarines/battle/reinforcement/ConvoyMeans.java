package com.dillon.starsectormarines.battle.reinforcement;

import com.dillon.starsectormarines.battle.air.AirBody;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.ground.ConvoyPlanner;
import com.dillon.starsectormarines.battle.ground.HybridAStarPlanner;
import com.dillon.starsectormarines.battle.ground.Vehicle;
import com.dillon.starsectormarines.battle.ground.VehicleType;
import com.dillon.starsectormarines.battle.mapgen.TraversalAxis;
import com.dillon.starsectormarines.battle.mapgen.road.RoadGraph;
import com.dillon.starsectormarines.battle.tactical.TacticalNode;
import com.fs.starfarer.api.Global;
import org.apache.log4j.Logger;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Convoy-vehicle delivery means. Spawns a {@link VehicleType#HEAVY_APC}
 * at a perimeter road-graph entry that's reachable to the request's rally
 * point, drives it in, deboards marines, and stays parked in overwatch
 * with its turret active. Routing sorts perimeter nodes by distance to
 * the rally, BFS-floods the reachable component from each, and picks
 * the entry whose component contains the best interior junction near
 * the rally. Falls back gracefully across disconnected components so a
 * stub perimeter doesn't kill the spawn.
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
    private final TraversalAxis axis;

    public ConvoyMeans(RoadGraph graph, TraversalAxis axis) {
        this.graph = graph;
        this.axis = axis;
    }

    @Override
    public boolean canFulfill(BattleSimulation sim, ReinforcementRequest req) {
        if (graph == null || graph.nodes().isEmpty()) return false;
        if (req.side != Faction.DEFENDER) return false;
        if (!req.hasRally()) return false;
        // Compound-as-supply gate: convoys are loaded out of the ARMORY.
        // Once every armory has flipped marine-held the trucks have nothing
        // to ferry — the dispatcher falls through to walk-in / shuttle, or
        // drops the request if every supply structure is captured. This is
        // the v3-quirk fix the design doc calls out: ARMORY captures
        // naturally retire convoy in priority order without explicit
        // re-ordering.
        if (!sim.getCompoundService().hasAliveCompound(
                TacticalNode.Kind.ARMORY, Faction.DEFENDER)) {
            return false;
        }
        return !graph.perimeterNodes().isEmpty();
    }

    @Override
    public void dispatch(BattleSimulation sim, ReinforcementRequest req) {
        int rx = req.rallyX;
        int ry = req.rallyY;
        int gw = sim.getGrid().getWidth();
        int gh = sim.getGrid().getHeight();

        List<RoadGraph.Node> perim = defenderSidePerimeter(graph.perimeterNodes(), gw, gh);
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
        boolean wasRefined = false;

        if (inboundCells[0].length >= 2) {
            int last = inboundCells[0].length - 1;
            float startFacing = AirBody.facingToward(
                    inboundCells[0][1] - inboundCells[0][0],
                    inboundCells[1][1] - inboundCells[1][0]);
            float goalFacing = AirBody.facingToward(
                    inboundCells[0][last] - inboundCells[0][last - 1],
                    inboundCells[1][last] - inboundCells[1][last - 1]);
            float[][] refined = HybridAStarPlanner.refine(
                    inboundCells[0], inboundCells[1], startFacing, goalFacing,
                    VehicleType.HEAVY_APC, sim.getGrid());
            if (refined != null) { inboundCells = refined; wasRefined = true; }
        }

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
        float[] inH = null;
        if (inboundCells.length > 2) {
            inH = new float[len + 1];
            inH[0] = AirBody.facingToward(inX[1] - inX[0], inY[1] - inY[0]);
            System.arraycopy(inboundCells[2], 0, inH, 1, len);
        }

        RoadGraph.Node exitNode = ConvoyPlanner.pickExitNode(graph, dest, entry);
        List<RoadGraph.Edge> outPath = ConvoyPlanner.planPath(graph, dest, exitNode);
        float[][] outCells;
        if (outPath != null && !outPath.isEmpty()) {
            outCells = ConvoyPlanner.expandToWaypoints(outPath, dest);
        } else {
            outCells = new float[][]{ new float[]{dest.cellX + 0.5f}, new float[]{dest.cellY + 0.5f} };
        }

        int inLast = inboundCells[0].length - 1;
        float lzX = inboundCells[0][inLast];
        float lzY = inboundCells[1][inLast];
        float lzFacing = AirBody.facingToward(
                inboundCells[0][inLast] - inboundCells[0][inLast - 1],
                inboundCells[1][inLast] - inboundCells[1][inLast - 1]);
        float distLzToDest = (float) Math.sqrt(
                (lzX - outCells[0][0]) * (lzX - outCells[0][0])
              + (lzY - outCells[1][0]) * (lzY - outCells[1][0]));
        if (distLzToDest > 0.5f) {
            float[] pX = new float[outCells[0].length + 1];
            float[] pY = new float[outCells[1].length + 1];
            pX[0] = lzX;
            pY[0] = lzY;
            System.arraycopy(outCells[0], 0, pX, 1, outCells[0].length);
            System.arraycopy(outCells[1], 0, pY, 1, outCells[1].length);
            outCells = new float[][] { pX, pY };
        }

        float exitOffX = exitNode.cellX + 0.5f;
        float exitOffY = exitNode.cellY + 0.5f;
        if (exitNode.cellY == 0)            exitOffY = -OFFMAP_PAD;
        else if (exitNode.cellY == gh - 1)  exitOffY = gh + OFFMAP_PAD;
        else if (exitNode.cellX == 0)       exitOffX = -OFFMAP_PAD;
        else if (exitNode.cellX == gw - 1)  exitOffX = gw + OFFMAP_PAD;
        if (outCells[0].length >= 2) {
            int outLast = outCells[0].length - 1;
            float exitFacing = AirBody.facingToward(
                    exitOffX - outCells[0][outLast], exitOffY - outCells[1][outLast]);
            outCells = ConvoyPlanner.refineWithFallback(
                    outCells[0], outCells[1], lzFacing, exitFacing,
                    VehicleType.HEAVY_APC, sim.getGrid());
        }
        int outLen = outCells[0].length;
        float[] outX = new float[outLen + 1];
        float[] outY = new float[outLen + 1];
        System.arraycopy(outCells[0], 0, outX, 0, outLen);
        System.arraycopy(outCells[1], 0, outY, 0, outLen);
        outX[outLen] = exitOffX;
        outY[outLen] = exitOffY;
        float[] outH = new float[outLen + 1];
        if (outCells.length > 2) {
            System.arraycopy(outCells[2], 0, outH, 0, outLen);
        }
        outH[outLen] = AirBody.facingToward(exitOffX - outX[outLen - 1], exitOffY - outY[outLen - 1]);

        Vehicle truck = new Vehicle(
                VehicleType.HEAVY_APC, Faction.DEFENDER,
                inX, inY, outX, outY,
                PENDING_SEC);
        truck.pathRefined = wasRefined;
        truck.inboundHeading = inH;
        truck.outboundHeading = outH;
        sim.addConvoyVehicle(truck);
        LOG.info("ConvoyMeans: dispatched HEAVY_APC entry=(" + entry.cellX + "," + entry.cellY
                + ") exit=(" + exitNode.cellX + "," + exitNode.cellY
                + ") rally=(" + rx + "," + ry + ") path=" + path.size() + "edges/" + inX.length + "wps");
    }

    /**
     * Filter perimeter nodes to the defender's half of the map. Excludes
     * the marine entry edge so convoys don't spawn behind the player's
     * beachhead. Keeps the two lateral edges (they're neutral flanks —
     * valid approach routes for a flanking convoy).
     */
    private List<RoadGraph.Node> defenderSidePerimeter(List<RoadGraph.Node> nodes, int gw, int gh) {
        List<RoadGraph.Node> out = new ArrayList<>(nodes.size());
        for (RoadGraph.Node n : nodes) {
            if (isMarineEntryEdge(n, gw, gh)) continue;
            out.add(n);
        }
        return out.isEmpty() ? nodes : out;
    }

    private boolean isMarineEntryEdge(RoadGraph.Node n, int gw, int gh) {
        if (axis == TraversalAxis.SOUTH_TO_NORTH) return n.cellY == 0;
        if (axis == TraversalAxis.WEST_TO_EAST)   return n.cellX == 0;
        return false;
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
