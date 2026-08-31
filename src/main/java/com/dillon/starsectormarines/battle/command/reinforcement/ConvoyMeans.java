package com.dillon.starsectormarines.battle.command.reinforcement;

import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.FactionUnitRoster;
import com.dillon.starsectormarines.battle.vehicle.ConvoyPlanner;
import com.dillon.starsectormarines.battle.air.AirBody;
import com.dillon.starsectormarines.battle.vehicle.TerrainCostField;
import com.dillon.starsectormarines.battle.vehicle.VehicleMission;
import com.dillon.starsectormarines.battle.vehicle.VehicleState;
import com.dillon.starsectormarines.battle.vehicle.VehicleClearance;
import com.dillon.starsectormarines.battle.vehicle.VehicleController;
import com.dillon.starsectormarines.battle.vehicle.VehicleRoutePlanner;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.dillon.starsectormarines.battle.world.gen.TraversalAxis;
import com.dillon.starsectormarines.battle.world.gen.road.RoadGraph;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.setup.GroundRosterProfile;
import com.dillon.starsectormarines.battle.setup.InfantryLoadoutRolls;
import com.dillon.starsectormarines.ops.RiskLevel;
import com.fs.starfarer.api.Global;
import org.apache.log4j.Logger;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Convoy-vehicle delivery means. It proves a complete inbound/drop/outbound
 * journey before spawning a {@link VehicleType#HEAVY_APC}, then uses the
 * ordinary vehicle lifecycle to drive, deboard, and depart.
 * Perimeter entries and viable junctions are ranked deterministically and
 * tried until one has both turn-feasible route legs. An optional mission
 * policy may constrain entry, drop band, and delivered-squad ownership;
 * without one the legacy defender-side behavior is retained.
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
    /**
     * How much longer a drive is than the straight line it covers.
     *
     * <p>Roads bend round blocks and a truck takes the junctions that exist
     * rather than the ones it would like. Used only to compare this means
     * against the others, so it wants to be about right rather than exact.
     */
    private static final float ROAD_DETOUR = 1.4f;
    /** Minimum cell separation between a fresh dispatch's destination junction and any already-active convoy truck's LZ. Soft preference — route candidates degrade to overlap only after separated peers. */
    private static final int MIN_DEST_SEPARATION = 4;
    /** Max Chebyshev rings used to resolve an interior junction onto the vehicle-clearance mask. */
    private static final int SNAP_RADIUS = 8;
    /**
     * A perimeter graph node is not a valid full-body routing pose: an APC
     * centered one cell inside the edge still hangs off-map, so the
     * footprint-aware route validator correctly rejects it.  Route proof
     * therefore begins/ends at this fully in-bounds staging depth; dispatch
     * still prepends/appends the actual off-map point.
     */
    private static final int PERIMETER_STAGING_INSET = 2;

    private final RoadGraph graph;
    private final TraversalAxis axis;
    private final GroundRosterProfile groundRoster;
    private final RiskLevel risk;
    private final DeliveryDeploymentPolicy deploymentPolicy;
    /**
     * Per-battle terrain cost field, baked lazily on first dispatch. Ground kinds
     * are effectively static (rubble appears only on wall breach); a slightly
     * stale macro route is fine — the rolling local planner handles live terrain.
     */
    private TerrainCostField costField;
    public ConvoyMeans(RoadGraph graph, TraversalAxis axis) {
        this(graph, axis, null, RiskLevel.LOW, null);
    }

    public ConvoyMeans(RoadGraph graph, TraversalAxis axis,
                       GroundRosterProfile groundRoster, RiskLevel risk) {
        this(graph, axis, groundRoster, risk, null);
    }

    public ConvoyMeans(RoadGraph graph, TraversalAxis axis,
                       GroundRosterProfile groundRoster, RiskLevel risk,
                       DeliveryDeploymentPolicy deploymentPolicy) {
        this.graph = graph;
        this.axis = axis;
        this.groundRoster = groundRoster;
        this.risk = risk != null ? risk : RiskLevel.LOW;
        this.deploymentPolicy = deploymentPolicy;
    }

    @Override
    public boolean canFulfill(BattleView sim, ReinforcementRequest req) {
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
        // And somewhere a truck can actually come on at. This used to ask only
        // whether the road graph had perimeter nodes at all, which is a
        // question about the map rather than about the delivery: a graph gate
        // one cell inside the edge is not a valid pose for a full APC body, so
        // a map can be covered in perimeter nodes and admit no vehicle
        // anywhere. Measured on a production Conquest fixture, every one of
        // sixteen convoy dispatches failed for exactly that and every one of
        // them was promised first — the probe said yes, the commit proved
        // otherwise, and the request fell through having burned the attempt.
        return entryNode(sim, deploymentFor(req)) != null;
    }

    /**
     * How long a truck takes to reach the drop: the drive in from the nearest
     * eligible perimeter entry, plus the staging delay before it appears.
     *
     * <p>Estimated off a straight line rather than the real route, which is not
     * a corner cut but the only affordable answer — proving a route is the
     * expensive half of {@link #dispatch} and this runs on every request for
     * every means. The straight line is scaled by {@link #ROAD_DETOUR} because
     * a drive through a city never is one.
     */
    @Override
    public float arrivalSeconds(BattleView sim, ReinforcementRequest req) {
        DeliveryDeployment deployment = deploymentFor(req);
        RoadGraph.Node entry = entryNode(sim, deployment);
        if (entry == null) return Float.MAX_VALUE;
        float dx = entry.cellX - deployment.hintX();
        float dy = entry.cellY - deployment.hintY();
        float drive = (float) Math.sqrt(dx * dx + dy * dy) * ROAD_DETOUR;
        return PENDING_SEC + drive / VehicleType.HEAVY_APC.maxSpeed;
    }

    /**
     * The perimeter gate a truck would come on at for this delivery — the one
     * nearest the drop that can actually take a full APC body — or null when
     * no eligible gate can.
     *
     * <p>Shared by the feasibility probe and the arrival estimate so the entry
     * that decides whether this means can deliver is the same entry it quotes
     * a time from. Rebuilds the clearance mask per call for the reason
     * {@link #clearanceFor} gives: wrecks close cells during a battle, and a
     * retained mask would keep promising an entry that a burnt-out truck is
     * now sitting in.
     *
     * <p><b>A necessary condition, not the proof.</b> The drive itself is
     * still proven at commit, because proving it costs about seventy
     * milliseconds against this probe's half of one — far too much for
     * something asked of every means on every request, and asked again by the
     * counterattack muster on its own cadence. What this closes is the failure
     * that actually occurs: across twenty-two measured route plans on the
     * canonical fixtures, every single failure was the entry, and none was the
     * route or the destination.
     */
    private RoadGraph.Node entryNode(BattleView sim, DeliveryDeployment deployment) {
        int width = sim.getGrid().getWidth();
        int height = sim.getGrid().getHeight();
        List<RoadGraph.Node> perimeter = deployment.strictDefenderRearEntry()
                ? defenderRearPerimeter(graph.perimeterNodes(), width, height)
                : defenderSidePerimeter(graph.perimeterNodes(), width, height);
        if (perimeter.isEmpty()) return null;
        VehicleClearance clearance = clearanceFor(sim,
                VehicleClearance.radiusForWidth(VehicleType.HEAVY_APC.visualWidthCells));
        for (RoadGraph.Node node : sortedByDistance(perimeter,
                deployment.hintX(), deployment.hintY())) {
            if (perimeterRouteCell(clearance, node, width, height) != null) return node;
        }
        return null;
    }

    /** This request's delivery terms, or the legacy ones on a battle with no commanding authority. */
    private DeliveryDeployment deploymentFor(ReinforcementRequest req) {
        if (deploymentPolicy == null) return DeliveryDeployment.legacy(req);
        DeliveryDeployment deployment = deploymentPolicy.deploymentFor(req);
        return deployment != null ? deployment : DeliveryDeployment.legacy(req);
    }

    @Override
    public ReinforcementDispatchResult dispatch(BattleControl sim,
                                                ReinforcementRequest req) {
        DeliveryDeployment deployment = deploymentFor(req);
        int rx = deployment.hintX();
        int ry = deployment.hintY();
        int gw = sim.getGrid().getWidth();
        int gh = sim.getGrid().getHeight();
        RoutePlan route = routePlan(sim, deployment, rx, ry);
        if (route == null) {
            LOG.warn("ConvoyMeans: no complete HEAVY_APC route from "
                    + (deployment.strictDefenderRearEntry() ? "defender rear" : "eligible perimeter")
                    + " to hint=(" + rx + "," + ry + ") minForward="
                    + deployment.minimumDefenderForward());
            return ReinforcementDispatchResult.REJECTED;
        }
        RoadGraph.Node entry = route.entry();
        RoadGraph.Node dest = route.destination();
        RoadGraph.Node exitNode = route.exit();
        float[][] inboundCells = route.inbound();
        float[][] outCells = route.outbound();

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

        int inLast = inboundCells[0].length - 1;
        float lzX = inboundCells[0][inLast];
        float lzY = inboundCells[1][inLast];
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
        int outLen = outCells[0].length;
        float[] outX = new float[outLen + 1];
        float[] outY = new float[outLen + 1];
        System.arraycopy(outCells[0], 0, outX, 0, outLen);
        System.arraycopy(outCells[1], 0, outY, 0, outLen);
        outX[outLen] = exitOffX;
        outY[outLen] = exitOffY;

        VehicleMission mission = new VehicleMission(
                inX, inY, outX, outY,
                PENDING_SEC, VehicleType.HEAVY_APC.capacity);
        mission.commandClaim = deployment.squadClaim();
        // Stash the routing inputs so the recovery ladder can re-route mid-drive.
        mission.routeCostField = route.cost();
        mission.routeClearance = route.clearance();
        // Objective assignment (progressive-reinforcement slice 4): resolve the
        // request's objective to a tactical node now, at dispatch time, so the
        // deboarded squad is assigned the moment it deboards rather than only
        // once it physically walks to the position — see ObjectiveNodes.
        mission.assignNode = ObjectiveNodes.resolve(sim.getTacticalMap(), req);
        if (mission.assignNode == null && req.hasObjective()
                && sim.getZoneGraph() != null) {
            mission.assignZoneId = sim.getZoneGraph().zoneIdAt(
                    req.objectiveX, req.objectiveY);
        }
        mission.commandOwnsObjective = deployment.commandOwnsObjective();
        GroundRosterProfile effectiveRoster = groundRoster != null
                ? groundRoster : sim.getGroundRoster();
        mission.deboardUnitType = effectiveRoster != null
                ? effectiveRoster.unitType(GroundRosterProfile.ForceTier.BULK)
                : FactionUnitRoster.forFaction(req.side).infantry();
        if (effectiveRoster != null) {
            mission.marineLoadout = InfantryLoadoutRolls.defenderSquad(
                    VehicleType.HEAVY_APC.capacity, effectiveRoster,
                    GroundRosterProfile.ForceTier.BULK, risk, sim.random());
        } else {
            mission.marineLoadout = InfantryLoadoutRolls.defenderSquad(
                    VehicleType.HEAVY_APC.capacity, mission.deboardUnitType,
                    risk, sim.random());
        }
        sim.addConvoyVehicle(VehicleType.HEAVY_APC, Faction.DEFENDER, mission);
        LOG.debug("ConvoyMeans: dispatched HEAVY_APC entry=(" + entry.cellX + "," + entry.cellY
                + ") exit=(" + exitNode.cellX + "," + exitNode.cellY
                + ") drop=(" + (int) lzX + "," + (int) lzY + ") hint=("
                + rx + "," + ry + ") minForward="
                + deployment.minimumDefenderForward() + " wps="
                + inX.length + "in/" + outX.length + "out");
        return ReinforcementDispatchResult.COMMITTED;
    }

    private record RoutePlan(RoadGraph.Node entry,
                             RoadGraph.Node destination,
                             RoadGraph.Node exit,
                             float[][] inbound,
                             float[][] outbound,
                             TerrainCostField cost,
                             VehicleClearance clearance) { }

    /** Proves both travel legs before a world actor is created. */
    private RoutePlan routePlan(BattleControl sim, DeliveryDeployment deployment,
                                int hintX, int hintY) {
        int width = sim.getGrid().getWidth();
        int height = sim.getGrid().getHeight();
        List<RoadGraph.Node> perimeter = deployment.strictDefenderRearEntry()
                ? defenderRearPerimeter(graph.perimeterNodes(), width, height)
                : defenderSidePerimeter(graph.perimeterNodes(), width, height);
        if (perimeter.isEmpty()) return null;

        List<RoadGraph.Node> entries = sortedByDistance(perimeter, hintX, hintY);
        List<int[]> reserved = activeConvoyDestinations(sim);
        LandingZoneScorer scorer = new LandingZoneScorer(
                sim.getGrid(), sim.getTopology());
        int radius = VehicleClearance.radiusForWidth(
                VehicleType.HEAVY_APC.visualWidthCells);
        TerrainCostField cost = costFieldFor(sim);
        VehicleClearance clearance = clearanceFor(sim, radius);

        for (RoadGraph.Node entry : entries) {
            int[] entryCell = perimeterRouteCell(clearance, entry,
                    width, height);
            if (entryCell == null) continue;
            List<RoadGraph.Node> destinations = interiorJunctionsWithin(
                    scorer, reachableFrom(entry), hintX, hintY, reserved,
                    deployment.minimumDefenderForward());
            for (RoadGraph.Node destination : destinations) {
                int[] destinationCell = VehicleRoutePlanner.snapToMask(clearance,
                        destination.cellX, destination.cellY, SNAP_RADIUS);
                if (destinationCell == null
                        || entryCell[0] == destinationCell[0]
                        && entryCell[1] == destinationCell[1]
                        || !scorer.isViable(destinationCell[0], destinationCell[1])
                        || !behindMinimum(destinationCell[0], destinationCell[1],
                        deployment.minimumDefenderForward())) {
                    continue;
                }
                float[][] inbound = VehicleRoutePlanner.routeDrivable(
                        entryCell[0], entryCell[1],
                        destinationCell[0], destinationCell[1],
                        sim.getGrid(), cost, clearance,
                        VehicleType.HEAVY_APC);
                if (inbound == null) continue;

                List<RoadGraph.Node> exits = deployment.strictDefenderRearEntry()
                        ? sortedByDistance(perimeter,
                        destination.cellX, destination.cellY)
                        : List.of(ConvoyPlanner.pickExitNode(
                        graph, destination, entry));
                for (RoadGraph.Node exit : exits) {
                    int[] exitCell = perimeterRouteCell(clearance, exit,
                            width, height);
                    if (exitCell == null) continue;
                    float[][] outbound = VehicleRoutePlanner.routeDrivable(
                            destinationCell[0], destinationCell[1],
                            exitCell[0], exitCell[1],
                            sim.getGrid(), cost, clearance,
                            VehicleType.HEAVY_APC);
                    if (outbound == null) continue;
                    // The one bend on neither polyline: the turn from the way
                    // the truck arrives to the way it must leave. An LZ whose
                    // entry and exit disagree by more than the chassis can turn
                    // in the room available delivers its marines and then
                    // strands the vehicle for the rest of the battle.
                    if (!canLeaveTheWayItArrived(sim, inbound, outbound)) continue;
                    return new RoutePlan(entry, destination, exit,
                            inbound, outbound, cost, clearance);
                }
            }
        }
        return null;
    }

    /**
     * Whether a truck arriving down {@code inbound} can point itself along
     * {@code outbound} at the drop point. Both are {@code [xs][ys]} polylines
     * meeting at the LZ.
     */
    private static boolean canLeaveTheWayItArrived(BattleView sim,
                                                   float[][] inbound, float[][] outbound) {
        int in = inbound[0].length;
        if (in < 2 || outbound[0].length < 2) return true;
        float lzX = inbound[0][in - 1];
        float lzY = inbound[1][in - 1];
        float approach = AirBody.facingToward(lzX - inbound[0][in - 2], lzY - inbound[1][in - 2]);
        float depart = AirBody.facingToward(outbound[0][1] - outbound[0][0],
                outbound[1][1] - outbound[1][0]);
        return VehicleController.canReverseDirectionAt(sim.getGrid(),
                VehicleType.HEAVY_APC, lzX, lzY, approach, depart);
    }

    /** Lazily bakes (and caches) the per-battle terrain cost field from the map's ground kinds. */
    private TerrainCostField costFieldFor(BattleView sim) {
        if (costField == null) costField = TerrainCostField.from(sim.getTopology());
        return costField;
    }

    /**
     * Builds a fresh clearance snapshot for each dispatch. Wrecks can close
     * cells during the battle, so retaining the original mask would let later
     * reinforcements prove routes through destroyed vehicles.
     */
    private VehicleClearance clearanceFor(BattleView sim, int radius) {
        return VehicleClearance.erode(sim.getGrid(), radius);
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

    /** Strict Conquest source edge: defender rear only, with no lateral fallback. */
    private List<RoadGraph.Node> defenderRearPerimeter(
            List<RoadGraph.Node> nodes, int width, int height) {
        List<RoadGraph.Node> out = new ArrayList<>();
        for (RoadGraph.Node node : nodes) {
            if ((axis == TraversalAxis.SOUTH_TO_NORTH
                    && node.cellY == height - 1)
                    || (axis == TraversalAxis.WEST_TO_EAST
                    && node.cellX == width - 1)) {
                out.add(node);
            }
        }
        return out;
    }

    /** Pull a graph-edge endpoint inward until the complete APC pose fits. */
    private int[] perimeterRouteCell(VehicleClearance clearance,
                                     RoadGraph.Node node,
                                     int width, int height) {
        int inwardX = 0;
        int inwardY = 0;
        if (axis == TraversalAxis.SOUTH_TO_NORTH && node.cellY == height - 1) {
            inwardY = -1;
        } else if (axis == TraversalAxis.WEST_TO_EAST && node.cellX == width - 1) {
            inwardX = -1;
        } else if (node.cellY == 0) {
            inwardY = 1;
        } else if (node.cellY == height - 1) {
            inwardY = -1;
        } else if (node.cellX == 0) {
            inwardX = 1;
        } else if (node.cellX == width - 1) {
            inwardX = -1;
        } else {
            return null;
        }
        // Keep the graph gate's lateral coordinate. A sideways snap would
        // leave the coarse off-map tail free to cut diagonally through a
        // blocker before full-body validation begins.
        int x = node.cellX + inwardX * PERIMETER_STAGING_INSET;
        int y = node.cellY + inwardY * PERIMETER_STAGING_INSET;
        return clearance.isPassable(x, y) ? new int[]{x, y} : null;
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

    /** Ranked viable drop junctions; route proof, not graph proximity, makes the commitment. */
    private List<RoadGraph.Node> interiorJunctionsWithin(
            LandingZoneScorer scorer, Set<RoadGraph.Node> reachable,
            int hintX, int hintY, List<int[]> reserved,
            int minimumForward) {
        List<RoadGraph.Node> candidates = new ArrayList<>();
        for (RoadGraph.Node node : reachable) {
            if (node.perimeter || node.degree() < 2
                    || !scorer.isViable(node.cellX, node.cellY)
                    || !behindMinimum(node.cellX, node.cellY, minimumForward)) {
                continue;
            }
            candidates.add(node);
        }
        candidates.sort(Comparator
                .comparingInt((RoadGraph.Node node) -> node.degree() >= 3 ? 0 : 1)
                .thenComparingInt(node -> nearAnyReserved(
                        node.cellX, node.cellY, reserved) ? 1 : 0)
                .thenComparingInt(node -> distanceSquared(
                        node.cellX, node.cellY, hintX, hintY))
                .thenComparingInt(node -> node.cellX)
                .thenComparingInt(node -> node.cellY));
        return candidates;
    }

    private boolean behindMinimum(int x, int y, int minimumForward) {
        if (minimumForward < 0) return true;
        int forward = axis == TraversalAxis.WEST_TO_EAST ? x : y;
        return forward >= minimumForward;
    }

    private static int distanceSquared(int ax, int ay, int bx, int by) {
        int dx = ax - bx;
        int dy = ay - by;
        return dx * dx + dy * dy;
    }

    /** {@code (lzCellX, lzCellY)} of every convoy vehicle that's still inbound or landed. DEPARTING / GONE trucks aren't holding the cell any more, so they're excluded. */
    private static List<int[]> activeConvoyDestinations(BattleView sim) {
        List<int[]> out = new ArrayList<>();
        for (long id : sim.getConvoyVehicleIds()) {
            VehicleMission m = sim.convoyMission(id);
            if (m == null || m.state == VehicleState.DEPARTING || m.state == VehicleState.GONE) continue;
            out.add(new int[]{(int) m.lzX, (int) m.lzY});
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
