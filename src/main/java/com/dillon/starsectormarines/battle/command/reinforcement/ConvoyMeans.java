package com.dillon.starsectormarines.battle.command.reinforcement;

import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.FactionUnitRoster;
import com.dillon.starsectormarines.battle.vehicle.ClearanceComponents;
import com.dillon.starsectormarines.battle.vehicle.ConvoyPlanner;
import com.dillon.starsectormarines.battle.air.AirBody;
import com.dillon.starsectormarines.battle.vehicle.RouteSearchBudget;
import com.dillon.starsectormarines.battle.vehicle.TerrainCostField;
import com.dillon.starsectormarines.battle.vehicle.VehicleMission;
import com.dillon.starsectormarines.battle.vehicle.VehicleState;
import com.dillon.starsectormarines.battle.vehicle.VehicleClearance;
import com.dillon.starsectormarines.battle.vehicle.VehicleClearanceCache;
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
    /**
     * Grid searches one {@link #advance} spends on one route proof.
     *
     * <p>A cost-field A* over the 560x336 Conquest map costs about two and a
     * half milliseconds, so four of them is a tenth of the frame budget and
     * leaves room for the road-graph ranking the first step of a proof also
     * does. The whole proof is {@link RouteProofJob#SEARCH_BUDGET} searches, so
     * this also bounds how long a convoy can go on answering RETRYABLE: eight
     * ticks, and then it has either committed or fallen through.
     *
     * <p>It is deliberately not tuned to "one search per tick, whatever that
     * costs". A proof that takes half a second of wall clock to finish is a
     * reinforcement arriving noticeably late for no reason a player could name;
     * what was wrong was the whole enumeration landing in one frame, not the
     * searching itself.
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
    /**
     * Per-battle terrain cost field, baked lazily on first dispatch. Ground kinds
     * are effectively static (rubble appears only on wall breach); a slightly
     * stale macro route is fine — the rolling local planner handles live terrain.
     *
     * <p>Deliberately never invalidated, and
     * {@link com.dillon.starsectormarines.battle.nav.NavigationGrid}'s
     * changed-cell log ({@code tiled-navigation-derivations.md}) is not the
     * fact that would tell it to: {@link TerrainCostField#from} reads
     * {@link com.dillon.starsectormarines.battle.world.model.CellTopology}'s
     * {@code GroundKind} array, which the nav grid's own log knows nothing
     * about, so this field has nothing to catch up from there —
     * {@code CellTopology} keeps a change log of its own if that staleness is
     * ever worth closing.
     */
    private TerrainCostField costField;
    /**
     * Per-battle clearance mask and component labels for the one chassis this
     * means drives, rebuilt only when the grid's passability changes. See
     * {@link VehicleClearanceCache} for why the topology revision is the whole
     * of the invalidation.
     */
    private final VehicleClearanceCache clearanceCache = new VehicleClearanceCache(
            VehicleClearance.radiusForWidth(VehicleType.HEAVY_APC.visualWidthCells));
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
     * a time from. It reads the retained clearance mask rather than eroding
     * one: a probe asked of every request on every reinforcement tick cannot
     * afford a full-grid sweep, and the mask is only stale when the grid says
     * so. See {@link #clearanceFor}.
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
        List<RoadGraph.Node> perimeter = deployment.strictDefenderRearEntry()
                ? defenderRearPerimeter(axis, graph.perimeterNodes(), width, height)
                : defenderSidePerimeter(axis, graph.perimeterNodes(), width, height);
        if (perimeter.isEmpty()) return null;
        VehicleClearance clearance = clearanceFor(sim);
        for (RoadGraph.Node node : sortedByDistance(perimeter,
                deployment.hintX(), deployment.hintY())) {
            if (perimeterRouteCell(axis, clearance, node, width, height) != null) return node;
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
     * mask, the labels and every partial search were all proved against a map
     * that no longer exists.
     */
    @Override
    public boolean advance(float dt, BattleControl sim) {
        if (proofs.isEmpty()) return false;
        long revision = sim.getNavigationGridRevision();
        boolean finishedSomething = false;
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
            if (proof.job.state() != RouteProofJob.State.RUNNING) continue;
            if (proof.job.step(SEARCHES_PER_TICK) != RouteProofJob.State.RUNNING) {
                finishedSomething = true;
            }
        }
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
            // Standing a proof up is itself this tick's work, and on the first
            // dispatch of a battle it is the expensive part: the terrain cost
            // field and the clearance component labels are both a sweep of the
            // whole map. Measured, that tick came to 13ms before a single
            // search. Adding four searches on top of it is the same mistake the
            // job exists to fix, one tick smaller, so the first step waits for
            // the next tick.
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

    /**
     * Starts this delivery's route proof, or {@code null} when the map offers no
     * eligible perimeter to come on at.
     */
    private RouteProofJob startProof(BattleControl sim, DeliveryDeployment deployment,
                                     long gridRevision) {
        return RouteProofJob.start(graph, axis, deployment, sim.getGrid(), gridRevision,
                costFieldFor(sim), clearanceFor(sim),
                clearanceCache.components(sim.getGrid(), gridRevision),
                new LandingZoneScorer(sim.getGrid(), sim.getTopology()),
                activeConvoyDestinations(sim));
    }

    /** Lazily bakes (and caches) the per-battle terrain cost field from the map's ground kinds. */
    private TerrainCostField costFieldFor(BattleView sim) {
        if (costField == null) costField = TerrainCostField.from(sim.getTopology());
        return costField;
    }

    /**
     * The clearance snapshot for this battle's current passability.
     *
     * <p>Held across dispatches rather than eroded per call. It used to be
     * rebuilt every time because the map can close ground under a proved route
     * — an aircraft settling onto a road is the live case — but "the map might
     * have changed" is a question the grid answers exactly, and answering it by
     * sweeping 188,160 cells cost about twelve milliseconds a time — paid by
     * the dispatch, paid again by the labelling beside it, and paid again by
     * every feasibility probe that never dispatches. See
     * {@link VehicleClearanceCache}.
     */
    private VehicleClearance clearanceFor(BattleView sim) {
        return clearanceCache.clearance(sim.getGrid(),
                sim.getNavigationGridRevision());
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
