package com.dillon.starsectormarines.battle.command.reinforcement;

import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.profile.TickInnerProfile;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.FactionUnitRoster;
import com.dillon.starsectormarines.battle.air.AirBody;
import com.dillon.starsectormarines.battle.vehicle.ProgressiveVehicleField;
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

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

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
    /**
     * A perimeter graph node is not a valid full-body routing pose: an APC
     * centered one cell inside the edge still hangs off-map, so the
     * footprint-aware route validator correctly rejects it.  Route proof
     * therefore begins/ends at this fully in-bounds staging depth; dispatch
     * still prepends/appends the actual off-map point.
     */
    private static final int PERIMETER_STAGING_INSET = 2;
    private static final int APC_CLEARANCE_RADIUS = VehicleClearance.radiusForWidth(
            VehicleType.HEAVY_APC.visualWidthCells);
    /**
     * New grid searches the means spends across all active proofs in one
     * {@link #advance}. Node expansions have a separate shared tick ceiling.
     *
     * <p>The whole proof retains {@link RouteProofJob#SEARCH_BUDGET} attempts,
     * but one disconnected A* may span several ticks; attempts alone are not
     * an adequate per-tick work bound without the node-expansion ceiling.
     *
     * <p>Ready proofs are visited round-robin so one long failed search cannot
     * permanently monopolize the shared per-tick allowance.
     */
    static final int SEARCHES_PER_TICK = 4;

    /**
     * Sim-seconds a proof may go untouched by a dispatch before it is
     * abandoned.
     *
     * <p>A request that is still asking re-posts itself and is offered to this
     * means again on the next reinforcement cadence, so a proof nobody has
     * asked about for two cadences belongs to a request that was dropped as
     * undeliverable or served by another means. Nothing signals that directly —
     * the dispatcher has no reason to tell a means it lost — so the silence is
     * the signal.
     */
    private static final float ABANDON_AFTER_SECONDS =
            ReinforcementService.REINFORCEMENT_TICK_PERIOD * 2f;

    private final RoadGraph graph;
    private final TraversalAxis axis;
    private final GroundRosterProfile groundRoster;
    private final RiskLevel risk;
    private final DeliveryDeploymentPolicy deploymentPolicy;
    /** Diagnostic count of frozen route views captured by actual proof requests. */
    private int routeFieldCaptures;
    /**
     * Route proofs in flight, keyed by the request they belong to.
     *
     * <p>Identity, and deliberately: a RETRYABLE dispatch re-posts the very same
     * {@link ReinforcementRequest} object, so the request itself is the handle
     * that survives from one attempt to the next. Two requests that happen to
     * carry the same coordinates are two deliveries and must not share a proof.
     */
    private final Map<ReinforcementRequest, InFlightProof> proofs =
            new IdentityHashMap<>();
    private int nextProofIndex;
    /** Static road-gate candidates; footprint viability is checked against the live grid. */
    private int perimeterWidth = -1;
    private int perimeterHeight = -1;
    private List<RoadGraph.Node> rearPerimeter = List.of();
    private List<RoadGraph.Node> sidePerimeter = List.of();

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
     * a time from. Only each candidate gate's footprint is tested here; the
     * full clearance mask is needed by an actual route proof, not by a probe
     * asked of every request on every reinforcement tick.
     *
     * <p><b>A necessary condition, not the proof.</b> The drive itself is
     * still proven at commit, because proving it is a bounded enumeration of
     * grid searches against this probe's half of one millisecond — far too much
     * for something asked of every means on every request, and asked again by
     * the counterattack muster on its own cadence. The gap between the two is
     * now bounded rather than open: the proof spends at most
     * {@link RouteProofJob#SEARCH_BUDGET} searches, and spends them a few per
     * tick rather than all inside the one that asked. It was neither, and a
     * 560x336 Conquest
     * dispatch reached a thousand of them and stalled the game thread for five
     * seconds; the figure of "about seventy milliseconds" that used to stand
     * here was measured on a much smaller map and was out by fifty times. What
     * this probe closes is the failure that actually occurs: across twenty-two
     * measured route plans on the canonical fixtures, every single failure was
     * the entry, and none was the route or the destination.
     */
    private RoadGraph.Node entryNode(BattleView sim, DeliveryDeployment deployment) {
        int width = sim.getGrid().getWidth();
        int height = sim.getGrid().getHeight();
        List<RoadGraph.Node> perimeter = perimeterFor(sim,
                deployment.strictDefenderRearEntry());
        if (perimeter.isEmpty()) return null;
        for (RoadGraph.Node node : sortedByDistance(perimeter,
                deployment.hintX(), deployment.hintY())) {
            if (perimeterRouteCell(axis, sim.getGrid(), node, width, height) != null) {
                return node;
            }
        }
        return null;
    }

    /** This request's delivery terms, or the legacy ones on a battle with no commanding authority. */
    private DeliveryDeployment deploymentFor(ReinforcementRequest req) {
        if (deploymentPolicy == null) return DeliveryDeployment.legacy(req);
        DeliveryDeployment deployment = deploymentPolicy.deploymentFor(req);
        return deployment != null ? deployment : DeliveryDeployment.legacy(req);
    }

    /**
     * Steps every route proof this means has in flight, once per sim tick.
     *
     * <p>The proofs are what make a dispatch expensive, and nothing about them
     * needs to happen inside the tick that asked. The dispatcher's own cadence
     * is a second apart, which is far too coarse to advance a job on, so the
     * stepping is per tick and the dispatch merely reads what is finished.
     *
     * <p>A proof is dropped rather than finished when the request it belongs to
     * has stopped asking — {@link #ABANDON_AFTER_SECONDS} without a dispatch
     * touching it means the request was dropped as undeliverable or served by
     * another means — or when the grid's passability moved under it, since the
     * field and every partial search were all proved against a map
     * that no longer exists.
     */
    @Override
    public boolean advance(float dt, BattleControl sim) {
        if (proofs.isEmpty()) return false;
        long revision = sim.getNavigationGridRevision();
        boolean finishedSomething = false;
        List<InFlightProof> running = new ArrayList<>();
        Iterator<Map.Entry<ReinforcementRequest, InFlightProof>> it =
                proofs.entrySet().iterator();
        while (it.hasNext()) {
            InFlightProof proof = it.next().getValue();
            proof.untouchedSeconds += dt;
            if (proof.untouchedSeconds > ABANDON_AFTER_SECONDS
                    || proof.job.gridRevision() != revision) {
                it.remove();
                continue;
            }
            if (proof.job.state() == RouteProofJob.State.RUNNING) running.add(proof);
        }
        int searchesLeft = SEARCHES_PER_TICK;
        int expansionsLeft = RouteProofJob.EXPANSIONS_PER_TICK;
        int count = running.size();
        int startIndex = count == 0 ? 0 : nextProofIndex % count;
        for (int offset = 0; offset < count
                && searchesLeft > 0 && expansionsLeft > 0; offset++) {
            InFlightProof proof = running.get((startIndex + offset) % count);
            int searchesBefore = proof.job.budget().spent();
            int clearanceBefore = proof.job.clearanceEvaluations();
            int costBefore = proof.job.costEvaluations();
            TickInnerProfile profile = TickInnerProfile.currentIfBound();
            long started = profile != null ? System.nanoTime() : 0L;
            RouteProofJob.State state = proof.job.step(searchesLeft, expansionsLeft);
            if (profile != null) {
                profile.record(TickInnerProfile.Bucket.CONVOY_ROUTE_PROOF_STEP,
                        System.nanoTime() - started);
                profile.recordConvoyRouteWork(
                        proof.job.clearanceEvaluations() - clearanceBefore,
                        proof.job.costEvaluations() - costBefore,
                        proof.job.expandedNodesThisStep(),
                        proof.job.budget().spent() - searchesBefore);
            }
            searchesLeft -= proof.job.budget().spent() - searchesBefore;
            expansionsLeft -= proof.job.expandedNodesThisStep();
            if (state != RouteProofJob.State.RUNNING) {
                finishedSomething = true;
            }
        }
        if (count > 0) nextProofIndex = (startIndex + 1) % count;
        return finishedSomething;
    }

    @Override
    public ReinforcementDispatchResult dispatch(BattleControl sim,
                                                ReinforcementRequest req) {
        DeliveryDeployment deployment = deploymentFor(req);
        int rx = deployment.hintX();
        int ry = deployment.hintY();
        int gw = sim.getGrid().getWidth();
        int gh = sim.getGrid().getHeight();
        long revision = sim.getNavigationGridRevision();
        InFlightProof proof = proofs.get(req);
        if (proof != null && proof.job.gridRevision() != revision) {
            proofs.remove(req);
            proof = null;
        }
        if (proof == null) {
            RouteProofJob job = startProof(sim, deployment, revision);
            if (job == null) {
                LOG.warn("ConvoyMeans: no eligible "
                        + (deployment.strictDefenderRearEntry() ? "defender rear" : "perimeter")
                        + " entry for hint=(" + rx + "," + ry + ")");
                return ReinforcementDispatchResult.REJECTED;
            }
            proof = new InFlightProof(job);
            proofs.put(req, proof);
            // The raw routing snapshot is this tick's preparation. Its
            // clearance and cost cells remain unexamined until search steps on
            // later ticks, so the first step still waits for the next tick.
        }
        proof.untouchedSeconds = 0f;
        if (proof.job.state() == RouteProofJob.State.RUNNING) {
            return ReinforcementDispatchResult.RETRYABLE;
        }
        proofs.remove(req);
        RoutePlan route = proof.job.plan();
        if (route == null) {
            LOG.warn("ConvoyMeans: no complete HEAVY_APC route from "
                    + (deployment.strictDefenderRearEntry() ? "defender rear" : "eligible perimeter")
                    + " to hint=(" + rx + "," + ry + ") minForward="
                    + deployment.minimumDefenderForward()
                    + " searches=" + proof.job.budget().spent()
                    + "/" + proof.job.budget().total());
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
        // Retain the frozen lazy view for the recovery ladder; unvisited cells
        // must keep the topology this journey was proved against.
        mission.routeFields = route.fields();
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

    /**
     * Starts this delivery's route proof, or {@code null} when the map offers no
     * eligible perimeter to come on at.
     */
    private RouteProofJob startProof(BattleControl sim, DeliveryDeployment deployment,
                                     long gridRevision) {
        List<RoadGraph.Node> perimeter = perimeterFor(sim,
                deployment.strictDefenderRearEntry());
        if (perimeter.isEmpty()) return null;
        List<RoadGraph.Node> viable = new ArrayList<>();
        NavigationGrid grid = sim.getGrid();
        for (RoadGraph.Node node : perimeter) {
            if (perimeterRouteCell(axis, grid, node,
                    grid.getWidth(), grid.getHeight()) != null) {
                viable.add(node);
            }
        }
        if (viable.isEmpty()) return null;
        TickInnerProfile profile = TickInnerProfile.currentIfBound();
        long started = profile != null ? System.nanoTime() : 0L;
        ProgressiveVehicleField fields = ProgressiveVehicleField.capture(
                sim.getGrid(), sim.getTopology(), APC_CLEARANCE_RADIUS);
        if (profile != null) profile.record(
                TickInnerProfile.Bucket.CONVOY_PROGRESSIVE_SNAPSHOT,
                System.nanoTime() - started);
        routeFieldCaptures++;
        return RouteProofJob.start(graph, axis, deployment,
                viable, gridRevision, fields,
                new LandingZoneScorer(sim.getGrid(), sim.getTopology()),
                activeConvoyDestinations(sim));
    }

    /** Road-graph perimeter selection is map-static; only full-body fit changes. */
    private List<RoadGraph.Node> perimeterFor(BattleView sim, boolean strictRear) {
        int width = sim.getGrid().getWidth();
        int height = sim.getGrid().getHeight();
        if (width != perimeterWidth || height != perimeterHeight) {
            List<RoadGraph.Node> nodes = graph.perimeterNodes();
            rearPerimeter = List.copyOf(defenderRearPerimeter(axis, nodes, width, height));
            sidePerimeter = List.copyOf(defenderSidePerimeter(axis, nodes, width, height));
            perimeterWidth = width;
            perimeterHeight = height;
        }
        return strictRear ? rearPerimeter : sidePerimeter;
    }

    /**
     * Filter perimeter nodes to the defender's half of the map. Excludes
     * the marine entry edge so convoys don't spawn behind the player's
     * beachhead. Keeps the two lateral edges (they're neutral flanks —
     * valid approach routes for a flanking convoy).
     */
    static List<RoadGraph.Node> defenderSidePerimeter(TraversalAxis axis,
                                                      List<RoadGraph.Node> nodes,
                                                      int gw, int gh) {
        List<RoadGraph.Node> out = new ArrayList<>(nodes.size());
        for (RoadGraph.Node n : nodes) {
            if (isMarineEntryEdge(axis, n, gw, gh)) continue;
            out.add(n);
        }
        return out.isEmpty() ? nodes : out;
    }

    /** Strict Conquest source edge: defender rear only, with no lateral fallback. */
    static List<RoadGraph.Node> defenderRearPerimeter(TraversalAxis axis,
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
    static int[] perimeterRouteCell(TraversalAxis axis, VehicleClearance clearance,
                                    RoadGraph.Node node,
                                    int width, int height) {
        int[] cell = perimeterStagingCell(axis, node, width, height);
        return cell != null && clearance.isPassable(cell[0], cell[1]) ? cell : null;
    }

    static int[] perimeterRouteCell(TraversalAxis axis, ProgressiveVehicleField fields,
                                    RoadGraph.Node node, int width, int height) {
        int[] cell = perimeterStagingCell(axis, node, width, height);
        return cell != null && fields.isPassable(cell[0], cell[1]) ? cell : null;
    }

    /** The same perimeter pose check as the route proof, without a full mask. */
    static int[] perimeterRouteCell(TraversalAxis axis, NavigationGrid grid,
                                    RoadGraph.Node node, int width, int height) {
        int[] cell = perimeterStagingCell(axis, node, width, height);
        return cell != null && VehicleClearance.fitsAt(grid, cell[0], cell[1],
                APC_CLEARANCE_RADIUS) ? cell : null;
    }

    private static int[] perimeterStagingCell(TraversalAxis axis,
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
        return new int[]{x, y};
    }

    /** Captures of raw route inputs; an entrance probe never makes one. */
    int routeFieldCaptures() { return routeFieldCaptures; }

    private static boolean isMarineEntryEdge(TraversalAxis axis, RoadGraph.Node n, int gw, int gh) {
        if (axis == TraversalAxis.SOUTH_TO_NORTH) return n.cellY == 0;
        if (axis == TraversalAxis.WEST_TO_EAST)   return n.cellX == 0;
        return false;
    }

    /** Sort {@code nodes} by squared distance to ({@code x, y}), ascending. Defensive copy — input list is not mutated. */
    static List<RoadGraph.Node> sortedByDistance(List<RoadGraph.Node> nodes, int x, int y) {
        List<RoadGraph.Node> out = new ArrayList<>(nodes);
        out.sort((a, b) -> {
            int adx = a.cellX - x, ady = a.cellY - y;
            int bdx = b.cellX - x, bdy = b.cellY - y;
            return Integer.compare(adx*adx + ady*ady, bdx*bdx + bdy*bdy);
        });
        return out;
    }

    /** Whether a cell lies behind the deployment's minimum safe forward band on this battle's axis. */
    static boolean behindMinimum(TraversalAxis axis, int x, int y, int minimumForward) {
        if (minimumForward < 0) return true;
        int forward = axis == TraversalAxis.WEST_TO_EAST ? x : y;
        return forward >= minimumForward;
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

    /** One route proof and how long it has gone without a dispatch asking after it. */
    private static final class InFlightProof {
        final RouteProofJob job;
        float untouchedSeconds;

        InFlightProof(RouteProofJob job) { this.job = job; }
    }
}
